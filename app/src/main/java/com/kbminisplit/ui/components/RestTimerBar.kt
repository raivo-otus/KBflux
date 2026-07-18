package com.kbminisplit.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kbminisplit.ui.theme.LocalHapticLevel
import com.kbminisplit.ui.util.formatElapsed
import kotlinx.coroutines.delay

/** Soft rest-guide marks (spec §4.7): short rest for priming/easy sets… */
private const val SHORT_REST_SECONDS = 90L

/** …full rest before the next heavy working set. */
private const val LONG_REST_SECONDS = 180L

/**
 * Two-stage rest guide pinned under the Tracker content.
 *
 * Counts up from the last resolved set over a hairline track that fills toward
 * the 3:00 end, with a notch at 1:30. Guidance only — nothing blocks: past 3:00
 * the bar dims and keeps counting until the next set restarts it or the session
 * rebuild hides it. A subtle haptic marks each threshold crossing.
 *
 * Elapsed time is re-derived from the wall clock on every tick, so time spent
 * backgrounded self-corrects on resume.
 */
@Composable
fun RestTimerBar(startedAtMillis: Long, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val hapticLevel by rememberUpdatedState(LocalHapticLevel.current)

    var elapsedSeconds by remember(startedAtMillis) {
        mutableLongStateOf(elapsedSince(startedAtMillis))
    }

    LaunchedEffect(startedAtMillis) {
        // Seed from the current elapsed so re-entering composition mid-rest
        // (rotation, tab switch) doesn't replay haptics for marks already passed.
        var prev = elapsedSince(startedAtMillis)
        while (true) {
            val elapsed = elapsedSince(startedAtMillis)
            val crossedShort = prev < SHORT_REST_SECONDS && elapsed >= SHORT_REST_SECONDS
            val crossedLong = prev < LONG_REST_SECONDS && elapsed >= LONG_REST_SECONDS
            if (crossedShort || crossedLong) {
                val constant = when (hapticLevel) {
                    0 -> HapticFeedbackConstants.CLOCK_TICK
                    1 -> HapticFeedbackConstants.VIRTUAL_KEY
                    else -> HapticFeedbackConstants.LONG_PRESS
                }
                view.performHapticFeedback(constant)
            }
            prev = elapsed
            elapsedSeconds = elapsed
            // Wake just past the next second boundary so the label never skips.
            val sinceStart = System.currentTimeMillis() - startedAtMillis
            delay((1_000L - sinceStart % 1_000L).coerceIn(50L, 1_000L))
        }
    }

    val colors = MaterialTheme.colorScheme
    // Past the long mark the guide has said its piece: dim and keep counting.
    val emphasis = if (elapsedSeconds >= LONG_REST_SECONDS) colors.outline else colors.onSurface
    val trackColor = colors.outlineVariant
    val fraction by animateFloatAsState(
        targetValue = (elapsedSeconds.toFloat() / LONG_REST_SECONDS).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
        label = "rest_fill",
    )

    Surface(modifier = modifier.fillMaxWidth().testTag("rest_timer")) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatElapsed(elapsedSeconds),
                    color = emphasis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        // Tabular digits: the label can't jitter as seconds tick.
                        fontFeatureSettings = "tnum",
                    ),
                )
                Spacer(Modifier.width(16.dp))
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    val trackY = size.height / 2f
                    val stroke = 2.dp.toPx()
                    val notchHalf = 3.dp.toPx()
                    drawLine(trackColor, Offset(0f, trackY), Offset(size.width, trackY), stroke)
                    if (fraction > 0f) {
                        drawLine(emphasis, Offset(0f, trackY), Offset(size.width * fraction, trackY), stroke)
                    }
                    // Guide notches: 1:30 midpoint and the 3:00 track end. Taller
                    // than the track so they stay legible over the fill.
                    val shortX = size.width * (SHORT_REST_SECONDS.toFloat() / LONG_REST_SECONDS)
                    val longX = size.width - stroke / 2f
                    listOf(shortX, longX).forEach { x ->
                        drawLine(emphasis, Offset(x, trackY - notchHalf), Offset(x, trackY + notchHalf), stroke)
                    }
                }
            }
        }
    }
}

private fun elapsedSince(startedAtMillis: Long): Long =
    ((System.currentTimeMillis() - startedAtMillis) / 1_000L).coerceAtLeast(0L)
