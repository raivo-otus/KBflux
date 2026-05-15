package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.progression.KbBumpSnooze
import java.time.YearMonth

/**
 * Compose [OnboardingDefaults] from the persisted user settings + per-movement
 * starting weights. Returns null when onboarding has not been completed yet.
 */
fun buildOnboardingDefaults(
    settings: UserSettingsEntity?,
    startingWeights: List<StartingWeightEntity>,
): OnboardingDefaults? {
    if (settings?.onboardedAt == null) return null
    val kb = settings.kbWeightKg ?: return null
    val reps = settings.startingTargetReps ?: return null
    return OnboardingDefaults(
        kbWeightKg = kb,
        startingWeightsBySlug = startingWeights.associate { it.exerciseSlug to it.weightKg },
        startingTargetReps = reps,
    )
}

fun UserSettingsEntity?.toKbBumpSnooze(): KbBumpSnooze? {
    val month = this?.kbBumpSnoozedAtMonth ?: return null
    val count = this.kbBumpSnoozeSessionCount ?: return null
    return KbBumpSnooze(
        snoozedAtMonth = YearMonth.parse(month),
        sessionCountAtSnooze = count,
    )
}
