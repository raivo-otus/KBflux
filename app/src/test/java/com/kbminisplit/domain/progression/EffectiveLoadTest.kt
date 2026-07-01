package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.ExerciseMechanic
import org.junit.Test

class EffectiveLoadTest {

    @Test
    fun `traditional load is the logged weight`() {
        val load = effectiveLoadKg(ExerciseMechanic.TRADITIONAL, loggedKg = 60.0, bodyweightKg = 80.0)

        assertThat(load).isEqualTo(60.0)
    }

    @Test
    fun `assisted load is bodyweight minus assistance`() {
        val load = effectiveLoadKg(ExerciseMechanic.ASSISTED, loggedKg = 20.0, bodyweightKg = 82.5)

        assertThat(load).isEqualTo(62.5)
    }

    @Test
    fun `assisted load rises as assistance drops`() {
        val moreAssist = effectiveLoadKg(ExerciseMechanic.ASSISTED, loggedKg = 30.0, bodyweightKg = 80.0)
        val lessAssist = effectiveLoadKg(ExerciseMechanic.ASSISTED, loggedKg = 20.0, bodyweightKg = 80.0)

        assertThat(lessAssist).isGreaterThan(moreAssist)
    }
}
