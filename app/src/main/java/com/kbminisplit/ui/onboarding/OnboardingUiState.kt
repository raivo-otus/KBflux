package com.kbminisplit.ui.onboarding

import com.kbminisplit.domain.model.Category
import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.OnboardingDefaults

enum class OnboardingStep { Kb, StrengthWeights, TargetReps }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Kb,
    val kbWeightInput: String = DefaultKbWeight,
    val strengthWeightInputs: Map<String, String> = DefaultStrengthWeights,
    val targetRepsInput: String = DefaultTargetReps,
    val standardMaxRepsInput: String = DefaultStandardMaxReps,
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
) {
    val kbWeightKg: Double? get() = kbWeightInput.toPositiveDoubleOrNull()
    fun strengthWeightKg(slug: String): Double? =
        strengthWeightInputs[slug]?.toPositiveDoubleOrNull()
    val targetReps: Int? get() = targetRepsInput.toIntOrNull()?.takeIf { it in TargetRepsRange }
    val standardMaxReps: Int? get() = standardMaxRepsInput.toIntOrNull()?.takeIf { it in TargetRepsRange }

    val kbStepValid: Boolean get() = kbWeightKg != null
    val strengthStepValid: Boolean
        get() = StrengthExercises.all { strengthWeightKg(it.slug) != null }
    val repsStepValid: Boolean get() = targetReps != null && standardMaxReps != null

    val canSubmit: Boolean
        get() = kbStepValid && strengthStepValid && repsStepValid && !isSaving

    fun toDefaults(): OnboardingDefaults? {
        val kb = kbWeightKg ?: return null
        val reps = targetReps ?: return null
        val sMax = standardMaxReps ?: return null
        val pairs = StrengthExercises.map { it.slug to (strengthWeightKg(it.slug) ?: return null) }
        return OnboardingDefaults(
            kbWeightKg = kb,
            startingWeightsBySlug = pairs.toMap(),
            startingTargetReps = reps,
            standardMaxReps = sMax,
        )
    }

    companion object {
        // Only the A/B/C strength lifts are onboarded — KB uses a single flow
        // weight, and movements with a defaultStartingWeightKg fallback (auxiliary
        // work, Assisted Dips) derive their first weight from that fallback instead.
        val StrengthExercises: List<Exercise> =
            ExerciseCatalog.all.filter {
                (it.category == Category.A || it.category == Category.B || it.category == Category.C) &&
                    it.defaultStartingWeightKg == null
            }

        val TargetRepsRange = 1..20

        const val DefaultKbWeight = "16"
        const val DefaultTargetReps = "8"
        const val DefaultStandardMaxReps = "12"

        val DefaultStrengthWeights: Map<String, String> = mapOf(
            ExerciseCatalog.LatPulldown.slug to "35",
            ExerciseCatalog.BarbellRow.slug to "30",
            ExerciseCatalog.Bench.slug to "40",
            ExerciseCatalog.Ohp.slug to "20",
            ExerciseCatalog.HighBarSquat.slug to "50",
            ExerciseCatalog.RomanianDeadlift.slug to "60",
        )
    }
}

private fun String.toPositiveDoubleOrNull(): Double? {
    val cleaned = trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    val v = cleaned.toDoubleOrNull() ?: return null
    return v.takeIf { it > 0.0 && it.isFinite() }
}
