package com.kbminisplit.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val sessions: List<SessionBackup>,
    val setEntries: List<SetEntryBackup>,
    val userSettings: UserSettingsBackup?,
    val startingWeights: List<StartingWeightBackup>,
    val inProgressSession: InProgressSessionBackup? = null,
    val inProgressSets: List<InProgressSetBackup> = emptyList(),
)

@Serializable
data class SessionBackup(
    val id: Long,
    val date: String,
    val split: String,
    val feedback: String,
    val kbWeightKg: Double,
    val completedAt: Long,
)

@Serializable
data class SetEntryBackup(
    val id: Long,
    val sessionId: Long,
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val weightKg: Double,
    val status: String,
)

@Serializable
data class UserSettingsBackup(
    val onboardedAt: Long?,
    val kbWeightKg: Double?,
    val startingTargetReps: Int?,
    val standardMaxReps: Int?,
    val kbBumpSnoozedAtMonth: String?,
    val kbBumpSnoozeSessionCount: Int?,
    val isDarkMode: Boolean?,
    val hapticLevel: Int,
)

@Serializable
data class StartingWeightBackup(
    val exerciseSlug: String,
    val weightKg: Double,
)

@Serializable
data class InProgressSessionBackup(
    val date: String,
    val split: String,
    val kbWeightKg: Double,
)

@Serializable
data class InProgressSetBackup(
    val id: Long,
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val weightKg: Double,
    val state: String,
)
