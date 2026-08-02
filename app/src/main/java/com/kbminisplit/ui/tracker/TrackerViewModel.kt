package com.kbminisplit.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.BodyweightState
import com.kbminisplit.data.repository.InProgressRepository
import com.kbminisplit.data.repository.InProgressSnapshot
import com.kbminisplit.data.repository.ProgramRepository
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.InProgressSet
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.ProgramDay
import com.kbminisplit.domain.model.ProgramGroup
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.progression.ResolvedDay
import com.kbminisplit.domain.progression.RestWeekState
import com.kbminisplit.domain.progression.bumpedWeightKg
import com.kbminisplit.domain.progression.dayCycleCount
import com.kbminisplit.domain.progression.isBodyweightStale
import com.kbminisplit.domain.progression.nextDay
import com.kbminisplit.domain.progression.nextKbWeight
import com.kbminisplit.domain.progression.resolveDay
import com.kbminisplit.domain.progression.shouldPromptRestWeek
import com.kbminisplit.ui.mapper.buildGroupBlocks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

sealed interface TrackerEvent {
    data class SessionCommitted(val date: LocalDate) : TrackerEvent
}

/**
 * Drives the Tracker tab. Four flows of truth:
 *
 *  - `ProgramRepository.observeProgram()` — what to do today
 *  - `InProgressRepository.observe()` — live button state (mid-session persistence)
 *  - `SessionRepository.observeAll()` — committed history (day turnover + rotation)
 *  - `SettingsRepository` — bodyweight and the rest-week counters
 *
 * On init it bootstraps an in-progress row if one is missing or stale (different
 * date, different day, or a program edit that changed today's movements). After a
 * session is committed the same routine fires so the next day appears immediately.
 *
 * Nothing here derives a weight from history: a movement's weight is whatever the
 * program says it is, and it only ever changes because the user changed it.
 */
