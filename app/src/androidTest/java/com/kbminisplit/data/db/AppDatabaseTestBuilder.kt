package com.kbminisplit.data.db

import android.content.Context
import androidx.room.Room

/**
 * Builds an in-memory [AppDatabase] for instrumented tests. No seed callback —
 * tests that need the exercise registry or the default program call
 * [seedExerciseRegistry] and [seedDefaultProgram] explicitly inside a
 * `runBlocking` block. Keeping the seed out of the builder lets tests assert on a
 * known starting state.
 */
fun buildInMemoryDatabase(context: Context): AppDatabase =
    Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
