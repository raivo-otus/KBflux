package com.kbminisplit.ui.progression

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.ui.util.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ProgressionScreen(
    viewModel: ProgressionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            MovementChart(uiState.kbProgression, uiState.windowStart, uiState.windowEnd)
        }
        items(uiState.strengthProgression) { progression ->
            MovementChart(progression, uiState.windowStart, uiState.windowEnd)
        }
    }
}

@Composable
fun MovementChart(
    progression: MovementProgression,
    windowStart: LocalDate,
    windowEnd: LocalDate,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = progression.exercise.displayName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            progression.dataPoints.lastOrNull()?.let { latest ->
                Text(
                    text = "${formatKg(latest.weightKg)} kg",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        WeightChart(progression.dataPoints, windowStart, windowEnd)
    }
}

private val ChartHeight = 180.dp
private val AxisDateFormat = DateTimeFormatter.ofPattern("MMM d")

/**
 * A minimal monochrome line chart: a solid weight line with a dot per session,
 * three faint gridlines with kg labels on the left, and the window's start,
 * middle, and end dates along the bottom. The x-domain is the fixed 8-week
 * window, so every chart on the screen shares the same time scale.
 */
@Composable
private fun WeightChart(
    dataPoints: List<ProgressionDataPoint>,
    windowStart: LocalDate,
    windowEnd: LocalDate,
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = Modifier
                .height(ChartHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.outline,
    )
    val textMeasurer = rememberTextMeasurer()

    val yBounds = remember(dataPoints) { chartYBounds(dataPoints.map { it.weightKg }) }
    val totalDays = remember(windowStart, windowEnd) {
        ChronoUnit.DAYS.between(windowStart, windowEnd).coerceAtLeast(1L)
    }

    Canvas(
        modifier = Modifier
            .height(ChartHeight)
            .fillMaxWidth(),
    ) {
        val (yMin, yMax) = yBounds
        val yMid = (yMin + yMax) / 2

        val yLabels = listOf(yMax, yMid, yMin).map {
            textMeasurer.measure(formatKg(it), labelStyle)
        }
        val xLabels = listOf(windowStart, windowStart.plusDays(totalDays / 2), windowEnd).map {
            textMeasurer.measure(it.format(AxisDateFormat), labelStyle)
        }

        val labelPad = 8.dp.toPx()
        val pointRadius = 2.5.dp.toPx()
        val latestPointRadius = 4.dp.toPx()

        val plotLeft = yLabels.maxOf { it.size.width } + labelPad
        val plotRight = size.width - latestPointRadius
        val plotTop = yLabels.first().size.height / 2f
        val plotBottom = size.height - xLabels.maxOf { it.size.height } - 6.dp.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        fun xOf(date: LocalDate): Float {
            val fraction = ChronoUnit.DAYS.between(windowStart, date).toFloat() / totalDays
            return plotLeft + plotWidth * fraction.coerceIn(0f, 1f)
        }

        fun yOf(weightKg: Double): Float =
            plotBottom - plotHeight * ((weightKg - yMin) / (yMax - yMin)).toFloat()

        // Gridlines with their kg labels, top to bottom.
        listOf(yMax, yMid, yMin).forEachIndexed { i, value ->
            val y = yOf(value)
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = yLabels[i]
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x = plotLeft - labelPad - label.size.width,
                    y = y - label.size.height / 2f,
                ),
            )
        }

        // Window start, middle, and end dates along the bottom.
        xLabels.forEachIndexed { i, label ->
            val x = when (i) {
                0 -> plotLeft
                1 -> plotLeft + (plotWidth - label.size.width) / 2f
                else -> plotRight - label.size.width
            }
            drawText(
                textLayoutResult = label,
                topLeft = Offset(x, plotBottom + 6.dp.toPx()),
            )
        }

        // The weight line itself: solid, with a dot per session and an
        // emphasized dot on the latest one.
        if (dataPoints.size > 1) {
            val path = Path()
            dataPoints.forEachIndexed { i, point ->
                val x = xOf(point.date)
                val y = yOf(point.weightKg)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        dataPoints.forEachIndexed { i, point ->
            drawCircle(
                color = lineColor,
                radius = if (i == dataPoints.lastIndex) latestPointRadius else pointRadius,
                center = Offset(xOf(point.date), yOf(point.weightKg)),
            )
        }
    }
}

/**
 * Pads the weight range outward to clean 2.5 kg multiples, with an even number
 * of steps so the middle gridline lands on a clean value too.
 */
private fun chartYBounds(weights: List<Double>): Pair<Double, Double> {
    val step = 2.5
    val lo = max(0.0, floor((weights.min() - step / 2) / step) * step)
    var hi = ceil((weights.max() + step / 2) / step) * step
    if (((hi - lo) / step).roundToInt() % 2 != 0) hi += step
    return lo to hi
}
