package com.kbminisplit.ui.tracker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.ui.components.FeedbackDot
import com.kbminisplit.ui.components.RestTimerBar
import com.kbminisplit.ui.components.SetButton
import com.kbminisplit.ui.util.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(viewModel: TrackerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restStartedAt by viewModel.restStartedAtMillis.collectAsStateWithLifecycle()
    var editingWeight by remember { mutableStateOf<WeightEditTarget?>(null) }

    // On days with an assisted lift, capture a current bodyweight up front: the
    // lead-in loads for assisted movements are derived from effective load, so
    // we ask before the user starts. Handled once per day — dismissing won't re-open
    // it (the persistent banner remains for manual entry).
    val ready = state as? TrackerUiState.Ready
    var bodyweightAskedForDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(ready?.date, ready?.bodyweightPrompt) {
        if (ready != null && ready.bodyweightPrompt && bodyweightAskedForDate != ready.date) {
            bodyweightAskedForDate = ready.date
            editingWeight = WeightEditTarget.Bodyweight(ready.currentBodyweightKg ?: DEFAULT_BODYWEIGHT_KG)
        }
    }

    Scaffold(
        bottomBar = {
            // The rest guide lives in the bottomBar slot so the Scaffold folds its
            // height into the content padding — it can never cover the last row of
            // set circles. Hidden once the feedback sheet takes over.
            var lastStartedAt by remember { mutableStateOf<Long?>(null) }
            restStartedAt?.let { lastStartedAt = it }
            AnimatedVisibility(
                visible = restStartedAt != null && ready != null && !ready.feedbackReady,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                // Render from the cached anchor so the exit animation shrinks the
                // bar instead of collapsing on empty content when the value nulls.
                lastStartedAt?.let { RestTimerBar(startedAtMillis = it) }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is TrackerUiState.Loading -> CenteredMessage("Loading…")
                is TrackerUiState.NoProgram -> CenteredMessage(
                    "No training days yet.\nAdd one in the Program tab.",
                )

                is TrackerUiState.Ready -> ReadyContent(
                    state = s,
                    onSetTap = viewModel::onSetTap,
                    onSetDoubleTap = viewModel::onSetDoubleTap,
                    onSetLongPress = viewModel::onSetLongPress,
                    onFeedback = viewModel::onFeedback,
                    onBumpToggle = viewModel::onBumpToggle,
                    onCircuitBumpAccept = viewModel::onCircuitBumpAccept,
                    onCircuitBumpSnooze = viewModel::onCircuitBumpSnooze,
                    onRestWeekAccept = viewModel::onRestWeekAccept,
                    onRestWeekSnooze = viewModel::onRestWeekSnooze,
                ) { editingWeight = it }
            }
        }
    }

    editingWeight?.let { target ->
        WeightEditDialog(
            title = when (target) {
                is WeightEditTarget.Circuit -> "Edit ${target.name} weight"
                is WeightEditTarget.Movement -> "Edit ${target.name} weight"
                is WeightEditTarget.Bodyweight -> "Weekly bodyweight"
            },
            fieldLabel = when (target) {
                is WeightEditTarget.Bodyweight -> "Bodyweight (kg)"
                else -> "Weight (kg)"
            },
            initialValue = target.weightKg,
            onConfirm = { newWeight ->
                when (target) {
                    is WeightEditTarget.Circuit ->
                        viewModel.onCircuitWeightChange(target.groupId, newWeight)

                    is WeightEditTarget.Movement ->
                        viewModel.onMovementWeightChange(target.programItemId, newWeight)

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

    data class Circuit(
        val groupId: Long,
        val name: String,
        override val weightKg: Double,
    ) : WeightEditTarget

    data class Movement(
        val programItemId: Long,
        val name: String,
        override val weightKg: Double,
    ) : WeightEditTarget

    data class Bodyweight(override val weightKg: Double) : WeightEditTarget
}

/** Sensible default shown in the bodyweight dialog before any entry exists. */
private const val DEFAULT_BODYWEIGHT_KG = 80.0

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    onBumpToggle: (Long) -> Unit,
    onCircuitBumpAccept: (Long) -> Unit,
    onCircuitBumpSnooze: (Long) -> Unit,
    onRestWeekAccept: () -> Unit,
    onRestWeekSnooze: () -> Unit,
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

        Header(date = state.date, dayName = state.dayName)

        Spacer(Modifier.height(20.dp))

        if (state.restWeekPrompt) {
            RestWeekBanner(onAccept = onRestWeekAccept, onSnooze = onRestWeekSnooze)
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

        state.groups.forEachIndexed { index, group ->
            when (group) {
                is GroupBlock.Circuit -> CircuitSection(
                    block = group,
                    onTap = onSetTap,
                    onDoubleTap = onSetDoubleTap,
                    onLongPress = onSetLongPress,
                    onBumpAccept = { onCircuitBumpAccept(group.groupId) },
                    onBumpSnooze = { onCircuitBumpSnooze(group.groupId) },
                    onWeightTap = {
                        onWeightTap(
                            WeightEditTarget.Circuit(group.groupId, group.name, group.weightKg),
                        )
                    },
                )

                is GroupBlock.Standard -> StandardSection(
                    block = group,
                    onTap = onSetTap,
                    onDoubleTap = onSetDoubleTap,
                    onLongPress = onSetLongPress,
                    onBumpToggle = onBumpToggle,
                    onWeightTap = onWeightTap,
                )
            }
            if (index < state.groups.lastIndex) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Feedback is mandatory. Veto Hidden so swipe-down / back can't dismiss;
    // the only exit is tapping one of the dots.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (state.feedbackReady) {
        ModalBottomSheet(
            onDismissRequest = { /* No-op: feedback is mandatory. */ },
            sheetState = sheetState,
        ) {
            FeedbackSheet(onFeedback = onFeedback)
        }
    }
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
private fun Header(date: LocalDate, dayName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = date.format(HEADER_DATE_FORMAT),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared shell for the Tracker's prompt banners. */
@Composable
private fun BannerSurface(
    testTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun RestWeekBanner(onAccept: () -> Unit, onSnooze: () -> Unit) {
    BannerSurface(testTag = "rest_week_banner") {
        Text(
            text = "That's two solid months of training. Take a rest week?",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Every movement drops one increment so you have a runway back up.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAccept) { Text("Take a rest week") }
            OutlinedButton(onClick = onSnooze) { Text("Not yet") }
        }
    }
}

@Composable
private fun CircuitBumpBanner(
    bump: CircuitBumpState,
    onAccept: () -> Unit,
    onSnooze: () -> Unit,
) {
    BannerSurface(testTag = "circuit_bump_banner") {
        Text(
            text = "It's been 3 months — move up to ${formatKg(bump.targetKg)} kg?",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAccept) { Text("Go to ${formatKg(bump.targetKg)} kg") }
            OutlinedButton(onClick = onSnooze) { Text("Not yet") }
        }
    }
}

@Composable
private fun BodyweightBanner(onUpdate: () -> Unit) {
    BannerSurface(testTag = "bodyweight_banner") {
        Text(
            text = "Weekly check-in — what's your bodyweight?",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onUpdate) { Text("Update bodyweight") }
    }
}

@Composable
private fun CircuitSection(
    block: GroupBlock.Circuit,
    onTap: (SetCell) -> Unit,
    onDoubleTap: (SetCell) -> Unit,
    onLongPress: (SetCell) -> Unit,
    onBumpAccept: () -> Unit,
    onBumpSnooze: () -> Unit,
    onWeightTap: () -> Unit,
) {
    block.bump?.let { bump ->
        CircuitBumpBanner(bump = bump, onAccept = onBumpAccept, onSnooze = onBumpSnooze)
        Spacer(Modifier.height(16.dp))
    }

    SectionTitle(
        text = "${block.name} · ${formatKg(block.weightKg)} kg",
        modifier = Modifier.tripleClickable(onClick = onWeightTap),
    )
    Spacer(Modifier.height(12.dp))
    block.movements.forEach { movement ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = movement.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = movement.repsLabel,
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
        block.rounds.forEachIndexed { idx, cell ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Round ${idx + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SetButton(
                    status = cell.status,
                    contentDescription = "${block.name} round ${idx + 1}",
                    onComplete = { onTap(cell) },
                    onFail = { onDoubleTap(cell) },
                    onRevert = { onLongPress(cell) },
                )
            }
        }
    }
}

@Composable
private fun StandardSection(
    block: GroupBlock.Standard,
    onTap: (SetCell) -> Unit,
    onDoubleTap: (SetCell) -> Unit,
    onLongPress: (SetCell) -> Unit,
    onBumpToggle: (Long) -> Unit,
    onWeightTap: (WeightEditTarget) -> Unit,
) {
    block.movements.forEachIndexed { index, row ->
        MovementSection(
            row = row,
            onTap = onTap,
            onDoubleTap = onDoubleTap,
            onLongPress = onLongPress,
            onBumpToggle = { onBumpToggle(row.programItemId) },
            onWeightTap = {
                onWeightTap(WeightEditTarget.Movement(row.programItemId, row.name, row.weightKg))
            },
        )
        if (index < block.movements.lastIndex) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MovementSection(
    row: MovementRow,
    onTap: (SetCell) -> Unit,
    onDoubleTap: (SetCell) -> Unit,
    onLongPress: (SetCell) -> Unit,
    onBumpToggle: () -> Unit,
    onWeightTap: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.name,
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
            text = row.repRangeLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
    row.effectiveLoadKg?.let { effective ->
        Spacer(Modifier.height(4.dp))
        Text(
            // The logged number is machine assistance; effective load is shown once
            // a bodyweight has been entered for the week.
            text = "Assistance · Effective ${formatKg(effective)} kg",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.leadIn.forEachIndexed { idx, cell ->
            val label = if (row.leadIn.size == 2 && idx == 0) "Prime" else "Warm-up"
            SetButtonWithLabel(
                label = label,
                cell = cell,
                // The number is the acclimatization load to plate up for this lead-in set.
                centerText = formatKg(cell.weightKg),
                contentDescription = "${row.name} $label set",
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onLongPress = onLongPress,
                modifier = Modifier.weight(1f),
            )
        }
        row.working.forEachIndexed { idx, cell ->
            SetButtonWithLabel(
                label = "Work",
                cell = cell,
                contentDescription = "${row.name} working set ${idx + 1}",
                onTap = onTap,
                onDoubleTap = onDoubleTap,
                onLongPress = onLongPress,
                modifier = Modifier.weight(1f),
            )
        }
    }
    row.bump?.let { bump ->
        Spacer(Modifier.height(10.dp))
        BumpChip(bump = bump, onClick = onBumpToggle)
    }
}

/**
 * The offer that appears the moment every working set of a movement is completed.
 * Armed means next session starts at the new weight; tapping again gives it back.
 */
@Composable
private fun BumpChip(bump: BumpState, onClick: () -> Unit) {
    val label = if (bump.isArmed) {
        "Next time: ${formatKg(bump.targetKg)} kg — tap to undo"
    } else {
        "All sets done · go to ${formatKg(bump.targetKg)} kg?"
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bump_chip"),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (bump.isArmed) 6.dp else 2.dp,
        color = if (bump.isArmed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (bump.isArmed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
        )
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
    modifier: Modifier = Modifier,
    centerText: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        SetButton(
            status = cell.status,
            contentDescription = contentDescription,
            onComplete = { onTap(cell) },
            onFail = { onDoubleTap(cell) },
            onRevert = { onLongPress(cell) },
            centerText = centerText,
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

private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
