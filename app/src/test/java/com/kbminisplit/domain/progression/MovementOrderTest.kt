package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Split
import org.junit.Test
import java.time.LocalDate

class MovementOrderTest {

    @Test
    fun `first ever A uses canonical order`() {
        val (first, second) = movementOrder(emptyList(), Split.A)

        assertThat(first).isEqualTo(ExerciseCatalog.LatPulldown)
        assertThat(second).isEqualTo(ExerciseCatalog.BarbellRow)
    }

    @Test
    fun `second A flips the pair`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 5, 1), Split.A, m1Weight = 50.0, m1Reps = 8),
        )

        val (first, second) = movementOrder(history, Split.A)

        assertThat(first).isEqualTo(ExerciseCatalog.BarbellRow)
        assertThat(second).isEqualTo(ExerciseCatalog.LatPulldown)
    }

    @Test
    fun `alternation continues over many A cycles`() {
        var history = emptyList<com.kbminisplit.domain.model.Session>()
        val seen = mutableListOf<Pair<String, String>>()

        repeat(6) { i ->
            val order = movementOrder(history, Split.A)
            seen += order.first.slug to order.second.slug
            history = history + strengthSession(
                date = LocalDate.of(2026, 5, 1).plusDays(i.toLong() * 3),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 8,
            )
        }

        val pull = ExerciseCatalog.LatPulldown.slug
        val row = ExerciseCatalog.BarbellRow.slug
        assertThat(seen).containsExactly(
            pull to row,
            row to pull,
            pull to row,
            row to pull,
            pull to row,
            row to pull,
        ).inOrder()
    }

    @Test
    fun `other splits do not influence A ordering`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 5, 1), Split.B, m1Weight = 60.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 2), Split.C, m1Weight = 80.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 3), Split.B, m1Weight = 60.0, m1Reps = 8),
        )

        val (first, second) = movementOrder(history, Split.A)

        // Zero past A sessions → canonical order.
        assertThat(first).isEqualTo(ExerciseCatalog.LatPulldown)
        assertThat(second).isEqualTo(ExerciseCatalog.BarbellRow)
    }

    @Test
    fun `B split alternates Bench Assisted Dip`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 5, 1), Split.B, m1Weight = 60.0, m1Reps = 8),
        )

        val (first, second) = movementOrder(history, Split.B)

        assertThat(first).isEqualTo(ExerciseCatalog.AssistedDip)
        assertThat(second).isEqualTo(ExerciseCatalog.Bench)
    }

    @Test
    fun `C split alternates Squat Romanian Deadlift`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 5, 1), Split.C, m1Weight = 80.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 2), Split.C, m1Weight = 80.0, m1Reps = 8),
        )

        val (first, second) = movementOrder(history, Split.C)

        // 2 past C sessions → canonical again.
        assertThat(first).isEqualTo(ExerciseCatalog.HighBarSquat)
        assertThat(second).isEqualTo(ExerciseCatalog.RomanianDeadlift)
    }
}
