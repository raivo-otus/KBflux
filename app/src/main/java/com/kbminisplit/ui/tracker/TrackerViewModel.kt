package com.kbminisplit.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.InProgressRepository
import com.kbminisplit.data.repository.InProgressSnapshot
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.model.Prescription
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import com.kbminisplit.domain.progression.KbBumpSnooze
import com.kbminisplit.domain.progression.movementOrder
import com.kbminisplit.domain.progression.nextSplit
import com.kbminisplit.domain.progression.getPrescription
import com.kbminisplit.domain.progression.shouldPromptKbBump
import com.kbminisplit.ui.mapper.toKbBlock
import com.kbminisplit.ui.mapper.toStrengthRows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

sealed interface TrackerEvent {
    data class SessionCommitted(val date: LocalDate) : TrackerEvent
}

/**
 * Drives the Tracker tab. Owns three flows of truth:
 *
 *  - `InProgressRepository.observe()` — live button state (mid-session persistence)
 *  - `SessionRepository.observeAll()` — committed history (feeds progression)
 *  - `SettingsRepository.observeOnboardingDefaults()` + `observeKbBumpSnooze()` —
 *    onboarding baseline + monthly KB-bump snooze
 *
 * On init it bootstraps an in-progress row if one is missing or stale (different
 * date or different expected split). After a session is committed the same
 * routine fires so the next day's prescription appears immediately.
 */
