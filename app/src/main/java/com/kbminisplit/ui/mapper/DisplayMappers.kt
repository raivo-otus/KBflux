package com.kbminisplit.ui.mapper

import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.InProgressSet
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.progression.PRIME_FRACTION
import com.kbminisplit.domain.progression.ResolvedDay
import com.kbminisplit.domain.progression.ResolvedGroup
import com.kbminisplit.domain.progression.WARMUP_FRACTION
import com.kbminisplit.domain.progression.acclimatizationFloorKg
import com.kbminisplit.domain.progression.acclimatizationLoadKg
import com.kbminisplit.domain.progression.bumpedWeightKg
import com.kbminisplit.domain.progression.canBump
import com.kbminisplit.domain.progression.effectiveLoadKg
import com.kbminisplit.domain.progression.nextKbWeight
import com.kbminisplit.domain.progression.shouldPromptCircuitBump
import com.kbminisplit.ui.tracker.BumpState
import com.kbminisplit.ui.tracker.CircuitBumpState
import com.kbminisplit.ui.tracker.CircuitMovement
import com.kbminisplit.ui.tracker.GroupBlock
import com.kbminisplit.ui.tracker.MovementRow
import com.kbminisplit.ui.tracker.SetCell

/**
 * Turns today's resolved plan plus the live in-progress rows into the blocks the
 * Tracker renders.
 *
 * A group with no rows yet is skipped rather than rendered empty — that is how a
 * deferred group stays hidden until the earlier work is done.
 */
fun buildGroupBlocks(
    day: ResolvedDay,
    sets: List<InProgressSet>,
    bodyweightKg: Double?,
    nowMillis: Long,
): List<GroupBlock> {
    val setsByGroup = sets.groupBy { it.programGroupId }
    return day.groups.mapNotNull { resolved ->
        val groupSets = setsByGroup[resolved.group.id].orEmpty()
        if (groupSets.isEmpty()) return@mapNotNull null
        if (resolved.group.isCircuit) {
            resolved.toCircuitBlock(groupSets, nowMillis)
        } else {
            resolved.toStandardBlock(groupSets, bodyweightKg)
        }
    }
}

private fun ResolvedGroup.toCircuitBlock(
    groupSets: List<InProgressSet>,
    nowMillis: Long,
): GroupBlock.Circuit {
    val rounds = groupSets
        .filter { it.isCircuitRound }
        .sortedBy { it.setIndex }
        .map { it.toCell() }
    val weightKg = rounds.firstOrNull()?.weightKg ?: group.weightKg ?: 0.0
    val nextRung = group.weightKg?.let { nextKbWeight(it) }
    return GroupBlock.Circuit(
        groupId = group.id,
        name = group.name,
        weightKg = weightKg,
        movements = items.map {
            CircuitMovement(name = it.name, repsLabel = it.repsLabel())
        },
        rounds = rounds,
        // Only offer the bell change before the first round is touched — swapping
        // bells mid-circuit would invalidate the rounds already logged.
        bump = if (
            nextRung != null &&
            rounds.all { it.status == SetStatus.Pending } &&
            shouldPromptCircuitBump(group, nowMillis)
        ) {
            CircuitBumpState(currentKg = group.weightKg ?: weightKg, targetKg = nextRung)
        } else {
            null
        },
    )
}

private fun ResolvedGroup.toStandardBlock(
    groupSets: List<InProgressSet>,
    bodyweightKg: Double?,
): GroupBlock.Standard {
    val setsByItem = groupSets.groupBy { it.programItemId }
    return GroupBlock.Standard(
        groupId = group.id,
        name = group.name,
        movements = items.mapNotNull { item ->
            item.toMovementRow(setsByItem[item.id].orEmpty(), bodyweightKg)
        },
    )
}

private fun ProgramItem.toMovementRow(
    itemSets: List<InProgressSet>,
    bodyweightKg: Double?,
): MovementRow? {
    val working = itemSets.filter { !it.isPriming }.sortedBy { it.setIndex }
    val reference = working.firstOrNull() ?: return null
    // Lead-ins are ordered by setIndex: 0 = prime, 1 = warm-up.
    val leadIn = itemSets.filter { it.isPriming }.sortedBy { it.setIndex }

    // Effective load only applies to assisted movements and only once a bodyweight
    // is known — otherwise the subtext is simply omitted.
    val effectiveLoad = bodyweightKg
        ?.takeIf { mechanic == ExerciseMechanic.ASSISTED }
        ?.let { effectiveLoadKg(mechanic, reference.weightKg, it) }

    // Acclimatization loads shown inside the circles, derived from the working
    // weight. When assisted with no bodyweight yet, fall back to the working pin.
    val floor = acclimatizationFloorKg(reference.weightKg)
    val leadInCells = leadIn.map { entry ->
        // With a single lead-in set it should be the warm-up (the heavier one),
        // not the prime, so the fraction follows the count rather than the index.
        val fraction = if (leadIn.size == 1 || entry.setIndex > 0) WARMUP_FRACTION else PRIME_FRACTION
        val circleKg = acclimatizationLoadKg(
            mechanic, reference.weightKg, fraction, floor, bodyweightKg,
        ) ?: reference.weightKg
        entry.toCell().copy(weightKg = circleKg)
    }

    val sessionKg = reference.weightKg
    val allCompleted = working.all { it.status == SetStatus.Completed }
    val isArmed = currentWeightKg != sessionKg

    return MovementRow(
        programItemId = id,
        name = name,
        weightKg = sessionKg,
        repRangeLabel = repsLabel(),
        leadIn = leadInCells,
        working = working.map { it.toCell() },
        effectiveLoadKg = effectiveLoad,
        bump = when {
            !allCompleted -> null
            isArmed -> BumpState(targetKg = currentWeightKg, isArmed = true)
            !canBump(this) -> null
            else -> BumpState(targetKg = bumpedWeightKg(this), isArmed = false)
        },
    )
}

/** "8–12", or "8–12/side" for a movement counted one side at a time. */
private fun ProgramItem.repsLabel(): String =
    if (isPerSide) "$repRangeLabel/side" else repRangeLabel

fun InProgressSet.toCell() = SetCell(id = id, status = status, weightKg = weightKg)
