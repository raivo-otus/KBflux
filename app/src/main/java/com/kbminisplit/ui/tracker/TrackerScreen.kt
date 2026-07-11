package com.kbminisplit.ui.tracker

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.composed
import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseMechanic
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Split
import com.kbminisplit.ui.components.FeedbackDot
import com.kbminisplit.ui.components.SetButton
import com.kbminisplit.ui.util.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(viewModel: TrackerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingWeight by remember { mutableStateOf<WeightEditTarget?>(null) }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is TrackerUiState.Loading -> LoadingPlaceholder()
                is TrackerUiState.Ready -> ReadyContent(
                    state = s,
                    onSetTap = viewModel::onSetTap,
                    onSetDoubleTap = viewModel::onSetDoubleTap,
                    onSetLongPress = viewModel::onSetLongPress,
                    onFeedback = viewModel::onFeedback,
                    onBumpAccept = viewModel::onKbBumpAccept,
                    onBumpSnooze = viewModel::onKbBumpSnooze,
                    onStartAux = viewModel::onStartAux,
                    onSkipAux = viewModel::onSkipAux,
                ) { editingWeight = it }
            }
        }
    }

    editingWeight?.let { target ->
        WeightEditDialog(
            title = when (target) {
                is WeightEditTarget.Kb -> "Edit KB Flow weight"
                is WeightEditTarget.Strength -> "Edit ${target.exercise.displayName} weight"
                is WeightEditTarget.Bodyweight -> "Weekly bodyweight"
            },
            fieldLabel = when (target) {
                is WeightEditTarget.Bodyweight -> "Bodyweight (kg)"
                else -> "Weight (kg)"
            },
            initialValue = target.weightKg,
            onConfirm = { newWeight ->
                when (target) {
                    is WeightEditTarget.Kb -> viewModel.onKbWeightChange(newWeight)
                    is WeightEditTarget.Strength -> viewModel.onExerciseWeightChange(target.exercise.slug, newWeight)
                    is WeightEditTarget.Bodyweight -> viewModel.onBodyweightEntered(newWeight)
                }
                editingWeight = null
            },
            onDismiss = { editingWeight = null },
        )
    }
}

private sealed interface WeightEditTarget {
    val weightKg: Double
    data class Kb(override val weightKg: Double) : WeightEditTarget
    data class Strength(val exercise: Exercise, override val weightKg: Double) : WeightEditTarget
    data class Bodyweight(override val weightKg: Double) : WeightEditTarget
}

/** Sensible default shown in the bodyweight dialog before any entry exists. */
private const val DEFAULT_BODYWEIGHT_KG = 80.0

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading…")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
    state: TrackerUiState.Ready,
    onSetTap: (SetCell) -> Unit,
    onSetDoubleTap: (SetCell) -> Unit,
    onSetLongPress: (SetCell) -> Unit,
    onFeedback: (Feedback) -> Unit,
    onBumpAccept: () -> Unit,
    onBumpSnooze: () -> Unit,
    onStartAux: () -> Unit,
    onSkipAux: () -> Unit,
    onWeightTap: (WeightEditTarget) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("tracker_ready"),
    ) {
        if (state.isFirstSession) {
            FirstSessionBanner()
            Spacer(Modifier.height(20.dp))
        }

        Header(date = state.date, split = state.split)

        Spacer(Modifier.height(20.dp))

        when (state.phase) {
            TrackerPhase.MAIN -> MainBlock(
                state = state,
                onSetTap = onSetTap,
                onSetDoubleTap = onSetDoubleTap,
                onSetLongPress = onSetLongPress,
                onBumpAccept = onBumpAccept,
                onBumpSnooze = onBumpSnooze,
                onWeightTap = onWeightTap,
            )

            TrackerPhase.AUX -> AuxBlock(
                aux = state.aux,
                onSetTap = onSetTap,
                onSetDoubleTap = onSetDoubleTap,
                onSetLongPress = onSetLongPress,
                onWeightTap = onWeightTap,
            )
        }
    }

    if (state.showAuxPrompt) {
        AuxPromptDialog(onYes = onStartAux, onNo = onSkipAux)
    }

    // Spec §4.4: feedback is mandatory. Veto Hidden so swipe-down / back
    // can't dismiss; the only exit is tapping one of the dots.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    if (state.feedbackReady) {
        ModalBottomSheet(
            onDismissRequest = { /* No-op: spec §4.4. */ },
            sheetState = sheetState,
        ) {
            FeedbackSheet(onFeedback = onFeedback)
        }
    }
}

