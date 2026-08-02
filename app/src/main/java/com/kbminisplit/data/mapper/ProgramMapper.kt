package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.ProgramDayEntity
import com.kbminisplit.data.entity.ProgramGroupEntity
import com.kbminisplit.data.entity.ProgramItemEntity
import com.kbminisplit.data.util.toEnumOrDefault
import com.kbminisplit.domain.model.GroupKind
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.ProgramDay
import com.kbminisplit.domain.model.ProgramGroup
import com.kbminisplit.domain.model.ProgramItem

/**
 * Assemble the nested [Program] from the three flat tables plus the exercise
 * registry, which supplies each item's display name.
 *
 * All three lists arrive ordered by `position`; grouping preserves that order, so
 * the nested lists come out in program order without re-sorting.
 */
fun buildProgram(
    days: List<ProgramDayEntity>,
    groups: List<ProgramGroupEntity>,
    items: List<ProgramItemEntity>,
    namesBySlug: Map<String, String>,
): Program {
    val itemsByGroup = items.groupBy { it.groupId }
    val groupsByDay = groups.groupBy { it.dayId }
    return Program(
        days = days.map { day ->
            ProgramDay(
                id = day.id,
                key = day.dayKey,
                name = day.name,
                position = day.position,
                groups = groupsByDay[day.id].orEmpty().map { group ->
                    group.toDomain(itemsByGroup[group.id].orEmpty(), namesBySlug)
                },
            )
        },
    )
}

fun ProgramGroupEntity.toDomain(
    items: List<ProgramItemEntity>,
    namesBySlug: Map<String, String>,
): ProgramGroup = ProgramGroup(
    id = id,
    name = name,
    kind = kind.toEnumOrDefault(GroupKind.STANDARD),
    position = position,
    rotates = rotates,
    isDeferred = isDeferred,
    rounds = rounds,
    circuitSlug = circuitSlug,
    weightKg = weightKg,
    usesLadder = usesLadder,
    weightChangedAt = weightChangedAt,
    bumpSnoozedAt = bumpSnoozedAt,
    items = items.map { it.toDomain(namesBySlug) },
)

fun ProgramItemEntity.toDomain(namesBySlug: Map<String, String>): ProgramItem = ProgramItem(
    id = id,
    exerciseSlug = exerciseSlug,
    name = namesBySlug[exerciseSlug] ?: exerciseSlug,
    position = position,
    sets = sets,
    minReps = minReps,
    maxReps = maxReps,
    leadInSets = leadInSets,
    weightStepKg = weightStepKg,
    isAssisted = isAssisted,
    isPerSide = isPerSide,
    currentWeightKg = currentWeightKg,
)

fun ProgramItem.toEntity(groupId: Long): ProgramItemEntity = ProgramItemEntity(
    id = id,
    groupId = groupId,
    exerciseSlug = exerciseSlug,
    position = position,
    sets = sets,
    minReps = minReps,
    maxReps = maxReps,
    leadInSets = leadInSets,
    weightStepKg = weightStepKg,
    isAssisted = isAssisted,
    isPerSide = isPerSide,
    currentWeightKg = currentWeightKg,
)
