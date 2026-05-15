package com.kbminisplit.ui.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Philosophy",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "KB MiniSplit is a single-purpose training tracker. It encodes one workout program and does nothing else. " +
                        "The design is driven by three core principles:\n\n" +
                        "• One screen does one thing.\n" +
                        "• Tap, don't type: No number pads during a workout.\n" +
                        "• The app decides: Your split, reps, and weight are derived from your history. You lift; the app keeps score.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Programming",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The app follows a structured rotation:\n\n" +
                        "• Every session starts with a KB Flow: 3 circuits of Swings, Clean & Press, Lunges, Goblet Squats, and Push-ups.\n" +
                        "• Followed by a strength split: A (Pull), B (Push), or C (Squat).\n\n" +
                        "Progression is automatic. Complete all sets to increase target reps next time. " +
                        "Once you hit 16 reps, weight increases and reps reset to 8. " +
                        "Kettlebell weight is prompted for a bump once a month.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Intention",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This app was built to remove friction. There are no edit screens because a completed session is a record, not a draft. " +
                        "The monochrome interface keeps you focused on the work. Color only appears when it matters: to show you how you performed.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
