package com.kbminisplit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kbminisplit.domain.model.SetStatus

/**
 * The one button that does almost everything on the Tracker. Three visual states
 * driven by [status]:
 *
 *  - [SetStatus.Pending]   — outlined, empty
 *  - [SetStatus.Completed] — filled with a checkmark glyph
 *  - [SetStatus.Failed]    — filled with an em-dash glyph
 *
 * Gestures (per spec §4.2):
 *  - single tap → Completed (short haptic)
 *  - double tap → Failed (long haptic)
 *  - long press → Pending (light haptic) — mid-session revert only
 *
 * The same single-tap-from-non-pending behavior is allowed so the user can
 * correct a double-tap-too-many: tap unconditionally moves toward Completed,
 * double-tap toward Failed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetButton(
    status: SetStatus,
    contentDescription: String,
    onComplete: () -> Unit,
    onFail: () -> Unit,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentStatus by rememberUpdatedState(status)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnFail by rememberUpdatedState(onFail)
    val currentOnRevert by rememberUpdatedState(onRevert)
    val targetScale = if (status == SetStatus.Pending) 1f else 1.04f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(),
        label = "set_button_scale",
    )

    val colors = MaterialTheme.colorScheme
    val (background, glyphColor) = when (status) {
        SetStatus.Pending -> colors.surface to Color.Transparent
        SetStatus.Completed -> colors.onSurface to colors.surface
        SetStatus.Failed -> colors.onSurface to colors.surface
    }
    val border = if (status == SetStatus.Pending) colors.outline else colors.onSurface

    Surface(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = status.name
            }
            .combinedClickable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnComplete()
                },
                onDoubleClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnFail()
                },
                onLongClick = {
                    if (currentStatus != SetStatus.Pending) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnRevert()
                    }
                },
                role = Role.Button,
            ),
        shape = CircleShape,
        color = background,
        border = BorderStroke(1.5.dp, border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val glyph = when (status) {
                SetStatus.Pending -> ""
                SetStatus.Completed -> "✓"
                SetStatus.Failed -> "–"
            }
            if (glyph.isNotEmpty()) {
                Text(
                    text = glyph,
                    color = glyphColor,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
