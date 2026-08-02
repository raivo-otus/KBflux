package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.ExerciseMechanic
import kotlin.math.roundToInt

/**
 * Acclimatization loads for a movement's lead-in sets — the numbers shown *inside*
 * the Prime and Warm-up circles on the Tracker.
 *
 *  - Prime   → [PRIME_FRACTION] (50%) of the working load
 *  - Warm-up → [WARMUP_FRACTION] (75%) of the working load
 *
 * The number is "what you set on the equipment": the load for a traditional lift,
 * the assistance pin for an assisted one. Both are rounded to
 * [ACCLIMATIZATION_STEP_KG] so the target is realistic to plate up.
 *
 * How many circles a movement gets is programmed per entry (`leadInSets`), which
 * is why the floor no longer needs to know whether something is a main lift or an
 * accessory: a movement light enough to make a ramp pointless is simply given no
 * lead-in sets.
 *
 * Derived on the fly, never stored.
 */

const val ACCLIMATIZATION_STEP_KG = 2.5
const val PRIME_FRACTION = 0.50
const val WARMUP_FRACTION = 0.75

private const val LEAD_IN_FLOOR_KG = 20.0

/** Round to the nearest [step] (2.5 kg by default) so the number is platable. */
fun roundToStepKg(value: Double, step: Double = ACCLIMATIZATION_STEP_KG): Double =
    (value / step).roundToInt() * step

/**
 * The floor a lead-in never drops below: a fixed 20 kg for anything heavier than
 * that, and the working load itself for anything lighter, so the ramp can never
 * ask for more than the work set.
 */
fun acclimatizationFloorKg(workingKg: Double): Double = minOf(LEAD_IN_FLOOR_KG, workingKg)

/**
 * The weight to show inside a Prime/Warm-up circle.
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
