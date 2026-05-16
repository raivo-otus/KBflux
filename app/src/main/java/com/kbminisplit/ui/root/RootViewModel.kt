package com.kbminisplit.ui.root

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.seedExerciseCatalog
import com.kbminisplit.data.model.BackupData
import com.kbminisplit.data.repository.BackupRepository
import com.kbminisplit.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

sealed class RootUiEvent {
    data class Message(val text: String) : RootUiEvent()
}

enum class RootRoute { Loading, Onboarding, Main }

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val route: Flow<RootRoute> =
        settingsRepository.observeIsOnboarded().map { onboarded ->
            if (onboarded) RootRoute.Main else RootRoute.Onboarding
        }

    val isDarkMode: Flow<Boolean?> = settingsRepository.observeIsDarkMode()
    val hapticLevel: Flow<Int> = settingsRepository.observeHapticLevel()

    private val _uiEvents = MutableSharedFlow<RootUiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = settingsRepository.observeIsDarkMode().first() ?: true
            settingsRepository.setDarkMode(!current)
        }
    }

    fun setHapticLevel(level: Int) {
        viewModelScope.launch {
            settingsRepository.setHapticLevel(level)
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

    fun exportData(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val backupData = backupRepository.getBackupData()
            val json = Json.encodeToString(backupData)
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
            }
        }
    }

    fun importData(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                } ?: run {
                    _uiEvents.emit(RootUiEvent.Message("Failed to read import file"))
                    return@launch
                }
                val backupData = Json.decodeFromString<BackupData>(json)
                withContext(Dispatchers.IO) {
                    backupRepository.restoreBackupData(backupData)
                    seedExerciseCatalog(database) // Ensure exercise catalog is present after restore
                }
                _uiEvents.emit(RootUiEvent.Message("Data imported successfully"))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(RootUiEvent.Message("Import failed: ${e.message ?: "Unknown error"}"))
            }
        }
    }
}
