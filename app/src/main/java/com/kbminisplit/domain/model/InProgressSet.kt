package com.kbminisplit.domain.model

/**
 * A live set button in the session being tracked right now.
 *
 * Unlike a committed [SetEntry] this carries its row [id] and the program slot it
 * came from. Buttons are addressed by [id] because a user-defined day may
 * legitimately program the same movement twice, which the exercise slug alone
 * cannot tell apart.
 *
 * [programItemId] is 0 for a circuit group's round rows, which belong to the
 * group rather than to any single movement.
 */
data class InProgressSet(
    val id: Long,
    val programGroupId: Long,
    val programItemId: Long,
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val targetRepsMax: Int?,
    val weightKg: Double,
    val status: SetStatus,
    val position: Int,
) {
    val isCircuitRound: Boolean get() = programItemId == NO_ITEM

    fun toSetEntry(): SetEntry = SetEntry(
        exerciseSlug = exerciseSlug,
        setIndex = setIndex,
        isPriming = isPriming,
        targetReps = targetReps,
        targetRepsMax = targetRepsMax,
        weightKg = weightKg,
        status = status,
        position = position,
    )

    companion object {
        /** [programItemId] sentinel for rows owned by a group rather than a movement. */
        const val NO_ITEM = 0L
    }
}
