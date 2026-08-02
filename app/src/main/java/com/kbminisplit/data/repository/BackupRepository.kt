package com.kbminisplit.data.repository

import androidx.room.withTransaction
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.ExerciseDao
import com.kbminisplit.data.db.ProgramDao
import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.model.BACKUP_VERSION
import com.kbminisplit.data.model.BackupData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val sessionDao: SessionDao,
    private val settingsDao: SettingsDao,
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
) {
    private val inProgressDao = database.inProgressDao()

    suspend fun getBackupData(): BackupData {
        return BackupData(
            version = BACKUP_VERSION,
            sessions = sessionDao.getAll(),
            setEntries = sessionDao.getAllSets(),
            userSettings = settingsDao.get(),
            startingWeights = settingsDao.getStartingWeights(),
            inProgressSession = inProgressDao.getSession(),
            inProgressSets = inProgressDao.getSets(),
            exercises = exerciseDao.getAll(),
            programDays = programDao.getDays(),
            programGroups = programDao.getGroups(),
            programItems = programDao.getItems(),
        )
    }

    suspend fun restoreBackupData(data: BackupData) {
        if (data.version > BACKUP_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: ${data.version}")
        }

        database.withTransaction {
            // Program first: its rows are the only ones nothing else points at, and
            // clearing days cascades the groups and items.
            programDao.clear()
            sessionDao.clear()
            settingsDao.deleteSettings()
            settingsDao.deleteStartingWeights()
            inProgressDao.clear()

            // The registry is the foreign-key target for everything below, so it is
            // restored first and only ever gains rows.
            exerciseDao.insertAll(data.exercises)

            sessionDao.insertSessions(data.sessions)
            sessionDao.insertSets(data.setEntries)
            data.userSettings?.let { settingsDao.upsert(it) }
            settingsDao.upsertStartingWeights(data.startingWeights)

            data.programDays.forEach { programDao.insertDay(it) }
            data.programGroups.forEach { programDao.insertGroup(it) }
            data.programItems.forEach { programDao.insertItem(it) }

            data.inProgressSession?.let { inProgressDao.upsertSession(it) }
            inProgressDao.upsertSets(data.inProgressSets)
        }
    }
}
