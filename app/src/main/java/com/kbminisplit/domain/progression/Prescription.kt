package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.model.Prescription
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split

/**
 * Today's prescription for a strength movement, derived from history per spec §9.2.
 *
 * Rule:
 *  - Deload: If the user fails to complete the sets and/or logs red for a split
 *    on three consecutive sessions, reduce weight by 1 step and reset to min reps.
 *  - Standard Progression:
 *    - Look at the most recent session that contained this movement's working sets.
 *    - If any working set failed → repeat (same weight, same target reps).
 *    - Else if target reps < maxReps → same weight, target reps + 1.
 *    - Else (target reps == maxReps, all completed) → weight ± step, target reps reset to minReps.
 *  - If movement was never logged → onboarding starting values.
 *
 * For an [ExerciseMechanic.ASSISTED] movement the weight direction inverts:
 * graduation *reduces* the assistance pin (floored at 0) and deload *adds*
 * assistance, since the logged number is help, not load.
 *
 * `history` is expected in chronological order (oldest first).
 */
fun getPrescription(
    history: List<Session>,
    exercise: Exercise,
    onboarding: OnboardingDefaults,
): Prescription {
    val split = splitFor(exercise)
    if (split != null && shouldDeload(history, split)) {
        val lastWithMovement = history.lastOrNull { session ->
            session.sets.any { it.exerciseSlug == exercise.slug && !it.isPriming }
        }
        val lastWeight = if (lastWithMovement != null) {
            lastWithMovement.sets.first { it.exerciseSlug == exercise.slug && !it.isPriming }.weightKg
        } else {
            startingWeightFor(exercise, onboarding)
        }
        // Deload makes the session easier. TRADITIONAL → less weight; ASSISTED →
        // *more* assistance (higher pin), which is the same "easier" direction.
        return Prescription(
            exercise = exercise,
            weightKg = deloadWeightFor(exercise, lastWeight),
            targetReps = exercise.minReps
        )
    }

    val maxReps = onboarding.standardMaxReps

    val lastWithMovement = history.lastOrNull { session ->
        session.sets.any { it.exerciseSlug == exercise.slug && !it.isPriming }
    }

    if (lastWithMovement == null) {
        val startingWeight = startingWeightFor(exercise, onboarding)
        // If the global starting reps are outside this movement's range, use its minimum
        val reps = if (onboarding.startingTargetReps > maxReps) {
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
        reps < maxReps -> Prescription(exercise, weight, reps + 1)
        // Graduation: hit max reps on every set. TRADITIONAL adds load; ASSISTED
        // drops a step of assistance (floored at 0 — i.e. unassisted bodyweight).
        else -> Prescription(exercise, graduatedWeightFor(exercise, weight), exercise.minReps)
    }
}

/** Next weight after a deload, respecting the movement's [ExerciseMechanic]. */
private fun deloadWeightFor(exercise: Exercise, lastWeight: Double): Double =
    when (exercise.mechanic) {
        ExerciseMechanic.TRADITIONAL -> (lastWeight - exercise.weightStepKg).coerceAtLeast(0.0)
        ExerciseMechanic.ASSISTED -> lastWeight + exercise.weightStepKg
    }

/** Next weight after hitting the rep ceiling, respecting the [ExerciseMechanic]. */
private fun graduatedWeightFor(exercise: Exercise, weight: Double): Double =
    when (exercise.mechanic) {
        ExerciseMechanic.TRADITIONAL -> weight + exercise.weightStepKg
        ExerciseMechanic.ASSISTED -> (weight - exercise.weightStepKg).coerceAtLeast(0.0)
    }

/**
 * Starting weight for a movement's first-ever session: the onboarding value if
 * one exists, otherwise the movement's [Exercise.defaultStartingWeightKg] fallback
 * (auxiliary movements aren't onboarded).
 */
private fun startingWeightFor(exercise: Exercise, onboarding: OnboardingDefaults): Double =
    onboarding.startingWeightsBySlug[exercise.slug]
        ?: exercise.defaultStartingWeightKg
        ?: error("No starting weight (onboarding or default) for ${exercise.slug}")

private fun shouldDeload(history: List<Session>, split: Split): Boolean {
    val lastThreeForSplit = history.asReversed().asSequence()
        .filter { it.split == split }
        .take(3)
        .toList()

    if (lastThreeForSplit.size < 3) return false

    return lastThreeForSplit.all { session ->
        val failedAnyWorkingSet = session.sets.any { set ->
            !set.isPriming &&
                    set.exerciseSlug != ExerciseCatalog.KbFlow.slug &&
                    set.status == SetStatus.Failed
        }
        session.feedback == Feedback.Red || failedAnyWorkingSet
    }
}

private fun splitFor(exercise: Exercise): Split? {
    return Split.entries.find { split ->
        val (m1, m2) = ExerciseCatalog.strengthForSplit(split)
        m1.slug == exercise.slug || m2.slug == exercise.slug
    }
}
