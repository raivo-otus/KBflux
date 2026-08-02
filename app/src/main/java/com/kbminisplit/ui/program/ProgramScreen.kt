package com.kbminisplit.ui.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.domain.model.GroupKind
import com.kbminisplit.domain.model.ProgramDay
import com.kbminisplit.domain.model.ProgramGroup
import com.kbminisplit.domain.model.ProgramItem
import com.kbminisplit.ui.components.NumberField
import com.kbminisplit.ui.components.toWeightOrNull
import com.kbminisplit.ui.util.formatKg

/**
 * The Program tab: the whole split, editable in place.
 *
 * Reordering uses up/down arrows rather than drag — the app has no drag-and-drop
 * anywhere else, and a list this short doesn't justify the dependency.
 */
@Composable
fun ProgramScreen(viewModel: ProgramViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("program_screen"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.program.isEmpty && !state.isLoading) {
                item {
                    Text(
                        text = "No training days yet. Add one to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.program.days, key = { it.id }) { day ->
                DayCard(
                    day = day,
                    isFirst = day == state.program.days.first(),
                    isLast = day == state.program.days.last(),
                    isExpanded = day.id in state.expandedDayIds,
                    viewModel = viewModel,
                )
            }

            item {
                Button(
                    onClick = viewModel::onAddDay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_day"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Add training day")
                }
            }
        }
    }

    state.editingItem?.let { draft ->
        ItemEditorSheet(
            draft = draft,
            onChange = viewModel::onItemDraftChange,
            onSave = viewModel::onSaveItem,
            onDelete = draft.itemId?.let { id -> { viewModel.onDeleteItem(id) } },
            onDismiss = viewModel::onDismissItemEditor,
        )
    }

    state.editingGroup?.let { draft ->
        GroupEditorDialog(
            draft = draft,
            onChange = viewModel::onGroupDraftChange,
            onSave = viewModel::onSaveGroup,
            onDismiss = viewModel::onDismissGroupEditor,
        )
    }
}

