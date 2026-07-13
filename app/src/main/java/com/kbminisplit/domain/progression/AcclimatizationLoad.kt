package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Category
import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseMechanic
import kotlin.math.roundToInt

/**
 * Acclimatization loads for the two lead-in sets of every strength movement — the
 * numbers shown *inside* the Prime and Warm-up circles on the Tracker.
 *
 *  - Prime   → [PRIME_FRACTION] (50%) of the working load
 *  - Warm-up → [WARMUP_FRACTION] (75%) of the working load
 *
 * The number is "what you set on the equipment": the load for a traditional lift,
 * the assistance pin for an assisted one. Both are rounded to [ACCLIMATIZATION_STEP_KG]
 * so the target is realistic to plate up, and floored so a light movement still has a
 * sensible lead-in ([MAIN_FLOOR_KG] for main lifts, [AUX_FLOOR_KG] for auxiliaries).
 *
 * Derived on the fly (never stored), mirroring how prescriptions and effective load
 * are computed rather than persisted.
 */

const val ACCLIMATIZATION_STEP_KG = 2.5
const val PRIME_FRACTION = 0.50
const val WARMUP_FRACTION = 0.75

private const val MAIN_FLOOR_KG = 20.0
private const val AUX_FLOOR_KG = 5.0

/** Round to the nearest [step] (2.5 kg by default) so the number is platable. */
fun roundToStepKg(value: Double, step: Double = ACCLIMATIZATION_STEP_KG): Double =
    (value / step).roundToInt() * step

/** The acclimatization floor: heavier for main lifts (A/B/C) than for auxiliaries. */
fun acclimatizationFloorKg(exercise: Exercise): Double =
    if (exercise.category == Category.AUX) AUX_FLOOR_KG else MAIN_FLOOR_KG

/**
 * The weight to show inside a Prime/Warm-up circle for a strength movement.
 *
 * TRADITIONAL: [fraction] of the working load, rounded to 2.5 kg, floored at [floorKg],
 * and never heavier than the work set (guards very light or bodyweight movements).
 *
 * ASSISTED: the logged number is machine *assistance*, so a lighter set means *more*
 * assistance. We reduce the *effective* load (bodyweight − pin) by [fraction], then
 * solve back for the pin that achieves it. This needs a current [bodyweightKg]; when
 * none is known yet the result is `null`, and the caller falls back to the working pin
 * (no ramp) until a bodyweight is entered.
 */
fun acclimatizationLoadKg(
    mechanic: ExerciseMechanic,
    workingKg: Double,
    fraction: Double,
    floorKg: Double,
    bodyweightKg: Double?,
): Double? = when (mechanic) {
    ExerciseMechanic.TRADITIONAL ->
        minOf(workingKg, maxOf(floorKg, roundToStepKg(workingKg * fraction)))

    ExerciseMechanic.ASSISTED -> {
        if (bodyweightKg == null) {
            null
        } else {
            val workingEffective = bodyweightKg - workingKg
            // Reduce effective load by the fraction, but never below the floor and
            // never above the working effective load (which would make the lead-in
            // harder than the work set).
            val targetEffective = minOf(workingEffective, maxOf(floorKg, workingEffective * fraction))
            // Raise the pin to hit that effective load; more assistance = never lighter
            // (harder) than the working pin.
            roundToStepKg(bodyweightKg - targetEffective).coerceAtLeast(workingKg)
        }
    }
}
