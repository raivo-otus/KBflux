package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.Split
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class KbBumpPromptTest {

    @Test
    fun `no history never prompts`() {
        val today = LocalDate.of(2026, 5, 1)

        assertThat(shouldPromptKbBump(emptyList(), today)).isFalse()
    }

    @Test
    fun `previous month with no sessions does not prompt`() {
        // History only contains sessions further back than the previous month.
        val history = listOf(
            strengthSession(LocalDate.of(2026, 3, 28), Split.A, m1Weight = 50.0, m1Reps = 8),
        )
        val today = LocalDate.of(2026, 5, 1)

        assertThat(shouldPromptKbBump(history, today)).isFalse()
    }

    @Test
    fun `first session of new month with prior month sessions prompts`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 4, 28), Split.A, m1Weight = 50.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 4, 30), Split.B, m1Weight = 60.0, m1Reps = 8),
        )
        val today = LocalDate.of(2026, 5, 1)

        assertThat(shouldPromptKbBump(history, today)).isTrue()
    }

    @Test
    fun `second session of the month does not prompt without snooze`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 4, 28), Split.A, m1Weight = 50.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 1), Split.B, m1Weight = 60.0, m1Reps = 8),
        )
        val today = LocalDate.of(2026, 5, 3)

        assertThat(shouldPromptKbBump(history, today)).isFalse()
    }

    @Test
    fun `snoozed prompt is suppressed for the next session`() {
        // Snooze fired before the 5/1 session was logged. Count at snooze = 2 (the April sessions).
        val history = listOf(
            strengthSession(LocalDate.of(2026, 4, 28), Split.A, m1Weight = 50.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 4, 30), Split.B, m1Weight = 60.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 1), Split.C, m1Weight = 80.0, m1Reps = 8),
        )
        val snooze = KbBumpSnooze(YearMonth.of(2026, 5), sessionCountAtSnooze = 2)
        val today = LocalDate.of(2026, 5, 3)

        // One session has completed since the snooze (5/1). We need two.
        assertThat(shouldPromptKbBump(history, today, snooze)).isFalse()
    }

    @Test
    fun `snoozed prompt re-fires after two more sessions logged`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 4, 28), Split.A, m1Weight = 50.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 4, 30), Split.B, m1Weight = 60.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 1), Split.C, m1Weight = 80.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 3), Split.A, m1Weight = 50.0, m1Reps = 8),
        )
        val snooze = KbBumpSnooze(YearMonth.of(2026, 5), sessionCountAtSnooze = 2)
        val today = LocalDate.of(2026, 5, 5)

        assertThat(shouldPromptKbBump(history, today, snooze)).isTrue()
    }

    @Test
    fun `month rollover invalidates a prior-month snooze`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 4, 28), Split.A, m1Weight = 50.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 1), Split.B, m1Weight = 60.0, m1Reps = 8),
        )
        val staleSnooze = KbBumpSnooze(YearMonth.of(2026, 5), sessionCountAtSnooze = 2)
        val today = LocalDate.of(2026, 6, 1)

        assertThat(shouldPromptKbBump(history, today, staleSnooze)).isTrue()
    }

    @Test
    fun `snooze in a different month still respects the first-of-month rule`() {
        // Snooze in April; today is June; May had zero sessions → still don't fire.
        val history = listOf(
            strengthSession(LocalDate.of(2026, 4, 5), Split.A, m1Weight = 50.0, m1Reps = 8),
        )
        val staleSnooze = KbBumpSnooze(YearMonth.of(2026, 4), sessionCountAtSnooze = 1)
        val today = LocalDate.of(2026, 6, 1)

        assertThat(shouldPromptKbBump(history, today, staleSnooze)).isFalse()
    }

    @Test
    fun `prompts on first session ever in a brand-new month after one prior month session`() {
        val history = listOf(
            strengthSession(LocalDate.of(2026, 1, 30), Split.A, m1Weight = 50.0, m1Reps = 8),
        )
        val today = LocalDate.of(2026, 2, 2)

        assertThat(shouldPromptKbBump(history, today)).isTrue()
    }
}
