package com.kbminisplit.data.repository

import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.entity.UserSettingsEntity
import com.kbminisplit.domain.progression.RestWeekState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Latest bodyweight check-in: the value and when it was logged (both null until first entry). */
data class BodyweightState(val kg: Double?, val loggedAtMillis: Long?)

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val clock: Clock,
) {

    /**
     * True until the user has seen the Program tab once. There is no onboarding
     * wizard any more — this only decides which tab the app opens on.
     */
    fun observeIsFirstLaunch(): Flow<Boolean> =
        settingsDao.observe().map { it?.onboardedAt == null }

    suspend fun markProgramSeen() {
        val existing = current()
        if (existing.onboardedAt != null) return
        settingsDao.upsert(existing.copy(onboardedAt = clock.millis()))
    }

    fun observeIsDarkMode(): Flow<Boolean?> =
        settingsDao.observe().map { it?.isDarkMode }

    fun observeHapticLevel(): Flow<Int> =
        settingsDao.observe().map { it?.hapticLevel ?: 1 }

    /** Latest weekly bodyweight check-in (value + timestamp), null until first entry. */
    fun observeBodyweight(): Flow<BodyweightState> =
        settingsDao.observe().map { BodyweightState(it?.bodyweightKg, it?.bodyweightLoggedAt) }

    fun observeRestWeek(): Flow<RestWeekState> =
        settingsDao.observe().map {
            RestWeekState(
                anchorSessions = it?.restWeekAnchorSessions ?: 0,
                snoozedAtSessions = it?.restWeekSnoozedAtSessions,
            )
        }

    /** Records a rest week taken at [historySize] sessions and clears any snooze. */
    suspend fun takeRestWeek(historySize: Int) {
        settingsDao.upsert(
            current().copy(
                restWeekAnchorSessions = historySize,
                restWeekSnoozedAtSessions = null,
            ),
        )
    }

    suspend fun snoozeRestWeek(historySize: Int) {
        settingsDao.upsert(current().copy(restWeekSnoozedAtSessions = historySize))
    }

    /** Record a weekly bodyweight check-in. Stamped time drives the staleness prompt. */
    suspend fun updateBodyweight(weightKg: Double) {
        settingsDao.upsert(
            current().copy(bodyweightKg = weightKg, bodyweightLoggedAt = clock.millis()),
        )
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        settingsDao.upsert(current().copy(isDarkMode = enabled))
    }

    suspend fun setHapticLevel(level: Int) {
        settingsDao.upsert(current().copy(hapticLevel = level))
    }

    private suspend fun current(): UserSettingsEntity = settingsDao.get() ?: UserSettingsEntity()
}
