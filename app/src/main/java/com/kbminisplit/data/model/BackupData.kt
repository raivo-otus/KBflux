package com.kbminisplit.data.model

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
import kotlinx.serialization.Serializable

/**
 * Version 2 adds the program tables and the exercise registry.
 *
 * A version 1 file simply has none of them; restoring one leaves the program
 * empty, and the seed then rebuilds the default program from the settings and
 * starting weights the backup did carry.
 */
@Serializable
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val sessions: List<SessionEntity>,
    val setEntries: List<SetEntryEntity>,
    val userSettings: UserSettingsEntity?,
    val startingWeights: List<StartingWeightEntity>,
    val inProgressSession: InProgressSessionEntity? = null,
    val inProgressSets: List<InProgressSetEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val programDays: List<ProgramDayEntity> = emptyList(),
    val programGroups: List<ProgramGroupEntity> = emptyList(),
    val programItems: List<ProgramItemEntity> = emptyList(),
)

const val BACKUP_VERSION = 2
