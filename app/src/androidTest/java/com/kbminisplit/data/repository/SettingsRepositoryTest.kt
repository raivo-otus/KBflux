package com.kbminisplit.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.buildInMemoryDatabase
import com.kbminisplit.data.db.seedExerciseCatalog
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.progression.KbBumpSnooze
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SettingsRepository

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runBlocking {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
        seedExerciseCatalog(db)
        repo = SettingsRepository(db.settingsDao(), fixedClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val sampleDefaults = OnboardingDefaults(
        kbWeightKg = 16.0,
        startingWeightsBySlug = mapOf(
            ExerciseCatalog.LatPulldown.slug to 50.0,
            ExerciseCatalog.BarbellRow.slug to 40.0,
            ExerciseCatalog.Bench.slug to 60.0,
            ExerciseCatalog.Ohp.slug to 35.0,
            ExerciseCatalog.HighBarSquat.slug to 70.0,
            ExerciseCatalog.Deadlift.slug to 80.0,
        ),
        startingTargetReps = 8,
    )

    @Test
    fun save_then_read_round_trips_onboarding_defaults() = runBlocking {
        repo.saveOnboarding(sampleDefaults)

        val read = repo.getOnboardingDefaults()
        assertThat(read).isEqualTo(sampleDefaults)
    }

    @Test
    fun onboarding_returns_null_before_save() = runBlocking {
        assertThat(repo.getOnboardingDefaults()).isNull()
    }

    @Test
    fun saving_onboarding_preserves_existing_snooze_state() = runBlocking {
        repo.saveOnboarding(sampleDefaults)
        repo.saveKbBumpSnooze(KbBumpSnooze(YearMonth.of(2026, 5), sessionCountAtSnooze = 3))

        repo.saveOnboarding(sampleDefaults.copy(kbWeightKg = 18.0))

        val settings = db.settingsDao().get()
        assertThat(settings?.kbWeightKg).isEqualTo(18.0)
        assertThat(settings?.kbBumpSnoozedAtMonth).isEqualTo("2026-05")
        assertThat(settings?.kbBumpSnoozeSessionCount).isEqualTo(3)
    }

    @Test
    fun clearing_snooze_writes_nulls() = runBlocking {
        repo.saveOnboarding(sampleDefaults)
        repo.saveKbBumpSnooze(KbBumpSnooze(YearMonth.of(2026, 5), 3))

        repo.saveKbBumpSnooze(null)

        val settings = db.settingsDao().get()
        assertThat(settings?.kbBumpSnoozedAtMonth).isNull()
        assertThat(settings?.kbBumpSnoozeSessionCount).isNull()
    }
}
