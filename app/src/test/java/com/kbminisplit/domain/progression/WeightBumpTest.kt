package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeightBumpTest {

    @Test
    fun `a traditional movement bumps up by its own step`() {
        assertThat(bumpedWeightKg(item(1, "Bench", currentWeightKg = 60.0, weightStepKg = 2.5)))
            .isEqualTo(62.5)
        assertThat(bumpedWeightKg(item(2, "Pulldown", currentWeightKg = 55.0, weightStepKg = 5.0)))
            .isEqualTo(60.0)
    }

    @Test
    fun `an assisted movement bumps by needing less assistance`() {
        val dips = item(1, "Dips", currentWeightKg = 40.0, weightStepKg = 2.5, isAssisted = true)

        assertThat(bumpedWeightKg(dips)).isEqualTo(37.5)
    }

    @Test
    fun `assisted assistance never goes below zero`() {
        val dips = item(1, "Dips", currentWeightKg = 1.0, weightStepKg = 2.5, isAssisted = true)

        assertThat(bumpedWeightKg(dips)).isEqualTo(0.0)
    }

    @Test
    fun `deload is the exact inverse of a bump`() {
        val bench = item(1, "Bench", currentWeightKg = 60.0, weightStepKg = 2.5)
        val dips = item(2, "Dips", currentWeightKg = 40.0, weightStepKg = 2.5, isAssisted = true)

        assertThat(deloadedWeightKg(bench)).isEqualTo(57.5)
        assertThat(deloadedWeightKg(dips)).isEqualTo(42.5)
    }

    @Test
    fun `a traditional deload never goes below zero`() {
        val curl = item(1, "Curl", currentWeightKg = 1.0, weightStepKg = 2.0)

        assertThat(deloadedWeightKg(curl)).isEqualTo(0.0)
    }

    @Test
    fun `no bump is offered once assistance reaches zero`() {
        val unassisted = item(1, "Dips", currentWeightKg = 0.0, weightStepKg = 2.5, isAssisted = true)

        assertThat(canBump(unassisted)).isFalse()
    }

    @Test
    fun `a traditional movement can always bump`() {
        assertThat(canBump(item(1, "Back Extension", currentWeightKg = 0.0, weightStepKg = 2.0)))
            .isTrue()
    }
}
