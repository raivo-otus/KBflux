package com.kbminisplit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kbminisplit.data.entity.StartingWeightEntity
import com.kbminisplit.data.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    @Query("SELECT * FROM user_settings WHERE id = 0")
    fun observe(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 0")
    suspend fun get(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: UserSettingsEntity)

    @Query("SELECT * FROM starting_weight")
    fun observeStartingWeights(): Flow<List<StartingWeightEntity>>

    @Query("SELECT * FROM starting_weight")
    suspend fun getStartingWeights(): List<StartingWeightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStartingWeights(weights: List<StartingWeightEntity>)
}
