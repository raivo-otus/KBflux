package com.kbminisplit.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.BodyweightState
import com.kbminisplit.data.repository.InProgressRepository
import com.kbminisplit.data.repository.InProgressSnapshot
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.model.Prescription
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import com.kbminisplit.domain.progression.KbBumpSnooze
import com.kbminisplit.domain.progression.isBodyweightStale
import com.kbminisplit.domain.progression.kbRepScheme
import com.kbminisplit.domain.progression.movementOrder
import com.kbminisplit.domain.progression.nextKbWeight
import com.kbminisplit.domain.progression.nextSplit
import com.kbminisplit.domain.progression.getPrescription
import com.kbminisplit.domain.progression.shouldPromptKbBump
import com.kbminisplit.ui.mapper.toKbBlock
import com.kbminisplit.ui.mapper.toStrengthRows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
 *    onboarding baseline + KB-bump snooze
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

    // Transient "user tapped No to auxiliary work" flag. Not persisted: aux
    // presence is derived from the in-progress rows, but a decision to *skip*
    // aux has no backing row, so it lives here and resets on each fresh session.
    private val auxSkipped = MutableStateFlow(false)

    private val historyFlow = sessionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val defaultsFlow = settingsRepository.observeOnboardingDefaults()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val bodyweightFlow = settingsRepository.observeBodyweight()
        .stateIn(viewModelScope, SharingStarted.Eagerly, BodyweightState(null, null))

    val state: StateFlow<TrackerUiState> = combine(
        inProgressRepository.observe(),
        historyFlow,
        // Nested so the 5-arg combine stays typed: onboarding defaults + bodyweight
        // both originate from settings.
        combine(defaultsFlow, bodyweightFlow) { defaults, bodyweight -> defaults to bodyweight },
        settingsRepository.observeKbBumpSnooze(),
        auxSkipped,
    ) { inProgress, history, defaultsAndBodyweight, snooze, skipAux ->
        val (defaults, bodyweight) = defaultsAndBodyweight
        if (defaults == null || inProgress == null) {
            TrackerUiState.Loading
        } else {
            buildReady(inProgress, history, defaults, bodyweight, snooze, skipAux)
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
                        // Snapshot current bodyweight so historical effective load
                        // stays fixed even if bodyweight is later corrected.
                        bodyweightKg = bodyweightFlow.value.kg,
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

    /** User chose to do auxiliary work: append the aux block to the session. */
    fun onStartAux() {
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val snapshot = inProgressRepository.get() ?: return@launch
                val auxExercises = ExerciseCatalog.auxForSplit(snapshot.split)
                val auxSlugs = auxExercises.map { it.slug }.toSet()
                // Idempotent: don't re-add if aux rows already exist.
                if (snapshot.sets.any { it.exerciseSlug in auxSlugs }) return@launch
                val defaults = settingsRepository.getOnboardingDefaults() ?: return@launch
                val history = sessionRepository.getAll()
                inProgressRepository.addSets(buildAuxSets(auxExercises, defaults, history))
            } finally {
                processing.set(false)
            }
        }
    }

    /** User declined auxiliary work: go straight to the feedback sheet. */
    fun onSkipAux() {
        auxSkipped.value = true
    }

    /** Record the weekly bodyweight check-in from the Tracker prompt. */
    fun onBodyweightEntered(kg: Double) {
        viewModelScope.launch { settingsRepository.updateBodyweight(kg) }
    }

    fun onKbBumpAccept() {
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val defaults = defaultsFlow.value ?: return@launch
                val newKg = nextKbWeight(defaults.kbWeightKg) ?: return@launch
                // No snooze stamp needed: the new weight has no completed sessions
                // yet, which suppresses the prompt until 3 months pass again.
                settingsRepository.bumpKbWeight(newKg)
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
            auxSkipped.value = false
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
        auxSkipped.value = false
    }

    private fun buildBootstrapSets(
        split: Split,
        defaults: OnboardingDefaults,
        history: List<Session>,
    ): List<SetEntry> {
        val kbWeight = defaults.kbWeightKg
        val (m1, m2) = movementOrder(history, split)
        return buildList {
            // KB Flow: one row per completed circuit (spec §2.2). The movement
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

    private fun buildAuxSets(
        auxExercises: List<Exercise>,
        defaults: OnboardingDefaults,
        history: List<Session>,
    ): List<SetEntry> = buildList {
        auxExercises.forEach { exercise ->
            val rx = getPrescription(history, exercise, defaults)
            add(primeFor(exercise, rx))
            repeat(STRENGTH_WORKING_SETS) { idx ->
                add(workingFor(exercise, rx, idx + 1))
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
        bodyweight: BodyweightState,
        snooze: KbBumpSnooze?,
        auxSkipped: Boolean,
    ): TrackerUiState.Ready {
        val (m1, m2) = movementOrder(history, snapshot.split)
        // Rep scheme keys on the snapshot weight so the labels always agree with
        // the "KB Flow · X kg" header, even mid-edit.
        val kbBlock = snapshot.sets.toKbBlock(snapshot.split, kbRepScheme(history, snapshot.kbWeightKg))
        val strengthRows = snapshot.sets.toStrengthRows(listOf(m1, m2), bodyweight.kg)

        val kbAllResolved = kbBlock.circuits.all { it.status != SetStatus.Pending }
        val strengthAllResolved = strengthRows.all { row -> row.isResolved() }
        val mainResolved = kbBlock.circuits.isNotEmpty() && kbAllResolved && strengthAllResolved

        // Aux rows appear only once the user opts in; presence drives the phase.
        val auxRows = snapshot.sets.toStrengthRows(ExerciseCatalog.auxForSplit(snapshot.split), bodyweight.kg)
        val auxPresent = auxRows.isNotEmpty()
        val auxResolved = auxPresent && auxRows.all { row -> row.isResolved() }

        val phase = if (auxPresent) TrackerPhase.AUX else TrackerPhase.MAIN
        val showAuxPrompt = phase == TrackerPhase.MAIN && mainResolved && !auxSkipped
        val feedbackReady = (mainResolved && auxSkipped) || auxResolved

        val noKbTouched = kbBlock.circuits.all { it.status == SetStatus.Pending }
        // Prompt keys on the settings weight (not the snapshot) so accepting a
        // bump hides it immediately, before the in-progress rebuild lands.
        val nextKb = nextKbWeight(defaults.kbWeightKg)
        val kbBump = if (
            noKbTouched &&
            nextKb != null &&
            shouldPromptKbBump(history, snapshot.date, defaults.kbWeightKg, snooze)
        ) {
            KbBumpState(
                currentKg = defaults.kbWeightKg,
                targetKg = nextKb,
            )
        } else {
            null
        }

        // Nudge for a weekly bodyweight only when it actually matters today (an
        // assisted movement is programmed) and the last check-in has gone stale.
        val hasAssisted = strengthRows.any { it.exercise.mechanic == ExerciseMechanic.ASSISTED }
        val bodyweightPrompt = hasAssisted &&
            isBodyweightStale(bodyweight.loggedAtMillis, clock.millis())

        return TrackerUiState.Ready(
            date = snapshot.date,
            split = snapshot.split,
            kbWeightKg = snapshot.kbWeightKg,
            kbBlock = kbBlock,
            strength = strengthRows,
            mainResolved = mainResolved,
            kbBump = kbBump,
            isFirstSession = history.isEmpty(),
            phase = phase,
            aux = auxRows,
            showAuxPrompt = showAuxPrompt,
            feedbackReady = feedbackReady,
            bodyweightPrompt = bodyweightPrompt,
            currentBodyweightKg = bodyweight.kg,
        )
    }

    private fun StrengthMovementRow.isResolved(): Boolean =
        prime.status != SetStatus.Pending && working.all { it.status != SetStatus.Pending }

    companion object {
        const val KB_ROUNDS = 3
        const val STRENGTH_WORKING_SETS = 3
    }
}
