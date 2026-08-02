package com.kbminisplit.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kbminisplit.data.entity.ExerciseEntity
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.entity.ProgramDayEntity
import com.kbminisplit.data.entity.ProgramGroupEntity
import com.kbminisplit.data.entity.ProgramItemEntity
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity

/**
 * Migration policy: every schema change bumps [DB_VERSION] and adds an explicit
 * [androidx.room.migration.Migration]. We do not use destructive migrations in
 * release builds — losing a user's training history is worse than crashing on
 * first launch of a buggy build.
 */
@Database(
    entities = [
        ExerciseEntity::class,
        UserSettingsEntity::class,
        StartingWeightEntity::class,
        SessionEntity::class,
        SetEntryEntity::class,
        InProgressSessionEntity::class,
        InProgressSetEntity::class,
        ProgramDayEntity::class,
        ProgramGroupEntity::class,
        ProgramItemEntity::class,
    ],
    version = AppDatabase.DB_VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3)
    ],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun inProgressDao(): InProgressDao
    abstract fun programDao(): ProgramDao

    companion object {
        const val DB_NAME = "kbminisplit.db"
        const val DB_VERSION = 7

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Update slug in set_entry
                db.execSQL("UPDATE set_entry SET exerciseSlug = 'romanian_deadlift' WHERE exerciseSlug = 'deadlift'")
                // 2. Update slug in starting_weight
                db.execSQL("UPDATE starting_weight SET exerciseSlug = 'romanian_deadlift' WHERE exerciseSlug = 'deadlift'")
                // 3. Update slug in in_progress_set
                db.execSQL("UPDATE in_progress_set SET exerciseSlug = 'romanian_deadlift' WHERE exerciseSlug = 'deadlift'")

                // 4. Drop deadliftMaxReps from user_settings
                db.execSQL("""
                    CREATE TABLE user_settings_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        onboardedAt INTEGER,
                        kbWeightKg REAL,
                        startingTargetReps INTEGER,
                        standardMaxReps INTEGER,
                        kbBumpSnoozedAtMonth TEXT,
                        kbBumpSnoozeSessionCount INTEGER,
                        isDarkMode INTEGER,
                        hapticLevel INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO user_settings_new (id, onboardedAt, kbWeightKg, startingTargetReps, standardMaxReps, kbBumpSnoozedAtMonth, kbBumpSnoozeSessionCount, isDarkMode, hapticLevel)
                    SELECT id, onboardedAt, kbWeightKg, startingTargetReps, standardMaxReps, kbBumpSnoozedAtMonth, kbBumpSnoozeSessionCount, isDarkMode, hapticLevel FROM user_settings
                """.trimIndent())
                db.execSQL("DROP TABLE user_settings")
                db.execSQL("ALTER TABLE user_settings_new RENAME TO user_settings")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE session SET feedback = 'Green' WHERE feedback = 'Ideal session'")
            }
        }

        // Bodyweight tracking for assisted movements (e.g. Assisted Dips). Adds the
        // current weekly bodyweight + its timestamp to user_settings, and a
        // per-session bodyweight snapshot (mirroring kbWeightKg) to session. All
        // nullable — existing rows keep NULL until the next check-in.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN bodyweightKg REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN bodyweightLoggedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE session ADD COLUMN bodyweightKg REAL DEFAULT NULL")
            }
        }

        /**
         * User-defined programs. Adds the three program tables, widens set entries
         * to carry a rep range and the order they were performed in, and adds the
         * rest-week counters.
         *
         * The two in-progress tables are dropped and rebuilt rather than migrated:
         * their rows are now addressed by program item instead of by exercise
         * slug, and a half-finished session is ephemeral data that the Tracker
         * re-bootstraps on the next open. Committed history is untouched.
         *
         * This migration only creates structure. The default program is written by
         * [seedDefaultProgram] on the same open, where it can carry over the user's
         * starting weights in Kotlin rather than in SQL.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `program_day` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dayKey` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_program_day_dayKey` " +
                        "ON `program_day` (`dayKey`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `program_group` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dayId` INTEGER NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `rotates` INTEGER NOT NULL, " +
                        "`isDeferred` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, " +
                        "`circuitSlug` TEXT, `weightKg` REAL, `usesLadder` INTEGER NOT NULL, " +
                        "`weightChangedAt` INTEGER, `bumpSnoozedAt` INTEGER, " +
                        "FOREIGN KEY(`dayId`) REFERENCES `program_day`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_program_group_dayId` " +
                        "ON `program_group` (`dayId`)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `program_item` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, `exerciseSlug` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `sets` INTEGER NOT NULL, " +
                        "`minReps` INTEGER NOT NULL, `maxReps` INTEGER NOT NULL, " +
                        "`leadInSets` INTEGER NOT NULL, `weightStepKg` REAL NOT NULL, " +
                        "`isAssisted` INTEGER NOT NULL, `isPerSide` INTEGER NOT NULL, " +
                        "`currentWeightKg` REAL NOT NULL, " +
                        "FOREIGN KEY(`groupId`) REFERENCES `program_group`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`exerciseSlug`) REFERENCES `exercise`(`slug`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_program_item_groupId` " +
                        "ON `program_item` (`groupId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_program_item_exerciseSlug` " +
                        "ON `program_item` (`exerciseSlug`)",
                )

                db.execSQL("ALTER TABLE set_entry ADD COLUMN targetRepsMax INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE set_entry ADD COLUMN position INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "ALTER TABLE user_settings " +
                        "ADD COLUMN restWeekAnchorSessions INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE user_settings " +
                        "ADD COLUMN restWeekSnoozedAtSessions INTEGER DEFAULT NULL",
                )

                db.execSQL("DROP TABLE IF EXISTS `in_progress_set`")
                db.execSQL("DROP TABLE IF EXISTS `in_progress_session`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `in_progress_session` (" +
                        "`id` INTEGER NOT NULL, `date` TEXT NOT NULL, `dayKey` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `in_progress_set` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`programGroupId` INTEGER NOT NULL, `programItemId` INTEGER NOT NULL, " +
                        "`exerciseSlug` TEXT NOT NULL, `setIndex` INTEGER NOT NULL, " +
                        "`isPriming` INTEGER NOT NULL, `targetReps` INTEGER, " +
                        "`targetRepsMax` INTEGER, `weightKg` REAL NOT NULL, `state` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`exerciseSlug`) REFERENCES `exercise`(`slug`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT )",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_in_progress_set_programGroupId_programItemId_setIndex_isPriming` " +
                        "ON `in_progress_set` (`programGroupId`, `programItemId`, `setIndex`, `isPriming`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_in_progress_set_exerciseSlug` " +
                        "ON `in_progress_set` (`exerciseSlug`)",
                )
            }
        }
    }
}
