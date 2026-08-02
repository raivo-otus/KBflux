package com.kbminisplit.data.repository

import com.kbminisplit.data.db.InProgressDao
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.mapper.toDomain
import com.kbminisplit.data.mapper.toEntity
import com.kbminisplit.domain.model.InProgressSet
import com.kbminisplit.domain.model.SetStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class InProgressSnapshot(
    val date: LocalDate,
    val dayKey: String,
    val sets: List<InProgressSet>,
)

@Singleton
class InProgressRepository @Inject constructor(
    private val inProgressDao: InProgressDao,
) {

    fun observe(): Flow<InProgressSnapshot?> =
        combine(inProgressDao.observeSession(), inProgressDao.observeSets()) { session, sets ->
            if (session == null) {
                null
            } else {
                InProgressSnapshot(
                    date = LocalDate.parse(session.date),
                    dayKey = session.dayKey,
                    sets = sets.map { it.toDomain() },
                )
            }
        }

    suspend fun get(): InProgressSnapshot? {
        val session = inProgressDao.getSession() ?: return null
        return InProgressSnapshot(
            date = LocalDate.parse(session.date),
            dayKey = session.dayKey,
            sets = inProgressDao.getSets().map { it.toDomain() },
        )
    }

    suspend fun start(date: LocalDate, dayKey: String, sets: List<InProgressSet>) {
        inProgressDao.replace(
            session = InProgressSessionEntity(date = date.toString(), dayKey = dayKey),
            sets = sets.map { it.toEntity() },
        )
    }

    /**
     * Appends sets without disturbing existing rows. Used to reveal a deferred
     * group once the earlier ones are resolved; the new rows belong to a group
     * that has none yet, so the unique slot index is not violated.
     */
    suspend fun addSets(sets: List<InProgressSet>) {
        inProgressDao.upsertSets(sets.map { it.toEntity() })
    }

    suspend fun updateSetState(id: Long, state: SetStatus) {
        inProgressDao.updateState(id, state.name)
    }

    suspend fun updateItemWeight(programItemId: Long, weightKg: Double) {
        inProgressDao.updateItemWeight(programItemId, weightKg)
    }

    suspend fun updateCircuitWeight(programGroupId: Long, weightKg: Double) {
        inProgressDao.updateCircuitWeight(programGroupId, weightKg)
    }

    suspend fun clear() = inProgressDao.clear()
}
