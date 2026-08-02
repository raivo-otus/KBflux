package com.kbminisplit.data.repository

import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.ExerciseDao
import com.kbminisplit.data.db.InProgressDao
import com.kbminisplit.data.db.ProgramDao
import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.entity.ExerciseEntity
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.entity.ProgramDayEntity
import com.kbminisplit.data.entity.ProgramGroupEntity
import com.kbminisplit.data.entity.ProgramItemEntity
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import com.kbminisplit.data.model.BACKUP_VERSION
import com.kbminisplit.data.model.BackupData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class BackupRepositoryTest {

    private val database: AppDatabase = mockk()
    private val sessionDao: SessionDao = mockk(relaxed = true)
    private val settingsDao: SettingsDao = mockk(relaxed = true)
    private val inProgressDao: InProgressDao = mockk(relaxed = true)
    private val exerciseDao: ExerciseDao = mockk(relaxed = true)
    private val programDao: ProgramDao = mockk(relaxed = true)
    private lateinit var repository: BackupRepository

    private val session = SessionEntity(
        id = 1, date = "2026-01-01", dayKey = "A", feedback = "Green",
        circuitWeightKg = 16.0, completedAt = 1000L,
    )
    private val setEntry = SetEntryEntity(
        id = 1, sessionId = 1, exerciseSlug = "bench", setIndex = 1, isPriming = false,
        targetReps = 8, targetRepsMax = 12, weightKg = 60.0, status = "Completed", position = 0,
    )
    private val settings = UserSettingsEntity(onboardedAt = 500L, hapticLevel = 1)
    private val programDay = ProgramDayEntity(id = 1, dayKey = "A", name = "Pull", position = 0)
    private val programGroup = ProgramGroupEntity(
        id = 1, dayId = 1, name = "Main", kind = "STANDARD", position = 0,
    )
    private val programItem = ProgramItemEntity(
        id = 1, groupId = 1, exerciseSlug = "bench", position = 0, currentWeightKg = 60.0,
    )

    @Before
    fun setUp() {
        every { database.inProgressDao() } returns inProgressDao

        // Mock withTransaction to just execute the block
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionBlock = slot<suspend () -> Any>()
        coEvery { database.withTransaction(capture(transactionBlock)) } coAnswers {
            transactionBlock.captured.invoke()
        }

        repository = BackupRepository(database, sessionDao, settingsDao, exerciseDao, programDao)
    }

    @Test
    fun `getBackupData collects history, settings, in-progress and the program`() {
        runTest {
            coEvery { sessionDao.getAll() } returns listOf(session)
            coEvery { sessionDao.getAllSets() } returns listOf(setEntry)
            coEvery { settingsDao.get() } returns settings
            coEvery { settingsDao.getStartingWeights() } returns
                listOf(StartingWeightEntity("bench", 60.0))
            coEvery { inProgressDao.getSession() } returns
                InProgressSessionEntity(date = "2026-01-02", dayKey = "B")
            coEvery { inProgressDao.getSets() } returns listOf(
                InProgressSetEntity(
                    id = 1, programGroupId = 1, programItemId = 1, exerciseSlug = "bench",
                    setIndex = 1, isPriming = false, targetReps = 8, targetRepsMax = 12,
                    weightKg = 60.0, state = "Pending", position = 0,
                ),
            )
            coEvery { exerciseDao.getAll() } returns listOf(ExerciseEntity("bench", "Bench Press"))
            coEvery { programDao.getDays() } returns listOf(programDay)
            coEvery { programDao.getGroups() } returns listOf(programGroup)
            coEvery { programDao.getItems() } returns listOf(programItem)

            val backup = repository.getBackupData()

            assertThat(backup.version).isEqualTo(BACKUP_VERSION)
            assertThat(backup.sessions).hasSize(1)
            assertThat(backup.setEntries.single().targetRepsMax).isEqualTo(12)
            assertThat(backup.userSettings?.onboardedAt).isEqualTo(500L)
            assertThat(backup.inProgressSession?.dayKey).isEqualTo("B")
            assertThat(backup.exercises.single().slug).isEqualTo("bench")
            assertThat(backup.programDays).hasSize(1)
            assertThat(backup.programGroups).hasSize(1)
            assertThat(backup.programItems).hasSize(1)
        }
    }

    @Test
    fun `restoreBackupData clears everything and reinserts, registry first`() {
        runTest {
            val data = BackupData(
                sessions = listOf(session),
                setEntries = listOf(setEntry),
                userSettings = settings,
                startingWeights = listOf(StartingWeightEntity("bench", 60.0)),
                exercises = listOf(ExerciseEntity("bench", "Bench Press")),
                programDays = listOf(programDay),
                programGroups = listOf(programGroup),
                programItems = listOf(programItem),
            )
            coEvery { programDao.clear() } just Runs
            coEvery { sessionDao.clear() } just Runs

            repository.restoreBackupData(data)

            coVerify { programDao.clear() }
            coVerify { sessionDao.clear() }
            coVerify { settingsDao.deleteSettings() }
            coVerify { inProgressDao.clear() }
            coVerify { exerciseDao.insertAll(match { it.single().slug == "bench" }) }
            coVerify { sessionDao.insertSessions(match { it.single().dayKey == "A" }) }
            coVerify { programDao.insertDay(match { it.dayKey == "A" }) }
            coVerify { programDao.insertGroup(match { it.name == "Main" }) }
            coVerify { programDao.insertItem(match { it.currentWeightKg == 60.0 }) }
        }
    }

    @Test
    fun `a version 1 backup restores without a program`() {
        runTest {
            val legacy = BackupData(
                version = 1,
                sessions = listOf(session),
                setEntries = listOf(setEntry),
                userSettings = settings,
                startingWeights = listOf(StartingWeightEntity("bench", 60.0)),
            )
            coEvery { programDao.clear() } just Runs
            coEvery { sessionDao.clear() } just Runs

            repository.restoreBackupData(legacy)

            coVerify { sessionDao.insertSessions(match { it.size == 1 }) }
            coVerify(exactly = 0) { programDao.insertDay(any()) }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `restoreBackupData throws for a backup from a newer version`() {
        runTest {
            repository.restoreBackupData(
                BackupData(
                    version = BACKUP_VERSION + 1,
                    sessions = emptyList(),
                    setEntries = emptyList(),
                    userSettings = null,
                    startingWeights = emptyList(),
                ),
            )
        }
    }
}
