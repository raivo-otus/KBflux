package com.kbminisplit.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.buildInMemoryDatabase
import com.kbminisplit.domain.model.GroupKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ProgramRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ProgramRepository

    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
        repo = ProgramRepository(db.programDao(), db.exerciseDao(), fixedClock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A day with one standard group, returning (dayId, groupId). */
    private suspend fun dayWithGroup(name: String = "Push"): Pair<Long, Long> {
        val dayId = repo.addDay(name)
        val groupId = repo.addGroup(dayId, "Main", GroupKind.STANDARD)
        return dayId to groupId
    }

    @Test
    fun adding_a_day_assigns_a_unique_key_and_the_next_position() {
        runBlocking {
            repo.addDay("Pull")
            repo.addDay("Push")

            val days = repo.getProgram().days
            assertThat(days.map { it.key }).containsExactly("A", "B").inOrder()
            assertThat(days.map { it.position }).containsExactly(0, 1).inOrder()
        }
    }

    @Test
    fun adding_a_movement_registers_the_exercise_and_keeps_every_field() {
        runBlocking {
            val (_, groupId) = dayWithGroup()

            repo.addItem(
                groupId = groupId,
                name = "Bench Press",
                sets = 4,
                minReps = 5,
                maxReps = 8,
                leadInSets = 1,
                weightStepKg = 5.0,
                isAssisted = true,
                isPerSide = true,
                currentWeightKg = 70.0,
            )

            val item = repo.getProgram().days.single().groups.single().items.single()
            assertThat(item.name).isEqualTo("Bench Press")
            assertThat(item.exerciseSlug).isEqualTo("bench_press")
            assertThat(item.sets).isEqualTo(4)
            assertThat(item.minReps).isEqualTo(5)
            assertThat(item.maxReps).isEqualTo(8)
            assertThat(item.leadInSets).isEqualTo(1)
            assertThat(item.weightStepKg).isEqualTo(5.0)
            assertThat(item.isAssisted).isTrue()
            assertThat(item.isPerSide).isTrue()
            assertThat(item.currentWeightKg).isEqualTo(70.0)
        }
    }

    @Test
    fun the_same_movement_name_on_two_days_shares_one_slug() {
        runBlocking {
            val (_, groupA) = dayWithGroup("Pull")
            val (_, groupB) = dayWithGroup("Push")

            repo.addItem(groupA, "Bench Press", currentWeightKg = 60.0)
            repo.addItem(groupB, "bench press", currentWeightKg = 80.0)

            val items = repo.getProgram().days.flatMap { it.groups }.flatMap { it.items }
            assertThat(items.map { it.exerciseSlug }.distinct()).containsExactly("bench_press")
            // Sharing an identity does not mean sharing a weight.
            assertThat(items.map { it.currentWeightKg }).containsExactly(60.0, 80.0)
        }
    }

    @Test
    fun renaming_a_movement_follows_it_everywhere_including_history() {
        runBlocking {
            val (_, groupId) = dayWithGroup()
            val itemId = repo.addItem(groupId, "Bench Press", currentWeightKg = 60.0)

            repo.updateItem(
                itemId = itemId,
                name = "Incline Bench",
                sets = 3,
                minReps = 8,
                maxReps = 12,
                leadInSets = 2,
                weightStepKg = 2.5,
                isAssisted = false,
                isPerSide = false,
                currentWeightKg = 60.0,
            )

            val item = repo.getProgram().days.single().groups.single().items.single()
            assertThat(item.name).isEqualTo("Incline Bench")
            // The slug is untouched, so already-logged sets still resolve.
            assertThat(item.exerciseSlug).isEqualTo("bench_press")
            assertThat(db.exerciseDao().getBySlug("bench_press")?.displayName)
                .isEqualTo("Incline Bench")
        }
    }

    @Test
    fun editing_a_movement_preserves_its_accumulated_weight_row() {
        runBlocking {
            val (_, groupId) = dayWithGroup()
            val itemId = repo.addItem(groupId, "Bench Press", currentWeightKg = 60.0)
            repo.setItemWeight(itemId, 72.5)

            repo.updateItem(
                itemId = itemId,
                name = "Bench Press",
                sets = 5,
                minReps = 3,
                maxReps = 5,
                leadInSets = 2,
                weightStepKg = 2.5,
                isAssisted = false,
                isPerSide = false,
                currentWeightKg = 72.5,
            )

            val item = repo.getProgram().days.single().groups.single().items.single()
            assertThat(item.id).isEqualTo(itemId)
            assertThat(item.currentWeightKg).isEqualTo(72.5)
        }
    }

    @Test
    fun moving_a_movement_reorders_it_and_keeps_positions_dense() {
        runBlocking {
            val (_, groupId) = dayWithGroup()
            repo.addItem(groupId, "A")
            val b = repo.addItem(groupId, "B")
            repo.addItem(groupId, "C")

            repo.moveItem(b, -1)

            val items = repo.getProgram().days.single().groups.single().items
            assertThat(items.map { it.name }).containsExactly("B", "A", "C").inOrder()
            assertThat(items.map { it.position }).containsExactly(0, 1, 2).inOrder()
        }
    }

    @Test
    fun moving_past_either_end_is_a_no_op() {
        runBlocking {
            val (_, groupId) = dayWithGroup()
            val a = repo.addItem(groupId, "A")
            val b = repo.addItem(groupId, "B")

            repo.moveItem(a, -1)
            repo.moveItem(b, 1)

            val items = repo.getProgram().days.single().groups.single().items
            assertThat(items.map { it.name }).containsExactly("A", "B").inOrder()
        }
    }

    @Test
    fun deleting_a_movement_closes_the_gap_in_positions() {
        runBlocking {
            val (_, groupId) = dayWithGroup()
            repo.addItem(groupId, "A")
            val b = repo.addItem(groupId, "B")
            repo.addItem(groupId, "C")

            repo.deleteItem(b)

            val items = repo.getProgram().days.single().groups.single().items
            assertThat(items.map { it.name }).containsExactly("A", "C").inOrder()
            assertThat(items.map { it.position }).containsExactly(0, 1).inOrder()
        }
    }

    @Test
    fun deleting_a_day_takes_its_groups_and_movements_with_it() {
        runBlocking {
            val (dayId, groupId) = dayWithGroup()
            repo.addItem(groupId, "Bench Press")

            repo.deleteDay(dayId)

            assertThat(repo.getProgram().isEmpty).isTrue()
            assertThat(db.programDao().getGroups()).isEmpty()
            assertThat(db.programDao().getItems()).isEmpty()
            // The registry keeps the movement so history still renders.
            assertThat(db.exerciseDao().getBySlug("bench_press")).isNotNull()
        }
    }

    @Test
    fun a_circuit_group_gets_its_own_sentinel_exercise() {
        runBlocking {
            val dayId = repo.addDay("Push")

            repo.addGroup(dayId, "Kettlebell flow", GroupKind.CIRCUIT)

            val group = repo.getProgram().days.single().groups.single()
            assertThat(group.isCircuit).isTrue()
            assertThat(group.circuitSlug).isNotNull()
            assertThat(db.exerciseDao().getBySlug(group.circuitSlug!!)).isNotNull()
        }
    }

    @Test
    fun setting_a_circuit_weight_restarts_the_ladder_clock_and_clears_a_snooze() {
        runBlocking {
            val dayId = repo.addDay("Push")
            val groupId = repo.addGroup(dayId, "Kettlebell flow", GroupKind.CIRCUIT)
            repo.snoozeGroupBump(groupId)

            repo.setGroupWeight(groupId, 20.0)

            val group = repo.getProgram().days.single().groups.single()
            assertThat(group.weightKg).isEqualTo(20.0)
            assertThat(group.weightChangedAt).isEqualTo(fixedClock.millis())
            assertThat(group.bumpSnoozedAt).isNull()
        }
    }

    @Test
    fun a_deload_moves_every_movement_one_step_easier_at_once() {
        runBlocking {
            val (_, groupId) = dayWithGroup()
            repo.addItem(groupId, "Bench", weightStepKg = 2.5, currentWeightKg = 60.0)
            repo.addItem(groupId, "Pulldown", weightStepKg = 5.0, currentWeightKg = 55.0)
            repo.addItem(groupId, "Dips", weightStepKg = 2.5, isAssisted = true, currentWeightKg = 40.0)
            repo.addItem(groupId, "Back Extension", weightStepKg = 2.0, currentWeightKg = 0.0)

            repo.deloadAllItems()

            val byName = repo.getProgram().days.single().groups.single().items
                .associate { it.name to it.currentWeightKg }
            assertThat(byName["Bench"]).isEqualTo(57.5)
            assertThat(byName["Pulldown"]).isEqualTo(50.0)
            // Assisted goes the other way: more assistance is easier.
            assertThat(byName["Dips"]).isEqualTo(42.5)
            // Never below zero.
            assertThat(byName["Back Extension"]).isEqualTo(0.0)
        }
    }

    @Test
    fun group_edits_round_trip() {
        runBlocking {
            val (_, groupId) = dayWithGroup()

            repo.updateGroup(
                groupId = groupId,
                name = "Accessories",
                rotates = false,
                isDeferred = true,
            )

            val group = repo.getProgram().days.single().groups.single()
            assertThat(group.name).isEqualTo("Accessories")
            assertThat(group.rotates).isFalse()
            assertThat(group.isDeferred).isTrue()
        }
    }
}
