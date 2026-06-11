package com.kbminisplit.ui.progression

import app.cash.turbine.test
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val historyFlow = MutableStateFlow<List<Session>>(emptyList())
    private lateinit var sessionRepository: SessionRepository

    // Fixed "today" so the 8-week window is deterministic: 2023-03-01,
    // making the window start 2023-01-04.
    private val fixedClock = Clock.fixed(Instant.parse("2023-03-01T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2023, 3, 1)
    private val windowStart = today.minusWeeks(8)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk {
            every { observeAll() } returns historyFlow
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has window bounds and empty charts for every movement`() = runTest {
        val vm = ProgressionViewModel(sessionRepository, fixedClock)
        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.windowStart).isEqualTo(windowStart)
            assertThat(state.windowEnd).isEqualTo(today)
            assertThat(state.kbProgression.dataPoints).isEmpty()
            assertThat(state.strengthProgression).hasSize(6)
            state.strengthProgression.forEach { progression ->
                assertThat(progression.dataPoints).isEmpty()
            }
        }
    }

    @Test
    fun `maps sessions to progression data points`() = runTest {
        val vm = ProgressionViewModel(sessionRepository, fixedClock)

        vm.uiState.test {
            assertThat(awaitItem().kbProgression.dataPoints).isEmpty()

            val date1 = LocalDate.of(2023, 2, 1)
            val date2 = LocalDate.of(2023, 2, 4)
            val sessions = listOf(
                createSession(date1, Split.A, 16.0, 50.0, 8),
                createSession(date2, Split.A, 16.0, 52.5, 8),
            )
            historyFlow.value = sessions

            val state = awaitItem()

            // KB Progression
            assertThat(state.kbProgression.dataPoints).hasSize(2)
            assertThat(state.kbProgression.dataPoints[0].date).isEqualTo(date1)
            assertThat(state.kbProgression.dataPoints[0].weightKg).isEqualTo(16.0)
            assertThat(state.kbProgression.dataPoints[1].weightKg).isEqualTo(16.0)

            // Strength Progression (Lat Pulldown)
            val pulldown = state.strengthProgression.first { it.exercise == ExerciseCatalog.LatPulldown }
            assertThat(pulldown.dataPoints).hasSize(2)
            assertThat(pulldown.dataPoints[0].weightKg).isEqualTo(50.0)
            assertThat(pulldown.dataPoints[1].weightKg).isEqualTo(52.5)
        }
    }

    @Test
    fun `excludes sessions older than the 8-week window`() = runTest {
        val vm = ProgressionViewModel(sessionRepository, fixedClock)

        vm.uiState.test {
            assertThat(awaitItem().kbProgression.dataPoints).isEmpty()

            val sessions = listOf(
                createSession(windowStart.minusWeeks(4), Split.A, 12.0, 40.0, 8),
                createSession(windowStart.minusDays(1), Split.A, 12.0, 42.5, 8),
                createSession(windowStart, Split.A, 16.0, 45.0, 8),
                createSession(windowStart.plusWeeks(3), Split.A, 16.0, 47.5, 8),
                createSession(today, Split.A, 16.0, 50.0, 8),
            )
            historyFlow.value = sessions

            val state = awaitItem()

            // Only the boundary date and newer survive.
            assertThat(state.kbProgression.dataPoints).hasSize(3)
            assertThat(state.kbProgression.dataPoints.first().date).isEqualTo(windowStart)
            assertThat(state.kbProgression.dataPoints.last().date).isEqualTo(today)

            val pulldown = state.strengthProgression.first { it.exercise == ExerciseCatalog.LatPulldown }
            assertThat(pulldown.dataPoints.map { it.weightKg }).containsExactly(45.0, 47.5, 50.0).inOrder()
        }
    }

    private fun createSession(
        date: LocalDate,
        split: Split,
        kbWeight: Double,
        strengthWeight: Double,
        targetReps: Int,
    ): Session {
        val exercise = ExerciseCatalog.strengthForSplit(split).first
        val sets = listOf(
            SetEntry(exercise.slug, 0, true, null, strengthWeight, SetStatus.Completed),
            SetEntry(exercise.slug, 1, false, targetReps, strengthWeight, SetStatus.Completed),
            SetEntry(exercise.slug, 2, false, targetReps, strengthWeight, SetStatus.Completed),
            SetEntry(exercise.slug, 3, false, targetReps, strengthWeight, SetStatus.Completed),
        )
        return Session(
            date = date,
            split = split,
            feedback = Feedback.Green,
            kbWeightKg = kbWeight,
            sets = sets,
        )
    }
}
