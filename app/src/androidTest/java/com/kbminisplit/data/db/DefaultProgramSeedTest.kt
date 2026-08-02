package com.kbminisplit.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultProgramSeedTest {

    private lateinit var db: AppDatabase

    private val now = 1_000_000L

    @Before
    fun setUp() {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun registry_covers_every_slug_the_app_has_ever_logged() {
        runBlocking {
            seedExerciseRegistry(db)

            val slugs = db.exerciseDao().getAll().map { it.slug }
            assertThat(slugs).containsAtLeastElementsIn(REGISTRY_SEED.keys)
            // Retired movements stay resolvable so old sessions still render.
            assertThat(slugs).contains("ohp")
            assertThat(slugs).contains(KB_FLOW_SLUG)
        }
    }

    @Test
    fun registry_seed_never_overwrites_a_rename() {
        runBlocking {
            seedExerciseRegistry(db)
            db.exerciseDao().rename("bench", "Incline Bench")

            seedExerciseRegistry(db)

            assertThat(db.exerciseDao().getBySlug("bench")?.displayName).isEqualTo("Incline Bench")
        }
    }

    @Test
    fun seed_builds_the_three_day_split() {
        runBlocking {
            seedExerciseRegistry(db)
            seedDefaultProgram(db, now)

            val days = db.programDao().getDays()
            assertThat(days.map { it.dayKey }).containsExactly("A", "B", "C").inOrder()
            assertThat(days.map { it.name }).containsExactly("Pull", "Push", "Legs").inOrder()
            assertThat(days.map { it.position }).containsExactly(0, 1, 2).inOrder()
        }
    }

    @Test
    fun each_day_has_a_circuit_a_main_block_and_a_deferred_accessory_block() {
        runBlocking {
            seedExerciseRegistry(db)
            seedDefaultProgram(db, now)

            val dayA = db.programDao().getDays().first { it.dayKey == "A" }
            val groups = db.programDao().getGroups().filter { it.dayId == dayA.id }

            assertThat(groups.map { it.kind })
                .containsExactly("CIRCUIT", "STANDARD", "STANDARD").inOrder()
            assertThat(groups[0].circuitSlug).isEqualTo(KB_FLOW_SLUG)
            assertThat(groups[0].usesLadder).isTrue()
            assertThat(groups[0].rounds).isEqualTo(3)
            assertThat(groups[1].rotates).isTrue()
            assertThat(groups[2].isDeferred).isTrue()
        }
    }

    @Test
    fun seed_carries_over_existing_starting_weights() {
        runBlocking {
            seedExerciseRegistry(db)
            db.settingsDao().upsert(
                UserSettingsEntity(onboardedAt = 1L, kbWeightKg = 20.0, standardMaxReps = 15),
            )
            db.settingsDao().upsertStartingWeights(
                listOf(
                    StartingWeightEntity("bench", 82.5),
                    StartingWeightEntity("high_bar_squat", 100.0),
                ),
            )

            seedDefaultProgram(db, now)

            val items = db.programDao().getItems()
            assertThat(items.first { it.exerciseSlug == "bench" }.currentWeightKg).isEqualTo(82.5)
            assertThat(items.first { it.exerciseSlug == "high_bar_squat" }.currentWeightKg)
                .isEqualTo(100.0)
            // A movement without a stored weight keeps its seed default.
            assertThat(items.first { it.exerciseSlug == "barbell_row" }.currentWeightKg)
                .isEqualTo(30.0)
        }
    }

    @Test
    fun seed_carries_over_the_kettlebell_size_and_rep_ceiling() {
        runBlocking {
            seedExerciseRegistry(db)
            db.settingsDao().upsert(
                UserSettingsEntity(onboardedAt = 1L, kbWeightKg = 20.0, standardMaxReps = 15),
            )

            seedDefaultProgram(db, now)

            val circuits = db.programDao().getGroups().filter { it.kind == "CIRCUIT" }
            assertThat(circuits.map { it.weightKg }).containsExactly(20.0, 20.0, 20.0)

            val bench = db.programDao().getItems().first { it.exerciseSlug == "bench" }
            assertThat(bench.minReps).isEqualTo(8)
            assertThat(bench.maxReps).isEqualTo(15)
        }
    }

    @Test
    fun circuit_rep_ranges_are_not_overridden_by_the_rep_ceiling() {
        runBlocking {
            seedExerciseRegistry(db)
            db.settingsDao().upsert(
                UserSettingsEntity(onboardedAt = 1L, kbWeightKg = 16.0, standardMaxReps = 15),
            )

            seedDefaultProgram(db, now)

            val swings = db.programDao().getItems().first { it.exerciseSlug == "swings" }
            assertThat(swings.minReps).isEqualTo(20)
            assertThat(swings.maxReps).isEqualTo(32)
        }
    }

    @Test
    fun accessories_seed_without_lead_in_sets() {
        runBlocking {
            seedExerciseRegistry(db)
            seedDefaultProgram(db, now)

            val fly = db.programDao().getItems().first { it.exerciseSlug == "side_delt_fly" }
            val bench = db.programDao().getItems().first { it.exerciseSlug == "bench" }
            assertThat(fly.leadInSets).isEqualTo(0)
            assertThat(bench.leadInSets).isEqualTo(2)
        }
    }

    @Test
    fun assisted_dips_seed_as_an_assisted_movement() {
        runBlocking {
            seedExerciseRegistry(db)
            seedDefaultProgram(db, now)

            val dips = db.programDao().getItems().first { it.exerciseSlug == "assisted_dip" }
            assertThat(dips.isAssisted).isTrue()
            assertThat(dips.currentWeightKg).isEqualTo(40.0)
        }
    }

    @Test
    fun seed_is_idempotent_and_never_clobbers_an_edited_program() {
        runBlocking {
            seedExerciseRegistry(db)
            seedDefaultProgram(db, now)
            val bench = db.programDao().getItems().first { it.exerciseSlug == "bench" }
            db.programDao().setItemWeight(bench.id, 999.0)

            seedDefaultProgram(db, now)
            seedDefaultProgram(db, now)

            assertThat(db.programDao().getDays()).hasSize(3)
            assertThat(db.programDao().getItem(bench.id)?.currentWeightKg).isEqualTo(999.0)
        }
    }

    @Test
    fun the_ladder_clock_starts_from_the_first_session_at_the_current_bell() {
        runBlocking {
            seedExerciseRegistry(db)
            db.settingsDao().upsert(UserSettingsEntity(onboardedAt = 1L, kbWeightKg = 16.0))
            // Two sessions on an older bell, then three on the current one.
            listOf(
                "2026-01-01" to 12.0,
                "2026-01-03" to 12.0,
                "2026-02-01" to 16.0,
                "2026-02-03" to 16.0,
                "2026-02-05" to 16.0,
            ).forEach { (date, kb) ->
                db.sessionDao().insertSession(
                    com.kbminisplit.data.entity.SessionEntity(
                        date = date,
                        dayKey = "A",
                        feedback = "Green",
                        circuitWeightKg = kb,
                        completedAt = 0L,
                    ),
                )
            }

            seedDefaultProgram(db, now)

            val circuit = db.programDao().getGroups().first { it.kind == "CIRCUIT" }
            val expected = java.time.LocalDate.parse("2026-02-01")
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            assertThat(circuit.weightChangedAt).isEqualTo(expected)
        }
    }
}
