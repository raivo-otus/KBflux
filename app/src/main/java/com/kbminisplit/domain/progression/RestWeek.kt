package com.kbminisplit.domain.progression

/**
 * The periodic nudge to take a week off and come back a step lighter.
 *
 * Counting sessions rather than calendar time makes "consistent logging" fall out
 * for free: at three sessions a week the prompt lands after roughly two months,
 * and training less often simply pushes it further out instead of nagging someone
 * who hasn't accumulated the fatigue.
 */

/** Sessions since the last rest week before the prompt appears (≈ 2 months at 3×/week). */
const val REST_WEEK_SESSIONS = 24

/** How many more sessions a snooze buys before the prompt returns. */
const val REST_WEEK_SNOOZE_SESSIONS = 2

/**
 * Where the rest-week clock stands: the session count when the last rest week was
 * taken, and the count when the prompt was last snoozed (null if it wasn't).
 */
data class RestWeekState(
    val anchorSessions: Int = 0,
    val snoozedAtSessions: Int? = null,
)

fun shouldPromptRestWeek(historySize: Int, state: RestWeekState): Boolean {
    if (historySize - state.anchorSessions < REST_WEEK_SESSIONS) return false
    val snoozedAt = state.snoozedAtSessions ?: return true
    return historySize - snoozedAt >= REST_WEEK_SNOOZE_SESSIONS
}
