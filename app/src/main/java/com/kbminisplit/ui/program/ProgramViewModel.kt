package com.kbminisplit.ui.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.ProgramRepository
import com.kbminisplit.domain.model.GroupKind
import com.kbminisplit.domain.model.ProgramGroup
import com.kbminisplit.domain.model.ProgramItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Program tab.
 *
 * The program itself is observed straight from the database, so every edit lands
 * in one place and the Tracker sees it immediately. Only transient editor state —
 * which day is expanded, which draft is open — lives here.
 */
@HiltViewModel
class ProgramViewModel @Inject constructor(
    private val programRepository: ProgramRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgramUiState())
    val state: StateFlow<ProgramUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            programRepository.observeProgram().collect { program ->
                _state.update { current ->
                    current.copy(
                        program = program,
                        isLoading = false,
                        // Open the first day on the first load so the tab isn't a
                        // wall of collapsed rows.
                        expandedDayIds = if (current.isLoading) {
                            setOfNotNull(program.days.firstOrNull()?.id)
                        } else {
                            current.expandedDayIds
                        },
                    )
                }
            }
        }
    }

    fun onToggleDay(dayId: Long) {
        _state.update { current ->
            val expanded = current.expandedDayIds
            current.copy(
                expandedDayIds = if (dayId in expanded) expanded - dayId else expanded + dayId,
            )
        }
    }

    // ── Days ────────────────────────────────────────────────────────────────

    fun onAddDay() {
        viewModelScope.launch {
            val id = programRepository.addDay("Day ${_state.value.program.days.size + 1}")
            _state.update { it.copy(expandedDayIds = it.expandedDayIds + id) }
        }
    }

    fun onRenameDay(dayId: Long, name: String) {
        viewModelScope.launch { programRepository.renameDay(dayId, name.trim()) }
    }

    fun onDeleteDay(dayId: Long) {
        viewModelScope.launch { programRepository.deleteDay(dayId) }
    }

    fun onMoveDay(dayId: Long, delta: Int) {
        viewModelScope.launch { programRepository.moveDay(dayId, delta) }
    }

    // ── Groups ──────────────────────────────────────────────────────────────

    fun onAddGroup(dayId: Long, kind: GroupKind) {
        viewModelScope.launch {
            val name = if (kind == GroupKind.CIRCUIT) "Circuit" else "Block"
            programRepository.addGroup(dayId, name, kind)
        }
    }

    fun onMoveGroup(groupId: Long, delta: Int) {
        viewModelScope.launch { programRepository.moveGroup(groupId, delta) }
    }

    fun onDeleteGroup(groupId: Long) {
        viewModelScope.launch { programRepository.deleteGroup(groupId) }
    }

    fun onEditGroup(group: ProgramGroup) {
        _state.update {
            it.copy(
                editingGroup = GroupDraft(
                    groupId = group.id,
                    name = group.name,
                    isCircuit = group.isCircuit,
                    rotates = group.rotates,
                    isDeferred = group.isDeferred,
                    rounds = group.rounds.toString(),
                    usesLadder = group.usesLadder,
                ),
            )
        }
    }

    fun onGroupDraftChange(draft: GroupDraft) {
        _state.update { it.copy(editingGroup = draft) }
    }

    fun onDismissGroupEditor() {
        _state.update { it.copy(editingGroup = null) }
    }

    fun onSaveGroup() {
        val draft = _state.value.editingGroup ?: return
        if (!draft.isValid) return
        viewModelScope.launch {
            programRepository.updateGroup(
                groupId = draft.groupId,
                name = draft.name.trim(),
                rotates = draft.rotates,
                isDeferred = draft.isDeferred,
                rounds = draft.roundsValue,
                usesLadder = draft.usesLadder,
            )
            _state.update { it.copy(editingGroup = null) }
        }
    }

    /** Sets a circuit's shared weight, which also restarts its ladder clock. */
    fun onCircuitWeightChange(groupId: Long, weightKg: Double) {
        viewModelScope.launch { programRepository.setGroupWeight(groupId, weightKg) }
    }

    // ── Items ───────────────────────────────────────────────────────────────

    fun onAddItem(group: ProgramGroup) {
        _state.update {
            it.copy(editingItem = ItemDraft.blank(group.id, isCircuitItem = group.isCircuit))
        }
    }

    fun onEditItem(item: ProgramItem, group: ProgramGroup) {
        _state.update {
            it.copy(editingItem = ItemDraft.of(item, group.id, isCircuitItem = group.isCircuit))
        }
    }

    fun onItemDraftChange(draft: ItemDraft) {
        _state.update { it.copy(editingItem = draft) }
    }

    fun onDismissItemEditor() {
        _state.update { it.copy(editingItem = null) }
    }

    fun onSaveItem() {
        val draft = _state.value.editingItem ?: return
        if (!draft.isValid) return
        viewModelScope.launch {
            if (draft.itemId == null) {
                programRepository.addItem(
                    groupId = draft.groupId,
                    name = draft.name,
                    sets = draft.setsValue!!,
                    minReps = draft.minRepsValue!!,
                    maxReps = draft.maxRepsValue!!,
                    leadInSets = draft.leadInSets,
                    weightStepKg = draft.weightStepValue!!,
                    isAssisted = draft.isAssisted,
                    isPerSide = draft.isPerSide,
                    currentWeightKg = draft.weightValue!!,
                )
            } else {
                programRepository.updateItem(
                    itemId = draft.itemId,
                    name = draft.name,
                    sets = draft.setsValue!!,
                    minReps = draft.minRepsValue!!,
                    maxReps = draft.maxRepsValue!!,
                    leadInSets = draft.leadInSets,
                    weightStepKg = draft.weightStepValue!!,
                    isAssisted = draft.isAssisted,
                    isPerSide = draft.isPerSide,
                    currentWeightKg = draft.weightValue!!,
                )
            }
            _state.update { it.copy(editingItem = null) }
        }
    }

    fun onDeleteItem(itemId: Long) {
        viewModelScope.launch {
            programRepository.deleteItem(itemId)
            _state.update { it.copy(editingItem = null) }
        }
    }

    fun onMoveItem(itemId: Long, delta: Int) {
        viewModelScope.launch { programRepository.moveItem(itemId, delta) }
    }
}
