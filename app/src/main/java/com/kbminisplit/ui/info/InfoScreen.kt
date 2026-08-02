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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kbminisplit.BuildConfig

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
            Section(
                title = "Philosophy",
                body = "KB MiniSplit is a training tracker built around one idea: during a " +
                    "session you should be tapping, not typing.\n\n" +
                    "• One screen does one thing.\n" +
                    "• Tap, don't type: no number pads mid-workout.\n" +
                    "• You decide the programming; the app keeps score and gets out of the way.",
            )

            Section(
                title = "Your program",
                body = "The Program tab holds your split. Add as many training days as you " +
                    "want, and inside each one group your movements into blocks.\n\n" +
                    "Every movement carries its own sets, rep range, weight, increment, " +
                    "lead-in sets, and whether it's assisted.\n\n" +
                    "A block set to rotate shuffles its movements by one each time that day " +
                    "comes around, so the same lift is never permanently last. A block set to " +
                    "defer stays hidden until the earlier work is done. A circuit block " +
                    "tracks rounds instead of sets, and can climb the kettlebell ladder " +
                    "(8-10-12-16-20-24-28-32 kg) with a prompt every three months.",
            )

            Section(
                title = "Progression",
                body = "Nothing moves your weights except you.\n\n" +
                    "Reps are a range, not a target — 8–12 means anywhere in there counts. " +
                    "Complete every working set of a movement and a chip appears offering the " +
                    "next weight up. Take it and the next session starts there; ignore it and " +
                    "you stay put.\n\n" +
                    "Fail a set and nothing changes. That's the point: train to failure, then " +
                    "milk the weight until you clear it again.\n\n" +
                    "After 24 logged sessions the app suggests a rest week and drops every " +
                    "movement by one increment, giving you a runway to climb back through.",
            )

            Section(
                title = "Intention",
                body = "This app was built to remove friction. A completed session is a record, " +
                    "not a draft, so there are no edit screens for history. The monochrome " +
                    "interface keeps you focused on the work. Color only appears when it " +
                    "matters: to show you how you performed.",
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = body, style = MaterialTheme.typography.bodyLarge)
    Spacer(modifier = Modifier.height(24.dp))
}
