package com.kbminisplit.domain.model

data class Exercise(
    val slug: String,
    val displayName: String,
    val category: Category,
    val isPerSide: Boolean,
    val weightStepKg: Double,
    val minReps: Int = 8,
    val maxReps: Int = 16,
    /**
     * Fallback starting weight used by [com.kbminisplit.domain.progression.getPrescription]
     * when a movement has no onboarding starting weight (i.e. auxiliary movements,
     * which aren't part of onboarding). Domain-only — not persisted.
     */
    val defaultStartingWeightKg: Double? = null,
    /**
     * Whether the logged weight is a traditional load or machine assistance.
     * Flips the direction of double progression. Domain-only — not persisted.
     */
    val mechanic: ExerciseMechanic = ExerciseMechanic.TRADITIONAL,
)
