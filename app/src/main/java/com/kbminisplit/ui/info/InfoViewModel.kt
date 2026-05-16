package com.kbminisplit.ui.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.OnboardingDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InfoViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val onboardingDefaults: StateFlow<OnboardingDefaults?> = settingsRepository.observeOnboardingDefaults()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}
