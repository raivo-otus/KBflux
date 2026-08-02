package com.kbminisplit.ui.program

import com.kbminisplit.domain.model.MAX_LEAD_IN_SETS
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.ui.components.toCountOrNull
import com.kbminisplit.ui.components.toWeightOrNull

/** Increments offered in the item editor; any other value can still be typed. */
val WEIGHT_STEP_OPTIONS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

val SETS_RANGE = 1..10
val REPS_RANGE = 1..100
val ROUNDS_RANGE = 1..20

data class ProgramUiState(
    val program: Program = Program.EMPTY,
    val isLoading: Boolean = true,
    /** Which days are expanded in the list. Collapsed by default beyond the first. */
    val expandedDayIds: Set<Long> = emptySet(),
    val editingItem: ItemDraft? = null,
    val editingGroup: GroupDraft? = null,
)

/**
 * The item editor's fields, held as raw strings so a half-typed number doesn't
 * fight the user. Validity is derived, and Save is gated on it.
 */
data class ItemDraft(
    val itemId: Long?,
    val groupId: Long,
    val isCircuitItem: Boolean,
    val name: String = "",
    val sets: String = "3",
    val minReps: String = "8",
    val maxReps: String = "12",
    val weight: String = "0",
    val weightStep: String = "2.5",
    val leadInSets: Int = 2,
    val isAssisted: Boolean = false,
    val isPerSide: Boolean = false,
) {
    val setsValue: Int? get() = if (isCircuitItem) 0 else sets.toCountOrNull(SETS_RANGE)
    val minRepsValue: Int? get() = minReps.toCountOrNull(REPS_RANGE)
    val maxRepsValue: Int? get() = maxReps.toCountOrNull(REPS_RANGE)
    val weightValue: Double? get() = weight.toWeightOrNull()
    val weightStepValue: Double? get() = weightStep.toWeightOrNull()

    val isNameValid: Boolean get() = name.isNotBlank()
    val isRepRangeValid: Boolean
        get() = minRepsValue != null && maxRepsValue != null && minRepsValue!! <= maxRepsValue!!

    val isValid: Boolean
        get() = isNameValid &&
            isRepRangeValid &&
            setsValue != null &&
            weightValue != null &&
            weightStepValue != null &&
            leadInSets in 0..MAX_LEAD_IN_SETS

    companion object {
        fun of(item: ProgramItem, groupId: Long, isCircuitItem: Boolean) = ItemDraft(
            itemId = item.id,
            groupId = groupId,
            isCircuitItem = isCircuitItem,
            name = item.name,
            sets = item.sets.toString(),
            minReps = item.minReps.toString(),
            maxReps = item.maxReps.toString(),
            weight = formatNumber(item.currentWeightKg),
            weightStep = formatNumber(item.weightStepKg),
            leadInSets = item.leadInSets,
            isAssisted = item.isAssisted,
            isPerSide = item.isPerSide,
        )

        fun blank(groupId: Long, isCircuitItem: Boolean) = ItemDraft(
            itemId = null,
            groupId = groupId,
            isCircuitItem = isCircuitItem,
            sets = if (isCircuitItem) "0" else "3",
            leadInSets = if (isCircuitItem) 0 else 2,
        )
    }
}

/** The group editor's fields. Circuit-only values are ignored for standard groups. */
data class GroupDraft(
    val groupId: Long,
    val name: String,
    val isCircuit: Boolean,
    val rotates: Boolean,
    val isDeferred: Boolean,
    val rounds: String,
    val usesLadder: Boolean,
) {
    val roundsValue: Int? get() = if (isCircuit) rounds.toCountOrNull(ROUNDS_RANGE) else 0
    val isValid: Boolean get() = name.isNotBlank() && roundsValue != null
}

internal fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
