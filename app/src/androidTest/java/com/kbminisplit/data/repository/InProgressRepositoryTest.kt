package com.kbminisplit.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.buildInMemoryDatabase
import com.kbminisplit.data.db.seedExerciseRegistry
import com.kbminisplit.domain.model.InProgressSet
import com.kbminisplit.domain.model.SetStatus
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

    private val date = LocalDate.of(2026, 5, 15)

    @Before
    fun setUp() = runBlocking {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
        seedExerciseRegistry(db)
        repo = InProgressRepository(db.inProgressDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        setIndex: Int,
        isPriming: Boolean,
        itemId: Long = 1L,
        groupId: Long = 10L,
        slug: String = "bench",
        weightKg: Double = 60.0,
        position: Int = 0,
    ) = InProgressSet(
        id = 0,
        programGroupId = groupId,
        programItemId = itemId,
        exerciseSlug = slug,
        setIndex = setIndex,
        isPriming = isPriming,
        targetReps = if (isPriming) null else 8,
        targetRepsMax = if (isPriming) null else 12,
        weightKg = weightKg,
        status = SetStatus.Pending,
        position = position,
    )

    private fun benchRows() = listOf(
        row(0, isPriming = true),
        row(1, isPriming = true),
        row(1, isPriming = false),
        row(2, isPriming = false),
        row(3, isPriming = false),
    )

    @Test
    fun start_then_get_round_trips_the_session_header_and_its_rows() = runBlocking {
        repo.start(date = date, dayKey = "A", sets = benchRows())

        val snap = repo.get()
        assertThat(snap).isNotNull()
        assertThat(snap!!.date).isEqualTo(date)
        assertThat(snap.dayKey).isEqualTo("A")
        assertThat(snap.sets).hasSize(5)
        assertThat(snap.sets.all { it.status == SetStatus.Pending }).isTrue()
        assertThat(snap.sets.single { !it.isPriming && it.setIndex == 1 }.targetRepsMax)
            .isEqualTo(12)
    }

    @Test
    fun rows_come_back_in_the_order_the_session_is_performed() = runBlocking {
        val circuit = List(3) { round ->
            row(round, isPriming = false, itemId = 0L, groupId = 5L, slug = "kb_flow", position = 0)
        }
        val bench = benchRows().map { it.copy(position = 1) }
        repo.start(date, "A", circuit + bench)

        val slugs = repo.get()!!.sets.map { it.exerciseSlug }
        assertThat(slugs.take(3)).containsExactly("kb_flow", "kb_flow", "kb_flow")
        // Within a movement, lead-ins come before working sets.
        val benchRows = repo.get()!!.sets.drop(3)
        assertThat(benchRows.take(2).all { it.isPriming }).isTrue()
    }

    @Test
    fun a_button_tap_persists_by_row_id() = runBlocking {
        repo.start(date, "A", benchRows())
        val target = repo.get()!!.sets.first { !it.isPriming }

        repo.updateSetState(target.id, SetStatus.Completed)

        val reread = repo.get()!!.sets.single { it.id == target.id }
        assertThat(reread.status).isEqualTo(SetStatus.Completed)
        assertThat(repo.get()!!.sets.count { it.status == SetStatus.Completed }).isEqualTo(1)
    }

    @Test
    fun the_same_movement_can_appear_twice_in_one_day() = runBlocking {
        // Two program items sharing a slug — the old exercise-keyed unique index
        // could not represent this.
        val first = benchRows()
        val second = benchRows().map { it.copy(programItemId = 2L, position = 1) }

        repo.start(date, "A", first + second)

        assertThat(repo.get()!!.sets).hasSize(10)
    }

    @Test
    fun a_weight_correction_applies_to_every_row_of_that_movement_only() = runBlocking {
        val bench = benchRows()
        val other = benchRows().map { it.copy(programItemId = 2L, weightKg = 30.0, position = 1) }
        repo.start(date, "A", bench + other)

        repo.updateItemWeight(1L, 65.0)

        val sets = repo.get()!!.sets
        assertThat(sets.filter { it.programItemId == 1L }.map { it.weightKg }.distinct())
            .containsExactly(65.0)
        assertThat(sets.filter { it.programItemId == 2L }.map { it.weightKg }.distinct())
            .containsExactly(30.0)
    }

    @Test
    fun a_circuit_weight_correction_hits_only_the_group_rows() = runBlocking {
        val circuit = List(3) { round ->
            row(round, isPriming = false, itemId = 0L, groupId = 5L, slug = "kb_flow", weightKg = 16.0)
        }
        repo.start(date, "A", circuit + benchRows())

        repo.updateCircuitWeight(5L, 20.0)

        val sets = repo.get()!!.sets
        assertThat(sets.filter { it.isCircuitRound }.map { it.weightKg }.distinct())
            .containsExactly(20.0)
        assertThat(sets.filter { !it.isCircuitRound }.map { it.weightKg }.distinct())
            .containsExactly(60.0)
    }

    @Test
    fun addSets_appends_a_revealed_group_without_disturbing_the_rest() = runBlocking {
        repo.start(date, "A", benchRows())
        val completed = repo.get()!!.sets.first()
        repo.updateSetState(completed.id, SetStatus.Completed)

        repo.addSets(
            listOf(row(1, isPriming = false, itemId = 9L, groupId = 20L, slug = "bicep_curl", position = 1)),
        )

        val sets = repo.get()!!.sets
        assertThat(sets).hasSize(6)
        assertThat(sets.single { it.id == completed.id }.status).isEqualTo(SetStatus.Completed)
    }

    @Test
    fun clear_removes_the_session_and_its_rows() = runBlocking {
        repo.start(date, "A", benchRows())

        repo.clear()

        assertThat(repo.get()).isNull()
        assertThat(db.inProgressDao().getSets()).isEmpty()
    }

    @Test
    fun start_replaces_any_previous_in_progress_state() = runBlocking {
        repo.start(date, "A", benchRows())

        repo.start(date.plusDays(1), "B", benchRows().take(1))

        val snap = repo.get()!!
        assertThat(snap.date).isEqualTo(date.plusDays(1))
        assertThat(snap.dayKey).isEqualTo("B")
        assertThat(snap.sets).hasSize(1)
    }
}
