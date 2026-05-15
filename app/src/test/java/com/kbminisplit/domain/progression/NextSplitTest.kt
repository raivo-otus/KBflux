package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Split
import org.junit.Test
import java.time.LocalDate

class NextSplitTest {

    @Test
    fun `empty history yields A`() {
        assertThat(nextSplit(emptyList())).isEqualTo(Split.A)
    }

    @Test
    fun `after A comes B`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = ExerciseCatalog.LatPulldown.weightStepKg * 20,
                m1Reps = 8,
            ),
        )
        assertThat(nextSplit(history)).isEqualTo(Split.B)
    }

    @Test
    fun `after B comes C`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 5, 2), Split.B, m1Weight = 60.0, m1Reps = 8),
        )
        assertThat(nextSplit(history)).isEqualTo(Split.C)
    }

    @Test
    fun `after C wraps to A`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 5, 3), Split.C, m1Weight = 80.0, m1Reps = 8),
        )
        assertThat(nextSplit(history)).isEqualTo(Split.A)
    }

    @Test
    fun `only the most recent session matters`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 5, 1), Split.A, m1Weight = 50.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 2), Split.B, m1Weight = 60.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 3), Split.C, m1Weight = 80.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 4), Split.A, m1Weight = 50.0, m1Reps = 9),
        )
        assertThat(nextSplit(history)).isEqualTo(Split.B)
    }
}
