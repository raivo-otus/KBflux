package com.kbminisplit.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.seedExerciseCatalog
import com.kbminisplit.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class RootRoute { Loading, Onboarding, Main }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
) : ViewModel() {

    val route: Flow<RootRoute> =
        settingsRepository.observeIsOnboarded().map { onboarded ->
            if (onboarded) RootRoute.Main else RootRoute.Onboarding
        }

    val isDarkMode: Flow<Boolean?> = settingsRepository.observeIsDarkMode()

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = settingsRepository.observeIsDarkMode().first() ?: true
            settingsRepository.setDarkMode(!current)
        }
    }

    fun wipeAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                seedExerciseCatalog(database)
            }
        }
    }
}
