package com.kbminisplit.ui.log

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.repository.ProgramRepository
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.progression.day
import com.kbminisplit.domain.progression.program
import com.kbminisplit.domain.progression.workingSets
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
class LogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"))
    private val today: LocalDate = LocalDate.now(fixedClock)

    private val historyFlow = MutableStateFlow<List<Session>>(emptyList())
    private val programFlow = MutableStateFlow(program(day(1, "A", "Pull")))
    private val namesFlow = MutableStateFlow(
        mapOf("kb_flow" to "Kettlebell flow", "lat_pulldown" to "Lat Pulldown"),
    )

    private lateinit var sessionRepository: SessionRepository
    private lateinit var programRepository: ProgramRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk(relaxed = true) {
            every { observeAll() } returns historyFlow
        }
        programRepository = mockk(relaxed = true) {
            every { observeProgram() } returns programFlow
            every { observeExerciseNames() } returns namesFlow
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = LogViewModel(sessionRepository, programRepository, fixedClock)

    @Test
    fun `state becomes Ready with non-empty rows after first emission`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as LogUiState.Ready
            assertThat(ready.rows).isNotEmpty()
            assertThat(ready.todayRowIndex).isAtLeast(0)
        }
    }

    @Test
    fun `state reflects session feedback as Logged cell`() {
        val sessionDate = today.minusDays(2)
        historyFlow.value = listOf(sessionWithSets(sessionDate, Feedback.Yellow))

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as LogUiState.Ready
            val cell = ready.rows.filterIsInstance<LogRow.Week>()
                .flatMap { it.days }
                .single { it.date == sessionDate && it.state !is DayCellState.Outside }
            assertThat(cell.state).isEqualTo(DayCellState.Logged(Feedback.Yellow))
        }
    }

    @Test
    fun `onCellTap opens the detail labelled with the day's name`() {
        val sessionDate = today.minusDays(2)
        historyFlow.value = listOf(sessionWithSets(sessionDate, Feedback.Green))

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(sessionDate)
            advanceUntilIdle()

            val detail = vm.selected.value
            assertThat(detail).isNotNull()
            assertThat(detail!!.date).isEqualTo(sessionDate)
            assertThat(detail.feedback).isEqualTo(Feedback.Green)
            assertThat(detail.dayLabel).isEqualTo("Pull")
            assertThat(detail.movements.map { it.name })
                .containsExactly("Kettlebell flow", "Lat Pulldown").inOrder()
        }
    }

    @Test
    fun `a session on a since-deleted day falls back to its raw key`() {
        val sessionDate = today.minusDays(2)
        historyFlow.value = listOf(sessionWithSets(sessionDate, Feedback.Green, dayKey = "GONE"))

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(sessionDate)
            advanceUntilIdle()

            assertThat(vm.selected.value!!.dayLabel).isEqualTo("GONE")
        }
    }

    @Test
    fun `detail movements carry weight, rep range and per-set statuses in order`() {
        val sessionDate = today.minusDays(1)
        historyFlow.value = listOf(sessionWithSets(sessionDate, Feedback.Green))

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(sessionDate)
            advanceUntilIdle()

            val pulldown = vm.selected.value!!.movements.single { it.name == "Lat Pulldown" }
            assertThat(pulldown.weightKg).isEqualTo(50.0)
            assertThat(pulldown.repsLabel).isEqualTo("8–12")
            assertThat(pulldown.statuses).containsExactly(
                SetStatus.Completed, // prime
                SetStatus.Completed,
                SetStatus.Completed,
                SetStatus.Failed,
            ).inOrder()
        }
    }

    @Test
    fun `a circuit shows no rep label`() {
        val sessionDate = today.minusDays(1)
        historyFlow.value = listOf(sessionWithSets(sessionDate, Feedback.Green))

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(sessionDate)
            advanceUntilIdle()

            val circuit = vm.selected.value!!.movements.single { it.name == "Kettlebell flow" }
            assertThat(circuit.repsLabel).isNull()
            assertThat(circuit.statuses).hasSize(3)
        }
    }

    @Test
    fun `a session logged before rep ranges shows a single number`() {
        val sessionDate = today.minusDays(3)
        val legacySet = SetEntry(
            exerciseSlug = "lat_pulldown",
            setIndex = 1,
            isPriming = false,
            targetReps = 10,
            targetRepsMax = null,
            weightKg = 45.0,
            status = SetStatus.Completed,
            position = 0,
        )
        historyFlow.value = listOf(
            Session(sessionDate, "A", Feedback.Green, 16.0, listOf(legacySet)),
        )

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(sessionDate)
            advanceUntilIdle()

            assertThat(vm.selected.value!!.movements.single().repsLabel).isEqualTo("10")
        }
    }

    @Test
    fun `onCellTap on a date with no session is a no-op`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(today.minusDays(5))
            advanceUntilIdle()

            assertThat(vm.selected.value).isNull()
        }
    }

    @Test
    fun `onDismissDetail clears the selected detail`() {
        val sessionDate = today.minusDays(2)
        historyFlow.value = listOf(sessionWithSets(sessionDate, Feedback.Red))

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(sessionDate)
            advanceUntilIdle()
            assertThat(vm.selected.value).isNotNull()

            vm.onDismissDetail()
            advanceUntilIdle()
            assertThat(vm.selected.value).isNull()
        }
    }

    @Test
    fun `an empty program still renders the grid`() {
        programFlow.value = Program.EMPTY

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            assertThat((vm.state.value as LogUiState.Ready).rows).isNotEmpty()
        }
    }

    // ---- helpers ----

    private fun sessionWithSets(
        date: LocalDate,
        feedback: Feedback,
        dayKey: String = "A",
    ): Session {
        val circuit = (0 until 3).map { idx ->
            SetEntry(
                exerciseSlug = "kb_flow",
                setIndex = idx,
                isPriming = false,
                targetReps = null,
                targetRepsMax = null,
                weightKg = 16.0,
                status = SetStatus.Completed,
                position = 0,
            )
        }
        val prime = SetEntry(
            exerciseSlug = "lat_pulldown",
            setIndex = 0,
            isPriming = true,
            targetReps = null,
            targetRepsMax = null,
            weightKg = 50.0,
            status = SetStatus.Completed,
            position = 1,
        )
        val working = workingSets(
            slug = "lat_pulldown",
            weightKg = 50.0,
            position = 1,
            statuses = listOf(SetStatus.Completed, SetStatus.Completed, SetStatus.Failed),
        )
        return Session(
            date = date,
            dayKey = dayKey,
            feedback = feedback,
            circuitWeightKg = 16.0,
            sets = circuit + prime + working,
        )
    }
}
