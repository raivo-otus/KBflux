package com.kbminisplit.ui.root

import android.content.ContentResolver
import android.net.Uri
import app.cash.turbine.test
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.repository.BackupRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.data.model.BackupData
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val backupRepository: BackupRepository = mockk(relaxed = true)

    private val isOnboardedFlow = MutableStateFlow(false)
    private val isDarkModeFlow = MutableStateFlow<Boolean?>(null)
    private val hapticLevelFlow = MutableStateFlow(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock Dispatchers.IO to use testDispatcher
        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher

        every { settingsRepository.observeIsOnboarded() } returns isOnboardedFlow
        every { settingsRepository.observeIsDarkMode() } returns isDarkModeFlow
        every { settingsRepository.observeHapticLevel() } returns hapticLevelFlow
    }

    @After
    fun tearDown() {
        unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `route is Onboarding when not onboarded`() = runTest(testDispatcher) {
        isOnboardedFlow.value = false
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        
        vm.route.test {
            assertThat(awaitItem()).isEqualTo(RootRoute.Onboarding)
        }
    }

    @Test
    fun `route is Main when onboarded`() = runTest(testDispatcher) {
        isOnboardedFlow.value = true
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        
        vm.route.test {
            assertThat(awaitItem()).isEqualTo(RootRoute.Main)
        }
    }

    @Test
    fun `toggleDarkMode updates repository`() = runTest(testDispatcher) {
        isDarkModeFlow.value = true
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        
        vm.toggleDarkMode()
        advanceUntilIdle()
        
        coVerify { settingsRepository.setDarkMode(false) }
    }

    @Test
    fun `setHapticLevel updates repository`() = runTest(testDispatcher) {
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        
        vm.setHapticLevel(2)
        advanceUntilIdle()
        
        coVerify { settingsRepository.setHapticLevel(2) }
    }

    @Test
    fun `wipeAllData clears database and re-seeds`() = runTest(testDispatcher) {
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        
        vm.wipeAllData()
        advanceUntilIdle()
        
        coVerify { database.clearAllTables() }
    }

    @Test
    fun `exportData writes to output stream`() = runTest(testDispatcher) {
        val contentResolver: ContentResolver = mockk()
        val uri: Uri = mockk()
        val outputStream = ByteArrayOutputStream()
        val backupData = BackupData(
            version = 1,
            sessions = emptyList(),
            setEntries = emptyList(),
            userSettings = null,
            startingWeights = emptyList(),
            inProgressSession = null,
            inProgressSets = emptyList()
        )
        
        coEvery { backupRepository.getBackupData() } returns backupData
        every { contentResolver.openOutputStream(uri) } returns outputStream
        
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        vm.exportData(contentResolver, uri)
        advanceUntilIdle()
        
        assertThat(outputStream.toByteArray()).isNotEmpty()
    }

    @Test
    fun `importData reads from input stream and restores and notifies success`() = runTest(testDispatcher) {
        val contentResolver: ContentResolver = mockk()
        val uri: Uri = mockk()
        val json = """{"version":1,"sessions":[],"setEntries":[],"userSettings":null,"startingWeights":[],"inProgressSession":null,"inProgressSets":[]}"""
        val inputStream = ByteArrayInputStream(json.toByteArray())
        
        every { contentResolver.openInputStream(uri) } returns inputStream
        
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        vm.uiEvents.test {
            vm.importData(contentResolver, uri)
            assertThat(awaitItem()).isEqualTo(RootUiEvent.Message("Data imported successfully"))
        }
        
        coVerify { backupRepository.restoreBackupData(any()) }
    }

    @Test
    fun `importData notifies error on failure`() = runTest(testDispatcher) {
        val contentResolver: ContentResolver = mockk()
        val uri: Uri = mockk()
        
        every { contentResolver.openInputStream(uri) } throws RuntimeException("File not found")
        
        val vm = RootViewModel(settingsRepository, database, backupRepository)
        vm.uiEvents.test {
            vm.importData(contentResolver, uri)
            val event = awaitItem() as RootUiEvent.Message
            assertThat(event.text).contains("Import failed")
        }
    }
}
