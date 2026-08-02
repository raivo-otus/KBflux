package com.kbminisplit.domain.model

/**
 * The user's training program: an ordered list of days that the Tracker rotates
 * through. Everything the app prescribes comes from here — there is no hardcoded
 * catalog and no progression engine deriving weights from history.
 */
data class Program(val days: List<ProgramDay>) {
    val isEmpty: Boolean get() = days.isEmpty()

    fun dayByKey(key: String): ProgramDay? = days.firstOrNull { it.key == key }

    fun itemById(id: Long): ProgramItem? =
        days.asSequence().flatMap { it.groups }.flatMap { it.items }.firstOrNull { it.id == id }

    fun groupById(id: Long): ProgramGroup? =
        days.asSequence().flatMap { it.groups }.firstOrNull { it.id == id }

    companion object {
        val EMPTY = Program(emptyList())
    }
}

/**
 * One training day. [key] is the stable identity written to `session.split`, so it
 * must never change once sessions reference it — renaming a day changes [name]
 * only. [position] drives the automatic turnover order.
 */
data class ProgramDay(
    val id: Long,
    val key: String,
    val name: String,
    val position: Int,
    val groups: List<ProgramGroup>,
)

/** How a group's movements are performed, which decides how its buttons are laid out. */
enum class GroupKind {
    /** Each movement gets its own lead-in and working set buttons. */
    STANDARD,

    /** Movements are labels only; one button per round covers the whole group. */
    CIRCUIT,
}

/**
 * A block of movements inside a day.
 *
 * [rotates] shifts the movement order by one every time the day comes around, so
 * the same movement is not always first. [isDeferred] holds the group back until
 * every earlier group is resolved (the accessory block's two-stage reveal).
 *
 * CIRCUIT groups carry their own shared [weightKg] instead of per-movement
 * weights, tracked over [rounds] rows stored under [circuitSlug]. When
 * [usesLadder] is set the weight climbs the kettlebell ladder via a prompt every
 * three months; [weightChangedAt] anchors that clock and [bumpSnoozedAt] defers it.
 */
data class ProgramGroup(
    val id: Long,
    val name: String,
    val kind: GroupKind,
    val position: Int,
    val rotates: Boolean,
    val isDeferred: Boolean,
    val rounds: Int,
    val circuitSlug: String?,
    val weightKg: Double?,
    val usesLadder: Boolean,
    val weightChangedAt: Long?,
    val bumpSnoozedAt: Long?,
    val items: List<ProgramItem>,
) {
    val isCircuit: Boolean get() = kind == GroupKind.CIRCUIT
}

/**
 * One movement as programmed on a given day. Every parameter lives here rather
 * than on a shared exercise definition, so the same movement can be programmed
 * differently on different days.
 *
 * [currentWeightKg] is the live working weight — the single source of truth,
 * written by the Tracker's bump chip, its weight editor, the Program editor and
 * the rest-week deload. Nothing is derived from history.
 *
 * [exerciseSlug] is the stable key written to `set_entry`, and [name] is the
 * display name held in the exercise registry against that slug.
 */
data class ProgramItem(
    val id: Long,
    val exerciseSlug: String,
    val name: String,
    val position: Int,
    val sets: Int,
    val minReps: Int,
    val maxReps: Int,
    /** Lead-in circles before the work sets: 0 none, 1 warm-up, 2 prime + warm-up. */
    val leadInSets: Int,
    val weightStepKg: Double,
    val isAssisted: Boolean,
    val isPerSide: Boolean,
    val currentWeightKg: Double,
) {
    /**
     * Assistance inverts the direction of every weight change, so the existing
     * effective-load and acclimatization helpers keep taking an [ExerciseMechanic].
     */
    val mechanic: ExerciseMechanic
        get() = if (isAssisted) ExerciseMechanic.ASSISTED else ExerciseMechanic.TRADITIONAL

    /** "8–12", or "12" when the range is a single number. */
    val repRangeLabel: String
        get() = if (minReps == maxReps) "$minReps" else "$minReps–$maxReps"
}

/** Highest lead-in count a movement can have (prime + warm-up). */
const val MAX_LEAD_IN_SETS = 2
