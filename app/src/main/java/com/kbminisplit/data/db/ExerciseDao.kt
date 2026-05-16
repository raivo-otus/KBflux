package com.kbminisplit.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kbminisplit.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercise ORDER BY slug")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise ORDER BY slug")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): ExerciseEntity?

    @Upsert
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercise")
    suspend fun clear()
}
