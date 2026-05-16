package com.kbminisplit.ui.progression

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
import java.time.LocalDate
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val historyFlow = MutableStateFlow<List<Session>>(emptyList())
    private lateinit var sessionRepository: SessionRepository

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
    fun `initial state is empty`() = runTest {
        val vm = ProgressionViewModel(sessionRepository)
        vm.uiState.test {
            val state = awaitItem()
            assertThat(state.kbProgression.dataPoints).isEmpty()
            assertThat(state.strengthProgression).isEmpty()
        }
    }

    @Test
    fun `maps sessions to progression data points`() = runTest {
        val vm = ProgressionViewModel(sessionRepository)
        
        vm.uiState.test {
            assertThat(awaitItem().kbProgression.dataPoints).isEmpty()

            val date1 = LocalDate.of(2023, 1, 1)
            val date2 = LocalDate.of(2023, 1, 4)
            val sessions = listOf(
                createSession(date1, Split.A, 16.0, 50.0, 8),
                createSession(date2, Split.A, 16.0, 52.5, 8)
            )
            historyFlow.value = sessions

            val state = awaitItem()
            
            // KB Progression
            assertThat(state.kbProgression.dataPoints).hasSize(2)
            assertThat(state.kbProgression.dataPoints[0].weightKg).isEqualTo(16.0)
            assertThat(state.kbProgression.dataPoints[1].weightKg).isEqualTo(16.0)

            // Strength Progression (Lat Pulldown)
            val pulldown = state.strengthProgression.first { it.exercise == ExerciseCatalog.LatPulldown }
            assertThat(pulldown.dataPoints).hasSize(2)
            assertThat(pulldown.dataPoints[0].weightKg).isEqualTo(50.0)
            assertThat(pulldown.dataPoints[1].weightKg).isEqualTo(52.5)
            assertThat(pulldown.dataPoints[0].targetReps).isEqualTo(8)
        }
    }

    @Test
    fun `limits data points to last 30`() = runTest {
        val vm = ProgressionViewModel(sessionRepository)
        
        vm.uiState.test {
            assertThat(awaitItem().kbProgression.dataPoints).isEmpty()

            val sessions = (1..40).map { i ->
                createSession(LocalDate.of(2023, 1, 1).plusDays(i.toLong()), Split.A, 16.0, 50.0 + i, 8)
            }
            historyFlow.value = sessions

            val state = awaitItem()
            assertThat(state.kbProgression.dataPoints).hasSize(30)
            assertThat(state.strengthProgression.first { it.dataPoints.isNotEmpty() }.dataPoints).hasSize(30)
            
            // Should be the LATEST 30
            assertThat(state.kbProgression.dataPoints.first().date).isEqualTo(LocalDate.of(2023, 1, 1).plusDays(11))
            assertThat(state.kbProgression.dataPoints.last().date).isEqualTo(LocalDate.of(2023, 1, 1).plusDays(40))
        }
    }

    private fun createSession(
        date: LocalDate,
        split: Split,
        kbWeight: Double,
        strengthWeight: Double,
        targetReps: Int
    ): Session {
        val exercise = ExerciseCatalog.strengthForSplit(split).first
        val sets = listOf(
            SetEntry(exercise.slug, 0, true, null, strengthWeight, SetStatus.Completed),
            SetEntry(exercise.slug, 1, false, targetReps, strengthWeight, SetStatus.Completed),
            SetEntry(exercise.slug, 2, false, targetReps, strengthWeight, SetStatus.Completed),
            SetEntry(exercise.slug, 3, false, targetReps, strengthWeight, SetStatus.Completed)
        )
        return Session(
            date = date,
            split = split,
            feedback = Feedback.Green,
            kbWeightKg = kbWeight,
            sets = sets
        )
    }
}
