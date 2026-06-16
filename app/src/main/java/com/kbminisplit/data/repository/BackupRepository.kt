package com.kbminisplit.data.repository

import androidx.room.withTransaction
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.model.BackupData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val database: AppDatabase,
    private val sessionDao: SessionDao,
    private val settingsDao: SettingsDao,
) {
    private val inProgressDao = database.inProgressDao()

    suspend fun getBackupData(): BackupData {
        return BackupData(
            version = 1,
            sessions = sessionDao.getAll(),
            setEntries = sessionDao.getAllSets(),
            userSettings = settingsDao.get(),
            startingWeights = settingsDao.getStartingWeights(),
            inProgressSession = inProgressDao.getSession(),
            inProgressSets = inProgressDao.getSets()
        )
    }

    suspend fun restoreBackupData(data: BackupData) {
        if (data.version > 1) {
            throw IllegalArgumentException("Unsupported backup version: ${data.version}")
        }

        database.withTransaction {
            sessionDao.clear()
            settingsDao.deleteSettings()
            settingsDao.deleteStartingWeights()
            inProgressDao.clear()

            sessionDao.insertSessions(data.sessions)
            sessionDao.insertSets(data.setEntries)
            data.userSettings?.let { settingsDao.upsert(it) }
            settingsDao.upsertStartingWeights(data.startingWeights)

            data.inProgressSession?.let { inProgressDao.upsertSession(it) }
            inProgressDao.upsertSets(data.inProgressSets)
        }
    }
}
