package com.kbminisplit.data.repository

import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import com.kbminisplit.data.mapper.buildOnboardingDefaults
import com.kbminisplit.data.mapper.toKbBumpSnooze
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.progression.KbBumpSnooze
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao,
    private val clock: Clock,
) {

    fun observeIsOnboarded(): Flow<Boolean> =
        settingsDao.observe().map { it?.onboardedAt != null }

    fun observeOnboardingDefaults(): Flow<OnboardingDefaults?> =
        combine(settingsDao.observe(), settingsDao.observeStartingWeights()) { s, w ->
            buildOnboardingDefaults(s, w)
        }

    suspend fun getOnboardingDefaults(): OnboardingDefaults? =
        buildOnboardingDefaults(settingsDao.get(), settingsDao.getStartingWeights())

    fun observeKbBumpSnooze(): Flow<KbBumpSnooze?> =
        settingsDao.observe().map { it.toKbBumpSnooze() }

    suspend fun saveOnboarding(defaults: OnboardingDefaults) {
        val existing = settingsDao.get()
        settingsDao.upsert(
            UserSettingsEntity(
                onboardedAt = existing?.onboardedAt ?: clock.millis(),
                kbWeightKg = defaults.kbWeightKg,
                startingTargetReps = defaults.startingTargetReps,
                kbBumpSnoozedAtMonth = existing?.kbBumpSnoozedAtMonth,
                kbBumpSnoozeSessionCount = existing?.kbBumpSnoozeSessionCount,
            ),
        )
        settingsDao.upsertStartingWeights(
            defaults.startingWeightsBySlug.map { (slug, w) ->
                StartingWeightEntity(exerciseSlug = slug, weightKg = w)
            },
        )
    }

    suspend fun saveKbBumpSnooze(snooze: KbBumpSnooze?) {
        val existing = settingsDao.get() ?: return
        settingsDao.upsert(
            existing.copy(
                kbBumpSnoozedAtMonth = snooze?.snoozedAtMonth?.toString(),
                kbBumpSnoozeSessionCount = snooze?.sessionCountAtSnooze,
            ),
        )
    }

    /**
     * Bump the persisted KB weight and clear any active snooze. Called from the
     * Tracker after the user accepts the monthly KB bump prompt (spec §9.3).
     */
    suspend fun bumpKbWeight(newKg: Double) {
        val existing = settingsDao.get() ?: return
        settingsDao.upsert(
            existing.copy(
                kbWeightKg = newKg,
                kbBumpSnoozedAtMonth = null,
                kbBumpSnoozeSessionCount = null,
            ),
        )
    }
}
