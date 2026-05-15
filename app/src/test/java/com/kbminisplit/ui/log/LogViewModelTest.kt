package com.kbminisplit.ui.log

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
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
    private lateinit var sessionRepository: SessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk(relaxed = true) {
            every { observeAll() } returns historyFlow
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = LogViewModel(sessionRepository, fixedClock)

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
    fun `onCellTap with a logged date opens the matching detail`() {
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
            assertThat(detail.split).isEqualTo(Split.A)
            assertThat(detail.kbWeightKg).isEqualTo(16.0)
            assertThat(detail.kbCircuits).hasSize(3)
            assertThat(detail.strength).hasSize(2)
        }
    }

    @Test
    fun `detail strength rows carry weight target reps and per-set statuses`() {
        val sessionDate = today.minusDays(1)
        historyFlow.value = listOf(sessionWithSets(sessionDate, Feedback.Green))

        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onCellTap(sessionDate)
            advanceUntilIdle()

            val detail = vm.selected.value!!
            val pulldown = detail.strength.single {
                it.exerciseDisplayName == ExerciseCatalog.LatPulldown.displayName
            }
            assertThat(pulldown.weightKg).isEqualTo(50.0)
            assertThat(pulldown.targetReps).isEqualTo(8)
            assertThat(pulldown.primeStatus).isEqualTo(SetStatus.Completed)
            assertThat(pulldown.workingStatuses).containsExactly(
                SetStatus.Completed,
                SetStatus.Completed,
                SetStatus.Failed,
            ).inOrder()
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

    // ---- helpers ----

    private fun sessionWithSets(date: LocalDate, feedback: Feedback): Session {
        val kbSets = (0 until 3).map { idx ->
            SetEntry(
                exerciseSlug = ExerciseCatalog.KbFlow.slug,
                setIndex = idx,
                isPriming = false,
                targetReps = null,
                weightKg = 16.0,
                status = SetStatus.Completed,
            )
        }
        val pulldownSets = strengthSets(ExerciseCatalog.LatPulldown.slug, weightKg = 50.0)
        val rowSets = strengthSets(ExerciseCatalog.BarbellRow.slug, weightKg = 40.0)
        return Session(
            date = date,
            split = Split.A,
            feedback = feedback,
            kbWeightKg = 16.0,
            sets = kbSets + pulldownSets + rowSets,
        )
    }

    private fun strengthSets(slug: String, weightKg: Double): List<SetEntry> {
        val prime = SetEntry(
            exerciseSlug = slug,
            setIndex = 0,
            isPriming = true,
            targetReps = null,
            weightKg = weightKg,
            status = SetStatus.Completed,
        )
        val working = listOf(SetStatus.Completed, SetStatus.Completed, SetStatus.Failed)
            .mapIndexed { idx, status ->
                SetEntry(
                    exerciseSlug = slug,
                    setIndex = idx + 1,
                    isPriming = false,
                    targetReps = 8,
                    weightKg = weightKg,
                    status = status,
                )
            }
        return listOf(prime) + working
    }
}
