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
    fun `kb flow contains exactly the five canonical movements`() {
        val slugs = ExerciseCatalog.kbFlow.map { it.slug }

        assertThat(slugs).containsExactly(
            "swings", "clean_and_press", "lunge", "goblet_squat", "push_up",
        ).inOrder()
    }

    @Test
    fun `strength pairs match the spec`() {
        assertThat(ExerciseCatalog.strengthForSplit(Split.A))
            .isEqualTo(ExerciseCatalog.LatPulldown to ExerciseCatalog.BarbellRow)
        assertThat(ExerciseCatalog.strengthForSplit(Split.B))
            .isEqualTo(ExerciseCatalog.Bench to ExerciseCatalog.Ohp)
        assertThat(ExerciseCatalog.strengthForSplit(Split.C))
            .isEqualTo(ExerciseCatalog.HighBarSquat to ExerciseCatalog.Deadlift)
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
    fun `KB movements step in 2 kg, strength in 2 point 5 kg, push-up does not step`() {
        ExerciseCatalog.kbFlow.filter { it.slug != "push_up" }.forEach {
            assertThat(it.weightStepKg).isEqualTo(2.0)
        }
        assertThat(ExerciseCatalog.PushUp.weightStepKg).isEqualTo(0.0)

        ExerciseCatalog.all.filter { it.category != Category.KB }.forEach {
            assertThat(it.weightStepKg).isEqualTo(2.5)
        }
    }
}
