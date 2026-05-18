package com.kbminisplit.ui.tracker

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                )
            }
        }
    }
}

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

        state.kbBump?.let { bump ->
            KbBumpBanner(
                bump = bump,
                onAccept = onBumpAccept,
                onSnooze = onBumpSnooze,
            )
            Spacer(Modifier.height(16.dp))
        }

        KbFlowSection(
            kbWeightKg = state.kbWeightKg,
            block = state.kbBlock,
            onTap = onSetTap,
            onDoubleTap = onSetDoubleTap,
            onLongPress = onSetLongPress,
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
            )
            if (index < state.strength.lastIndex) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Spec §4.4: feedback is mandatory. Veto Hidden so swipe-down / back
    // can't dismiss; the only exit is tapping one of the dots.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    if (state.allButtonsResolved) {
        ModalBottomSheet(
            onDismissRequest = { /* No-op: spec §4.4. */ },
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
                text = "It's been a month — bump KB to ${formatKg(bump.targetKg)} kg?",
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
private fun KbFlowSection(
    kbWeightKg: Double,
    block: KbBlock,
    onTap: (SetCell) -> Unit,
    onDoubleTap: (SetCell) -> Unit,
    onLongPress: (SetCell) -> Unit,
) {
    SectionTitle(text = "KB Flow · ${formatKg(kbWeightKg)} kg")
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
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "x ${row.targetReps}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

private val Split.label: String
    get() = when (this) {
        Split.A -> "Pull"
        Split.B -> "Push"
        Split.C -> "Legs"
    }

private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
