package com.kbminisplit.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.buildInMemoryDatabase
import com.kbminisplit.domain.progression.REST_WEEK_SESSIONS
import com.kbminisplit.domain.progression.shouldPromptRestWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SettingsRepository

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
        repo = SettingsRepository(db.settingsDao(), fixedClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun a_fresh_install_reports_first_launch() = runBlocking {
        assertThat(repo.observeIsFirstLaunch().first()).isTrue()
    }

    @Test
    fun seeing_the_program_tab_retires_first_launch() = runBlocking {
        repo.markProgramSeen()

        assertThat(repo.observeIsFirstLaunch().first()).isFalse()
        assertThat(db.settingsDao().get()?.onboardedAt).isEqualTo(fixedClock.millis())
    }

    @Test
    fun marking_the_program_seen_twice_keeps_the_original_stamp() = runBlocking {
        repo.markProgramSeen()
        val first = db.settingsDao().get()?.onboardedAt

        repo.markProgramSeen()

        assertThat(db.settingsDao().get()?.onboardedAt).isEqualTo(first)
    }

    @Test
    fun bodyweight_round_trips_with_its_timestamp() = runBlocking {
        repo.updateBodyweight(81.5)

        val state = repo.observeBodyweight().first()
        assertThat(state.kg).isEqualTo(81.5)
        assertThat(state.loggedAtMillis).isEqualTo(fixedClock.millis())
    }

    @Test
    fun dark_mode_and_haptics_round_trip_without_clobbering_each_other() = runBlocking {
        repo.setDarkMode(false)
        repo.setHapticLevel(2)

        assertThat(repo.observeIsDarkMode().first()).isFalse()
        assertThat(repo.observeHapticLevel().first()).isEqualTo(2)
    }

    @Test
    fun a_fresh_install_starts_the_rest_week_counter_at_zero() = runBlocking {
        val state = repo.observeRestWeek().first()

        assertThat(state.anchorSessions).isEqualTo(0)
        assertThat(state.snoozedAtSessions).isNull()
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS, state)).isTrue()
    }

    @Test
    fun taking_a_rest_week_moves_the_anchor_and_clears_the_snooze() = runBlocking {
        repo.snoozeRestWeek(REST_WEEK_SESSIONS)

        repo.takeRestWeek(REST_WEEK_SESSIONS)

        val state = repo.observeRestWeek().first()
        assertThat(state.anchorSessions).isEqualTo(REST_WEEK_SESSIONS)
        assertThat(state.snoozedAtSessions).isNull()
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS, state)).isFalse()
    }

    @Test
    fun a_snooze_persists_and_suppresses_the_prompt() = runBlocking {
        repo.snoozeRestWeek(REST_WEEK_SESSIONS)

        val state = repo.observeRestWeek().first()
        assertThat(state.snoozedAtSessions).isEqualTo(REST_WEEK_SESSIONS)
        assertThat(shouldPromptRestWeek(REST_WEEK_SESSIONS, state)).isFalse()
    }

    @Test
    fun rest_week_state_survives_other_settings_writes() = runBlocking {
        repo.takeRestWeek(30)

        repo.setHapticLevel(0)
        repo.updateBodyweight(79.0)

        assertThat(repo.observeRestWeek().first().anchorSessions).isEqualTo(30)
    }
}
