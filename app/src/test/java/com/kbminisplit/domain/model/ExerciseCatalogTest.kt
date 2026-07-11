package com.kbminisplit.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExerciseCatalogTest {

    @Test
    fun `slugs are unique`() {
        val slugs = ExerciseCatalog.all.map { it.slug }

        assertThat(slugs.toSet()).hasSize(slugs.size)
    }

    @Test
    fun `kb flow movements are themed to the split`() {
        assertThat(ExerciseCatalog.kbFlowForSplit(Split.A).map { it.slug }).containsExactly(
            "swings", "high_pull", "goblet_squat",
        ).inOrder()
        assertThat(ExerciseCatalog.kbFlowForSplit(Split.B).map { it.slug }).containsExactly(
            "swings", "clean_and_press", "goblet_squat",
        ).inOrder()
        assertThat(ExerciseCatalog.kbFlowForSplit(Split.C).map { it.slug }).containsExactly(
            "swings", "goblet_squat", "snatch",
        ).inOrder()
    }

    @Test
    fun `unilateral kb movements are flagged per-side`() {
        assertThat(ExerciseCatalog.HighPull.isPerSide).isTrue()
        assertThat(ExerciseCatalog.Snatch.isPerSide).isTrue()
        assertThat(ExerciseCatalog.CleanAndPress.isPerSide).isTrue()
        assertThat(ExerciseCatalog.Swings.isPerSide).isFalse()
        assertThat(ExerciseCatalog.GobletSquat.isPerSide).isFalse()
    }

    @Test
    fun `strength pairs match the spec`() {
        assertThat(ExerciseCatalog.strengthForSplit(Split.A))
            .isEqualTo(ExerciseCatalog.LatPulldown to ExerciseCatalog.BarbellRow)
        assertThat(ExerciseCatalog.strengthForSplit(Split.B))
            .isEqualTo(ExerciseCatalog.Bench to ExerciseCatalog.AssistedDip)
        assertThat(ExerciseCatalog.strengthForSplit(Split.C))
            .isEqualTo(ExerciseCatalog.HighBarSquat to ExerciseCatalog.RomanianDeadlift)
    }

    @Test
    fun `bySlug round-trips every catalog entry`() {
        ExerciseCatalog.all.forEach { exercise ->
            assertThat(ExerciseCatalog.bySlug(exercise.slug)).isEqualTo(exercise)
        }
    }

    @Test
    fun `bySlug returns null for unknown slug`() {
        assertThat(ExerciseCatalog.bySlug("nope")).isNull()
    }

    @Test
    fun `KB movements step in 2 kg, strength in 2 point 5 kg`() {
        ExerciseCatalog.all.filter { it.category == Category.KB }.forEach {
            assertThat(it.weightStepKg).isEqualTo(2.0)
        }

        val strengthCategories = listOf(Category.A, Category.B, Category.C)
        ExerciseCatalog.all.filter { it.category in strengthCategories }.forEach {
            assertThat(it.weightStepKg).isEqualTo(2.5)
        }
    }

    @Test
    fun `aux movements match the spec per split`() {
        assertThat(ExerciseCatalog.auxForSplit(Split.A).map { it.slug }).containsExactly(
            "side_delt_fly", "tricep_extension", "back_extension",
        ).inOrder()
        assertThat(ExerciseCatalog.auxForSplit(Split.B).map { it.slug }).containsExactly(
            "side_delt_fly", "bicep_curl", "back_extension",
        ).inOrder()
        assertThat(ExerciseCatalog.auxForSplit(Split.C).map { it.slug }).containsExactly(
            "side_delt_fly", "tricep_extension", "bicep_curl",
        ).inOrder()
    }

    @Test
    fun `aux movements carry a default starting weight and step in 2 kg`() {
        ExerciseCatalog.all.filter { it.category == Category.AUX }.forEach {
            assertThat(it.weightStepKg).isEqualTo(2.0)
            assertThat(it.defaultStartingWeightKg).isNotNull()
        }
    }
}
