package com.kbminisplit.ui.root

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.seedDefaultProgram
import com.kbminisplit.data.db.seedExerciseRegistry
import com.kbminisplit.data.model.BackupData
import com.kbminisplit.data.repository.BackupRepository
import com.kbminisplit.data.di.IoDispatcher
import com.kbminisplit.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject

sealed class RootUiEvent {
    data class Message(val text: String) : RootUiEvent()
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
    private val backupRepository: BackupRepository,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** True until the Program tab has been seen once; decides which tab opens first. */
    val isFirstLaunch: Flow<Boolean> = settingsRepository.observeIsFirstLaunch()

    val isDarkMode: Flow<Boolean?> = settingsRepository.observeIsDarkMode()
    val hapticLevel: Flow<Int> = settingsRepository.observeHapticLevel()

    private val _uiEvents = MutableSharedFlow<RootUiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    fun markProgramSeen() {
        viewModelScope.launch { settingsRepository.markProgramSeen() }
    }

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
            withContext(ioDispatcher) {
                database.clearAllTables()
                reseed()
            }
        }
    }

    fun exportData(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val backupData = backupRepository.getBackupData()
            val json = Json.encodeToString(backupData)
            withContext(ioDispatcher) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
            }
        }
    }

    fun importData(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(ioDispatcher) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                } ?: run {
                    _uiEvents.emit(RootUiEvent.Message("Failed to read import file"))
                    return@launch
                }
                val backupData = Json.decodeFromString<BackupData>(json)
                withContext(ioDispatcher) {
                    backupRepository.restoreBackupData(backupData)
                    // A backup taken before programs existed carries no days, so
                    // rebuild the default one from whatever settings it restored.
                    reseed()
                }
                _uiEvents.emit(RootUiEvent.Message("Data imported successfully"))
            } catch (e: Exception) {
                e.printStackTrace()
                _uiEvents.emit(RootUiEvent.Message("Import failed: ${e.message ?: "Unknown error"}"))
            }
        }
    }

    /** Restores the registry and, only if no program survived, the default one. */
    private suspend fun reseed() {
        seedExerciseRegistry(database)
        seedDefaultProgram(database, clock.millis())
    }
}
