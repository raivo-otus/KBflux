package com.kbminisplit.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the upgrade to user-defined programs.
 *
 * The point of this migration is that an existing install lands on exactly the
 * program it was already following, with the weights it was already using — so
 * these tests care much more about what survives than about what is created.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /** A v6 database holding a realistic mid-program install. */
    private fun seedV6(): SupportSQLiteDatabase = helper.createDatabase(TEST_DB, 6).apply {
        execSQL(
            "INSERT INTO exercise (slug, displayName, category, isPerSide, weightStepKg, minReps, maxReps) " +
                "VALUES ('bench', 'Bench Press', 'B', 0, 2.5, 8, 16), " +
                "('kb_flow', 'KB Flow', 'KB', 0, 2.0, 8, 16)",
        )
        execSQL(
            "INSERT INTO user_settings " +
                "(id, onboardedAt, kbWeightKg, startingTargetReps, standardMaxReps, " +
                "kbBumpSnoozedAtMonth, kbBumpSnoozeSessionCount, isDarkMode, hapticLevel, " +
                "bodyweightKg, bodyweightLoggedAt) " +
                "VALUES (0, 1000, 20.0, 8, 15, NULL, NULL, 1, 2, 82.0, 5000)",
        )
        execSQL("INSERT INTO starting_weight (exerciseSlug, weightKg) VALUES ('bench', 82.5)")
        execSQL(
            "INSERT INTO session (id, date, split, feedback, kbWeightKg, completedAt, bodyweightKg) " +
                "VALUES (1, '2026-02-01', 'B', 'Green', 20.0, 9000, 82.0)",
        )
        execSQL(
            "INSERT INTO set_entry " +
                "(id, sessionId, exerciseSlug, setIndex, isPriming, targetReps, weightKg, status) " +
                "VALUES (1, 1, 'bench', 1, 0, 11, 82.5, 'Completed')",
        )
        // A half-finished session, which the migration is allowed to discard.
        execSQL(
            "INSERT INTO in_progress_session (id, date, split, kbWeightKg) " +
                "VALUES (0, '2026-02-03', 'C', 20.0)",
        )
        execSQL(
            "INSERT INTO in_progress_set " +
                "(id, exerciseSlug, setIndex, isPriming, targetReps, weightKg, state) " +
                "VALUES (1, 'bench', 1, 0, 11, 82.5, 'Pending')",
        )
        close()
    }

    private fun migrate(): SupportSQLiteDatabase {
        seedV6()
        return helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)
    }

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun migrate6To7_matches_the_generated_schema() {
        // runMigrationsAndValidate throws if the resulting schema differs from v7.
        migrate().close()
    }

    @Test
    fun committed_history_survives_untouched() {
        migrate().use { db ->
            db.query("SELECT date, split, feedback, kbWeightKg FROM session").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("2026-02-01")
                assertThat(c.getString(1)).isEqualTo("B")
                assertThat(c.getString(2)).isEqualTo("Green")
                assertThat(c.getDouble(3)).isEqualTo(20.0)
            }
        }
    }

    @Test
    fun existing_sets_gain_a_null_rep_ceiling_and_a_zero_position() {
        migrate().use { db ->
            db.query("SELECT targetReps, targetRepsMax, position FROM set_entry").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(11)
                assertThat(c.isNull(1)).isTrue()
                assertThat(c.getInt(2)).isEqualTo(0)
            }
        }
    }

    @Test
    fun the_rest_week_counters_start_from_zero() {
        migrate().use { db ->
            db.query(
                "SELECT restWeekAnchorSessions, restWeekSnoozedAtSessions FROM user_settings",
            ).use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getInt(0)).isEqualTo(0)
                assertThat(c.isNull(1)).isTrue()
            }
        }
    }

    @Test
    fun the_program_tables_are_created_empty_for_the_seed_to_fill() {
        migrate().use { db ->
            assertThat(db.count("program_day")).isEqualTo(0)
            assertThat(db.count("program_group")).isEqualTo(0)
            assertThat(db.count("program_item")).isEqualTo(0)
        }
    }

    @Test
    fun the_half_finished_session_is_dropped_rather_than_migrated() {
        migrate().use { db ->
            assertThat(db.count("in_progress_session")).isEqualTo(0)
            assertThat(db.count("in_progress_set")).isEqualTo(0)
        }
    }

    @Test
    fun opening_the_migrated_database_seeds_the_program_from_the_old_settings() {
        migrate().close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        try {
            runBlocking {
                // The callback normally does this on open; call it directly so the
                // test doesn't race a background coroutine.
                seedExerciseRegistry(db)
                seedDefaultProgram(db, nowMillis = 123_456L)

                assertThat(db.programDao().getDays().map { it.dayKey })
                    .containsExactly("A", "B", "C").inOrder()

                // The user's own numbers came across.
                val bench = db.programDao().getItems().first { it.exerciseSlug == "bench" }
                assertThat(bench.currentWeightKg).isEqualTo(82.5)
                assertThat(bench.maxReps).isEqualTo(15)

                val circuit = db.programDao().getGroups().first { it.kind == "CIRCUIT" }
                assertThat(circuit.weightKg).isEqualTo(20.0)

                // History still resolves to a program day.
                assertThat(db.programDao().getDays().map { it.dayKey }).contains("B")
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
