package com.kbminisplit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.ui.theme.FeedbackColors

/**
 * Large colored dot used in the post-session feedback sheet. The only place in
 * the app where these three colors appear (spec §1.4).
 */
@Composable
fun FeedbackDot(
    feedback: Feedback,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color: Color = when (feedback) {
        Feedback.Red -> FeedbackColors.Red
        Feedback.Yellow -> FeedbackColors.Yellow
        Feedback.Green -> FeedbackColors.Green
    }
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(color)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
            .clickable { onClick() },
    )
}
