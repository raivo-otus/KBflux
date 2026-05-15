package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.Split

/**
 * Today's split is the one after the most recent completed session's split,
 * rotating A → B → C → A. With no history, today is Split.A.
 *
 * `history` is expected in chronological order (oldest first).
 */
fun nextSplit(history: List<Session>): Split {
    val last = history.lastOrNull() ?: return Split.A
    return when (last.split) {
        Split.A -> Split.B
        Split.B -> Split.C
        Split.C -> Split.A
    }
}
