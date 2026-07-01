package com.kbminisplit.ui.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.progression.effectiveLoadKg
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ProgressionDataPoint(
    val date: LocalDate,
    val weightKg: Double,
)

data class MovementProgression(
    val exercise: Exercise,
    val dataPoints: List<ProgressionDataPoint>,
)

data class ProgressionUiState(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val kbProgression: MovementProgression,
    val strengthProgression: List<MovementProgression>,
)

@HiltViewModel
class ProgressionViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ProgressionUiState> = sessionRepository.observeAll()
        .map { sessions -> buildState(sessions, today = LocalDate.now(clock)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = buildState(sessions = emptyList(), today = LocalDate.now(clock)),
        )

    private fun buildState(sessions: List<Session>, today: LocalDate): ProgressionUiState {
        val windowStart = today.minusWeeks(WINDOW_WEEKS)
        // Sessions arrive date-sorted from the DB; keep only those inside the window.
        val windowed = sessions.filter { !it.date.isBefore(windowStart) }

        val kbDataPoints = windowed.map { session ->
            ProgressionDataPoint(date = session.date, weightKg = session.kbWeightKg)
        }

        val strengthProgression = strengthExercises.map { exercise ->
            val dataPoints = windowed.mapNotNull { session ->
                // All working sets of a movement share one target weight within a
                // session, so the first working set carries the session's weight.
                val set = session.sets
                    .firstOrNull { it.exerciseSlug == exercise.slug && !it.isPriming }
                    ?: return@mapNotNull null
                // Assisted movements chart effective load (bodyweight − pin) so the
                // line rises as assistance drops. Points without a bodyweight
                // snapshot can't be placed and are skipped.
                val load = when (exercise.mechanic) {
                    ExerciseMechanic.TRADITIONAL -> set.weightKg
                    ExerciseMechanic.ASSISTED -> session.bodyweightKg
                        ?.let { effectiveLoadKg(exercise.mechanic, set.weightKg, it) }
                        ?: return@mapNotNull null
                }
                ProgressionDataPoint(date = session.date, weightKg = load)
            }
            MovementProgression(exercise, dataPoints)
        }

        return ProgressionUiState(
            windowStart = windowStart,
            windowEnd = today,
            kbProgression = MovementProgression(ExerciseCatalog.KbFlow, kbDataPoints),
            strengthProgression = strengthProgression,
        )
    }

    private companion object {
        /** The charts show a sliding window over the most recent 8 weeks (§6.1). */
        const val WINDOW_WEEKS = 8L

        val strengthExercises = listOf(
            ExerciseCatalog.LatPulldown,
            ExerciseCatalog.BarbellRow,
            ExerciseCatalog.Bench,
            ExerciseCatalog.AssistedDip,
            ExerciseCatalog.HighBarSquat,
            ExerciseCatalog.RomanianDeadlift,
        )
    }
}