@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val inProgressRepository: InProgressRepository,
    private val programRepository: ProgramRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _events = MutableSharedFlow<TrackerEvent>()
    val events = _events.asSharedFlow()

    private val processing = AtomicBoolean(false)

    // Rest-guide anchor: epoch millis of the last set resolved (completed or
    // failed). In-memory only — a rest guide doesn't need to survive process
    // death — so it lives here rather than in the in-progress tables.
    private val _restStartedAtMillis = MutableStateFlow<Long?>(null)
    val restStartedAtMillis: StateFlow<Long?> = _restStartedAtMillis.asStateFlow()

    // Serializes revealing deferred groups so racing final taps wait instead of skipping.
    private val revealMutex = Mutex()

    private val historyFlow = sessionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val programFlow = programRepository.observeProgram()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Program.EMPTY)

    /** The days available to jump to from the admin gesture. */
    val days: StateFlow<List<ProgramDay>> = programFlow
        .map { it.days }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val bodyweightFlow = settingsRepository.observeBodyweight()
        .stateIn(viewModelScope, SharingStarted.Eagerly, BodyweightState(null, null))

    val state: StateFlow<TrackerUiState> = combine(
        inProgressRepository.observe(),
        historyFlow,
        programFlow,
        bodyweightFlow,
        settingsRepository.observeRestWeek(),
    ) { inProgress, history, program, bodyweight, restWeek ->
        when {
            program.isEmpty -> TrackerUiState.NoProgram
            inProgress == null -> TrackerUiState.Loading
            else -> buildReady(inProgress, history, program, bodyweight, restWeek)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TrackerUiState.Loading)

    init {
        viewModelScope.launch {
            // Re-bootstrap whenever the *shape* of the program changes, so an edit
            // made in the Program tab shows up on the Tracker immediately rather
            // than tomorrow. Keying on structure rather than the whole program
            // means a weight change — including the bump chip's own write — does
            // not churn the session.
            programFlow
                .map { it.structureKey() }
                .distinctUntilChanged()
                .collect { bootstrapIfNeeded() }
        }
    }

    fun onSetTap(cell: SetCell) = updateSet(cell, SetStatus.Completed)
    fun onSetDoubleTap(cell: SetCell) = updateSet(cell, SetStatus.Failed)
    fun onSetLongPress(cell: SetCell) = updateSet(cell, SetStatus.Pending)

    /**
     * Mid-session weight correction. Writes both the live rows and the program, so
     * the change applies to the set you are about to do *and* to next time.
     */
    fun onMovementWeightChange(programItemId: Long, newKg: Double) {
        viewModelScope.launch {
            inProgressRepository.updateItemWeight(programItemId, newKg)
            programRepository.setItemWeight(programItemId, newKg)
        }
    }

    fun onCircuitWeightChange(programGroupId: Long, newKg: Double) {
        viewModelScope.launch {
            inProgressRepository.updateCircuitWeight(programGroupId, newKg)
            programRepository.setGroupWeight(programGroupId, newKg)
        }
    }

    /**
     * Takes or gives back the weight bump offered on a completed movement.
     *
     * Only the program is written — this session keeps the weight actually lifted.
     * "Armed" is therefore not stored anywhere: it is simply the program weight
     * differing from the session weight, which makes the toggle its own undo.
     */
    fun onBumpToggle(programItemId: Long) {
        viewModelScope.launch {
            val item = programFlow.value.itemById(programItemId) ?: return@launch
            val sessionKg = sessionWeightFor(programItemId) ?: return@launch
            val target = if (item.currentWeightKg != sessionKg) {
                sessionKg
            } else {
                bumpedWeightKg(item)
            }
            programRepository.setItemWeight(programItemId, target)
        }
    }

    fun onCircuitBumpAccept(programGroupId: Long) {
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val group = programFlow.value.groupById(programGroupId) ?: return@launch
                val next = group.weightKg?.let { nextKbWeight(it) } ?: return@launch
                programRepository.setGroupWeight(programGroupId, next)
                inProgressRepository.updateCircuitWeight(programGroupId, next)
            } finally {
                processing.set(false)
            }
        }
    }

    fun onCircuitBumpSnooze(programGroupId: Long) {
        viewModelScope.launch { programRepository.snoozeGroupBump(programGroupId) }
    }

    /** Record the weekly bodyweight check-in from the Tracker prompt. */
    fun onBodyweightEntered(kg: Double) {
        viewModelScope.launch { settingsRepository.updateBodyweight(kg) }
    }

    /** Takes the rest week: every movement drops one step and the counter resets. */
    fun onRestWeekAccept() {
        if (!processing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                programRepository.deloadAllItems()
                settingsRepository.takeRestWeek(historyFlow.value.size)
                // Today's rows still carry the pre-deload weights; rebuild so the
                // session in front of the user matches what was just decided.
                inProgressRepository.clear()
                bootstrapIfNeeded()
            } finally {
                processing.set(false)
            }
        }
    }

    fun onRestWeekSnooze() {
        viewModelScope.launch { settingsRepository.snoozeRestWeek(historyFlow.value.size) }
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
                        dayKey = snapshot.dayKey,
                        feedback = feedback,
                        circuitWeightKg = primaryCircuitWeight(snapshot),
                        sets = snapshot.sets.map { it.toSetEntry() },
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

    /** Force today onto a specific day of the program (the admin gesture). */
    fun forceDay(dayKey: String) {
        viewModelScope.launch {
            val program = programRepository.getProgram()
            val day = program.dayByKey(dayKey) ?: return@launch
            val history = sessionRepository.getAll()
            val resolved = resolveDay(day, dayCycleCount(history, day.key))

            inProgressRepository.clear()
            inProgressRepository.start(
                date = LocalDate.now(clock),
                dayKey = day.key,
                sets = buildSets(resolved, includeDeferred = false),
            )
            _restStartedAtMillis.value = null
        }
    }

    private fun updateSet(cell: SetCell, newStatus: SetStatus) {
        // Any resolved set — completed or failed — restarts the rest guide;
        // reverting to pending never does.
        if (newStatus != SetStatus.Pending) {
            _restStartedAtMillis.value = clock.millis()
        }
        viewModelScope.launch {
            inProgressRepository.updateSetState(cell.id, newStatus)
            // Completing a set can never un-complete a movement, so only the other
            // two transitions can strand an armed bump.
            if (newStatus != SetStatus.Completed) {
                disarmIncompleteBumps()
            }
            if (newStatus != SetStatus.Pending) {
                revealDeferredGroups()
            }
        }
    }

    /**
     * Gives back any bump whose movement is no longer fully completed, so undoing
     * a set undoes the offer it produced.
     *
     * Only a weight that is exactly one step above the session weight is reverted,
     * which leaves a deliberate edit made from the Program tab alone.
     */
    private suspend fun disarmIncompleteBumps() {
        val snapshot = inProgressRepository.get() ?: return
        val program = programRepository.getProgram()
        snapshot.sets
            .filter { !it.isPriming && !it.isCircuitRound }
            .groupBy { it.programItemId }
            .forEach { (itemId, working) ->
                if (working.all { it.status == SetStatus.Completed }) return@forEach
                val item = program.itemById(itemId) ?: return@forEach
                val sessionKg = working.first().weightKg
                if (item.currentWeightKg == sessionKg) return@forEach
                val armed = bumpedWeightKg(sessionKg, item.weightStepKg, item.isAssisted)
                if (item.currentWeightKg == armed) {
                    programRepository.setItemWeight(itemId, sessionKg)
                }
            }
    }

    /**
     * Reveals groups held back until the earlier work is done. Fires from
     * [updateSet] when the last visible set resolves, and from
     * [bootstrapIfNeeded] as a safety net for a snapshot that resolved without
     * ever gaining its deferred rows (app updated mid-session, or process death
     * between the final tap and the append).
     */
    private suspend fun revealDeferredGroups() {
        revealMutex.withLock {
            val snapshot = inProgressRepository.get() ?: return
            if (snapshot.sets.any { it.status == SetStatus.Pending }) return
            val resolved = resolveToday(snapshot.dayKey) ?: return
            val presentGroups = snapshot.sets.map { it.programGroupId }.toSet()
            val missing = resolved.groups.filter { it.group.id !in presentGroups }
            if (missing.isEmpty()) return
            inProgressRepository.addSets(
                buildSets(ResolvedDay(resolved.day, missing), includeDeferred = true),
            )
        }
    }

    private suspend fun bootstrapIfNeeded() {
        val program = programRepository.getProgram()
        if (program.isEmpty) {
            inProgressRepository.clear()
            return
        }
        val history = sessionRepository.getAll()
        val today = LocalDate.now(clock)
        val day = nextDay(history, program) ?: return
        val resolved = resolveDay(day, dayCycleCount(history, day.key))
        val existing = inProgressRepository.get()

        if (!needsFreshSession(existing, today, resolved)) {
            revealDeferredGroups()
            return
        }

        inProgressRepository.start(
            date = today,
            dayKey = day.key,
            sets = buildSets(resolved, includeDeferred = false),
        )
        _restStartedAtMillis.value = null
    }

    /**
     * A stored session survives only while it still matches today's plan. A new
     * date, a different day, or a program edit that changed which slots today
     * needs all mean the buttons on screen no longer describe the workout.
     */
    private fun needsFreshSession(
        existing: InProgressSnapshot?,
        today: LocalDate,
        resolved: ResolvedDay,
    ): Boolean {
        if (existing == null) return true
        if (existing.date != today) return true
        if (existing.dayKey != resolved.key) return true
        val expected = buildSets(resolved, includeDeferred = false).map { it.slotKey }.toSet()
        val present = existing.sets.map { it.slotKey }.toSet()
        return !present.containsAll(expected)
    }

    /**
     * Builds today's rows, group by group in program order.
     *
     * `position` is stamped from the rotated order rather than the program order,
     * so the Log later replays the session as it was actually performed.
     */
    private fun buildSets(day: ResolvedDay, includeDeferred: Boolean): List<InProgressSet> {
        var position = 0
        return buildList {
            day.groups.forEach { resolved ->
                if (resolved.group.isDeferred && !includeDeferred) return@forEach
                if (resolved.group.isCircuit) {
                    addAll(circuitRows(resolved.group, position++))
                } else {
                    resolved.items.forEach { item ->
                        addAll(movementRows(resolved.group, item, position++))
                    }
                }
            }
        }
    }

    /** One row per round; the movements inside a circuit are labels, not buttons. */
    private fun circuitRows(group: ProgramGroup, position: Int): List<InProgressSet> {
        val slug = group.circuitSlug ?: return emptyList()
        return (0 until group.rounds).map { round ->
            blankRow(
                groupId = group.id,
                itemId = InProgressSet.NO_ITEM,
                slug = slug,
                setIndex = round,
                isPriming = false,
                targetReps = null,
                targetRepsMax = null,
                weightKg = group.weightKg ?: 0.0,
                position = position,
            )
        }
    }

    /**
     * Lead-in rows then working rows. Both lead-ins are priming rows told apart by
     * setIndex (0 = prime, 1 = warm-up); with a single lead-in only the warm-up is
     * built. They store the working weight as a neutral placeholder — the number
     * shown in the circle is derived at display time.
     */
    private fun movementRows(
        group: ProgramGroup,
        item: ProgramItem,
        position: Int,
    ): List<InProgressSet> = buildList {
        val leadInIndices = when (item.leadInSets.coerceIn(0, 2)) {
            0 -> emptyList()
            1 -> listOf(1)
            else -> listOf(0, 1)
        }
        leadInIndices.forEach { setIndex ->
            add(
                blankRow(
                    groupId = group.id,
                    itemId = item.id,
                    slug = item.exerciseSlug,
                    setIndex = setIndex,
                    isPriming = true,
                    targetReps = null,
                    targetRepsMax = null,
                    weightKg = item.currentWeightKg,
                    position = position,
                ),
            )
        }
        repeat(item.sets.coerceAtLeast(1)) { index ->
            add(
                blankRow(
                    groupId = group.id,
                    itemId = item.id,
                    slug = item.exerciseSlug,
                    setIndex = index + 1,
                    isPriming = false,
                    targetReps = item.minReps,
                    targetRepsMax = item.maxReps,
                    weightKg = item.currentWeightKg,
                    position = position,
                ),
            )
        }
    }

    @Suppress("LongParameterList")
    private fun blankRow(
        groupId: Long,
        itemId: Long,
        slug: String,
        setIndex: Int,
        isPriming: Boolean,
        targetReps: Int?,
        targetRepsMax: Int?,
        weightKg: Double,
        position: Int,
    ) = InProgressSet(
        id = 0,
        programGroupId = groupId,
        programItemId = itemId,
        exerciseSlug = slug,
        setIndex = setIndex,
        isPriming = isPriming,
        targetReps = targetReps,
        targetRepsMax = targetRepsMax,
        weightKg = weightKg,
        status = SetStatus.Pending,
        position = position,
    )

    private suspend fun resolveToday(dayKey: String): ResolvedDay? {
        val day = programRepository.getProgram().dayByKey(dayKey) ?: return null
        return resolveDay(day, dayCycleCount(sessionRepository.getAll(), dayKey))
    }

    private fun sessionWeightFor(programItemId: Long): Double? =
        (state.value as? TrackerUiState.Ready)
            ?.groups
            ?.filterIsInstance<GroupBlock.Standard>()
            ?.flatMap { it.movements }
            ?.firstOrNull { it.programItemId == programItemId }
            ?.weightKg

    /** The weight snapshotted onto the session: the day's first circuit, if any. */
    private fun primaryCircuitWeight(snapshot: InProgressSnapshot): Double =
        snapshot.sets.firstOrNull { it.isCircuitRound }?.weightKg ?: 0.0

    private fun buildReady(
        snapshot: InProgressSnapshot,
        history: List<Session>,
        program: Program,
        bodyweight: BodyweightState,
        restWeek: RestWeekState,
    ): TrackerUiState {
        val day = program.dayByKey(snapshot.dayKey) ?: return TrackerUiState.Loading
        val resolved = resolveDay(day, dayCycleCount(history, day.key))
        val groups = buildGroupBlocks(resolved, snapshot.sets, bodyweight.kg, clock.millis())

        // Everything programmed for today has been revealed and resolved.
        val allRevealed = resolved.groups.size == groups.size
        val feedbackReady = groups.isNotEmpty() && allRevealed && groups.all { it.isResolved }

        // Nudge for a weekly bodyweight only when it actually matters today (an
        // assisted movement is programmed) and the last check-in has gone stale.
        val hasAssisted = resolved.groups
            .flatMap { it.items }
            .any { it.mechanic == ExerciseMechanic.ASSISTED }

        return TrackerUiState.Ready(
            date = snapshot.date,
            dayKey = day.key,
            dayName = day.name,
            groups = groups,
            feedbackReady = feedbackReady,
            isFirstSession = history.isEmpty(),
            bodyweightPrompt = hasAssisted &&
                isBodyweightStale(bodyweight.loggedAtMillis, clock.millis()),
            currentBodyweightKg = bodyweight.kg,
            restWeekPrompt = shouldPromptRestWeek(history.size, restWeek),
        )
    }
}

/** Identifies the program slot a row fills, for comparing a stored session to today's plan. */
private val InProgressSet.slotKey: String
    get() = "$programGroupId/$programItemId/$setIndex/$isPriming"

/**
 * Everything about a program that changes which buttons a session needs. Weights
 * and names are deliberately absent: they change often and never require a rebuild.
 */
private fun Program.structureKey(): String = days.joinToString("|") { day ->
    day.key + day.groups.joinToString(",") { group ->
        "${group.id}/${group.kind}/${group.rotates}/${group.isDeferred}/${group.rounds}" +
            group.items.joinToString(";") { "${it.id}/${it.sets}/${it.leadInSets}" }
    }
}
