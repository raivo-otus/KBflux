package com.kbminisplit.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /**
     * Stamped the first time the user leaves the Program tab. There is no longer
     * an onboarding wizard — this only decides whether the app opens on Program
     * (first launch) or Tracker.
     */
    val onboardedAt: Long? = null,
    // Vestigial: the kettlebell weight now lives on the circuit group, and rep
    // ranges live on program items. Kept so the v6 schema needs no rebuild; the
    // seed reads kbWeightKg and standardMaxReps once to build the default program.
    val kbWeightKg: Double? = null,
    val startingTargetReps: Int? = null,
    val standardMaxReps: Int? = null,
    val kbBumpSnoozedAtMonth: String? = null,
    val kbBumpSnoozeSessionCount: Int? = null,
    val isDarkMode: Boolean? = null,
    @ColumnInfo(defaultValue = "1")
    val hapticLevel: Int = 1, // 0: Low, 1: Medium, 2: High
    // Latest weekly bodyweight check-in. This is the "current" value, snapshotted
    // onto each session at commit. loggedAt drives the weekly staleness prompt.
    @ColumnInfo(defaultValue = "NULL")
    val bodyweightKg: Double? = null,
    @ColumnInfo(defaultValue = "NULL")
    val bodyweightLoggedAt: Long? = null,
    /**
     * Session count when the last rest week was taken. The rest-week prompt fires
     * once the history has grown REST_WEEK_SESSIONS beyond this.
     */
    @ColumnInfo(defaultValue = "0")
    val restWeekAnchorSessions: Int = 0,
    /** Session count when the prompt was last snoozed, or null if it wasn't. */
    @ColumnInfo(defaultValue = "NULL")
    val restWeekSnoozedAtSessions: Int? = null,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
