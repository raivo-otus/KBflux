package com.kbminisplit.ui.root

import androidx.lifecycle.ViewModel
import com.kbminisplit.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

enum class RootRoute { Loading, Onboarding, Main }

@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val route: Flow<RootRoute> =
        settingsRepository.observeIsOnboarded().map { onboarded ->
            if (onboarded) RootRoute.Main else RootRoute.Onboarding
        }
}
