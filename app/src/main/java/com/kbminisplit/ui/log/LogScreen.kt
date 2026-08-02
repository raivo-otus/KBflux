package com.kbminisplit.ui.log

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.ui.theme.FeedbackColors
import com.kbminisplit.ui.theme.LocalHapticLevel
import com.kbminisplit.ui.util.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DETAIL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val CELL_SHAPE = RoundedCornerShape(4.dp)
private val CELL_SIZE = 32.dp
private val MONTH_LABEL_WIDTH = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: LogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val justCommittedDate by viewModel.justCommittedDate.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                LogUiState.Loading -> LoadingPlaceholder()
                is LogUiState.Ready -> if (s.rows.isEmpty()) {
                    EmptyState()
                } else {
                    WeekList(
                        state = s,
                        onCellTap = viewModel::onCellTap,
                        justCommittedDate = justCommittedDate,
                        onAnimationComplete = viewModel::clearJustCommitted,
                    )
                }
            }
        }
    }

    selected?.let { detail ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::onDismissDetail,
            sheetState = sheetState,
        ) {
            SessionDetailSheet(detail = detail)
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading…")
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No sessions yet — your log will fill in as you log workouts.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekList(
    state: LogUiState.Ready,
    onCellTap: (LocalDate) -> Unit,
    justCommittedDate: LocalDate?,
    onAnimationComplete: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Spec §5.1: auto-scroll to today on first open. Once the user has scrolled
    // we leave their position alone, so a session-commit emit doesn't jerk the
    // grid back to today.
    var hasScrolledToToday by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.todayRowIndex, state.rows.size) {
        if (!hasScrolledToToday && state.todayRowIndex in state.rows.indices) {
            listState.scrollToItem(state.todayRowIndex)
            hasScrolledToToday = true
        }
    }
    // If we just committed a session, force scroll to it so the animation is visible.
    LaunchedEffect(justCommittedDate) {
        if (justCommittedDate != null && state.todayRowIndex in state.rows.indices) {
            listState.animateScrollToItem(state.todayRowIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("log_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = state.rows, key = LogRow::key) { row ->
            when (row) {
                is LogRow.Week -> WeekRow(
                    row = row,
                    onCellTap = onCellTap,
                    justCommittedDate = justCommittedDate,
                    onAnimationComplete = onAnimationComplete,
                )
                is LogRow.MonthGap -> Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WeekRow(
    row: LogRow.Week,
    onCellTap: (LocalDate) -> Unit,
    justCommittedDate: LocalDate?,
    onAnimationComplete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(MONTH_LABEL_WIDTH),
            contentAlignment = Alignment.CenterStart,
        ) {
            row.monthLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.days.forEach { day ->
                DayCellView(
                    day = day,
                    onTap = onCellTap,
                    shouldAnimate = day.date == justCommittedDate,
                    onAnimationComplete = onAnimationComplete,
                )
            }
        }
    }
}

@Composable
private fun DayCellView(
    day: DayCell,
    onTap: (LocalDate) -> Unit,
    shouldAnimate: Boolean,
    onAnimationComplete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isOutside = day.state is DayCellState.Outside
    val view = LocalView.current
    val hapticLevel = LocalHapticLevel.current
    val density = LocalDensity.current
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate) {
            // Drop from above: 120dp fall
            val startOffset = with(density) { -120.dp.toPx() }
            offsetY.snapTo(startOffset)
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = EaseOutBack,
                ),
            )
            val constant = when (hapticLevel) {
                0 -> HapticFeedbackConstants.CLOCK_TICK
                1 -> HapticFeedbackConstants.VIRTUAL_KEY
                else -> HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(constant)
            onAnimationComplete()
        }
    }

    val borderWidth = if (day.isToday && !isOutside) 2.dp else 1.dp
    val borderColor = if (day.isToday && !isOutside) colors.onSurface else colors.outline
    val fillColor: Color = when (val s = day.state) {
        is DayCellState.Logged -> when (s.feedback) {
            Feedback.Red -> FeedbackColors.Red
            Feedback.Yellow -> FeedbackColors.Yellow
            Feedback.Green -> FeedbackColors.Green
        }
        else -> Color.Transparent
    }
    val isTappable = day.state is DayCellState.Logged
    val descriptor = day.contentDescription()

    val cellModifier = Modifier
        .size(CELL_SIZE)
        .offset { IntOffset(0, offsetY.value.roundToInt()) }
        .then(
            if (isOutside) Modifier
            else Modifier
                .background(fillColor, shape = CELL_SHAPE)
                .border(width = borderWidth, color = borderColor, shape = CELL_SHAPE),
        )
        .then(if (isTappable) Modifier.clickable { onTap(day.date) } else Modifier)
        .semantics { contentDescription = descriptor }

    Box(modifier = cellModifier, contentAlignment = Alignment.Center) {
        if (day.state is DayCellState.PastEmpty) {
            Text(
                text = "–",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun DayCell.contentDescription(): String {
    val date = date.toString()
    val base = when (val s = state) {
        is DayCellState.Outside -> return ""
        is DayCellState.PastEmpty -> "$date — no session"
        is DayCellState.Future -> "$date — future"
        is DayCellState.Logged -> "$date — session, feedback ${s.feedback.name.lowercase()}"
    }
    return if (isToday) "$base, today" else base
}

@Composable
private fun SessionDetailSheet(detail: SessionDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("log_detail_sheet"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = detail.dayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = detail.date.format(DETAIL_DATE_FORMAT),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FeedbackPip(feedback = detail.feedback)
        }
        Spacer(Modifier.height(16.dp))

        detail.movements.forEachIndexed { index, movement ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Text(
                text = buildString {
                    append(movement.name)
                    append(" · ${formatKg(movement.weightKg)} kg")
                    movement.repsLabel?.let { append(" · $it reps") }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            SetStatusRow(movement.statuses)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SetStatusRow(statuses: List<SetStatus>) {
    if (statuses.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        statuses.forEach { status ->
            val (glyph, weight, color) = when (status) {
                SetStatus.Completed -> Triple("✓", FontWeight.Bold, MaterialTheme.colorScheme.onSurface)
                SetStatus.Failed -> Triple("✗", FontWeight.Bold, MaterialTheme.colorScheme.onSurfaceVariant)
                SetStatus.Pending -> Triple("·", FontWeight.Normal, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = glyph,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = weight),
                color = color,
            )
        }
    }
}

@Composable
private fun FeedbackPip(feedback: Feedback) {
    val color = when (feedback) {
        Feedback.Red -> FeedbackColors.Red
        Feedback.Yellow -> FeedbackColors.Yellow
        Feedback.Green -> FeedbackColors.Green
    }
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(color),
    )
}
