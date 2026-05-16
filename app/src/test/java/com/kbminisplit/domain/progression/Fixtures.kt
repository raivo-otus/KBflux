package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import java.time.LocalDate

/** Builds the working sets (3 of them) for a strength movement at a given weight/reps. */
internal fun workingSets(
    exercise: Exercise,
    weight: Double,
    reps: Int,
    statuses: List<SetStatus> = List(3) { SetStatus.Completed },
): List<SetEntry> {
    require(statuses.size == 3) { "Working block has 3 sets, got ${statuses.size}" }
    return statuses.mapIndexed { idx, s ->
        SetEntry(
            exerciseSlug = exercise.slug,
            setIndex = idx + 1, // 0 is the prime
            isPriming = false,
            targetReps = reps,
            weightKg = weight,
            status = s,
        )
    }
}

internal fun primingSet(exercise: Exercise, weight: Double): SetEntry =
    SetEntry(
        exerciseSlug = exercise.slug,
        setIndex = 0,
        isPriming = true,
        targetReps = null,
        weightKg = weight,
        status = SetStatus.Completed,
    )

/**
 * A canonical strength session containing prime + 3 working sets for both
 * movements of `split` at the given weight/reps. Working sets default to all completed.
 */
internal fun strengthSession(
    date: LocalDate,
    split: Split,
    kbWeight: Double = 16.0,
    feedback: Feedback = Feedback.Green,
    m1Weight: Double,
    m1Reps: Int,
    m1Statuses: List<SetStatus> = List(3) { SetStatus.Completed },
    m2Weight: Double = m1Weight,
    m2Reps: Int = m1Reps,
    m2Statuses: List<SetStatus> = List(3) { SetStatus.Completed },
): Session {
    val (m1, m2) = ExerciseCatalog.strengthForSplit(split)
    val sets = buildList {
        add(primingSet(m1, m1Weight - 10.0))
        addAll(workingSets(m1, m1Weight, m1Reps, m1Statuses))
        add(primingSet(m2, m2Weight - 10.0))
        addAll(workingSets(m2, m2Weight, m2Reps, m2Statuses))
    }
    return Session(
        date = date,
        split = split,
        feedback = feedback,
        kbWeightKg = kbWeight,
        sets = sets,
    )
}

internal val DEFAULT_ONBOARDING = OnboardingDefaults(
    kbWeightKg = 16.0,
    startingWeightsBySlug = mapOf(
        ExerciseCatalog.LatPulldown.slug to 50.0,
        ExerciseCatalog.BarbellRow.slug to 40.0,
        ExerciseCatalog.Bench.slug to 60.0,
        ExerciseCatalog.Ohp.slug to 35.0,
        ExerciseCatalog.HighBarSquat.slug to 70.0,
        ExerciseCatalog.RomanianDeadlift.slug to 80.0,
    ),
    startingTargetReps = 8,
    standardMaxReps = 12,
)
