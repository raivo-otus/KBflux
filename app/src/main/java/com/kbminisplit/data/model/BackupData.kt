package com.kbminisplit.data.model

import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val sessions: List<SessionEntity>,
    val setEntries: List<SetEntryEntity>,
    val userSettings: UserSettingsEntity?,
    val startingWeights: List<StartingWeightEntity>,
    val inProgressSession: InProgressSessionEntity? = null,
    val inProgressSets: List<InProgressSetEntity> = emptyList(),
)
