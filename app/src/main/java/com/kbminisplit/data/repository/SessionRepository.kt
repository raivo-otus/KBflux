package com.kbminisplit.data.repository

import com.kbminisplit.data.db.SessionDao
import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.mapper.toDomain
import com.kbminisplit.data.mapper.toEntity
import com.kbminisplit.domain.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val clock: Clock,
) {

    fun observeAll(): Flow<List<Session>> =
        sessionDao.observeAll().map { entities -> entities.toDomain() }

    suspend fun getAll(): List<Session> = sessionDao.getAll().toDomain()

    suspend fun getByDate(date: LocalDate): Session? {
        val session = sessionDao.getByDate(date.toString()) ?: return null
        val sets = sessionDao.getSetsForSession(session.id)
        return session.toDomain(sets)
    }

    fun observeBetween(start: LocalDate, endInclusive: LocalDate): Flow<List<Session>> =
        sessionDao.observeBetween(start.toString(), endInclusive.toString())
            .map { entities -> entities.toDomain() }

    suspend fun addSession(session: Session): Long {
        val entity = session.toEntity(completedAt = clock.millis())
        val setEntities = session.sets.map { it.toEntity(sessionId = 0) }
        return sessionDao.insertSessionWithSets(entity, setEntities)
    }

    private suspend fun List<SessionEntity>.toDomain(): List<Session> =
        map { session ->
            val sets = sessionDao.getSetsForSession(session.id)
            session.toDomain(sets)
        }
}
