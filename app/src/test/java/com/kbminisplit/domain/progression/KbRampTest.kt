package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.Split
import org.junit.Test
import java.time.LocalDate

class KbRampTest {

    /** [count] sessions at [kbWeight], one day apart starting from [start]. */
    private fun sessionsAt(kbWeight: Double, count: Int, start: LocalDate): List<Session> =
        List(count) { idx ->
            strengthSession(
                start.plusDays(idx.toLong()),
                Split.A,
                kbWeight = kbWeight,
                m1Weight = 50.0,
                m1Reps = 8,
            )
        }

    @Test
    fun `nextKbWeight climbs the ladder`() {
        assertThat(nextKbWeight(8.0)).isEqualTo(10.0)
        assertThat(nextKbWeight(12.0)).isEqualTo(16.0)
        assertThat(nextKbWeight(30.0)).isEqualTo(32.0)
    }

    @Test
    fun `nextKbWeight from an off-ladder weight targets the next rung up`() {
        assertThat(nextKbWeight(14.0)).isEqualTo(16.0)
        assertThat(nextKbWeight(18.0)).isEqualTo(20.0)
        assertThat(nextKbWeight(6.0)).isEqualTo(8.0)
    }

    @Test
    fun `nextKbWeight at or above the top returns null`() {
        assertThat(nextKbWeight(32.0)).isNull()
        assertThat(nextKbWeight(40.0)).isNull()
    }

    @Test
    fun `empty history uses the full scheme`() {
        assertThat(kbRepScheme(emptyList(), 16.0)).isEqualTo(listOf(32, 16, 8))
    }

    @Test
    fun `weight never changed uses the full scheme regardless of history length`() {
        val history = sessionsAt(16.0, count = 2, start = LocalDate.of(2026, 5, 1))

        assertThat(kbRepScheme(history, 16.0)).isEqualTo(listOf(32, 16, 8))
    }

    @Test
    fun `first three workouts after a bump use the lowest stage`() {
        val old = sessionsAt(16.0, count = 5, start = LocalDate.of(2026, 1, 1))

        for (completedAtNewWeight in 0..2) {
            val history = old + sessionsAt(20.0, completedAtNewWeight, LocalDate.of(2026, 4, 1))
            assertThat(kbRepScheme(history, 20.0)).isEqualTo(listOf(20, 10, 5))
        }
    }

    @Test
    fun `scheme advances one stage per three completed workouts`() {
        val old = sessionsAt(16.0, count = 5, start = LocalDate.of(2026, 1, 1))

        val afterThree = old + sessionsAt(20.0, 3, LocalDate.of(2026, 4, 1))
        val afterSix = old + sessionsAt(20.0, 6, LocalDate.of(2026, 4, 1))
        val afterNine = old + sessionsAt(20.0, 9, LocalDate.of(2026, 4, 1))

        assertThat(kbRepScheme(afterThree, 20.0)).isEqualTo(listOf(24, 12, 6))
        assertThat(kbRepScheme(afterSix, 20.0)).isEqualTo(listOf(28, 14, 7))
        assertThat(kbRepScheme(afterNine, 20.0)).isEqualTo(listOf(32, 16, 8))
    }

    @Test
    fun `scheme stays at full once reached`() {
        val old = sessionsAt(16.0, count = 5, start = LocalDate.of(2026, 1, 1))
        val history = old + sessionsAt(20.0, 12, LocalDate.of(2026, 4, 1))

        assertThat(kbRepScheme(history, 20.0)).isEqualTo(listOf(32, 16, 8))
    }

    @Test
    fun `dropping back down in weight also restarts the ramp`() {
        val heavy = sessionsAt(20.0, count = 6, start = LocalDate.of(2026, 1, 1))
        val history = heavy + sessionsAt(16.0, 1, LocalDate.of(2026, 4, 1))

        assertThat(kbRepScheme(history, 16.0)).isEqualTo(listOf(20, 10, 5))
    }

    @Test
    fun `earlier sessions at the same weight do not count after a change in between`() {
        // 16 → 20 → 16: only the trailing 16 kg run counts.
        val history =
            sessionsAt(16.0, 9, LocalDate.of(2026, 1, 1)) +
                sessionsAt(20.0, 2, LocalDate.of(2026, 2, 1)) +
                sessionsAt(16.0, 4, LocalDate.of(2026, 4, 1))

        assertThat(kbRepScheme(history, 16.0)).isEqualTo(listOf(24, 12, 6))
    }
}
