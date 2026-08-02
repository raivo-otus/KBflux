package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class CircuitBumpPromptTest {

    private val changedAt = 0L
    private val threeMonths = Duration.ofDays(91).toMillis()

    @Test
    fun `no prompt before three months on the bell`() {
        val group = circuitGroup(1, weightKg = 16.0, weightChangedAt = changedAt)

        assertThat(shouldPromptCircuitBump(group, threeMonths - 1)).isFalse()
    }

    @Test
    fun `prompt fires once three months have passed`() {
        val group = circuitGroup(1, weightKg = 16.0, weightChangedAt = changedAt)

        assertThat(shouldPromptCircuitBump(group, threeMonths)).isTrue()
    }

    @Test
    fun `no prompt at the top of the ladder`() {
        val group = circuitGroup(1, weightKg = 32.0, weightChangedAt = changedAt)

        assertThat(shouldPromptCircuitBump(group, threeMonths * 4)).isFalse()
    }

    @Test
    fun `no prompt for a circuit that does not use the ladder`() {
        val group = circuitGroup(1, weightKg = 16.0, usesLadder = false, weightChangedAt = changedAt)

        assertThat(shouldPromptCircuitBump(group, threeMonths * 4)).isFalse()
    }

    @Test
    fun `no prompt for a standard group`() {
        val group = standardGroup(1, items = listOf(item(1, "Bench")))

        assertThat(shouldPromptCircuitBump(group, threeMonths * 4)).isFalse()
    }

    @Test
    fun `a snooze holds the prompt off for two weeks`() {
        val group = circuitGroup(
            1,
            weightKg = 16.0,
            weightChangedAt = changedAt,
            bumpSnoozedAt = threeMonths,
        )

        val oneWeekLater = threeMonths + Duration.ofDays(7).toMillis()
        assertThat(shouldPromptCircuitBump(group, oneWeekLater)).isFalse()
    }

    @Test
    fun `the prompt returns once the snooze expires`() {
        val group = circuitGroup(
            1,
            weightKg = 16.0,
            weightChangedAt = changedAt,
            bumpSnoozedAt = threeMonths,
        )

        val twoWeeksLater = threeMonths + Duration.ofDays(14).toMillis()
        assertThat(shouldPromptCircuitBump(group, twoWeeksLater)).isTrue()
    }

    @Test
    fun `changing the weight restarts the three-month clock`() {
        // The repository stamps weightChangedAt on every weight change, so a group
        // that just moved up is three months away from the next offer again.
        val group = circuitGroup(1, weightKg = 20.0, weightChangedAt = threeMonths)

        assertThat(shouldPromptCircuitBump(group, threeMonths + 1)).isFalse()
        assertThat(shouldPromptCircuitBump(group, threeMonths * 2)).isTrue()
    }

    @Test
    fun `a circuit with no weight yet never prompts`() {
        val group = circuitGroup(1, weightKg = null, weightChangedAt = changedAt)

        assertThat(shouldPromptCircuitBump(group, threeMonths * 4)).isFalse()
    }
}
