package com.kbminisplit.data.repository

import com.kbminisplit.data.db.ExerciseDao
import com.kbminisplit.data.db.ProgramDao
import com.kbminisplit.data.entity.ExerciseEntity
import com.kbminisplit.data.entity.ProgramDayEntity
import com.kbminisplit.data.entity.ProgramGroupEntity
import com.kbminisplit.data.entity.ProgramItemEntity
import com.kbminisplit.data.mapper.buildProgram
import com.kbminisplit.domain.model.GroupKind
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.domain.progression.deloadedWeightKg
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and edits the user's program.
 *
 * Two invariants everything else depends on:
 *  - **Positions stay dense.** Every mutation that can disturb ordering finishes by
 *    rewriting `0..n-1`, so no gaps accumulate and rotation arithmetic stays honest.
 *  - **Items are updated, never replaced.** A program item carries the user's
 *    accumulated working weight; delete-and-reinsert would reset it to a default.
 */
@Singleton
class ProgramRepository @Inject constructor(
    private val programDao: ProgramDao,
    private val exerciseDao: ExerciseDao,
    private val clock: Clock,
) {

    fun observeProgram(): Flow<Program> = combine(
        programDao.observeDays(),
        programDao.observeGroups(),
        programDao.observeItems(),
        exerciseDao.observeAll(),
    ) { days, groups, items, exercises ->
        buildProgram(days, groups, items, exercises.associate { it.slug to it.displayName })
    }

    /** Slug → display name for every movement ever registered, including retired ones. */
    fun observeExerciseNames(): Flow<Map<String, String>> =
        exerciseDao.observeAll().map { list -> list.associate { it.slug to it.displayName } }

    suspend fun getProgram(): Program = buildProgram(
        days = programDao.getDays(),
        groups = programDao.getGroups(),
        items = programDao.getItems(),
        namesBySlug = exerciseDao.getAll().associate { it.slug to it.displayName },
    )

    // ── Days ────────────────────────────────────────────────────────────────

    suspend fun addDay(name: String): Long {
        val days = programDao.getDays()
        return programDao.insertDay(
            ProgramDayEntity(
                dayKey = nextDayKey(days.map { it.dayKey }.toSet()),
                name = name,
                position = days.size,
            ),
        )
    }

    suspend fun renameDay(dayId: Long, name: String) {
        val day = programDao.getDays().firstOrNull { it.id == dayId } ?: return
        programDao.updateDay(day.copy(name = name))
    }

    suspend fun deleteDay(dayId: Long) {
        programDao.deleteDay(dayId)
        programDao.applyDayOrder(programDao.getDays().map { it.id })
    }

    /** Moves a day one place earlier (-1) or later (+1) in the turnover order. */
    suspend fun moveDay(dayId: Long, delta: Int) {
        val ordered = programDao.getDays().map { it.id }
        programDao.applyDayOrder(ordered.movedBy(dayId, delta) ?: return)
    }

    // ── Groups ──────────────────────────────────────────────────────────────

    suspend fun addGroup(dayId: Long, name: String, kind: GroupKind): Long {
        val siblings = programDao.getGroups().filter { it.dayId == dayId }
        // A circuit tracks rounds under a sentinel exercise, which needs a registry
        // row up front so the in-progress and set-entry foreign keys resolve.
        val circuitSlug = if (kind == GroupKind.CIRCUIT) {
            registerExercise(name = name, slugBase = "circuit_${dayId}_${siblings.size}")
        } else {
            null
        }
        return programDao.insertGroup(
            ProgramGroupEntity(
                dayId = dayId,
                name = name,
                kind = kind.name,
                position = siblings.size,
                circuitSlug = circuitSlug,
                weightKg = if (kind == GroupKind.CIRCUIT) 0.0 else null,
                weightChangedAt = if (kind == GroupKind.CIRCUIT) clock.millis() else null,
            ),
        )
    }

    suspend fun updateGroup(
        groupId: Long,
        name: String? = null,
        rotates: Boolean? = null,
        isDeferred: Boolean? = null,
        rounds: Int? = null,
        usesLadder: Boolean? = null,
    ) {
        val group = programDao.getGroup(groupId) ?: return
        programDao.updateGroup(
            group.copy(
                name = name ?: group.name,
                rotates = rotates ?: group.rotates,
                isDeferred = isDeferred ?: group.isDeferred,
                rounds = rounds ?: group.rounds,
                usesLadder = usesLadder ?: group.usesLadder,
            ),
        )
        if (name != null && group.circuitSlug != null) {
            exerciseDao.rename(group.circuitSlug, name)
        }
    }

    suspend fun deleteGroup(groupId: Long) {
        val group = programDao.getGroup(groupId) ?: return
        programDao.deleteGroup(groupId)
        programDao.applyGroupOrder(
            programDao.getGroups().filter { it.dayId == group.dayId }.map { it.id },
        )
    }

    suspend fun moveGroup(groupId: Long, delta: Int) {
        val group = programDao.getGroup(groupId) ?: return
        val ordered = programDao.getGroups().filter { it.dayId == group.dayId }.map { it.id }
        programDao.applyGroupOrder(ordered.movedBy(groupId, delta) ?: return)
    }

    /** Sets a circuit's shared weight, restarting its ladder clock and clearing any snooze. */
    suspend fun setGroupWeight(groupId: Long, weightKg: Double) {
        programDao.setGroupWeight(groupId, weightKg, clock.millis())
    }

    suspend fun snoozeGroupBump(groupId: Long) {
        programDao.setGroupBumpSnoozed(groupId, clock.millis())
    }

    // ── Items ───────────────────────────────────────────────────────────────

    suspend fun addItem(
        groupId: Long,
        name: String,
        sets: Int = 3,
        minReps: Int = 8,
        maxReps: Int = 12,
        leadInSets: Int = 2,
        weightStepKg: Double = 2.5,
        isAssisted: Boolean = false,
        isPerSide: Boolean = false,
        currentWeightKg: Double = 0.0,
    ): Long {
        val siblings = programDao.getItems().filter { it.groupId == groupId }
        return programDao.insertItem(
            ProgramItemEntity(
                groupId = groupId,
                exerciseSlug = registerExercise(name),
                position = siblings.size,
                sets = sets,
                minReps = minReps,
                maxReps = maxReps,
                leadInSets = leadInSets,
                weightStepKg = weightStepKg,
                isAssisted = isAssisted,
                isPerSide = isPerSide,
                currentWeightKg = currentWeightKg,
            ),
        )
    }

    /**
     * Applies an edit from the item sheet. The name is written to the exercise
     * registry rather than the item, so a rename follows the movement everywhere
     * it appears — including into already-logged history.
     */
    suspend fun updateItem(
        itemId: Long,
        name: String,
        sets: Int,
        minReps: Int,
        maxReps: Int,
        leadInSets: Int,
        weightStepKg: Double,
        isAssisted: Boolean,
        isPerSide: Boolean,
        currentWeightKg: Double,
    ) {
        val item = programDao.getItem(itemId) ?: return
        programDao.updateItem(
            item.copy(
                sets = sets,
                minReps = minReps,
                maxReps = maxReps,
                leadInSets = leadInSets,
                weightStepKg = weightStepKg,
                isAssisted = isAssisted,
                isPerSide = isPerSide,
                currentWeightKg = currentWeightKg,
            ),
        )
        exerciseDao.rename(item.exerciseSlug, name.trim())
    }

    suspend fun deleteItem(itemId: Long) {
        val item = programDao.getItem(itemId) ?: return
        programDao.deleteItem(itemId)
        programDao.applyItemOrder(
            programDao.getItems().filter { it.groupId == item.groupId }.map { it.id },
        )
    }

    suspend fun moveItem(itemId: Long, delta: Int) {
        val item = programDao.getItem(itemId) ?: return
        val ordered = programDao.getItems().filter { it.groupId == item.groupId }.map { it.id }
        programDao.applyItemOrder(ordered.movedBy(itemId, delta) ?: return)
    }

    suspend fun setItemWeight(itemId: Long, weightKg: Double) {
        programDao.setItemWeight(itemId, weightKg)
    }

    /**
     * One step easier on every movement, applied when a rest week is accepted.
     * Circuit weights are left alone: they climb a bell ladder on their own
     * three-month cadence, which is already the conservative one.
     */
    suspend fun deloadAllItems() {
        val weights = allItems().associate { it.id to deloadedWeightKg(it) }
        programDao.applyItemWeights(weights)
    }

    private suspend fun allItems(): List<ProgramItem> =
        getProgram().days.asSequence().flatMap { it.groups }.flatMap { it.items }.toList()

    // ── Registry ────────────────────────────────────────────────────────────

    /**
     * Resolves a movement name to a stable slug, creating a registry row when the
     * name is new. An exact name match reuses the existing slug, so programming
     * the same movement on two days keeps one identity in the Log.
     */
    private suspend fun registerExercise(name: String, slugBase: String? = null): String {
        val trimmed = name.trim().ifEmpty { "Movement" }
        val existing = exerciseDao.getAll()
        if (slugBase == null) {
            existing.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
                ?.let { return it.slug }
        }
        val slug = uniqueSlug(slugBase ?: slugify(trimmed), existing.map { it.slug }.toSet())
        exerciseDao.insertAll(listOf(ExerciseEntity(slug = slug, displayName = trimmed)))
        return slug
    }
}

/**
 * Returns the list with [id] shifted by [delta] places, or null when the move
 * would fall off either end (so the caller can skip the write entirely).
 */
private fun List<Long>.movedBy(id: Long, delta: Int): List<Long>? {
    val from = indexOf(id).takeIf { it >= 0 } ?: return null
    val to = from + delta
    if (to !in indices) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}

private fun slugify(name: String): String =
    name.lowercase()
        .map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifEmpty { "movement" }

private fun uniqueSlug(base: String, taken: Set<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("${base}_$n" in taken) n++
    return "${base}_$n"
}

/**
 * Day keys are written into session history and must stay unique forever. Single
 * letters keep them readable for the first 26 days; after that they are numbered.
 */
private fun nextDayKey(taken: Set<String>): String {
    ('A'..'Z').forEach { letter ->
        if (letter.toString() !in taken) return letter.toString()
    }
    var n = 1
    while ("day_$n" in taken) n++
    return "day_$n"
}
