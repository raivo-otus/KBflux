package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.ProgramDay
import com.kbminisplit.domain.model.Session

/**
 * Today's training day: the one after the most recent session's, wrapping at the
 * end of the program.
 *
 * Turnover is driven by session count, never by the calendar, so skipping a week
 * doesn't skip a day. If the last session was logged against a day that has since
 * been deleted — or there is no history at all — the program starts from the top.
 *
 * `history` is expected in chronological order (oldest first).
 */
fun nextDay(history: List<Session>, program: Program): ProgramDay? {
    val days = program.days
    if (days.isEmpty()) return null
    val lastKey = history.lastOrNull()?.dayKey ?: return days.first()
    val lastIndex = days.indexOfFirst { it.key == lastKey }
    if (lastIndex < 0) return days.first()
    return days[(lastIndex + 1) % days.size]
}
