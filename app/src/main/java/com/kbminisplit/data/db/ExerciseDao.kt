package com.kbminisplit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    /**
     * Insert-if-absent, not upsert: the registry is user-editable (a movement can
     * be renamed from the Program tab) and the seed runs on every database open,
     * so upserting would silently undo renames on the next launch.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("UPDATE exercise SET displayName = :displayName WHERE slug = :slug")
    suspend fun rename(slug: String, displayName: String)

    @Query("DELETE FROM exercise")
    suspend fun clear()
}
