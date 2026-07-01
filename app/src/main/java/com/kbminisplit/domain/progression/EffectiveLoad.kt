package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.ExerciseMechanic

/**
 * The physiological load a set represents, for display and charting.
 *
 *  - [ExerciseMechanic.TRADITIONAL]: the logged weight is the load.
 *  - [ExerciseMechanic.ASSISTED]: load is bodyweight minus the assistance pin,
 *    so it *rises* as the logged assistance drops.
 *
 * Derived on the fly (never stored) from the logged weight plus the bodyweight in
 * effect at the time — mirroring how the app derives prescriptions rather than
 * persisting them.
 */
fun effectiveLoadKg(
    mechanic: ExerciseMechanic,
    loggedKg: Double,
    bodyweightKg: Double,
): Double = when (mechanic) {
    ExerciseMechanic.TRADITIONAL -> loggedKg
    ExerciseMechanic.ASSISTED -> bodyweightKg - loggedKg
}
