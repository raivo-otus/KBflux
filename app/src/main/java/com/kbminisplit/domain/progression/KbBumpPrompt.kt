package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Session
import java.time.LocalDate
import java.time.YearMonth

/**
 * Snooze state persisted in user settings. Set when the user taps "not yet" on
 * the KB bump prompt. Cleared after the prompt is honored ("bump") or
 * naturally aged out when the calendar month rolls past [snoozedAtMonth].
 *
 * @param snoozedAtMonth calendar month in which the snooze occurred
 * @param sessionCountAtSnooze total session count at the moment of the snooze
 */
data class KbBumpSnooze(
    val snoozedAtMonth: YearMonth,
    val sessionCountAtSnooze: Int,
)

/**
 * Should the Tracker show the KB-bump prompt today? Per spec §9.3:
 *
 *  - Fire on the first session of a new calendar month, *if* the prior calendar
 *    month contained at least one completed session.
 *  - If the user previously tapped "not yet" within the current month, suppress
 *    the prompt until two more sessions have been logged since that snooze.
 *  - A month change naturally invalidates any prior-month snooze.
 *
 * `history` is expected in chronological order.
 */
fun shouldPromptKbBump(
    history: List<Session>,
    today: LocalDate,
    snooze: KbBumpSnooze? = null,
): Boolean {
    val thisMonth = YearMonth.from(today)

    // A snooze within the current month suppresses the prompt unless enough
    // sessions have passed. This check takes priority.
    if (snooze != null && snooze.snoozedAtMonth == thisMonth) {
        val sessionsSinceSnooze = history.size - snooze.sessionCountAtSnooze
        return sessionsSinceSnooze >= 2
    }

    // Standard prompt: first session of the month, provided the prior month
    // had at least one session.
    val hasSessionThisMonth = history.asReversed().asSequence()
        .takeWhile { YearMonth.from(it.date) == thisMonth }
        .any()
    if (hasSessionThisMonth) return false

    val prevMonth = thisMonth.minusMonths(1)
    return history.any { YearMonth.from(it.date) == prevMonth }
}
