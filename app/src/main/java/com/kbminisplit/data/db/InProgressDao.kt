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

    // Session order: movements in the order they were programmed for today (after
    // rotation), then prime and warm-up ahead of the working sets.
    @Query("SELECT * FROM in_progress_set ORDER BY position ASC, isPriming DESC, setIndex ASC")
    abstract fun observeSets(): Flow<List<InProgressSetEntity>>

    @Query("SELECT * FROM in_progress_set ORDER BY position ASC, isPriming DESC, setIndex ASC")
    abstract suspend fun getSets(): List<InProgressSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSession(session: InProgressSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSets(sets: List<InProgressSetEntity>)

    @Query("UPDATE in_progress_set SET state = :state WHERE id = :id")
    abstract suspend fun updateState(id: Long, state: String)

    /** Mid-session weight correction for one movement, across all of its sets. */
    @Query("UPDATE in_progress_set SET weightKg = :weightKg WHERE programItemId = :programItemId")
    abstract suspend fun updateItemWeight(programItemId: Long, weightKg: Double)

    /** Mid-session weight correction for a circuit group's round rows. */
    @Query(
        "UPDATE in_progress_set SET weightKg = :weightKg " +
            "WHERE programGroupId = :programGroupId AND programItemId = 0",
    )
    abstract suspend fun updateCircuitWeight(programGroupId: Long, weightKg: Double)

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
