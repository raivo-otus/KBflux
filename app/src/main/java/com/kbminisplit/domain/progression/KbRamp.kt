package com.kbminisplit.domain.progression

/**
 * The bell sizes a ladder circuit steps through. Kettlebells come in discrete
 * jumps rather than plate increments, so a ladder group moves between rungs
 * instead of adding a fixed step.
 */
val KB_WEIGHT_LADDER: List<Double> = listOf(8.0, 10.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0)

/** Next bell up the ladder, or null when already at (or above) the top. */
fun nextKbWeight(currentKg: Double): Double? = KB_WEIGHT_LADDER.firstOrNull { it > currentKg }
