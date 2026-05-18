package com.kbminisplit.domain.model

data class OnboardingDefaults(
    val kbWeightKg: Double,
    val startingWeightsBySlug: Map<String, Double>,
    val startingTargetReps: Int,
    val standardMaxReps: Int,
)
