package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration

class BodyweightPromptTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `stale when never logged`() {
        assertThat(isBodyweightStale(loggedAtMillis = null, nowMillis = now)).isTrue()
    }

    @Test
    fun `fresh within seven days`() {
        val threeDaysAgo = now - Duration.ofDays(3).toMillis()

        assertThat(isBodyweightStale(loggedAtMillis = threeDaysAgo, nowMillis = now)).isFalse()
    }

    @Test
    fun `stale at exactly seven days`() {
        val sevenDaysAgo = now - Duration.ofDays(7).toMillis()

        assertThat(isBodyweightStale(loggedAtMillis = sevenDaysAgo, nowMillis = now)).isTrue()
    }

    @Test
    fun `stale beyond seven days`() {
        val tenDaysAgo = now - Duration.ofDays(10).toMillis()

        assertThat(isBodyweightStale(loggedAtMillis = tenDaysAgo, nowMillis = now)).isTrue()
    }
}
