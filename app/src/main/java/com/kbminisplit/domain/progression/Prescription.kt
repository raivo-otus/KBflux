package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.model.Prescription
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetStatus

/**
 * Today's prescription for a strength movement, derived from history per spec §9.2.
 *
 * Rule:
 *  - Look at the most recent session that contained this movement's working sets.
 *  - If any working set failed → repeat (same weight, same target reps).
 *  - Else if target reps < 16 → same weight, target reps + 1.
 *  - Else (target reps == 16, all completed) → weight + step, target reps reset to 8.
 *  - If movement was never logged → onboarding starting values.
 *
 * `history` is expected in chronological order (oldest first).
 */
fun prescription(
    history: List<Session>,
    exercise: Exercise,
    onboarding: OnboardingDefaults,
): Prescription {
    val lastWithMovement = history.lastOrNull { session ->
        session.sets.any { it.exerciseSlug == exercise.slug && !it.isPriming }
    }

    if (lastWithMovement == null) {
        val startingWeight = onboarding.startingWeightsBySlug[exercise.slug]
            ?: error("No onboarding starting weight for ${exercise.slug}")
        // If the global starting reps are outside this movement's range, use its minimum
        val reps = if (onboarding.startingTargetReps > exercise.maxReps) {
            exercise.minReps
        } else {
            onboarding.startingTargetReps.coerceAtLeast(exercise.minReps)
        }
        return Prescription(exercise, startingWeight, reps)
    }

    val workingSets = lastWithMovement.sets.filter {
        it.exerciseSlug == exercise.slug && !it.isPriming
    }
    val reference = workingSets.first()
    val weight = reference.weightKg
    val reps = reference.targetReps
        ?: error("Strength working set must have targetReps (${exercise.slug})")
    val allCompleted = workingSets.all { it.status == SetStatus.Completed }

    return when {
        !allCompleted -> Prescription(exercise, weight, reps)
        reps < exercise.maxReps -> Prescription(exercise, weight, reps + 1)
        else -> Prescription(exercise, weight + exercise.weightStepKg, exercise.minReps)
    }
}
