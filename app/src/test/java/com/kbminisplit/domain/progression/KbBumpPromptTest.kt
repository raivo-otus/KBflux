package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.Split
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class KbBumpPromptTest {

    private fun kbSession(date: LocalDate, kbWeight: Double = 16.0): Session =
        strengthSession(date, Split.A, kbWeight = kbWeight, m1Weight = 50.0, m1Reps = 8)

    @Test
    fun `no history never prompts`() {
        val today = LocalDate.of(2026, 5, 1)

        assertThat(shouldPromptKbBump(emptyList(), today, currentKbWeightKg = 16.0)).isFalse()
    }

    @Test
    fun `less than 3 months at current weight does not prompt`() {
        val history = listOf(
            kbSession(LocalDate.of(2026, 3, 1)),
            kbSession(LocalDate.of(2026, 4, 15)),
        )
        val today = LocalDate.of(2026, 5, 15)

        assertThat(shouldPromptKbBump(history, today, currentKbWeightKg = 16.0)).isFalse()
    }

    @Test
    fun `prompts once the current weight has been in use for 3 months`() {
        val history = listOf(
            kbSession(LocalDate.of(2026, 2, 1)),
            kbSession(LocalDate.of(2026, 3, 15)),
        )
        val today = LocalDate.of(2026, 5, 1)

        assertThat(shouldPromptKbBump(history, today, currentKbWeightKg = 16.0)).isTrue()
    }

    @Test
    fun `day before the 3-month boundary does not prompt`() {
        val history = listOf(
            kbSession(LocalDate.of(2026, 2, 1)),
        )
        val today = LocalDate.of(2026, 4, 30)

        assertThat(shouldPromptKbBump(history, today, currentKbWeightKg = 16.0)).isFalse()
    }

    @Test
    fun `freshly changed weight with no completed session does not prompt`() {
        // All history is at the old weight; the current weight's run is empty.
        val history = listOf(
            kbSession(LocalDate.of(2025, 12, 1), kbWeight = 16.0),
            kbSession(LocalDate.of(2026, 1, 15), kbWeight = 16.0),
        )
        val today = LocalDate.of(2026, 5, 1)

        assertThat(shouldPromptKbBump(history, today, currentKbWeightKg = 20.0)).isFalse()
    }

    @Test
    fun `three-month clock starts at the trailing run, not the first session ever`() {
        // Old-weight sessions since January must not count toward the 16 kg run
        // that only started in March.
        val history = listOf(
            kbSession(LocalDate.of(2026, 1, 1), kbWeight = 12.0),
            kbSession(LocalDate.of(2026, 1, 20), kbWeight = 12.0),
            kbSession(LocalDate.of(2026, 3, 1), kbWeight = 16.0),
        )

        assertThat(
            shouldPromptKbBump(history, LocalDate.of(2026, 5, 15), currentKbWeightKg = 16.0),
        ).isFalse()
        assertThat(
            shouldPromptKbBump(history, LocalDate.of(2026, 6, 15), currentKbWeightKg = 16.0),
        ).isTrue()
    }

    @Test
    fun `top of the ladder never prompts`() {
        val history = listOf(
            kbSession(LocalDate.of(2025, 6, 1), kbWeight = 32.0),
        )
        val today = LocalDate.of(2026, 5, 1)

        assertThat(shouldPromptKbBump(history, today, currentKbWeightKg = 32.0)).isFalse()
        assertThat(shouldPromptKbBump(history, today, currentKbWeightKg = 40.0)).isFalse()
    }

    @Test
    fun `snoozed prompt is suppressed until two more sessions are logged`() {
        val history = listOf(
            kbSession(LocalDate.of(2026, 1, 2)),
            kbSession(LocalDate.of(2026, 1, 28)),
            kbSession(LocalDate.of(2026, 5, 1)),
        )
        val snooze = KbBumpSnooze(YearMonth.of(2026, 5), sessionCountAtSnooze = 2)
        val today = LocalDate.of(2026, 5, 3)

        // One session since the snooze; we need two.
        assertThat(shouldPromptKbBump(history, today, 16.0, snooze)).isFalse()
    }

    @Test
    fun `snoozed prompt re-fires after two more sessions logged`() {
        val history = listOf(
            kbSession(LocalDate.of(2026, 1, 2)),
            kbSession(LocalDate.of(2026, 1, 28)),
            kbSession(LocalDate.of(2026, 5, 1)),
            kbSession(LocalDate.of(2026, 5, 3)),
        )
        val snooze = KbBumpSnooze(YearMonth.of(2026, 5), sessionCountAtSnooze = 2)
        val today = LocalDate.of(2026, 5, 5)

        assertThat(shouldPromptKbBump(history, today, 16.0, snooze)).isTrue()
    }
}
