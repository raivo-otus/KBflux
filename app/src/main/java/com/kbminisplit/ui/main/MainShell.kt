package com.kbminisplit.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.domain.model.Prescription
import com.kbminisplit.domain.model.Split

/**
 * Phase 3 placeholder: shows today's split + prescription as text so we can
 * verify that onboarding wrote the right defaults and the progression engine
 * reads them. Phase 4 replaces this with the real Tracker UI.
 */
@Composable
fun MainShell(viewModel: MainShellViewModel = hiltViewModel()) {
    val plan by viewModel.today.collectAsStateWithLifecycle(initialValue = null)

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val current = plan
            if (current == null) {
                Text("Loading…")
            } else {
                PlanCard(plan = current)
            }
        }
    }
}

@Composable
private fun PlanCard(plan: TodayPlan) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("today_plan"),
    ) {
        Text(
            text = "${plan.split.name} — ${plan.split.label}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "KB Flow · ${formatKg(plan.kbWeightKg)} kg",
            style = MaterialTheme.typography.bodyLarge,
        )
        PrescriptionLine(plan.movement1)
        PrescriptionLine(plan.movement2)
        Text(
            text = "Tracker UI lands in Phase 4.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrescriptionLine(rx: Prescription) {
    Text(
        text = "${rx.exercise.displayName} · ${formatKg(rx.weightKg)} kg · target ${rx.targetReps} reps",
        style = MaterialTheme.typography.bodyLarge,
    )
}

private val Split.label: String
    get() = when (this) {
        Split.A -> "Pull"
        Split.B -> "Push"
        Split.C -> "Legs"
    }

private fun formatKg(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else rounded.toString()
}
