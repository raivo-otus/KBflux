package com.kbminisplit.ui.tracker

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import java.time.LocalDate

/**
 * Identity of a single button on the Tracker. Mirrors the unique index on
 * `in_progress_set` so the ViewModel can address sets without ambiguity.
 */
data class SetCell(
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val status: SetStatus,
)

/** Visual reference for a KB movement in the section header — no button attached. */
data class KbMovementLabel(
    val exercise: Exercise,
    val repsLabel: String,
)

/**
 * The KB Flow block: 5 movements shown as labels for reference, and three
 * round buttons — one per circuit (spec §2.2). Set tracking is at the circuit
 * level, not per movement.
 */
data class KbBlock(
    val movements: List<KbMovementLabel>,
    val circuits: List<SetCell>,
)

/** One row in the strength block: a movement, its weight + target reps, prime button, three working buttons. */
data class StrengthMovementRow(
    val exercise: Exercise,
    val weightKg: Double,
    val targetReps: Int,
    val prime: SetCell,
    val working: List<SetCell>,
)

sealed interface TrackerUiState {
    data object Loading : TrackerUiState

    data class Ready(
        val date: LocalDate,
        val split: Split,
        val kbWeightKg: Double,
        val kbBlock: KbBlock,
        val strength: List<StrengthMovementRow>,
        val allButtonsResolved: Boolean,
        val kbBump: KbBumpState?,
        val isFirstSession: Boolean = false,
    ) : TrackerUiState
}

/** State of the monthly KB-bump prompt (spec §9.3). */
data class KbBumpState(
    val currentKg: Double,
    val targetKg: Double,
)
