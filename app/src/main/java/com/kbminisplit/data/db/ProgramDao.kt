package com.kbminisplit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kbminisplit.data.entity.ProgramDayEntity
import com.kbminisplit.data.entity.ProgramGroupEntity
import com.kbminisplit.data.entity.ProgramItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * The program is read as three flat, ordered lists and assembled into the nested
 * [com.kbminisplit.domain.model.Program] in the repository — the same shape as
 * [InProgressDao], and cheaper than a relation query for a handful of rows.
 *
 * Updates are always in-place: a program item carries the user's accumulated
 * working weight, so delete-and-reinsert would silently reset it.
 */
@Dao
abstract class ProgramDao {

    @Query("SELECT * FROM program_day ORDER BY position ASC")
    abstract fun observeDays(): Flow<List<ProgramDayEntity>>

    @Query("SELECT * FROM program_group ORDER BY position ASC")
    abstract fun observeGroups(): Flow<List<ProgramGroupEntity>>

    @Query("SELECT * FROM program_item ORDER BY position ASC")
    abstract fun observeItems(): Flow<List<ProgramItemEntity>>

    @Query("SELECT * FROM program_day ORDER BY position ASC")
    abstract suspend fun getDays(): List<ProgramDayEntity>

    @Query("SELECT * FROM program_group ORDER BY position ASC")
    abstract suspend fun getGroups(): List<ProgramGroupEntity>

    @Query("SELECT * FROM program_item ORDER BY position ASC")
    abstract suspend fun getItems(): List<ProgramItemEntity>

    @Query("SELECT COUNT(*) FROM program_day")
    abstract suspend fun dayCount(): Int

    @Query("SELECT * FROM program_item WHERE id = :id")
    abstract suspend fun getItem(id: Long): ProgramItemEntity?

    @Query("SELECT * FROM program_group WHERE id = :id")
    abstract suspend fun getGroup(id: Long): ProgramGroupEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertDay(day: ProgramDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertGroup(group: ProgramGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertItem(item: ProgramItemEntity): Long

    @Update
    abstract suspend fun updateDay(day: ProgramDayEntity)

    @Update
    abstract suspend fun updateGroup(group: ProgramGroupEntity)

    @Update
    abstract suspend fun updateItem(item: ProgramItemEntity)

    @Query("DELETE FROM program_day WHERE id = :id")
    abstract suspend fun deleteDay(id: Long)

    @Query("DELETE FROM program_group WHERE id = :id")
    abstract suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM program_item WHERE id = :id")
    abstract suspend fun deleteItem(id: Long)

    @Query("UPDATE program_day SET position = :position WHERE id = :id")
    abstract suspend fun setDayPosition(id: Long, position: Int)

    @Query("UPDATE program_group SET position = :position WHERE id = :id")
    abstract suspend fun setGroupPosition(id: Long, position: Int)

    @Query("UPDATE program_item SET position = :position WHERE id = :id")
    abstract suspend fun setItemPosition(id: Long, position: Int)

    @Query("UPDATE program_item SET currentWeightKg = :weightKg WHERE id = :id")
    abstract suspend fun setItemWeight(id: Long, weightKg: Double)

    /** Changing a circuit's weight restarts its ladder clock and clears any snooze. */
    @Query(
        "UPDATE program_group SET weightKg = :weightKg, weightChangedAt = :changedAt, " +
            "bumpSnoozedAt = NULL WHERE id = :id",
    )
    abstract suspend fun setGroupWeight(id: Long, weightKg: Double, changedAt: Long)

    @Query("UPDATE program_group SET bumpSnoozedAt = :snoozedAt WHERE id = :id")
    abstract suspend fun setGroupBumpSnoozed(id: Long, snoozedAt: Long?)

    @Query("DELETE FROM program_day")
    abstract suspend fun deleteAllDays()

    /**
     * Rewrite positions from an explicit ordering, so a reorder always lands on a
     * dense 0..n-1 sequence instead of accumulating gaps from repeated swaps.
     */
    @Transaction
    open suspend fun applyDayOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setDayPosition(id, index) }
    }

    @Transaction
    open suspend fun applyGroupOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setGroupPosition(id, index) }
    }

    @Transaction
    open suspend fun applyItemOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setItemPosition(id, index) }
    }

    /** Applies a whole set of new working weights atomically (the rest-week deload). */
    @Transaction
    open suspend fun applyItemWeights(weightsByItemId: Map<Long, Double>) {
        weightsByItemId.forEach { (id, weightKg) -> setItemWeight(id, weightKg) }
    }

    /** Groups and items cascade from their day, so one delete clears the program. */
    @Transaction
    open suspend fun clear() {
        deleteAllDays()
    }
}
