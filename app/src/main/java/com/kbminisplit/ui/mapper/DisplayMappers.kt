package com.kbminisplit.ui.mapper

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.progression.effectiveLoadKg
import com.kbminisplit.ui.tracker.KbBlock
import com.kbminisplit.ui.tracker.KbMovementLabel
import com.kbminisplit.ui.tracker.SetCell
import com.kbminisplit.ui.tracker.StrengthMovementRow

fun List<SetEntry>.toKbBlock(): KbBlock {
    val circuits = filter { it.exerciseSlug == ExerciseCatalog.KbFlow.slug }
        .sortedBy { it.setIndex }
        .map { it.toCell() }
    
    return KbBlock(
        movements = ExerciseCatalog.kbFlowMovements.map {
            KbMovementLabel(exercise = it, repsLabel = kbRepsLabel(it.slug))
        },
        circuits = circuits,
    )
}

fun List<SetEntry>.toStrengthRows(
    exercises: List<Exercise>,
    bodyweightKg: Double? = null,
): List<StrengthMovementRow> {
    val setsBySlug = groupBy { it.exerciseSlug }
    return exercises.mapNotNull { exercise ->
        val all = setsBySlug[exercise.slug].orEmpty()
        val prime = all.firstOrNull { it.isPriming } ?: return@mapNotNull null
        val working = all.filter { !it.isPriming }.sortedBy { it.setIndex }
        val reference = working.firstOrNull() ?: return@mapNotNull null
        // Effective load only applies to assisted movements and only once a
        // bodyweight is known — otherwise the subtext is simply omitted.
        val effectiveLoad = bodyweightKg
            ?.takeIf { exercise.mechanic == ExerciseMechanic.ASSISTED }
            ?.let { effectiveLoadKg(exercise.mechanic, reference.weightKg, it) }
        StrengthMovementRow(
            exercise = exercise,
            weightKg = reference.weightKg,
            targetReps = reference.targetReps
                ?: error("Strength working set missing targetReps (${exercise.slug})"),
            prime = prime.toCell(),
            working = working.map { it.toCell() },
            effectiveLoadKg = effectiveLoad,
        )
    }
}

fun SetEntry.toCell() = SetCell(
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    status = status,
)

/**
 * Fixed KB rep prescriptions (spec §2.2). These are program constants, not
 * user-tracked, so they live in the UI layer rather than on `Exercise`.
 */
private fun kbRepsLabel(slug: String): String = when (slug) {
    ExerciseCatalog.Swings.slug -> "32"
    ExerciseCatalog.CleanAndPress.slug -> "16/side"
    ExerciseCatalog.GobletSquat.slug -> "8"
    else -> ""
}
