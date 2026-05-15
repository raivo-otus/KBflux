package com.kbminisplit.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbminisplit.domain.model.Exercise

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = state.step.ordinal,
        pageCount = { OnboardingStep.entries.size },
    )

    LaunchedEffect(state.step) {
        if (pagerState.currentPage != state.step.ordinal) {
            pagerState.animateScrollToPage(state.step.ordinal)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LinearProgressIndicator(
                progress = { (state.step.ordinal + 1f) / OnboardingStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false,
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) { pageIndex ->
                when (OnboardingStep.entries[pageIndex]) {
                    OnboardingStep.Kb -> KbWeightStep(
                        value = state.kbWeightInput,
                        onValueChange = viewModel::onKbWeightChanged,
                    )
                    OnboardingStep.StrengthWeights -> StrengthWeightsStep(
                        inputs = state.strengthWeightInputs,
                        onValueChange = viewModel::onStrengthWeightChanged,
                    )
                    OnboardingStep.TargetReps -> TargetRepsStep(
                        value = state.targetRepsInput,
                        onValueChange = viewModel::onTargetRepsChanged,
                    )
                }
            }

            BottomBar(
                state = state,
                onBack = viewModel::back,
                onNext = viewModel::next,
                onComplete = viewModel::complete,
            )
        }
    }
}

@Composable
private fun StepShell(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun KbWeightStep(value: String, onValueChange: (String) -> Unit) {
    StepShell(
        title = "Your kettlebell weight",
        subtitle = "Enter the kettlebell you'll use for the KB Flow.",
    ) {
        NumberField(
            value = value,
            onValueChange = onValueChange,
            label = "Kettlebell (kg)",
            decimal = true,
            tag = "field_kb_weight",
        )
    }
}

@Composable
private fun StrengthWeightsStep(
    inputs: Map<String, String>,
    onValueChange: (String, String) -> Unit,
) {
    StepShell(
        title = "Starting working weights",
        subtitle = "What can you handle today for 8 reps on each lift?",
    ) {
        OnboardingUiState.StrengthExercises.forEach { exercise: Exercise ->
            NumberField(
                value = inputs[exercise.slug].orEmpty(),
                onValueChange = { onValueChange(exercise.slug, it) },
                label = "${exercise.displayName} (kg)",
                decimal = true,
                tag = "field_${exercise.slug}",
            )
        }
    }
}

@Composable
private fun TargetRepsStep(value: String, onValueChange: (String) -> Unit) {
    StepShell(
        title = "Starting target reps",
        subtitle = "We'll add one rep each successful session, up to 16, then bump the weight.",
    ) {
        NumberField(
            value = value,
            onValueChange = onValueChange,
            label = "Target reps",
            decimal = false,
            tag = "field_target_reps",
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    decimal: Boolean,
    tag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 320.dp)
            .testTag(tag),
    )
}

@Composable
private fun BottomBar(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onComplete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step == OnboardingStep.Kb) {
            Spacer(Modifier.height(1.dp))
        } else {
            TextButton(onClick = onBack, enabled = !state.isSaving) {
                Text("Back")
            }
        }

        Box {
            when (state.step) {
                OnboardingStep.Kb -> Button(
                    onClick = onNext,
                    enabled = state.kbStepValid && !state.isSaving,
                    modifier = Modifier.testTag("btn_next"),
                ) { Text("Next") }
                OnboardingStep.StrengthWeights -> Button(
                    onClick = onNext,
                    enabled = state.strengthStepValid && !state.isSaving,
                    modifier = Modifier.testTag("btn_next"),
                ) { Text("Next") }
                OnboardingStep.TargetReps -> Button(
                    onClick = onComplete,
                    enabled = state.canSubmit,
                    modifier = Modifier.testTag("btn_done"),
                ) { Text("Start training") }
            }
        }
    }
}
