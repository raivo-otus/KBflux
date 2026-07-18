package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.ExerciseMechanic
import org.junit.Test

class AcclimatizationLoadTest {

    private val traditional = ExerciseMechanic.TRADITIONAL
    private val assisted = ExerciseMechanic.ASSISTED

    // ---- rounding ----

    @Test
    fun `roundToStepKg snaps to the nearest 2point5`() {
        assertThat(roundToStepKg(26.25)).isEqualTo(27.5)
        assertThat(roundToStepKg(37.5)).isEqualTo(37.5)
        assertThat(roundToStepKg(7.4)).isEqualTo(7.5)
        assertThat(roundToStepKg(6.0)).isEqualTo(5.0)
    }

    // ---- floors ----

    @Test
    fun `main lifts floor at 20 and auxiliaries at 5`() {
        assertThat(acclimatizationFloorKg(ExerciseCatalog.Bench)).isEqualTo(20.0)
        assertThat(acclimatizationFloorKg(ExerciseCatalog.HighBarSquat)).isEqualTo(20.0)
        assertThat(acclimatizationFloorKg(ExerciseCatalog.BicepCurl)).isEqualTo(5.0)
        assertThat(acclimatizationFloorKg(ExerciseCatalog.SideDeltFly)).isEqualTo(5.0)
    }

    // ---- traditional lifts ----

    @Test
    fun `traditional prime and warm-up are 50 and 75 percent, rounded to 2point5`() {
        // Working 50 kg, floor 20: 25 and 37.5 — both above the floor.
        val prime = acclimatizationLoadKg(traditional, 50.0, PRIME_FRACTION, 20.0, bodyweightKg = null)
        val warmup = acclimatizationLoadKg(traditional, 50.0, WARMUP_FRACTION, 20.0, bodyweightKg = null)
        assertThat(prime).isEqualTo(25.0)
        assertThat(warmup).isEqualTo(37.5)
    }

    @Test
    fun `traditional lead-in is floored for light main lifts`() {
        // 50% of 30 is 15 → floored to 20; 75% is 22.5 → above the floor.
        val prime = acclimatizationLoadKg(traditional, 30.0, PRIME_FRACTION, 20.0, bodyweightKg = null)
        val warmup = acclimatizationLoadKg(traditional, 30.0, WARMUP_FRACTION, 20.0, bodyweightKg = null)
        assertThat(prime).isEqualTo(20.0)
        assertThat(warmup).isEqualTo(22.5)
    }

    @Test
    fun `traditional auxiliary uses the lower 5kg floor`() {
        // 50% of 10 is 5 (at floor); 75% is 7.5.
        val prime = acclimatizationLoadKg(traditional, 10.0, PRIME_FRACTION, 5.0, bodyweightKg = null)
        val warmup = acclimatizationLoadKg(traditional, 10.0, WARMUP_FRACTION, 5.0, bodyweightKg = null)
        assertThat(prime).isEqualTo(5.0)
        assertThat(warmup).isEqualTo(7.5)
    }

    @Test
    fun `lead-in never exceeds the working weight`() {
        // Working weight sits at the floor: prime/warm-up collapse to the work weight
        // rather than prescribing something heavier.
        val prime = acclimatizationLoadKg(traditional, 20.0, PRIME_FRACTION, 20.0, bodyweightKg = null)
        assertThat(prime).isEqualTo(20.0)
        // Bodyweight movement (0 kg working): shows 0, not the 5 kg floor.
        val zero = acclimatizationLoadKg(traditional, 0.0, PRIME_FRACTION, 5.0, bodyweightKg = null)
        assertThat(zero).isEqualTo(0.0)
    }

    // ---- assisted lifts ----

    @Test
    fun `assisted lead-in raises the pin to cut effective load by the fraction`() {
        // Bodyweight 80, working pin 30 → effective 50. Prime effective 25 → pin 55;
        // warm-up effective 37.5 → pin 42.5. A higher pin means more assistance.
        val prime = acclimatizationLoadKg(assisted, 30.0, PRIME_FRACTION, 20.0, bodyweightKg = 80.0)
        val warmup = acclimatizationLoadKg(assisted, 30.0, WARMUP_FRACTION, 20.0, bodyweightKg = 80.0)
        assertThat(prime).isEqualTo(55.0)
        assertThat(warmup).isEqualTo(42.5)
    }

    @Test
    fun `assisted lead-in returns null without a bodyweight`() {
        val prime = acclimatizationLoadKg(assisted, 30.0, PRIME_FRACTION, 20.0, bodyweightKg = null)
        assertThat(prime).isNull()
    }

    @Test
    fun `assisted lead-in never drops below the working pin`() {
        // Effective load (15) already under the floor (20): no ramp — the pin stays at
        // the working value rather than dropping (which would be harder).
        val prime = acclimatizationLoadKg(assisted, 65.0, PRIME_FRACTION, 20.0, bodyweightKg = 80.0)
        assertThat(prime).isEqualTo(65.0)
    }
}
