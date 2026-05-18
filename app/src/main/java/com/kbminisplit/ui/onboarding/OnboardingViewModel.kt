package com.kbminisplit.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onKbWeightChanged(value: String) {
        _state.update { it.copy(kbWeightInput = value) }
    }

    fun onStrengthWeightChanged(slug: String, value: String) {
        _state.update {
            it.copy(strengthWeightInputs = it.strengthWeightInputs + (slug to value))
        }
    }

    fun onTargetRepsChanged(value: String) {
        _state.update { it.copy(targetRepsInput = value) }
    }

    fun onStandardMaxRepsChanged(value: String) {
        _state.update { it.copy(standardMaxRepsInput = value) }
    }

    fun goToStep(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
    }

    fun next() {
        val s = _state.value
        val nextStep = when (s.step) {
            OnboardingStep.Kb -> if (s.kbStepValid) OnboardingStep.StrengthWeights else return
            OnboardingStep.StrengthWeights ->
                if (s.strengthStepValid) OnboardingStep.TargetReps else return
            OnboardingStep.TargetReps -> return
        }
        _state.update { it.copy(step = nextStep) }
    }

    fun back() {
        val s = _state.value
        val prev = when (s.step) {
            OnboardingStep.Kb -> return
            OnboardingStep.StrengthWeights -> OnboardingStep.Kb
            OnboardingStep.TargetReps -> OnboardingStep.StrengthWeights
        }
        _state.update { it.copy(step = prev) }
    }

    fun complete() {
        val defaults = _state.value.toDefaults() ?: return
        if (_state.value.isSaving || _state.value.isComplete) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            settingsRepository.saveOnboarding(defaults)
            _state.update { it.copy(isSaving = false, isComplete = true) }
        }
    }
}
