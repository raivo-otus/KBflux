package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RestWeekTest {

    @Test
    fun `no prompt before the session threshold`() {
        assertThat(shouldPromptRestWeek(0, RestWeekState())).isFalse()
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS - 1, RestWeekState())).isFalse()
    }

    @Test
    fun `prompt fires exactly at the threshold`() {
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS, RestWeekState())).isTrue()
    }

    @Test
    fun `prompt keeps firing past the threshold until acted on`() {
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS + 5, RestWeekState())).isTrue()
    }

    @Test
    fun `the threshold is measured from the last rest week, not from zero`() {
        val state = RestWeekState(anchorSessions = 30)

        assertThat(shouldPromptRestWeek(50, state)).isFalse()
        assertThat(shouldPromptRestWeek(30 + REST_WEEK_SESSIONS, state)).isTrue()
    }

    @Test
    fun `a snooze suppresses the prompt for the next couple of sessions`() {
        val state = RestWeekState(snoozedAtSessions = REST_WEEK_SESSIONS)

        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS, state)).isFalse()
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS + 1, state)).isFalse()
    }

    @Test
    fun `the prompt returns once the snooze is spent`() {
        val state = RestWeekState(snoozedAtSessions = REST_WEEK_SESSIONS)

        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS + REST_WEEK_SNOOZE_SESSIONS, state))
            .isTrue()
    }

    @Test
    fun `taking a rest week clears the prompt for another full block`() {
        val after = RestWeekState(anchorSessions = REST_WEEK_SESSIONS, snoozedAtSessions = null)

        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS, after)).isFalse()
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS * 2 - 1, after)).isFalse()
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS * 2, after)).isTrue()
    }
}