@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val inProgressRepository: InProgressRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _events = MutableSharedFlow<TrackerEvent>()
    val events = _events.asSharedFlow()

    private val processing = AtomicBoolean(false)

    private val historyFlow = sessionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val defaultsFlow = settingsRepository.observeOnboardingDefaults()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<TrackerUiState> = combine(
        inProgressRepository.observe(),
        historyFlow,
        defaultsFlow,
        settingsRepository.observeKbBumpSnooze(),
    ) { inProgress, history, defaults, snooze ->
        if (defaults == null || inProgress == null) {
            TrackerUiState.Loading
        } else {
            buildReady(inProgress, history, defaults, snooze)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TrackerUiState.Loading)

    init {
        viewModelScope.launch {
            // Wait for defaults to be available before attempting first bootstrap.
            // This prevents a race where init runs before the database is seeded or onboarded.
            defaultsFlow.filterNotNull().first()
            bootstrapIfNeeded()
        }
    }

    fun onSetTap(cell: SetCell) = updateSet(cell, SetStatus.Completed)
    fun onSetDoubleTap(cell: SetCell) = updateSet(cell, SetStatus.Failed)
    fun onSetLongPress(cell: SetCell) = updateSet(cell, SetStatus.Pending)

    fun onKbWeightChange(newKg: Double) {
        viewModelScope.launch {
            inProgressRepository.updateKbWeight(newKg)
            inProgressRepository.updateExerciseWeight(ExerciseCatalog.KbFlow.slug, newKg, null)
            settingsRepository.updateKbWeight(newKg)
        }
    }

    fun onExerciseWeightChange(exerciseSlug: String, newKg: Double) {
        viewModelScope.launch {
            val minReps = ExerciseCatalog.bySlug(exerciseSlug)?.minReps
            inProgressRepository.updateExerciseWeight(exerciseSlug, newKg, minReps)
            settingsRepository.updateStartingWeight(exerciseSlug, newKg)
        }
    }

    fun onFeedback(feedback: Feedback) {
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val snapshot = inProgressRepository.get() ?: return@launch
                if (snapshot.sets.any { it.status == SetStatus.Pending }) return@launch

                val committedDate = snapshot.date
                sessionRepository.addSession(
                    Session(
                        date = committedDate,
                        split = snapshot.split,
                        feedback = feedback,
                        kbWeightKg = snapshot.kbWeightKg,
                        sets = snapshot.sets,
                    ),
                )
                // bootstrapIfNeeded() will call start() which already clears.
                bootstrapIfNeeded()
                _events.emit(TrackerEvent.SessionCommitted(committedDate))
            } finally {
                processing.set(false)
            }
        }
    }

    fun onKbBumpAccept() {
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val defaults = defaultsFlow.value ?: return@launch
                val newKg = defaults.kbWeightKg + KB_BUMP_STEP_KG
                settingsRepository.bumpKbWeight(newKg)
                // Snooze stamp prevents the prompt from re-appearing immediately on
                // the fresh in-progress (history hasn't recorded a session this month yet).
                settingsRepository.saveKbBumpSnooze(
                    KbBumpSnooze(
                        snoozedAtMonth = YearMonth.from(LocalDate.now(clock)),
                        sessionCountAtSnooze = historyFlow.value.size,
                    ),
                )
                inProgressRepository.clear()
                bootstrapIfNeeded()
            } finally {
                processing.set(false)
            }
        }
    }

    fun onKbBumpSnooze() {
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                settingsRepository.saveKbBumpSnooze(
                    KbBumpSnooze(
                        snoozedAtMonth = YearMonth.from(LocalDate.now(clock)),
                        sessionCountAtSnooze = historyFlow.value.size,
                    ),
                )
            } finally {
                processing.set(false)
            }
        }
    }

    fun forceSplit(split: Split) {
        viewModelScope.launch {
            val defaults = settingsRepository.getOnboardingDefaults() ?: return@launch
            val history = sessionRepository.getAll()
            val today = LocalDate.now(clock)

            inProgressRepository.clear()
            val sets = buildBootstrapSets(split, defaults, history)
            inProgressRepository.start(
                date = today,
                split = split,
                kbWeightKg = defaults.kbWeightKg,
                sets = sets,
            )
        }
    }

    private fun updateSet(cell: SetCell, newStatus: SetStatus) {
        viewModelScope.launch {
            inProgressRepository.updateSetState(
                exerciseSlug = cell.exerciseSlug,
                setIndex = cell.setIndex,
                isPriming = cell.isPriming,
                state = newStatus,
            )
        }
    }

    private suspend fun bootstrapIfNeeded() {
        val defaults = settingsRepository.getOnboardingDefaults() ?: return
        val history = sessionRepository.getAll()
        val today = LocalDate.now(clock)
        val expectedSplit = nextSplit(history)
        val (m1, m2) = movementOrder(history, expectedSplit)
        val existing = inProgressRepository.get()

        val needsFresh = when {
            existing == null -> true
            existing.date != today -> true
            existing.split != expectedSplit -> true
            // Schema-freshness guard: a pre-Phase-4 in-progress carries per-movement
            // KB rows instead of three `kb_flow` rows. Rebuild rather than render
            // an empty KB block.
            existing.sets.none { it.exerciseSlug == ExerciseCatalog.KbFlow.slug } -> true
            // Movement-order guard: if history changed such that the expected
            // exercises for this split flipped or changed, rebuild.
            existing.sets.none { it.exerciseSlug == m1.slug } -> true
            existing.sets.none { it.exerciseSlug == m2.slug } -> true
            else -> false
        }
        if (!needsFresh) return

        val sets = buildBootstrapSets(expectedSplit, defaults, history)
        inProgressRepository.start(
            date = today,
            split = expectedSplit,
            kbWeightKg = defaults.kbWeightKg,
            sets = sets,
        )
    }

    private fun buildBootstrapSets(
        split: Split,
        defaults: OnboardingDefaults,
        history: List<Session>,
    ): List<SetEntry> {
        val kbWeight = defaults.kbWeightKg
        val (m1, m2) = movementOrder(history, split)
        return buildList {
            // KB Flow: one row per completed circuit (spec §2.2). The 5 movement
            // labels are display-only — set tracking is at the circuit level.
            repeat(KB_ROUNDS) { round ->
                add(
                    SetEntry(
                        exerciseSlug = ExerciseCatalog.KbFlow.slug,
                        setIndex = round,
                        isPriming = false,
                        targetReps = null,
                        weightKg = kbWeight,
                        status = SetStatus.Pending,
                    ),
                )
            }
            listOf(m1, m2).forEach { exercise ->
                val rx = getPrescription(history, exercise, defaults)
                add(primeFor(exercise, rx))
                repeat(STRENGTH_WORKING_SETS) { idx ->
                    add(workingFor(exercise, rx, idx + 1))
                }
            }
        }
    }

    private fun primeFor(exercise: Exercise, rx: Prescription) = SetEntry(
        exerciseSlug = exercise.slug,
        setIndex = 0,
        isPriming = true,
        targetReps = null,
        weightKg = rx.weightKg,
        status = SetStatus.Pending,
    )

    private fun workingFor(exercise: Exercise, rx: Prescription, setIndex: Int) = SetEntry(
        exerciseSlug = exercise.slug,
        setIndex = setIndex,
        isPriming = false,
        targetReps = rx.targetReps,
        weightKg = rx.weightKg,
        status = SetStatus.Pending,
    )

    private fun buildReady(
        snapshot: InProgressSnapshot,
        history: List<Session>,
        defaults: OnboardingDefaults,
        snooze: KbBumpSnooze?,
    ): TrackerUiState.Ready {
        val (m1, m2) = movementOrder(history, snapshot.split)
        val kbBlock = snapshot.sets.toKbBlock()
        val strengthRows = snapshot.sets.toStrengthRows(listOf(m1, m2))

        val kbAllResolved = kbBlock.circuits.all { it.status != SetStatus.Pending }
        val strengthAllResolved = strengthRows.all { row ->
            row.prime.status != SetStatus.Pending &&
                row.working.all { it.status != SetStatus.Pending }
        }
        val allResolved = kbBlock.circuits.isNotEmpty() && kbAllResolved && strengthAllResolved
        val noKbTouched = kbBlock.circuits.all { it.status == SetStatus.Pending }
        val kbBump = if (
            noKbTouched &&
            shouldPromptKbBump(history, snapshot.date, snooze)
        ) {
            KbBumpState(
                currentKg = defaults.kbWeightKg,
                targetKg = defaults.kbWeightKg + KB_BUMP_STEP_KG,
            )
        } else {
            null
        }

        return TrackerUiState.Ready(
            date = snapshot.date,
            split = snapshot.split,
            kbWeightKg = snapshot.kbWeightKg,
            kbBlock = kbBlock,
            strength = strengthRows,
            allButtonsResolved = allResolved,
            kbBump = kbBump,
            isFirstSession = history.isEmpty(),
        )
    }

    companion object {
        const val KB_BUMP_STEP_KG = 2.0
        const val KB_ROUNDS = 3
        const val STRENGTH_WORKING_SETS = 3
    }
}
