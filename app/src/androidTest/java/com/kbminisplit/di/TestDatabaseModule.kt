package com.kbminisplit.di

import android.content.Context
import androidx.room.Room
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.DatabaseSeedCallback
import com.kbminisplit.data.db.ExerciseDao
import com.kbminisplit.data.db.InProgressDao
import com.kbminisplit.data.db.ProgramDao
import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.db.SettingsDao
import com.kbminisplit.data.di.DatabaseModule
import com.kbminisplit.data.di.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Provider
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneOffset.UTC)

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
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(
                DatabaseSeedCallback(
                    database = { databaseProvider.get() },
                    nowMillis = clock::millis,
                ),
            )
            .allowMainThreadQueries()
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
