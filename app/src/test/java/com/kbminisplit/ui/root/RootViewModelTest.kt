package com.kbminisplit.ui.root

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.model.BackupData
import com.kbminisplit.data.repository.BackupRepository
import com.kbminisplit.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val backupRepository: BackupRepository = mockk(relaxed = true)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"))

    private val isFirstLaunchFlow = MutableStateFlow(false)
    private val isDarkModeFlow = MutableStateFlow<Boolean?>(null)
    private val hapticLevelFlow = MutableStateFlow(1)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // The reseed path runs the default-program seed inside a transaction.
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionBlock = slot<suspend () -> Any>()
        coEvery { database.withTransaction(capture(transactionBlock)) } coAnswers {
            transactionBlock.captured.invoke()
        }

        every { settingsRepository.observeIsFirstLaunch() } returns isFirstLaunchFlow
        every { settingsRepository.observeIsDarkMode() } returns isDarkModeFlow
        every { settingsRepository.observeHapticLevel() } returns hapticLevelFlow
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
        Dispatchers.resetMain()
    }

    private fun newViewModel() =
        RootViewModel(settingsRepository, database, backupRepository, clock, testDispatcher)

    @Test
    fun `first launch is reported so the app can open on Program`() {
        runTest(testDispatcher) {
            isFirstLaunchFlow.value = true
            val vm = newViewModel()

            vm.isFirstLaunch.test {
                assertThat(awaitItem()).isTrue()
            }
        }
    }

    @Test
    fun `later launches are not first launch`() {
        runTest(testDispatcher) {
            isFirstLaunchFlow.value = false
            val vm = newViewModel()

            vm.isFirstLaunch.test {
                assertThat(awaitItem()).isFalse()
            }
        }
    }

    @Test
    fun `markProgramSeen retires the first-launch state`() {
        runTest(testDispatcher) {
            val vm = newViewModel()

            vm.markProgramSeen()
            advanceUntilIdle()

            coVerify { settingsRepository.markProgramSeen() }
        }
    }

    @Test
    fun `toggleDarkMode updates repository`() {
        runTest(testDispatcher) {
            isDarkModeFlow.value = true
            val vm = newViewModel()

            vm.toggleDarkMode()
            advanceUntilIdle()

            coVerify { settingsRepository.setDarkMode(false) }
        }
    }

    @Test
    fun `setHapticLevel updates repository`() {
        runTest(testDispatcher) {
            val vm = newViewModel()

            vm.setHapticLevel(2)
            advanceUntilIdle()

            coVerify { settingsRepository.setHapticLevel(2) }
        }
    }

    @Test
    fun `wipeAllData clears the database and re-seeds`() {
        runTest(testDispatcher) {
            val vm = newViewModel()

            vm.wipeAllData()
            advanceUntilIdle()

            coVerify { database.clearAllTables() }
            coVerify { database.exerciseDao() }
        }
    }

    @Test
    fun `exportData writes to output stream`() {
        runTest(testDispatcher) {
            val contentResolver: ContentResolver = mockk()
            val uri: Uri = mockk()
            val outputStream = ByteArrayOutputStream()
            val backupData = BackupData(
                sessions = emptyList(),
                setEntries = emptyList(),
                userSettings = null,
                startingWeights = emptyList(),
            )

            coEvery { backupRepository.getBackupData() } returns backupData
            every { contentResolver.openOutputStream(uri) } returns outputStream

            val vm = newViewModel()
            vm.exportData(contentResolver, uri)
            advanceUntilIdle()

            assertThat(outputStream.toByteArray()).isNotEmpty()
        }
    }

    @Test
    fun `importData restores and notifies success`() {
        runTest(testDispatcher) {
            val contentResolver: ContentResolver = mockk()
            val uri: Uri = mockk()
            val json = """{"version":2,"sessions":[],"setEntries":[],"userSettings":null,""" +
                """"startingWeights":[],"inProgressSession":null,"inProgressSets":[]}"""
            val inputStream = ByteArrayInputStream(json.toByteArray())

            every { contentResolver.openInputStream(uri) } returns inputStream

            val vm = newViewModel()
            vm.uiEvents.test {
                vm.importData(contentResolver, uri)
                assertThat(awaitItem()).isEqualTo(RootUiEvent.Message("Data imported successfully"))
            }

            coVerify { backupRepository.restoreBackupData(any()) }
        }
    }

    @Test
    fun `a version 1 backup still imports`() {
        runTest(testDispatcher) {
            val contentResolver: ContentResolver = mockk()
            val uri: Uri = mockk()
            val json = """{"version":1,"sessions":[],"setEntries":[],"userSettings":null,""" +
                """"startingWeights":[],"inProgressSession":null,"inProgressSets":[]}"""
            every { contentResolver.openInputStream(uri) } returns
                ByteArrayInputStream(json.toByteArray())

            val vm = newViewModel()
            vm.uiEvents.test {
                vm.importData(contentResolver, uri)
                assertThat(awaitItem()).isEqualTo(RootUiEvent.Message("Data imported successfully"))
            }
        }
    }

    @Test
    fun `importData notifies error on failure`() {
        runTest(testDispatcher) {
            val contentResolver: ContentResolver = mockk()
            val uri: Uri = mockk()

            every { contentResolver.openInputStream(uri) } throws RuntimeException("File not found")

            val vm = newViewModel()
            vm.uiEvents.test {
                vm.importData(contentResolver, uri)
                val event = awaitItem() as RootUiEvent.Message
                assertThat(event.text).contains("Import failed")
            }
        }
    }
}
