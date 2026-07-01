package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import com.kbminisplit.domain.model.Category
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.progression.KbBumpSnooze
import java.time.YearMonth

// Only the A/B/C strength lifts are onboarded and therefore expected to have a
// starting-weight row. KB is a single flow weight; auxiliary movements fall back
// to their own default and are never part of onboarding.
private val StrengthCategories = setOf(Category.A, Category.B, Category.C)

private val ExpectedStartingSlugs: Set<String> =
    ExerciseCatalog.all.filter { it.category in StrengthCategories }.map { it.slug }.toSet()

/**
 * Compose [OnboardingDefaults] from the persisted user settings + per-movement
 * starting weights. Returns null when onboarding has not been completed yet.
 *
 * Also returns null when `user_settings` reports onboarded but the
 * `starting_weight` table has not yet caught up — saveOnboarding writes the two
 * tables sequentially, so each backing flow emits independently and there is a
 * brief window where settings are present but weights are not. Callers must not
 * see a half-built defaults object, so we wait until every strength slug has a
 * row before returning a non-null value.
 */
fun buildOnboardingDefaults(
    settings: UserSettingsEntity?,
    startingWeights: List<StartingWeightEntity>,
): OnboardingDefaults? {
    if (settings?.onboardedAt == null) return null
    val kb = settings.kbWeightKg ?: return null
    val reps = settings.startingTargetReps ?: return null
    val standardMax = settings.standardMaxReps ?: 12
    val map = startingWeights.associate { it.exerciseSlug to it.weightKg }
    if (!map.keys.containsAll(ExpectedStartingSlugs)) return null
    return OnboardingDefaults(
        kbWeightKg = kb,
        startingWeightsBySlug = map,
        startingTargetReps = reps,
        standardMaxReps = standardMax,
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
