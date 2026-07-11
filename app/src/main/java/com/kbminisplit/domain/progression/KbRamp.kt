package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Session

/** The kettlebell sizes the user owns; bump targets come from this ladder (§9.3). */
val KB_WEIGHT_LADDER: List<Double> = listOf(8.0, 10.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0)

/**
 * Positional rep schemes for the KB flow (spec §2.2): first movement / second /
 * third. After a weight bump the scheme ramps one stage per 3 completed
 * workouts (one full A/B/C cycle) at the new weight, ending at the full scheme.
 */
val KB_REP_RAMP: List<List<Int>> = listOf(
    listOf(20, 10, 5),
    listOf(24, 12, 6),
    listOf(28, 14, 7),
    listOf(32, 16, 8),
)

/** Next kettlebell up the ladder, or null when already at (or above) the top. */
fun nextKbWeight(currentKg: Double): Double? = KB_WEIGHT_LADDER.firstOrNull { it > currentKg }

/**
 * The rep scheme to display today, given the completed-session [history]
 * (chronological) and the weight the current session will use.
 *
 * The ramp stage is derived, not persisted: the trailing run of sessions whose
 * snapshot equals [currentKbWeightKg] counts the workouts done since the last
 * weight change. Exact `==` is safe — compared values are stored copies of the
 * same setting, never arithmetic-derived. A run covering the entire history
 * means the weight has never changed (including empty history), so no ramp
 * applies; any weight change, up or down, restarts it.
 */
fun kbRepScheme(history: List<Session>, currentKbWeightKg: Double): List<Int> {
    val run = history.takeLastWhile { it.kbWeightKg == currentKbWeightKg }
    if (run.size == history.size) return KB_REP_RAMP.last()
    return KB_REP_RAMP[(run.size / 3).coerceAtMost(KB_REP_RAMP.lastIndex)]
}
