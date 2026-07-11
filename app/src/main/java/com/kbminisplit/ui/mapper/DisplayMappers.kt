package com.kbminisplit.ui.mapper

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.Split
import com.kbminisplit.domain.progression.effectiveLoadKg
import com.kbminisplit.ui.tracker.KbBlock
import com.kbminisplit.ui.tracker.KbMovementLabel
import com.kbminisplit.ui.tracker.SetCell
import com.kbminisplit.ui.tracker.StrengthMovementRow

/** The circuit cells tracked under the [ExerciseCatalog.KbFlow] sentinel slug. */
fun List<SetEntry>.toKbCircuits(): List<SetCell> =
    filter { it.exerciseSlug == ExerciseCatalog.KbFlow.slug }
        .sortedBy { it.setIndex }
        .map { it.toCell() }

/**
 * KB section for the Tracker: the split's themed movements labelled with the
 * positional [repScheme] (spec §2.2, ramped per §9.3), plus the circuit cells.
 */
fun List<SetEntry>.toKbBlock(split: Split, repScheme: List<Int>): KbBlock =
    KbBlock(
        movements = ExerciseCatalog.kbFlowForSplit(split).zip(repScheme) { exercise, reps ->
            KbMovementLabel(
                exercise = exercise,
                repsLabel = if (exercise.isPerSide) "$reps/side" else "$reps",
            )
        },
        circuits = toKbCircuits(),
    )

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
