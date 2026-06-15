package com.kbminisplit.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kbminisplit.data.entity.ExerciseEntity
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity

/**
 * v1 ships with version = 1 and no migrations.
 *
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

    companion object {
        const val DB_NAME = "kbminisplit.db"
        const val DB_VERSION = 5

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
    }
}
