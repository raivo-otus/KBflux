package com.kbminisplit.domain.model

/**
 * One logged set.
 *
 * Reps are a range: [targetReps] is the low end and [targetRepsMax] the high end.
 * Both are null for circuit rounds, which track completion rather than reps.
 * Sessions logged before rep ranges existed carry a single [targetReps] with a
 * null [targetRepsMax], and render as one number.
 *
 * [position] is the movement's ordinal within the session as actually performed,
 * i.e. after group rotation — it is what the Log orders by.
 */
data class SetEntry(
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val targetRepsMax: Int? = null,
    val weightKg: Double,
    val status: SetStatus,
    val position: Int = 0,
) {
    /** "8–12", "12", or null for a set that doesn't track reps. */
    val repRangeLabel: String?
        get() = when {
            targetReps == null -> null
            targetRepsMax == null || targetRepsMax == targetReps -> "$targetReps"
            else -> "$targetReps–$targetRepsMax"
        }
}
