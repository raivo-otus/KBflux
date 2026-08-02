package com.kbminisplit.ui.tracker

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.repository.BodyweightState
import com.kbminisplit.data.repository.InProgressRepository
import com.kbminisplit.data.repository.InProgressSnapshot
import com.kbminisplit.data.repository.ProgramRepository
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.InProgressSet
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.ProgramGroup
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.progression.REST_WEEK_SESSIONS
import com.kbminisplit.domain.progression.RestWeekState
import com.kbminisplit.domain.progression.circuitGroup
import com.kbminisplit.domain.progression.day
import com.kbminisplit.domain.progression.item
import com.kbminisplit.domain.progression.program
import com.kbminisplit.domain.progression.session
import com.kbminisplit.domain.progression.standardGroup
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"))
    private val today: LocalDate = LocalDate.now(fixedClock)

    private val bench = item(1, "Bench Press", currentWeightKg = 60.0, weightStepKg = 2.5)
    private val dips = item(
        2, "Assisted Dips",
        currentWeightKg = 40.0, weightStepKg = 2.5, isAssisted = true,
    )
    private val curl = item(3, "Bicep Curl", currentWeightKg = 10.0, leadInSets = 0, sets = 2)

    /** Circuit + rotating main pair + a deferred accessory block. */
    private val defaultProgram = program(
        day(
            1, "A", "Push",
            groups = listOf(
                circuitGroup(10, items = listOf(item(90, "Swings", minReps = 20, maxReps = 32))),
                standardGroup(11, name = "Main", items = listOf(bench, dips)),
                standardGroup(12, name = "Accessories", isDeferred = true, items = listOf(curl)),
            ),
        ),
        day(2, "B", "Pull", groups = listOf(standardGroup(20, items = listOf(item(4, "Row"))))),
    )

    private lateinit var sessionRepository: SessionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var inProgressRepository: InProgressRepository
    private lateinit var programRepository: ProgramRepository

    private val inProgressFlow = MutableStateFlow<InProgressSnapshot?>(null)
    private val historyFlow = MutableStateFlow<List<Session>>(emptyList())
    private val programFlow = MutableStateFlow(defaultProgram)
    private val bodyweightFlow = MutableStateFlow(BodyweightState(80.0, fixedClock.millis()))
    private val restWeekFlow = MutableStateFlow(RestWeekState())

    private var nextRowId = 1L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nextRowId = 1L

        sessionRepository = mockk(relaxed = true) {
            every { observeAll() } returns historyFlow
            coEvery { getAll() } answers { historyFlow.value }
            coEvery { addSession(any()) } answers {
                historyFlow.value = historyFlow.value + firstArg<Session>()
                1L
            }
        }

        settingsRepository = mockk(relaxed = true) {
            every { observeBodyweight() } returns bodyweightFlow
            every { observeRestWeek() } returns restWeekFlow
            coEvery { takeRestWeek(any()) } answers {
                restWeekFlow.value = RestWeekState(anchorSessions = firstArg())
            }
            coEvery { snoozeRestWeek(any()) } answers {
                restWeekFlow.value = restWeekFlow.value.copy(snoozedAtSessions = firstArg())
            }
        }

        programRepository = mockk(relaxed = true) {
            every { observeProgram() } returns programFlow
            coEvery { getProgram() } answers { programFlow.value }
            coEvery { setItemWeight(any(), any()) } answers {
                val itemId: Long = firstArg()
                val kg: Double = secondArg()
                programFlow.value = programFlow.value.mapItems { i ->
                    if (i.id == itemId) i.copy(currentWeightKg = kg) else i
                }
            }
            coEvery { deloadAllItems() } answers {
                programFlow.value = programFlow.value.mapItems { i ->
                    i.copy(
                        currentWeightKg = if (i.isAssisted) {
                            i.currentWeightKg + i.weightStepKg
                        } else {
                            (i.currentWeightKg - i.weightStepKg).coerceAtLeast(0.0)
                        },
                    )
                }
            }
        }

        inProgressRepository = mockk(relaxed = true) {
            every { observe() } returns inProgressFlow
            coEvery { get() } answers { inProgressFlow.value }
            coEvery { start(any(), any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                val sets = args[2] as List<InProgressSet>
                inProgressFlow.value = InProgressSnapshot(
                    date = firstArg(),
                    dayKey = secondArg(),
                    sets = sets.map { it.copy(id = nextRowId++) },
                )
            }
            coEvery { addSets(any()) } answers {
                @Suppress("UNCHECKED_CAST")
                val sets = args[0] as List<InProgressSet>
                val current = inProgressFlow.value ?: return@answers
                inProgressFlow.value =
                    current.copy(sets = current.sets + sets.map { it.copy(id = nextRowId++) })
            }
            coEvery { updateSetState(any(), any()) } answers {
                val id: Long = firstArg()
                val status: SetStatus = secondArg()
                val current = inProgressFlow.value ?: return@answers
                inProgressFlow.value = current.copy(
                    sets = current.sets.map { if (it.id == id) it.copy(status = status) else it },
                )
            }
            coEvery { updateItemWeight(any(), any()) } answers {
                val itemId: Long = firstArg()
                val kg: Double = secondArg()
                val current = inProgressFlow.value ?: return@answers
                inProgressFlow.value = current.copy(
                    sets = current.sets.map {
                        if (it.programItemId == itemId) it.copy(weightKg = kg) else it
                    },
                )
            }
            coEvery { clear() } answers { inProgressFlow.value = null }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = TrackerViewModel(
        sessionRepository = sessionRepository,
        settingsRepository = settingsRepository,
        inProgressRepository = inProgressRepository,
        programRepository = programRepository,
        clock = fixedClock,
    )

    private fun Program.mapItems(transform: (ProgramItem) -> ProgramItem) =
        mapGroups { it.copy(items = it.items.map(transform)) }

    private fun Program.mapGroups(transform: (ProgramGroup) -> ProgramGroup) = copy(
        days = days.map { d -> d.copy(groups = d.groups.map(transform)) },
    )

    private fun ready(vm: TrackerViewModel) = vm.state.value as TrackerUiState.Ready

    private fun standardBlocks(vm: TrackerViewModel) =
        ready(vm).groups.filterIsInstance<GroupBlock.Standard>()

    private fun movement(vm: TrackerViewModel, name: String): MovementRow =
        standardBlocks(vm).flatMap { it.movements }.first { it.name == name }

    // ---- bootstrap ----

    @Test
    fun `an empty program reports NoProgram instead of a session`() {
        runTest(testDispatcher) {
            programFlow.value = Program.EMPTY
            val vm = newViewModel()
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(TrackerUiState.NoProgram)
        }
    }

    @Test
    fun `bootstrap builds today's first day from the program`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val state = ready(vm)
            assertThat(state.dayKey).isEqualTo("A")
            assertThat(state.dayName).isEqualTo("Push")
            assertThat(state.date).isEqualTo(today)
        }
    }

    @Test
    fun `a circuit group renders one button per round`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val circuit = ready(vm).groups.filterIsInstance<GroupBlock.Circuit>().single()
            assertThat(circuit.rounds).hasSize(3)
            assertThat(circuit.weightKg).isEqualTo(16.0)
            assertThat(circuit.movements.single().repsLabel).isEqualTo("20–32")
        }
    }

    @Test
    fun `a movement gets its programmed lead-in and working sets`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val row = movement(vm, "Bench Press")
            assertThat(row.leadIn).hasSize(2)
            assertThat(row.working).hasSize(3)
            assertThat(row.weightKg).isEqualTo(60.0)
            assertThat(row.repRangeLabel).isEqualTo("8–12")
        }
    }

    @Test
    fun `reps are shown as a range, never as a single moving target`() {
        runTest(testDispatcher) {
            historyFlow.value = listOf(
                session(today.minusDays(6), "A"),
                session(today.minusDays(4), "B"),
            )
            val vm = newViewModel()
            advanceUntilIdle()

            // Two prior sessions would once have pushed the target up; the range holds.
            assertThat(movement(vm, "Bench Press").repRangeLabel).isEqualTo("8–12")
            assertThat(movement(vm, "Bench Press").weightKg).isEqualTo(60.0)
        }
    }

    @Test
    fun `the next day follows the last session in program order`() {
        runTest(testDispatcher) {
            historyFlow.value = listOf(session(today.minusDays(1), "A"))
            val vm = newViewModel()
            advanceUntilIdle()

            assertThat(ready(vm).dayKey).isEqualTo("B")
        }
    }

    @Test
    fun `a rotating group flips its movements on the day's second appearance`() {
        runTest(testDispatcher) {
            historyFlow.value = listOf(
                session(today.minusDays(2), "A"),
                session(today.minusDays(1), "B"),
            )
            val vm = newViewModel()
            advanceUntilIdle()

            val main = standardBlocks(vm).first { it.name == "Main" }
            assertThat(main.movements.map { it.name })
                .containsExactly("Assisted Dips", "Bench Press").inOrder()
        }
    }

    @Test
    fun `a movement with no lead-in sets gets only working buttons`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllVisible(vm)
            advanceUntilIdle()

            val row = movement(vm, "Bicep Curl")
            assertThat(row.leadIn).isEmpty()
            assertThat(row.working).hasSize(2)
        }
    }

    @Test
    fun `adding a movement in the Program tab rebuilds today's session`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            assertThat(standardBlocks(vm).first { it.name == "Main" }.movements).hasSize(2)

            programFlow.value = programFlow.value.mapGroups { group ->
                if (group.id == 11L) {
                    group.copy(items = group.items + item(5, "Face Pull", position = 2))
                } else {
                    group
                }
            }
            advanceUntilIdle()

            assertThat(standardBlocks(vm).first { it.name == "Main" }.movements.map { it.name })
                .containsExactly("Bench Press", "Assisted Dips", "Face Pull").inOrder()
        }
    }

    // ---- deferred groups ----

    @Test
    fun `a deferred group stays hidden until the earlier work is resolved`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            assertThat(ready(vm).groups.map { it.name })
                .containsExactly("Kettlebell flow", "Main").inOrder()
        }
    }

    @Test
    fun `the deferred group appears once everything before it resolves`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            resolveAllVisible(vm)
            advanceUntilIdle()

            assertThat(ready(vm).groups.map { it.name })
                .containsExactly("Kettlebell flow", "Main", "Accessories").inOrder()
            assertThat(ready(vm).feedbackReady).isFalse()
        }
    }

    @Test
    fun `feedback is offered only once every group is resolved`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllVisible(vm)
            advanceUntilIdle()
            resolveAllVisible(vm)
            advanceUntilIdle()

            assertThat(ready(vm).feedbackReady).isTrue()
        }
    }

    // ---- the bump chip ----

    @Test
    fun `no bump is offered while a movement is unfinished`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val row = movement(vm, "Bench Press")
            vm.onSetTap(row.working.first())
            advanceUntilIdle()

            assertThat(movement(vm, "Bench Press").bump).isNull()
        }
    }

    @Test
    fun `completing every working set offers the next weight up`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            completeWorkingSets(vm, "Bench Press")
            advanceUntilIdle()

            val bump = movement(vm, "Bench Press").bump
            assertThat(bump).isNotNull()
            assertThat(bump!!.isArmed).isFalse()
            assertThat(bump.targetKg).isEqualTo(62.5)
        }
    }

    @Test
    fun `a failed set offers no bump and leaves the weight alone`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            val row = movement(vm, "Bench Press")
            vm.onSetTap(row.working[0])
            vm.onSetTap(row.working[1])
            vm.onSetDoubleTap(row.working[2])
            advanceUntilIdle()

            assertThat(movement(vm, "Bench Press").bump).isNull()
            assertThat(programFlow.value.itemById(1)?.currentWeightKg).isEqualTo(60.0)
        }
    }

    @Test
    fun `taking the bump writes the new weight to the program, not to this session`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            completeWorkingSets(vm, "Bench Press")
            advanceUntilIdle()

            vm.onBumpToggle(1L)
            advanceUntilIdle()

            assertThat(programFlow.value.itemById(1)?.currentWeightKg).isEqualTo(62.5)
            // The session still records the weight actually lifted.
            assertThat(movement(vm, "Bench Press").weightKg).isEqualTo(60.0)
            assertThat(movement(vm, "Bench Press").bump?.isArmed).isTrue()
        }
    }

    @Test
    fun `tapping an armed bump gives it back`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            completeWorkingSets(vm, "Bench Press")
            advanceUntilIdle()
            vm.onBumpToggle(1L)
            advanceUntilIdle()

            vm.onBumpToggle(1L)
            advanceUntilIdle()

            assertThat(programFlow.value.itemById(1)?.currentWeightKg).isEqualTo(60.0)
            assertThat(movement(vm, "Bench Press").bump?.isArmed).isFalse()
        }
    }

    @Test
    fun `reverting a set disarms a bump that was already taken`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            completeWorkingSets(vm, "Bench Press")
            advanceUntilIdle()
            vm.onBumpToggle(1L)
            advanceUntilIdle()

            vm.onSetLongPress(movement(vm, "Bench Press").working.last())
            advanceUntilIdle()

            assertThat(programFlow.value.itemById(1)?.currentWeightKg).isEqualTo(60.0)
            assertThat(movement(vm, "Bench Press").bump).isNull()
        }
    }

    @Test
    fun `an assisted movement bumps by dropping assistance`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            completeWorkingSets(vm, "Assisted Dips")
            advanceUntilIdle()

            assertThat(movement(vm, "Assisted Dips").bump?.targetKg).isEqualTo(37.5)
        }
    }

    @Test
    fun `an assisted movement shows its effective load`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            // Bodyweight 80, 40 kg of assistance.
            assertThat(movement(vm, "Assisted Dips").effectiveLoadKg).isEqualTo(40.0)
            assertThat(movement(vm, "Bench Press").effectiveLoadKg).isNull()
        }
    }

    // ---- weight edits ----

    @Test
    fun `a manual weight edit changes both this session and the program`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onMovementWeightChange(1L, 65.0)
            advanceUntilIdle()

            assertThat(movement(vm, "Bench Press").weightKg).isEqualTo(65.0)
            assertThat(programFlow.value.itemById(1)?.currentWeightKg).isEqualTo(65.0)
        }
    }

    // ---- rest week ----

    @Test
    fun `the rest-week prompt appears after enough logged sessions`() {
        runTest(testDispatcher) {
            historyFlow.value = List(REST_WEEK_SESSIONS) { session(today.minusDays(1), "B") }
            val vm = newViewModel()
            advanceUntilIdle()

            assertThat(ready(vm).restWeekPrompt).isTrue()
        }
    }

    @Test
    fun `taking a rest week drops every movement one increment`() {
        runTest(testDispatcher) {
            historyFlow.value = List(REST_WEEK_SESSIONS) { session(today.minusDays(1), "B") }
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onRestWeekAccept()
            advanceUntilIdle()

            assertThat(programFlow.value.itemById(1)?.currentWeightKg).isEqualTo(57.5)
            // Assisted goes the other way: more assistance is the easier direction.
            assertThat(programFlow.value.itemById(2)?.currentWeightKg).isEqualTo(42.5)
            assertThat(ready(vm).restWeekPrompt).isFalse()
        }
    }

    @Test
    fun `snoozing the rest week hides the prompt for now`() {
        runTest(testDispatcher) {
            historyFlow.value = List(REST_WEEK_SESSIONS) { session(today.minusDays(1), "B") }
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onRestWeekSnooze()
            advanceUntilIdle()

            assertThat(ready(vm).restWeekPrompt).isFalse()
        }
    }

    // ---- commit ----

    @Test
    fun `committing records the day key, the reps performed and the order`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllVisible(vm)
            advanceUntilIdle()
            resolveAllVisible(vm)
            advanceUntilIdle()

            vm.onFeedback(Feedback.Green)
            advanceUntilIdle()

            val committed = historyFlow.value.single()
            assertThat(committed.dayKey).isEqualTo("A")
            assertThat(committed.feedback).isEqualTo(Feedback.Green)
            assertThat(committed.circuitWeightKg).isEqualTo(16.0)
            assertThat(committed.bodyweightKg).isEqualTo(80.0)

            val benchSet = committed.sets.first { it.exerciseSlug == "bench_press" && !it.isPriming }
            assertThat(benchSet.targetReps).isEqualTo(8)
            assertThat(benchSet.targetRepsMax).isEqualTo(12)
            // Circuit is position 0, then the two main movements, then the accessory.
            assertThat(committed.sets.map { it.position }.distinct()).isInOrder()
        }
    }

    @Test
    fun `committing bootstraps the following day`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllVisible(vm)
            advanceUntilIdle()
            resolveAllVisible(vm)
            advanceUntilIdle()

            vm.onFeedback(Feedback.Green)
            advanceUntilIdle()

            assertThat(ready(vm).dayKey).isEqualTo("B")
        }
    }

    // ---- admin ----

    @Test
    fun `forcing a day rebuilds the session on that day`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.forceDay("B")
            advanceUntilIdle()

            assertThat(ready(vm).dayKey).isEqualTo("B")
            assertThat(ready(vm).dayName).isEqualTo("Pull")
        }
    }

    @Test
    fun `the day list is exposed for the admin picker`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            assertThat(vm.days.value.map { it.key }).containsExactly("A", "B").inOrder()
        }
    }

    // ---- rest guide ----

    @Test
    fun `resolving a set starts the rest guide and reverting one does not clear it`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            assertThat(vm.restStartedAtMillis.value).isNull()

            val row = movement(vm, "Bench Press")
            vm.onSetTap(row.working.first())
            advanceUntilIdle()
            assertThat(vm.restStartedAtMillis.value).isEqualTo(fixedClock.millis())

            vm.onSetLongPress(movement(vm, "Bench Press").working.first())
            advanceUntilIdle()
            assertThat(vm.restStartedAtMillis.value).isEqualTo(fixedClock.millis())
        }
    }

    // ---- helpers ----

    /** Marks every currently visible button complete. */
    private fun resolveAllVisible(vm: TrackerViewModel) {
        ready(vm).groups.forEach { group ->
            when (group) {
                is GroupBlock.Circuit -> group.rounds.forEach(vm::onSetTap)
                is GroupBlock.Standard -> group.movements.forEach { row ->
                    (row.leadIn + row.working).forEach(vm::onSetTap)
                }
            }
        }
    }

    private fun completeWorkingSets(vm: TrackerViewModel, name: String) {
        movement(vm, name).working.forEach(vm::onSetTap)
    }
}
