package com.kbminisplit.ui.progression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ProgressionScreen(
    viewModel: ProgressionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column {
                Text(
                    text = uiState.kbProgression.exercise.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                MovementChart(uiState.kbProgression)
            }
        }
        items(uiState.strengthProgression) { progression ->
            Column {
                Text(
                    text = progression.exercise.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                MovementChart(progression)
            }
        }
    }
}

private val SteppedPointConnector = LineCartesianLayer.PointConnector { _, path, _, prevY, x, y ->
    path.lineTo(x, prevY)
    path.lineTo(x, y)
}

@Composable
fun MovementChart(progression: MovementProgression) {
    if (progression.dataPoints.isEmpty()) {
        Box(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val firstDate = remember(progression.dataPoints) {
        progression.dataPoints.first().date
    }
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }

    LaunchedEffect(progression.dataPoints) {
        modelProducer.runTransaction {
            lineSeries {
                series(
                    x = progression.dataPoints.map { ChronoUnit.DAYS.between(firstDate, it.date).toDouble() },
                    y = progression.dataPoints.map { it.weightKg }
                )
                if (progression.dataPoints.any { it.targetReps != null }) {
                    series(
                        x = progression.dataPoints.map { ChronoUnit.DAYS.between(firstDate, it.date).toDouble() },
                        y = progression.dataPoints.map { it.targetReps?.toDouble() ?: 0.0 }
                    )
                }
            }
        }
    }

    val weightColor = MaterialTheme.colorScheme.primary
    val repsColor = MaterialTheme.colorScheme.outline

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(weightColor)),
                        pointConnector = SteppedPointConnector
                    ),
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(repsColor))
                    )
                )
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { _, value, _ ->
                    firstDate.plusDays(value.toLong()).format(dateTimeFormatter)
                }
            ),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .height(200.dp)
            .fillMaxWidth(),
        zoomState = rememberVicoZoomState(zoomEnabled = true)
    )
}
