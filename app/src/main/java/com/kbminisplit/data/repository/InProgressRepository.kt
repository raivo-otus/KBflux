package com.kbminisplit.data.repository

import com.kbminisplit.data.db.InProgressDao
import com.kbminisplit.data.entity.InProgressSessionEntity
import com.kbminisplit.data.mapper.toDomain
import com.kbminisplit.data.mapper.toInProgressEntity
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class InProgressSnapshot(
    val date: LocalDate,
    val split: Split,
    val kbWeightKg: Double,
    val sets: List<SetEntry>,
)

@Singleton
class InProgressRepository @Inject constructor(
    private val inProgressDao: InProgressDao,
) {

    fun observe(): Flow<InProgressSnapshot?> =
        combine(inProgressDao.observeSession(), inProgressDao.observeSets()) { session, sets ->
            if (session == null) null
            else InProgressSnapshot(
                date = LocalDate.parse(session.date),
                split = Split.valueOf(session.split),
                kbWeightKg = session.kbWeightKg,
                sets = sets.map { it.toDomain() },
            )
        }

    suspend fun get(): InProgressSnapshot? {
        val session = inProgressDao.getSession() ?: return null
        return InProgressSnapshot(
            date = LocalDate.parse(session.date),
            split = Split.valueOf(session.split),
            kbWeightKg = session.kbWeightKg,
            sets = inProgressDao.getSets().map { it.toDomain() },
        )
    }

    suspend fun start(date: LocalDate, split: Split, kbWeightKg: Double, sets: List<SetEntry>) {
        inProgressDao.replace(
            session = InProgressSessionEntity(
                date = date.toString(),
                split = split.name,
                kbWeightKg = kbWeightKg,
            ),
            sets = sets.map { it.toInProgressEntity() },
        )
    }

    /**
     * Append sets to the current in-progress session without disturbing existing
     * rows. Used to add the auxiliary block on demand after the main workout.
     * Aux slugs differ from the main rows, so the unique
     * `(exerciseSlug, setIndex, isPriming)` index is not violated.
     */
    suspend fun addSets(sets: List<SetEntry>) {
        inProgressDao.upsertSets(sets.map { it.toInProgressEntity() })
    }

    suspend fun updateSetState(
        exerciseSlug: String,
        setIndex: Int,
        isPriming: Boolean,
        state: SetStatus,
    ) {
        inProgressDao.updateState(exerciseSlug, setIndex, isPriming, state.name)
    }

    suspend fun updateKbWeight(kbWeightKg: Double) {
        inProgressDao.updateKbWeight(kbWeightKg)
    }

    suspend fun updateExerciseWeight(exerciseSlug: String, weightKg: Double, targetReps: Int?) {
        inProgressDao.updateExerciseWeightAndReps(exerciseSlug, weightKg, targetReps)
    }

    suspend fun clear() = inProgressDao.clear()
}
