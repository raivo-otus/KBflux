package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KbRampTest {

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
    fun `the ladder is ordered and covers the usual bell sizes`() {
        assertThat(KB_WEIGHT_LADDER).isInOrder()
        assertThat(KB_WEIGHT_LADDER)
            .containsExactly(8.0, 10.0, 12.0, 16.0, 20.0, 24.0, 28.0, 32.0)
            .inOrder()
    }
}
