package com.kbminisplit.data.repository

import androidx.room.withTransaction
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import com.kbminisplit.data.model.BackupData
import com.kbminisplit.data.model.InProgressSessionBackup
import com.kbminisplit.data.model.InProgressSetBackup
import com.kbminisplit.data.model.SessionBackup
import com.kbminisplit.data.model.SetEntryBackup
import com.kbminisplit.data.model.StartingWeightBackup
import com.kbminisplit.data.model.UserSettingsBackup
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
        val sessions = sessionDao.getAll()
        val sets = sessionDao.getAllSets()
        val settings = settingsDao.get()
        val startingWeights = settingsDao.getStartingWeights()
        val inProgressSession = inProgressDao.getSession()
        val inProgressSets = inProgressDao.getSets()

        return BackupData(
            version = 1,
            sessions = sessions.map { it.toBackup() },
            setEntries = sets.map { it.toBackup() },
            userSettings = settings?.toBackup(),
            startingWeights = startingWeights.map { it.toBackup() },
            inProgressSession = inProgressSession?.toBackup(),
            inProgressSets = inProgressSets.map { it.toBackup() }
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

            sessionDao.insertSessions(data.sessions.map { it.toEntity() })
            sessionDao.insertSets(data.setEntries.map { it.toEntity() })
            data.userSettings?.let { settingsDao.upsert(it.toEntity()) }
            settingsDao.upsertStartingWeights(data.startingWeights.map { it.toEntity() })

            data.inProgressSession?.let { inProgressDao.upsertSession(it.toEntity()) }
            inProgressDao.upsertSets(data.inProgressSets.map { it.toEntity() })
        }
    }

    private fun SessionEntity.toBackup() = SessionBackup(
        id = id,
        date = date,
        split = split,
        feedback = feedback,
        kbWeightKg = kbWeightKg,
        completedAt = completedAt
    )

    private fun SetEntryEntity.toBackup() = SetEntryBackup(
        id = id,
        sessionId = sessionId,
        exerciseSlug = exerciseSlug,
        setIndex = setIndex,
        isPriming = isPriming,
        targetReps = targetReps,
        weightKg = weightKg,
        status = status
    )

    private fun UserSettingsEntity.toBackup() = UserSettingsBackup(
        onboardedAt = onboardedAt,
        kbWeightKg = kbWeightKg,
        startingTargetReps = startingTargetReps,
        standardMaxReps = standardMaxReps,
        kbBumpSnoozedAtMonth = kbBumpSnoozedAtMonth,
        kbBumpSnoozeSessionCount = kbBumpSnoozeSessionCount,
        isDarkMode = isDarkMode,
        hapticLevel = hapticLevel
    )

    private fun StartingWeightEntity.toBackup() = StartingWeightBackup(
        exerciseSlug = exerciseSlug,
        weightKg = weightKg
    )

    private fun InProgressSessionEntity.toBackup() = InProgressSessionBackup(
        date = date,
        split = split,
        kbWeightKg = kbWeightKg
    )

    private fun InProgressSetEntity.toBackup() = InProgressSetBackup(
        id = id,
        exerciseSlug = exerciseSlug,
        setIndex = setIndex,
        isPriming = isPriming,
        targetReps = targetReps,
        weightKg = weightKg,
        state = state
    )

    private fun SessionBackup.toEntity() = SessionEntity(
        id = id,
        date = date,
        split = split,
        feedback = feedback,
        kbWeightKg = kbWeightKg,
        completedAt = completedAt
    )

    private fun SetEntryBackup.toEntity() = SetEntryEntity(
        id = id,
        sessionId = sessionId,
        exerciseSlug = exerciseSlug,
        setIndex = setIndex,
        isPriming = isPriming,
        targetReps = targetReps,
        weightKg = weightKg,
        status = status
    )

    private fun UserSettingsBackup.toEntity() = UserSettingsEntity(
        id = UserSettingsEntity.SINGLETON_ID,
        onboardedAt = onboardedAt,
        kbWeightKg = kbWeightKg,
        startingTargetReps = startingTargetReps,
        standardMaxReps = standardMaxReps,
        kbBumpSnoozedAtMonth = kbBumpSnoozedAtMonth,
        kbBumpSnoozeSessionCount = kbBumpSnoozeSessionCount,
        isDarkMode = isDarkMode,
        hapticLevel = hapticLevel
    )

    private fun StartingWeightBackup.toEntity() = StartingWeightEntity(
        exerciseSlug = exerciseSlug,
        weightKg = weightKg
    )

    private fun InProgressSessionBackup.toEntity() = InProgressSessionEntity(
        id = InProgressSessionEntity.SINGLETON_ID,
        date = date,
        split = split,
        kbWeightKg = kbWeightKg
    )

    private fun InProgressSetBackup.toEntity() = InProgressSetEntity(
        id = id,
        exerciseSlug = exerciseSlug,
        setIndex = setIndex,
        isPriming = isPriming,
        targetReps = targetReps,
        weightKg = weightKg,
        state = state
    )
}
