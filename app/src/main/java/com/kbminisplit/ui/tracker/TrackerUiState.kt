package com.kbminisplit.ui.tracker

import com.kbminisplit.domain.model.SetStatus
import java.time.LocalDate

/**
 * Identity of a single button on the Tracker: the `in_progress_set` row it drives.
 *
 * Addressing by row id rather than by exercise means a day can legitimately
 * program the same movement twice without the two colliding.
 *
 * [weightKg] is the number shown inside the circle for a lead-in set; working and
 * circuit cells carry it too but don't render it.
 */
data class SetCell(
    val id: Long,
    val status: SetStatus,
    val weightKg: Double,
)

/** Visual reference for one movement in a circuit header — no button attached. */
data class CircuitMovement(
    val name: String,
    val repsLabel: String,
)

/**
 * The offer to move up a weight, shown once every working set of a movement is
 * completed. [isArmed] means the user has already taken it and the next session
 * will start at [targetKg]; tapping again puts it back.
 */
data class BumpState(
    val targetKg: Double,
    val isArmed: Boolean,
)

/** The three-month offer to move a ladder circuit onto the next bell. */
data class CircuitBumpState(
    val currentKg: Double,
    val targetKg: Double,
)

/** One movement in a standard group: its numbers, its buttons, and its bump offer. */
data class MovementRow(
    val programItemId: Long,
    val name: String,
    val weightKg: Double,
    val repRangeLabel: String,
    /** Prime then warm-up; empty when the movement is programmed with no lead-in. */
    val leadIn: List<SetCell>,
    val working: List<SetCell>,
    /**
     * Physiological load for an assisted movement (bodyweight − pin), for the
     * "Effective: X kg" subtext. Null for traditional lifts or when no bodyweight
     * is known yet.
     */
    val effectiveLoadKg: Double? = null,
    val bump: BumpState? = null,
) {
    val isResolved: Boolean
        get() = (leadIn + working).all { it.status != SetStatus.Pending }

    val isWorkComplete: Boolean
        get() = working.isNotEmpty() && working.all { it.status == SetStatus.Completed }
}

/** A block of today's session, rendered according to how its group is programmed. */
sealed interface GroupBlock {
    val groupId: Long
    val name: String
    val isResolved: Boolean

    /** Movements are labels; one button per round covers the whole group. */
    data class Circuit(
        override val groupId: Long,
        override val name: String,
        val weightKg: Double,
        val movements: List<CircuitMovement>,
        val rounds: List<SetCell>,
        val bump: CircuitBumpState? = null,
    ) : GroupBlock {
        override val isResolved: Boolean
            get() = rounds.isNotEmpty() && rounds.all { it.status != SetStatus.Pending }
    }

    /** Every movement gets its own lead-in and working buttons. */
    data class Standard(
        override val groupId: Long,
        override val name: String,
        val movements: List<MovementRow>,
    ) : GroupBlock {
        override val isResolved: Boolean
            get() = movements.isNotEmpty() && movements.all { it.isResolved }
    }
}

sealed interface TrackerUiState {
    data object Loading : TrackerUiState

    /** The program has no days — nothing can be prescribed until one is added. */
    data object NoProgram : TrackerUiState

    data class Ready(
        val date: LocalDate,
        val dayKey: String,
        val dayName: String,
        /** Groups revealed so far, in program order. Deferred ones appear later. */
        val groups: List<GroupBlock>,
        /** Everything the session needs is resolved — show the feedback sheet. */
        val feedbackReady: Boolean = false,
        val isFirstSession: Boolean = false,
        /** Weekly bodyweight check-in is due (assisted movement today + entry stale). */
        val bodyweightPrompt: Boolean = false,
        /** Latest known bodyweight, used to prefill the check-in dialog. */
        val currentBodyweightKg: Double? = null,
        /** Two months of consistent logging — offer a rest week and a deload. */
        val restWeekPrompt: Boolean = false,
    ) : TrackerUiState
}
