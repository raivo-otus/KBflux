package com.kbminisplit.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.buildInMemoryDatabase
import com.kbminisplit.data.db.seedExerciseCatalog
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SessionRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: SessionRepository

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runBlocking {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
        seedExerciseCatalog(db)
        repo = SessionRepository(db.sessionDao(), fixedClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleSession(date: LocalDate = LocalDate.of(2026, 5, 14)) = Session(
        date = date,
        split = Split.A,
        feedback = Feedback.Green,
        kbWeightKg = 16.0,
        sets = listOf(
            SetEntry(ExerciseCatalog.LatPulldown.slug, 0, isPriming = true, targetReps = null, weightKg = 40.0, status = SetStatus.Completed),
            SetEntry(ExerciseCatalog.LatPulldown.slug, 1, isPriming = false, targetReps = 8, weightKg = 50.0, status = SetStatus.Completed),
            SetEntry(ExerciseCatalog.LatPulldown.slug, 2, isPriming = false, targetReps = 8, weightKg = 50.0, status = SetStatus.Completed),
            SetEntry(ExerciseCatalog.LatPulldown.slug, 3, isPriming = false, targetReps = 8, weightKg = 50.0, status = SetStatus.Failed),
            SetEntry(ExerciseCatalog.BarbellRow.slug, 0, isPriming = true, targetReps = null, weightKg = 30.0, status = SetStatus.Completed),
            SetEntry(ExerciseCatalog.BarbellRow.slug, 1, isPriming = false, targetReps = 8, weightKg = 40.0, status = SetStatus.Completed),
            SetEntry(ExerciseCatalog.BarbellRow.slug, 2, isPriming = false, targetReps = 8, weightKg = 40.0, status = SetStatus.Completed),
            SetEntry(ExerciseCatalog.BarbellRow.slug, 3, isPriming = false, targetReps = 8, weightKg = 40.0, status = SetStatus.Completed),
        ),
    )

    @Test
    fun add_then_read_back_round_trips_all_fields() {
        runBlocking {
            val original = sampleSession()

            repo.addSession(original)

            val stored = repo.getByDate(original.date)
            assertThat(stored).isNotNull()
            assertThat(stored!!.split).isEqualTo(Split.A)
            assertThat(stored.feedback).isEqualTo(Feedback.Green)
            assertThat(stored.kbWeightKg).isEqualTo(16.0)
            assertThat(stored.sets).containsExactlyElementsIn(original.sets)
        }
    }

    @Test
    fun observe_all_emits_after_insert() {
        runBlocking {
            repo.observeAll().test {
                assertThat(awaitItem()).isEmpty()

                repo.addSession(sampleSession())

                val next = awaitItem()
                assertThat(next).hasSize(1)
                assertThat(next.single().sets).hasSize(8)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun observe_between_filters_by_date_range() {
        runBlocking {
            repo.addSession(sampleSession(LocalDate.of(2026, 5, 1)))
            repo.addSession(sampleSession(LocalDate.of(2026, 5, 10)))
            repo.addSession(sampleSession(LocalDate.of(2026, 6, 1)))

            repo.observeBetween(
                start = LocalDate.of(2026, 5, 1),
                endInclusive = LocalDate.of(2026, 5, 31),
            ).test {
                val window = awaitItem()
                assertThat(window.map { it.date }).containsExactly(
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 10),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
