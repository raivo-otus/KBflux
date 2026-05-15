package com.kbminisplit.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
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
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun settingsDao(): SettingsDao
    abstract fun inProgressDao(): InProgressDao

    companion object {
        const val DB_NAME = "kbminisplit.db"
        const val DB_VERSION = 1
    }
}
