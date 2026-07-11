package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Session
import java.time.LocalDate
import java.time.YearMonth

/**
 * Snooze state persisted in user settings. Set when the user taps "not yet" on
 * the KB bump prompt. Cleared after the prompt is honored ("bump").
 *
 * @param snoozedAtMonth calendar month in which the snooze occurred. Retained
 *   for storage compatibility (and debugging); suppression is purely
 *   session-count based.
 * @param sessionCountAtSnooze total session count at the moment of the snooze
 */
data class KbBumpSnooze(
    val snoozedAtMonth: YearMonth,
    val sessionCountAtSnooze: Int,
)

/**
 * Should the Tracker show the KB-bump prompt today? Per spec §9.3:
 *
 *  - Fire once the current KB weight has been in use for 3 months, measured
 *    from the first session of the trailing run of history at that weight.
 *  - Never fire at the top of the ladder (no bigger bell to offer), with an
 *    empty history, or before the current weight has a completed session.
 *  - Once due, the prompt persists every session until the user accepts or
 *    snoozes; "not yet" suppresses it until two more sessions have been logged.
 *
 * Accepting a bump needs no snooze stamp: the new weight's trailing run is
 * empty, which suppresses the prompt structurally until 3 months pass again.
 *
 * `history` is expected in chronological order.
 */
fun shouldPromptKbBump(
    history: List<Session>,
    today: LocalDate,
    currentKbWeightKg: Double,
    snooze: KbBumpSnooze? = null,
): Boolean {
    if (nextKbWeight(currentKbWeightKg) == null) return false

    // Sessions completed at the current weight since the last change; empty
    // covers both a blank history and a freshly changed weight.
    val run = history.takeLastWhile { it.kbWeightKg == currentKbWeightKg }
    if (run.isEmpty()) return false
    if (run.first().date.plusMonths(3).isAfter(today)) return false

    if (snooze != null && history.size - snooze.sessionCountAtSnooze < 2) return false
    return true
}
