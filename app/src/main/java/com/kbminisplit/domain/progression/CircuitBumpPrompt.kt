package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.ProgramGroup
import java.time.Duration

/** How long a circuit stays on one bell before the next rung is offered. */
private val LADDER_INTERVAL = Duration.ofDays(91) // ~3 months

/** How long a "not yet" holds the prompt off. */
private val LADDER_SNOOZE = Duration.ofDays(14)

/**
 * Should the Tracker offer to move a ladder circuit up to the next bell today?
 *
 * Kettlebells come in big discrete jumps, so unlike a barbell movement this is
 * paced by time rather than by performance: three months on one bell, then an
 * offer. The clock is [ProgramGroup.weightChangedAt], stamped whenever the weight
 * changes, so accepting a bump structurally silences the prompt for another three
 * months and a snooze holds it off for two weeks.
 *
 * Never fires for a non-ladder group, or at the top of the ladder where there is
 * no bigger bell to offer.
 */
fun shouldPromptCircuitBump(group: ProgramGroup, nowMillis: Long): Boolean {
    if (!group.usesLadder) return false
    val currentKg = group.weightKg ?: return false
    if (nextKbWeight(currentKg) == null) return false

    val changedAt = group.weightChangedAt ?: return false
    if (nowMillis - changedAt < LADDER_INTERVAL.toMillis()) return false

    val snoozedAt = group.bumpSnoozedAt ?: return true
    return nowMillis - snoozedAt >= LADDER_SNOOZE.toMillis()
}
