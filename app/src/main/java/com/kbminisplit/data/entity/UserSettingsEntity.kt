package com.kbminisplit.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val onboardedAt: Long?,
    val kbWeightKg: Double?,
    val startingTargetReps: Int?,
    val standardMaxReps: Int?,
    val kbBumpSnoozedAtMonth: String?,
    val kbBumpSnoozeSessionCount: Int?,
    val isDarkMode: Boolean? = null,
    @ColumnInfo(defaultValue = "1")
    val hapticLevel: Int = 1, // 0: Low, 1: Medium, 2: High
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
