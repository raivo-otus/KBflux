package com.kbminisplit.ui.main

import androidx.lifecycle.ViewModel
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.model.Prescription
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.Split
import com.kbminisplit.domain.progression.nextSplit
import com.kbminisplit.domain.progression.prescription
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class TodayPlan(
    val split: Split,
    val kbWeightKg: Double,
    val movement1: Prescription,
    val movement2: Prescription,
)

@HiltViewModel
class MainShellViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val today: Flow<TodayPlan?> = combine(
        settingsRepository.observeOnboardingDefaults(),
        sessionRepository.observeAll(),
    ) { defaults, history -> defaults?.let { computeToday(it, history) } }

    private fun computeToday(defaults: OnboardingDefaults, history: List<Session>): TodayPlan {
        val split = nextSplit(history)
        val (m1, m2) = ExerciseCatalog.strengthForSplit(split)
        return TodayPlan(
            split = split,
            kbWeightKg = defaults.kbWeightKg,
            movement1 = prescription(history, m1, defaults),
            movement2 = prescription(history, m2, defaults),
        )
    }
}
