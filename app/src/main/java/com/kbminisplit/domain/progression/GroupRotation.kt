package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.ProgramDay
import com.kbminisplit.domain.model.ProgramGroup
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.domain.model.Session

/** A day's groups with their movements already put in today's order. */
data class ResolvedDay(
    val day: ProgramDay,
    val groups: List<ResolvedGroup>,
) {
    val key: String get() = day.key
    val name: String get() = day.name
}

data class ResolvedGroup(
    val group: ProgramGroup,
    val items: List<ProgramItem>,
)

/**
 * Today's plan for a day: every group in program order, each with its movements
 * rotated for this appearance of the day. This is the single place rotation is
 * applied, so what gets stored and what gets rendered can never disagree.
 */
fun resolveDay(day: ProgramDay, cycleCount: Int): ResolvedDay = ResolvedDay(
    day = day,
    groups = day.groups.map { ResolvedGroup(it, rotatedItems(it, cycleCount)) },
)

/**
 * How many times a day has already been trained. Drives the rotation offset, so
 * the order advances once per appearance of that day rather than once per session.
 */
fun dayCycleCount(history: List<Session>, dayKey: String): Int =
    history.count { it.dayKey == dayKey }

/**
 * The group's movements in the order they should be performed today.
 *
 * A rotating group shifts by one every time its day comes around, so whatever was
 * first last time drops to the back and nothing is permanently stuck being done
 * last on tired arms. A group that doesn't rotate always runs in program order.
 *
 * With two movements this is the same alternation the app has always done for the
 * main lifts; with N it cycles through every starting position before repeating.
 */
fun rotatedItems(group: ProgramGroup, dayCycleCount: Int): List<ProgramItem> {
    val items = group.items
    if (!group.rotates || items.size < 2) return items
    val offset = Math.floorMod(dayCycleCount, items.size)
    return items.subList(offset, items.size) + items.subList(0, offset)
}