@Composable
private fun MainBlock(
    state: TrackerUiState.Ready,
    onSetTap: (SetCell) -> Unit,
    onSetDoubleTap: (SetCell) -> Unit,
    onSetLongPress: (SetCell) -> Unit,
    onBumpAccept: () -> Unit,
    onBumpSnooze: () -> Unit,
    onWeightTap: (WeightEditTarget) -> Unit,
) {
    state.kbBump?.let { bump ->
        KbBumpBanner(
            bump = bump,
            onAccept = onBumpAccept,
            onSnooze = onBumpSnooze,
        )
        Spacer(Modifier.height(16.dp))
    }

    if (state.bodyweightPrompt) {
        BodyweightBanner(
            onUpdate = {
                onWeightTap(
                    WeightEditTarget.Bodyweight(state.currentBodyweightKg ?: DEFAULT_BODYWEIGHT_KG),
                )
            },
        )
        Spacer(Modifier.height(16.dp))
    }

    KbFlowSection(
        kbWeightKg = state.kbWeightKg,
        block = state.kbBlock,
        onTap = onSetTap,
        onDoubleTap = onSetDoubleTap,
        onLongPress = onSetLongPress,
        onWeightTap = { onWeightTap(WeightEditTarget.Kb(state.kbWeightKg)) },
    )

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(20.dp))

    state.strength.forEachIndexed { index, row ->
        StrengthSection(
            row = row,
            onTap = onSetTap,
            onDoubleTap = onSetDoubleTap,
            onLongPress = onSetLongPress,
            onWeightTap = { onWeightTap(WeightEditTarget.Strength(row.exercise, row.weightKg)) },
        )
        if (index < state.strength.lastIndex) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AuxBlock(
    aux: List<StrengthMovementRow>,
    onSetTap: (SetCell) -> Unit,
    onSetDoubleTap: (SetCell) -> Unit,
    onSetLongPress: (SetCell) -> Unit,
    onWeightTap: (WeightEditTarget) -> Unit,
) {
    SectionTitle(text = "Auxiliary", modifier = Modifier.testTag("aux_block"))
    Spacer(Modifier.height(20.dp))

    aux.forEachIndexed { index, row ->
        StrengthSection(
            row = row,
            onTap = onSetTap,
            onDoubleTap = onSetDoubleTap,
            onLongPress = onSetLongPress,
            onWeightTap = { onWeightTap(WeightEditTarget.Strength(row.exercise, row.weightKg)) },
        )
        if (index < aux.lastIndex) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AuxPromptDialog(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Mandatory choice: no swipe/back dismiss. */ },
        modifier = Modifier.testTag("aux_prompt"),
        title = { Text("Auxiliary work?") },
        text = { Text("Nice work. Do you want to add your auxiliary movements before logging?") },
        confirmButton = {
            Button(onClick = onYes) { Text("Yes") }
        },
        dismissButton = {
            TextButton(onClick = onNo) { Text("No, finish") }
        },
    )
}

@Composable
private fun FirstSessionBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Welcome to your first session!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap a button when you complete a set. Double-tap if you fail it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Header(date: LocalDate, split: Split) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${split.name} — ${split.label}",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = date.format(HEADER_DATE_FORMAT),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KbBumpBanner(
    bump: KbBumpState,
    onAccept: () -> Unit,
    onSnooze: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kb_bump_banner"),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "It's been 3 months — bump KB to ${formatKg(bump.targetKg)} kg?",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAccept) {
                    Text("Bump to ${formatKg(bump.targetKg)} kg")
                }
                OutlinedButton(onClick = onSnooze) {
                    Text("Not yet")
                }
            }
        }
    }
}

@Composable
private fun BodyweightBanner(onUpdate: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bodyweight_banner"),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Weekly check-in — what's your bodyweight?",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onUpdate) {
                Text("Update bodyweight")
            }
        }
    }
}

