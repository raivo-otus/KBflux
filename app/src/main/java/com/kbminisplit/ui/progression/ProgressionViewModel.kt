package com.kbminisplit.ui.progression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class ProgressionDataPoint(
    val date: LocalDate,
    val weightKg: Double,
    val targetReps: Int?,
)

data class MovementProgression(
    val exercise: Exercise,
    val dataPoints: List<ProgressionDataPoint>,
)

data class ProgressionUiState(
    val kbProgression: MovementProgression,
    val strengthProgression: List<MovementProgression>,
)

@HiltViewModel
class ProgressionViewModel @Inject constructor(
    sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<ProgressionUiState> = sessionRepository.observeAll()
        .map { sessions ->
            // sessions are expected to be sorted by date from the DB.
            val lastSessions = sessions.takeLast(60) // Buffer to ensure we have enough relevant points.

            // KB Flow
            val kbDataPoints = lastSessions.map { session ->
                ProgressionDataPoint(
                    date = session.date,
                    weightKg = session.kbWeightKg,
                    targetReps = null
                )
            }.takeLast(30)

            // Strength movements
            val strengthExercises = listOf(
                ExerciseCatalog.LatPulldown,
                ExerciseCatalog.BarbellRow,
                ExerciseCatalog.Bench,
                ExerciseCatalog.Ohp,
                ExerciseCatalog.HighBarSquat,
                ExerciseCatalog.RomanianDeadlift
            )

            val strengthProgression = strengthExercises.map { exercise ->
                val dataPoints = lastSessions.mapNotNull { session ->
                    // In our model, a strength movement in a session has 1 prime + 3 working sets.
                    // All 3 working sets share the same target weight and reps for that session.
                    val set = session.sets.firstOrNull { it.exerciseSlug == exercise.slug && !it.isPriming }
                    set?.let {
                        ProgressionDataPoint(
                            date = session.date,
                            weightKg = it.weightKg,
                            targetReps = it.targetReps
                        )
                    }
                }.takeLast(30)
                MovementProgression(exercise, dataPoints)
            }

            ProgressionUiState(
                kbProgression = MovementProgression(ExerciseCatalog.KbFlow, kbDataPoints),
                strengthProgression = strengthProgression
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProgressionUiState(
                kbProgression = MovementProgression(ExerciseCatalog.KbFlow, emptyList()),
                strengthProgression = emptyList()
            )
        )
}
