package com.kbminisplit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.entity.SessionWithSets
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionDao {

    @Transaction
    @Query("SELECT * FROM session ORDER BY date ASC")
    abstract fun observeAllWithSets(): Flow<List<SessionWithSets>>

    @Transaction
    @Query("SELECT * FROM session ORDER BY date ASC")
    abstract suspend fun getAllWithSets(): List<SessionWithSets>

    @Query("SELECT * FROM session ORDER BY date ASC")
    abstract fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session ORDER BY date ASC")
    abstract suspend fun getAll(): List<SessionEntity>

    @Transaction
    @Query("SELECT * FROM session WHERE date = :date LIMIT 1")
    abstract suspend fun getByDateWithSets(date: String): SessionWithSets?

    @Query("SELECT * FROM session WHERE date = :date LIMIT 1")
    abstract suspend fun getByDate(date: String): SessionEntity?

    @Transaction
    @Query("SELECT * FROM session WHERE date BETWEEN :start AND :endInclusive ORDER BY date ASC")
    abstract fun observeBetweenWithSets(start: String, endInclusive: String): Flow<List<SessionWithSets>>

    @Query("SELECT * FROM set_entry WHERE sessionId = :sessionId ORDER BY isPriming DESC, setIndex ASC")
    abstract suspend fun getSetsForSession(sessionId: Long): List<SetEntryEntity>

    @Query("SELECT * FROM set_entry ORDER BY sessionId ASC, isPriming DESC, setIndex ASC")
    abstract suspend fun getAllSets(): List<SetEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSessions(sessions: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertSets(sets: List<SetEntryEntity>)

    @Transaction
    open suspend fun insertSessionWithSets(session: SessionEntity, sets: List<SetEntryEntity>): Long {
        val id = insertSession(session)
        insertSets(sets.map { it.copy(sessionId = id) })
        return id
    }

    @Query("DELETE FROM session")
    abstract suspend fun deleteAllSessions()

    @Query("DELETE FROM set_entry")
    abstract suspend fun deleteAllSets()

    @Transaction
    open suspend fun clear() {
        deleteAllSets()
        deleteAllSessions()
    }
}