@Composable
private fun DayCard(
    day: ProgramDay,
    isFirst: Boolean,
    isLast: Boolean,
    isExpanded: Boolean,
    viewModel: ProgramViewModel,
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onToggleDay(day.id) }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = day.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoveButtons(
                    canMoveUp = !isFirst,
                    canMoveDown = !isLast,
                    onMove = { viewModel.onMoveDay(day.id, it) },
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse ${day.name}" else "Expand ${day.name}",
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                day.groups.forEach { group ->
                    GroupSection(
                        day = day,
                        group = group,
                        viewModel = viewModel,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { viewModel.onAddGroup(day.id, GroupKind.STANDARD) }) {
                        Text("+ Block")
                    }
                    TextButton(onClick = { viewModel.onAddGroup(day.id, GroupKind.CIRCUIT) }) {
                        Text("+ Circuit")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { renaming = true }) { Text("Rename") }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (renaming) {
        TextPromptDialog(
            title = "Rename day",
            label = "Name",
            initial = day.name,
            onConfirm = {
                viewModel.onRenameDay(day.id, it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${day.name}?") },
            text = {
                Text(
                    "Its blocks and movements go with it. Sessions you've already " +
                        "logged against this day are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDeleteDay(day.id)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GroupSection(
    day: ProgramDay,
    group: ProgramGroup,
    viewModel: ProgramViewModel,
) {
    var editingWeight by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.onEditGroup(group) },
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = group.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MoveButtons(
                canMoveUp = group != day.groups.first(),
                canMoveDown = group != day.groups.last(),
                onMove = { viewModel.onMoveGroup(group.id, it) },
            )
            IconButton(onClick = { viewModel.onDeleteGroup(group.id) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${group.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (group.isCircuit) {
            Text(
                text = "Weight ${formatKg(group.weightKg ?: 0.0)} kg",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clickable { editingWeight = true }
                    .padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        group.items.forEach { item ->
            ItemRow(
                item = item,
                isCircuit = group.isCircuit,
                canMoveUp = item != group.items.first(),
                canMoveDown = item != group.items.last(),
                onClick = { viewModel.onEditItem(item, group) },
                onMove = { viewModel.onMoveItem(item.id, it) },
            )
        }

        TextButton(onClick = { viewModel.onAddItem(group) }) { Text("+ Movement") }
    }

    if (editingWeight) {
        NumberPromptDialog(
            title = "${group.name} weight",
            label = "Weight (kg)",
            initial = formatNumber(group.weightKg ?: 0.0),
            onConfirm = { value ->
                viewModel.onCircuitWeightChange(group.id, value)
                editingWeight = false
            },
            onDismiss = { editingWeight = false },
        )
    }
}

@Composable
private fun ItemRow(
    item: ProgramItem,
    isCircuit: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMove: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = item.summary(isCircuit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoveButtons(canMoveUp = canMoveUp, canMoveDown = canMoveDown, onMove = onMove)
    }
}

@Composable
private fun MoveButtons(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    Row {
        IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditorSheet(
    draft: ItemDraft,
    onChange: (ItemDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .testTag("item_editor"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (draft.itemId == null) "Add movement" else "Edit movement",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("Name") },
                singleLine = true,
                isError = draft.name.isNotEmpty() && !draft.isNameValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("item_name"),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = draft.minReps,
                    onValueChange = { onChange(draft.copy(minReps = it)) },
                    label = "Min reps",
                    isError = !draft.isRepRangeValid,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = draft.maxReps,
                    onValueChange = { onChange(draft.copy(maxReps = it)) },
                    label = "Max reps",
                    isError = !draft.isRepRangeValid,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!draft.isCircuitItem) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = draft.sets,
                        onValueChange = { onChange(draft.copy(sets = it)) },
                        label = "Sets",
                        isError = draft.setsValue == null,
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = draft.weight,
                        onValueChange = { onChange(draft.copy(weight = it)) },
                        label = "Weight (kg)",
                        decimal = true,
                        isError = draft.weightValue == null,
                        modifier = Modifier.weight(1f),
                    )
                }

                LabelledRow("Increment") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEIGHT_STEP_OPTIONS.forEach { step ->
                            FilterChip(
                                selected = draft.weightStepValue == step,
                                onClick = { onChange(draft.copy(weightStep = formatNumber(step))) },
                                label = { Text("${formatNumber(step)} kg") },
                            )
                        }
                    }
                }

                LabelledRow("Lead-in sets") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LEAD_IN_LABELS.forEachIndexed { count, label ->
                            FilterChip(
                                selected = draft.leadInSets == count,
                                onClick = { onChange(draft.copy(leadInSets = count)) },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                SwitchRow(
                    label = "Assisted",
                    hint = "The logged number is machine help, so progress lowers it.",
                    checked = draft.isAssisted,
                    onCheckedChange = { onChange(draft.copy(isAssisted = it)) },
                )
            }

            SwitchRow(
                label = "Per side",
                hint = "Reps are counted one side at a time.",
                checked = draft.isPerSide,
                onCheckedChange = { onChange(draft.copy(isPerSide = it)) },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSave,
                    enabled = draft.isValid,
                    modifier = Modifier.testTag("save_item"),
                ) {
                    Text("Save")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                onDelete?.let {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = it) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GroupEditorDialog(
    draft: GroupDraft,
    onChange: (GroupDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onChange(draft.copy(name = it)) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    label = "Rotate movements",
                    hint = "Shift the order by one each time this day comes around.",
                    checked = draft.rotates,
                    onCheckedChange = { onChange(draft.copy(rotates = it)) },
                )
                SwitchRow(
                    label = "Reveal later",
                    hint = "Stay hidden until the earlier blocks are done.",
                    checked = draft.isDeferred,
                    onCheckedChange = { onChange(draft.copy(isDeferred = it)) },
                )
                if (draft.isCircuit) {
                    NumberField(
                        value = draft.rounds,
                        onValueChange = { onChange(draft.copy(rounds = it)) },
                        label = "Rounds",
                        isError = draft.roundsValue == null,
                    )
                    SwitchRow(
                        label = "Kettlebell ladder",
                        hint = "Offer the next bell up every three months.",
                        checked = draft.usesLadder,
                        onCheckedChange = { onChange(draft.copy(usesLadder = it)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = draft.isValid) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun LabelledRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberPromptDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val value = text.toWeightOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NumberField(
                value = text,
                onValueChange = { text = it },
                label = label,
                decimal = true,
                isError = value == null,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = value != null,
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val LEAD_IN_LABELS = listOf("None", "Warm-up", "Prime + warm-up")

private fun ProgramDay.summary(): String {
    val movements = groups.sumOf { it.items.size }
    val blocks = groups.size
    return "$blocks ${if (blocks == 1) "block" else "blocks"} · " +
        "$movements ${if (movements == 1) "movement" else "movements"}"
}

private fun ProgramGroup.summary(): String = buildList {
    if (isCircuit) add("circuit · $rounds rounds") else add("block")
    if (rotates && items.size > 1) add("rotates")
    if (isDeferred) add("reveals later")
    if (isCircuit && usesLadder) add("ladder")
}.joinToString(" · ")

private fun ProgramItem.summary(isCircuit: Boolean): String = buildString {
    if (!isCircuit) append("$sets × ")
    append(repRangeLabel)
    if (isPerSide) append("/side")
    if (!isCircuit) {
        append(" · ${formatKg(currentWeightKg)} kg")
        append(" · +${formatKg(weightStepKg)}")
        if (isAssisted) append(" · assisted")
        if (leadInSets == 0) append(" · no lead-in")
    }
}
