package com.kbminisplit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.entity.InProgressSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InProgressDao {

    @Query("SELECT * FROM in_progress_session WHERE id = ${InProgressSessionEntity.SINGLETON_ID}")
    abstract fun observeSession(): Flow<InProgressSessionEntity?>

    @Query("SELECT * FROM in_progress_session WHERE id = ${InProgressSessionEntity.SINGLETON_ID}")
    abstract suspend fun getSession(): InProgressSessionEntity?

    @Query("SELECT * FROM in_progress_set ORDER BY exerciseSlug, isPriming DESC, setIndex ASC")
    abstract fun observeSets(): Flow<List<InProgressSetEntity>>

    @Query("SELECT * FROM in_progress_set ORDER BY exerciseSlug, isPriming DESC, setIndex ASC")
    abstract suspend fun getSets(): List<InProgressSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSession(session: InProgressSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSets(sets: List<InProgressSetEntity>)

    @Query("UPDATE in_progress_set SET state = :state WHERE exerciseSlug = :exerciseSlug AND setIndex = :setIndex AND isPriming = :isPriming")
    abstract suspend fun updateState(
        exerciseSlug: String,
        setIndex: Int,
        isPriming: Boolean,
        state: String,
    )

    @Query("UPDATE in_progress_session SET kbWeightKg = :kbWeightKg WHERE id = ${InProgressSessionEntity.SINGLETON_ID}")
    abstract suspend fun updateKbWeight(kbWeightKg: Double)

    @Query("UPDATE in_progress_set SET weightKg = :weightKg, targetReps = :targetReps WHERE exerciseSlug = :exerciseSlug")
    abstract suspend fun updateExerciseWeightAndReps(exerciseSlug: String, weightKg: Double, targetReps: Int?)

    @Query("UPDATE in_progress_set SET weightKg = :weightKg WHERE exerciseSlug = :exerciseSlug")
    abstract suspend fun updateExerciseWeight(exerciseSlug: String, weightKg: Double)

    @Query("DELETE FROM in_progress_set")
    abstract suspend fun deleteAllSets()

    @Query("DELETE FROM in_progress_session")
    abstract suspend fun deleteSession()

    @Transaction
    open suspend fun clear() {
        deleteAllSets()
        deleteSession()
    }

    @Transaction
    open suspend fun replace(session: InProgressSessionEntity, sets: List<InProgressSetEntity>) {
        clear()
        upsertSession(session)
        upsertSets(sets)
    }
}
