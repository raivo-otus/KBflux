package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.GroupKind
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.ProgramDay
import com.kbminisplit.domain.model.ProgramGroup
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import java.time.LocalDate

/**
 * Builders for program and history fixtures. Ids are supplied explicitly so a test
 * can address a specific movement without reaching through the tree.
 */

internal fun item(
    id: Long,
    name: String,
    slug: String = name.lowercase().replace(' ', '_'),
    position: Int = 0,
    sets: Int = 3,
    minReps: Int = 8,
    maxReps: Int = 12,
    leadInSets: Int = 2,
    weightStepKg: Double = 2.5,
    isAssisted: Boolean = false,
    isPerSide: Boolean = false,
    currentWeightKg: Double = 50.0,
) = ProgramItem(
    id = id,
    exerciseSlug = slug,
    name = name,
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

internal fun standardGroup(
    id: Long,
    name: String = "Main",
    position: Int = 0,
    rotates: Boolean = true,
    isDeferred: Boolean = false,
    items: List<ProgramItem>,
) = ProgramGroup(
    id = id,
    name = name,
    kind = GroupKind.STANDARD,
    position = position,
    rotates = rotates,
    isDeferred = isDeferred,
    rounds = 0,
    circuitSlug = null,
    weightKg = null,
    usesLadder = false,
    weightChangedAt = null,
    bumpSnoozedAt = null,
    items = items.mapIndexed { index, item -> item.copy(position = index) },
)

internal fun circuitGroup(
    id: Long,
    name: String = "Kettlebell flow",
    position: Int = 0,
    rounds: Int = 3,
    circuitSlug: String = "kb_flow",
    weightKg: Double? = 16.0,
    usesLadder: Boolean = true,
    weightChangedAt: Long? = 0L,
    bumpSnoozedAt: Long? = null,
    rotates: Boolean = false,
    items: List<ProgramItem> = emptyList(),
) = ProgramGroup(
    id = id,
    name = name,
    kind = GroupKind.CIRCUIT,
    position = position,
    rotates = rotates,
    isDeferred = false,
    rounds = rounds,
    circuitSlug = circuitSlug,
    weightKg = weightKg,
    usesLadder = usesLadder,
    weightChangedAt = weightChangedAt,
    bumpSnoozedAt = bumpSnoozedAt,
    items = items.mapIndexed { index, item -> item.copy(position = index) },
)

internal fun day(
    id: Long,
    key: String,
    name: String = "Day $key",
    position: Int = 0,
    groups: List<ProgramGroup> = emptyList(),
) = ProgramDay(
    id = id,
    key = key,
    name = name,
    position = position,
    groups = groups.mapIndexed { index, group -> group.copy(position = index) },
)

internal fun program(vararg days: ProgramDay) =
    Program(days.mapIndexed { index, day -> day.copy(position = index) })

/** Working sets for one movement, in session order. */
internal fun workingSets(
    slug: String,
    weightKg: Double,
    minReps: Int = 8,
    maxReps: Int = 12,
    position: Int = 0,
    statuses: List<SetStatus> = List(3) { SetStatus.Completed },
): List<SetEntry> = statuses.mapIndexed { index, status ->
    SetEntry(
        exerciseSlug = slug,
        setIndex = index + 1,
        isPriming = false,
        targetReps = minReps,
        targetRepsMax = maxReps,
        weightKg = weightKg,
        status = status,
        position = position,
    )
}

internal fun session(
    date: LocalDate,
    dayKey: String,
    feedback: Feedback = Feedback.Green,
    circuitWeightKg: Double = 16.0,
    sets: List<SetEntry> = emptyList(),
    bodyweightKg: Double? = null,
) = Session(
    date = date,
    dayKey = dayKey,
    feedback = feedback,
    circuitWeightKg = circuitWeightKg,
    sets = sets,
    bodyweightKg = bodyweightKg,
)

/** [count] sessions on [dayKey], one day apart from [start]. */
internal fun sessions(dayKey: String, count: Int, start: LocalDate): List<Session> =
    List(count) { index -> session(start.plusDays(index.toLong()), dayKey) }
