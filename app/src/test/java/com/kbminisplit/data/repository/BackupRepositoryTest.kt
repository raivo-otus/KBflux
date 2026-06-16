package com.kbminisplit.data.repository

import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.InProgressDao
import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import com.kbminisplit.data.model.BackupData
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import androidx.room.withTransaction

class BackupRepositoryTest {

    private val database: AppDatabase = mockk()
    private val sessionDao: SessionDao = mockk()
    private val settingsDao: SettingsDao = mockk()
    private val inProgressDao: InProgressDao = mockk()
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        every { database.inProgressDao() } returns inProgressDao
        
        // Mock withTransaction to just execute the block
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionBlock = slot<suspend () -> Any>()
        coEvery { database.withTransaction(capture(transactionBlock)) } coAnswers {
            transactionBlock.captured.invoke()
        }

        repository = BackupRepository(database, sessionDao, settingsDao)
    }

    @Test
    fun `getBackupData collects all data from DAOs including in-progress`() = runTest {
        val sessions = listOf(SessionEntity(id = 1, date = "2023-01-01", split = "A", feedback = "Good", kbWeightKg = 16.0, completedAt = 1000L))
        val sets = listOf(SetEntryEntity(id = 1, sessionId = 1, exerciseSlug = "push-up", setIndex = 0, isPriming = false, targetReps = 10, weightKg = 0.0, status = "COMPLETED"))
        val settings = UserSettingsEntity(onboardedAt = 500L, kbWeightKg = 16.0, startingTargetReps = 10, standardMaxReps = 15, kbBumpSnoozedAtMonth = null, kbBumpSnoozeSessionCount = 0, isDarkMode = true, hapticLevel = 1)
        val startingWeights = listOf(StartingWeightEntity(exerciseSlug = "push-up", weightKg = 0.0))
        val inProgressSession = InProgressSessionEntity(date = "2023-01-02", split = "B", kbWeightKg = 20.0)
        val inProgressSets = listOf(InProgressSetEntity(id = 1, exerciseSlug = "swing", setIndex = 0, isPriming = true, targetReps = 15, weightKg = 20.0, state = "IN_PROGRESS"))

        coEvery { sessionDao.getAll() } returns sessions
        coEvery { sessionDao.getAllSets() } returns sets
        coEvery { settingsDao.get() } returns settings
        coEvery { settingsDao.getStartingWeights() } returns startingWeights
        coEvery { inProgressDao.getSession() } returns inProgressSession
        coEvery { inProgressDao.getSets() } returns inProgressSets

        val backupData = repository.getBackupData()

        assertThat(backupData.sessions).hasSize(1)
        assertThat(backupData.sessions[0].id).isEqualTo(1)
        assertThat(backupData.setEntries).hasSize(1)
        assertThat(backupData.userSettings?.onboardedAt).isEqualTo(500L)
        assertThat(backupData.startingWeights).hasSize(1)
        assertThat(backupData.inProgressSession?.split).isEqualTo("B")
        assertThat(backupData.inProgressSets).hasSize(1)
        assertThat(backupData.inProgressSets[0].exerciseSlug).isEqualTo("swing")
    }

    @Test
    fun `restoreBackupData clears tables and inserts all data`() = runTest {
        val data = BackupData(
            version = 1,
            sessions = listOf(SessionEntity(id = 1, date = "2023-01-01", split = "A", feedback = "Good", kbWeightKg = 16.0, completedAt = 1000L)),
            setEntries = listOf(SetEntryEntity(id = 1, sessionId = 1, exerciseSlug = "push-up", setIndex = 0, isPriming = false, targetReps = 10, weightKg = 0.0, status = "COMPLETED")),
            userSettings = UserSettingsEntity(onboardedAt = 500L, kbWeightKg = 16.0, startingTargetReps = 10, standardMaxReps = 15, kbBumpSnoozedAtMonth = null, kbBumpSnoozeSessionCount = 0, isDarkMode = true, hapticLevel = 1),
            startingWeights = listOf(StartingWeightEntity(exerciseSlug = "push-up", weightKg = 0.0)),
            inProgressSession = InProgressSessionEntity(date = "2023-01-02", split = "B", kbWeightKg = 20.0),
            inProgressSets = listOf(InProgressSetEntity(id = 1, exerciseSlug = "swing", setIndex = 0, isPriming = true, targetReps = 15, weightKg = 20.0, state = "IN_PROGRESS"))
        )

        coEvery { sessionDao.clear() } just Runs
        coEvery { settingsDao.deleteSettings() } just Runs
        coEvery { settingsDao.deleteStartingWeights() } just Runs
        coEvery { inProgressDao.clear() } just Runs
        coEvery { sessionDao.insertSessions(any()) } just Runs
        coEvery { sessionDao.insertSets(any()) } just Runs
        coEvery { settingsDao.upsert(any()) } just Runs
        coEvery { settingsDao.upsertStartingWeights(any()) } just Runs
        coEvery { inProgressDao.upsertSession(any()) } just Runs
        coEvery { inProgressDao.upsertSets(any()) } just Runs

        repository.restoreBackupData(data)

        coVerify { sessionDao.clear() }
        coVerify { settingsDao.deleteSettings() }
        coVerify { settingsDao.deleteStartingWeights() }
        coVerify { inProgressDao.clear() }
        coVerify { sessionDao.insertSessions(match { it.size == 1 && it[0].id == 1L }) }
        coVerify { settingsDao.upsert(match { it.id == UserSettingsEntity.SINGLETON_ID }) }
        coVerify { inProgressDao.upsertSession(match { it.id == InProgressSessionEntity.SINGLETON_ID && it.split == "B" }) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `restoreBackupData throws for unsupported version`() = runTest {
        val data = BackupData(
            version = 2,
            sessions = emptyList(),
            setEntries = emptyList(),
            userSettings = null,
            startingWeights = emptyList()
        )

        repository.restoreBackupData(data)
    }
}
