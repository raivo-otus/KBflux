package com.kbminisplit.ui.mapper

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.Split
import com.kbminisplit.domain.progression.PRIME_FRACTION
import com.kbminisplit.domain.progression.WARMUP_FRACTION
import com.kbminisplit.domain.progression.acclimatizationFloorKg
import com.kbminisplit.domain.progression.acclimatizationLoadKg
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
        // Prime and Warm-up are both priming rows, ordered by setIndex (0 = prime,
        // 1 = warm-up). Historical sessions may have only the prime.
        val priming = all.filter { it.isPriming }.sortedBy { it.setIndex }
        val primeEntry = priming.firstOrNull() ?: return@mapNotNull null
        val warmupEntry = priming.getOrNull(1)
        val working = all.filter { !it.isPriming }.sortedBy { it.setIndex }
        val reference = working.firstOrNull() ?: return@mapNotNull null
        // Effective load only applies to assisted movements and only once a
        // bodyweight is known — otherwise the subtext is simply omitted.
        val effectiveLoad = bodyweightKg
            ?.takeIf { exercise.mechanic == ExerciseMechanic.ASSISTED }
            ?.let { effectiveLoadKg(exercise.mechanic, reference.weightKg, it) }
        // Acclimatization loads shown inside the circles, derived from the working
        // weight. When assisted with no bodyweight yet, fall back to the working pin.
        val floor = acclimatizationFloorKg(exercise)
        val primeKg = acclimatizationLoadKg(
            exercise.mechanic, reference.weightKg, PRIME_FRACTION, floor, bodyweightKg,
        ) ?: reference.weightKg
        val warmupKg = acclimatizationLoadKg(
            exercise.mechanic, reference.weightKg, WARMUP_FRACTION, floor, bodyweightKg,
        ) ?: reference.weightKg
        StrengthMovementRow(
            exercise = exercise,
            weightKg = reference.weightKg,
            targetReps = reference.targetReps
                ?: error("Strength working set missing targetReps (${exercise.slug})"),
            prime = primeEntry.toCell().copy(weightKg = primeKg),
            warmup = warmupEntry?.toCell()?.copy(weightKg = warmupKg),
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
    weightKg = weightKg,
)
