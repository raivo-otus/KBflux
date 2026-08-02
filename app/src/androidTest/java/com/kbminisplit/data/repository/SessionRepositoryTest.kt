package com.kbminisplit.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.buildInMemoryDatabase
import com.kbminisplit.data.db.seedExerciseRegistry
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
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

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = runBlocking {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
        seedExerciseRegistry(db)
        repo = SessionRepository(db.sessionDao(), fixedClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun movement(
        slug: String,
        weightKg: Double,
        position: Int,
        lastStatus: SetStatus = SetStatus.Completed,
    ): List<SetEntry> = listOf(
        SetEntry(slug, 0, isPriming = true, targetReps = null, weightKg = weightKg, status = SetStatus.Completed, position = position),
        SetEntry(slug, 1, isPriming = false, targetReps = 8, targetRepsMax = 12, weightKg = weightKg, status = SetStatus.Completed, position = position),
        SetEntry(slug, 2, isPriming = false, targetReps = 8, targetRepsMax = 12, weightKg = weightKg, status = SetStatus.Completed, position = position),
        SetEntry(slug, 3, isPriming = false, targetReps = 8, targetRepsMax = 12, weightKg = weightKg, status = lastStatus, position = position),
    )

    private fun sampleSession(date: LocalDate = LocalDate.of(2026, 5, 14)) = Session(
        date = date,
        dayKey = "A",
        feedback = Feedback.Green,
        circuitWeightKg = 16.0,
        sets = movement("lat_pulldown", 50.0, position = 0, lastStatus = SetStatus.Failed) +
            movement("barbell_row", 40.0, position = 1),
        bodyweightKg = 80.0,
    )

    @Test
    fun add_then_read_back_round_trips_all_fields() {
        runBlocking {
            val original = sampleSession()

            repo.addSession(original)

            val stored = repo.getByDate(original.date)
            assertThat(stored).isNotNull()
            assertThat(stored!!.dayKey).isEqualTo("A")
            assertThat(stored.feedback).isEqualTo(Feedback.Green)
            assertThat(stored.circuitWeightKg).isEqualTo(16.0)
            assertThat(stored.bodyweightKg).isEqualTo(80.0)
            assertThat(stored.sets).containsExactlyElementsIn(original.sets)
        }
    }

    @Test
    fun sets_read_back_in_the_order_they_were_performed() {
        runBlocking {
            repo.addSession(sampleSession())

            val stored = repo.getByDate(LocalDate.of(2026, 5, 14))!!
            assertThat(stored.sets.map { it.exerciseSlug }.distinct())
                .containsExactly("lat_pulldown", "barbell_row").inOrder()
            assertThat(stored.sets.first().isPriming).isTrue()
        }
    }

    @Test
    fun a_rep_range_survives_the_round_trip() {
        runBlocking {
            repo.addSession(sampleSession())

            val working = repo.getByDate(LocalDate.of(2026, 5, 14))!!
                .sets.first { !it.isPriming }
            assertThat(working.targetReps).isEqualTo(8)
            assertThat(working.targetRepsMax).isEqualTo(12)
            assertThat(working.repRangeLabel).isEqualTo("8–12")
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