@Composable
private fun KbFlowSection(
    kbWeightKg: Double,
    block: KbBlock,
    onTap: (SetCell) -> Unit,
    onDoubleTap: (SetCell) -> Unit,
    onLongPress: (SetCell) -> Unit,
    onWeightTap: () -> Unit,
) {
    SectionTitle(
        text = "KB Flow · ${formatKg(kbWeightKg)} kg",
        modifier = Modifier.tripleClickable(onClick = onWeightTap),
    )
    Spacer(Modifier.height(12.dp))
    block.movements.forEach { label ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label.exercise.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "x ${label.repsLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        block.circuits.forEachIndexed { idx, cell ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Circuit ${idx + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SetButton(
                    status = cell.status,
                    contentDescription = "KB Flow circuit ${idx + 1}",
                    onComplete = { onTap(cell) },
                    onFail = { onDoubleTap(cell) },
                    onRevert = { onLongPress(cell) },
                )
            }
        }
    }
}

@Composable
private fun StrengthSection(
    row: StrengthMovementRow,
    onTap: (SetCell) -> Unit,
    onDoubleTap: (SetCell) -> Unit,
    onLongPress: (SetCell) -> Unit,
    onWeightTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.exercise.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatKg(row.weightKg)} kg",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .tripleClickable(onClick = onWeightTap),
        )
        Text(
            text = "x ${row.targetReps}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
    if (row.exercise.mechanic == ExerciseMechanic.ASSISTED) {
        Spacer(Modifier.height(4.dp))
        Text(
            // The logged number is machine assistance; effective load is shown once
            // a bodyweight has been entered for the week.
            text = buildString {
                append("Assistance")
                row.effectiveLoadKg?.let { append(" · Effective ${formatKg(it)} kg") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SetButtonWithLabel(
            label = "Prime",
            cell = row.prime,
            contentDescription = "${row.exercise.displayName} priming set",
            onTap = onTap,
            onDoubleTap = onDoubleTap,
            onLongPress = onLongPress,
        )
        row.working.forEachIndexed { idx, cell ->
            SetButtonWithLabel(
                label = "Work",
                cell = cell,
                contentDescription = "${row.exercise.displayName} working set ${idx + 1}",
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onLongPress = onLongPress,
            )
        }
    }
}

@Composable
private fun SetButtonWithLabel(
    label: String,
    cell: SetCell,
    contentDescription: String,
    onTap: (SetCell) -> Unit,
    onDoubleTap: (SetCell) -> Unit,
    onLongPress: (SetCell) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        SetButton(
            status = cell.status,
            contentDescription = contentDescription,
            onComplete = { onTap(cell) },
            onFail = { onDoubleTap(cell) },
            onRevert = { onLongPress(cell) },
        )
    }
}

@Composable
private fun FeedbackSheet(onFeedback: (Feedback) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("feedback_sheet"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "How did that feel?",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FeedbackDot(
                feedback = Feedback.Red,
                contentDescription = "Feedback red",
                onClick = { onFeedback(Feedback.Red) },
            )
            FeedbackDot(
                feedback = Feedback.Yellow,
                contentDescription = "Feedback yellow",
                onClick = { onFeedback(Feedback.Yellow) },
            )
            FeedbackDot(
                feedback = Feedback.Green,
                contentDescription = "Feedback green",
                onClick = { onFeedback(Feedback.Green) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

/**
 * Custom modifier to detect triple taps.
 */
private fun Modifier.tripleClickable(onClick: () -> Unit): Modifier = composed {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var clickCount by remember { mutableIntStateOf(0) }

    Modifier.pointerInput(Unit) {
        detectTapGestures {
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 400) {
                clickCount++
            } else {
                clickCount = 1
            }
            lastClickTime = now
            if (clickCount >= 3) {
                onClick()
                clickCount = 0
            }
        }
    }
}

@Composable
private fun WeightEditDialog(
    title: String,
    initialValue: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    fieldLabel: String = "Weight (kg)",
) {
    var text by remember { mutableStateOf(formatKg(initialValue)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(fieldLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val weight = text.replace(',', '.').toDoubleOrNull()
                    if (weight != null && weight >= 0.0) {
                        onConfirm(weight)
                    }
                },
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private val Split.label: String
    get() = when (this) {
        Split.A -> "Pull"
        Split.B -> "Push"
        Split.C -> "Legs"
    }

private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
