package com.kbminisplit.data.di

import android.content.Context
import androidx.room.Room
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.DatabaseSeedCallback
import com.kbminisplit.data.db.ExerciseDao
import com.kbminisplit.data.db.InProgressDao
import com.kbminisplit.data.db.ProgramDao
import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.db.SettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        databaseProvider: Provider<AppDatabase>,
        clock: Clock,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addCallback(
                DatabaseSeedCallback(
                    database = { databaseProvider.get() },
                    nowMillis = clock::millis,
                ),
            )
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
            )
            .build()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideSettingsDao(db: AppDatabase): SettingsDao = db.settingsDao()

    @Provides
    fun provideInProgressDao(db: AppDatabase): InProgressDao = db.inProgressDao()

    @Provides
    fun provideProgramDao(db: AppDatabase): ProgramDao = db.programDao()
}
