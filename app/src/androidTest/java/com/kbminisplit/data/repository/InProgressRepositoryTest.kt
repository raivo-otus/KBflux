package com.kbminisplit.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.buildInMemoryDatabase
import com.kbminisplit.data.db.seedExerciseCatalog
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class InProgressRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: InProgressRepository

    @Before
    fun setUp() = runBlocking {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
        seedExerciseCatalog(db)
        repo = InProgressRepository(db.inProgressDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pendingSets(): List<SetEntry> = listOf(
        SetEntry(ExerciseCatalog.LatPulldown.slug, 0, isPriming = true, targetReps = null, weightKg = 40.0, status = SetStatus.Pending),
        SetEntry(ExerciseCatalog.LatPulldown.slug, 1, isPriming = false, targetReps = 8, weightKg = 50.0, status = SetStatus.Pending),
        SetEntry(ExerciseCatalog.LatPulldown.slug, 2, isPriming = false, targetReps = 8, weightKg = 50.0, status = SetStatus.Pending),
        SetEntry(ExerciseCatalog.LatPulldown.slug, 3, isPriming = false, targetReps = 8, weightKg = 50.0, status = SetStatus.Pending),
    )

    @Test
    fun start_then_get_round_trips_session_header_and_sets() = runBlocking {
        repo.start(
            date = LocalDate.of(2026, 5, 15),
            split = Split.A,
            kbWeightKg = 16.0,
            sets = pendingSets(),
        )

        val snap = repo.get()
        assertThat(snap).isNotNull()
        assertThat(snap!!.date).isEqualTo(LocalDate.of(2026, 5, 15))
        assertThat(snap.split).isEqualTo(Split.A)
        assertThat(snap.kbWeightKg).isEqualTo(16.0)
        assertThat(snap.sets).hasSize(4)
        assertThat(snap.sets.all { it.status == SetStatus.Pending }).isTrue()
    }

    @Test
    fun update_set_state_persists_button_change() = runBlocking {
        repo.start(LocalDate.of(2026, 5, 15), Split.A, 16.0, pendingSets())

        repo.updateSetState(
            exerciseSlug = ExerciseCatalog.LatPulldown.slug,
            setIndex = 1,
            isPriming = false,
            state = SetStatus.Completed,
        )

        val snap = repo.get()!!
        val target = snap.sets.single { it.setIndex == 1 && !it.isPriming }
        assertThat(target.status).isEqualTo(SetStatus.Completed)
    }

    @Test
    fun clear_removes_session_and_sets() = runBlocking {
        repo.start(LocalDate.of(2026, 5, 15), Split.A, 16.0, pendingSets())

        repo.clear()

        assertThat(repo.get()).isNull()
        assertThat(db.inProgressDao().getSets()).isEmpty()
    }

    @Test
    fun start_replaces_any_previous_in_progress_state() = runBlocking {
        repo.start(LocalDate.of(2026, 5, 15), Split.A, 16.0, pendingSets())

        val newSets = pendingSets().take(1)
        repo.start(LocalDate.of(2026, 5, 16), Split.B, 18.0, newSets)

        val snap = repo.get()!!
        assertThat(snap.date).isEqualTo(LocalDate.of(2026, 5, 16))
        assertThat(snap.split).isEqualTo(Split.B)
        assertThat(snap.kbWeightKg).isEqualTo(18.0)
        assertThat(snap.sets).hasSize(1)
    }
}
