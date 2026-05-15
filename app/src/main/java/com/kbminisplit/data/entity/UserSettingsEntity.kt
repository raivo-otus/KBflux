package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val onboardedAt: Long?,
    val kbWeightKg: Double?,
    val startingTargetReps: Int?,
    val kbBumpSnoozedAtMonth: String?,
    val kbBumpSnoozeSessionCount: Int?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
