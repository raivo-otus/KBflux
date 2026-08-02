package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SessionWithSets
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.util.toEnumOrDefault
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import java.time.LocalDate

fun SetEntryEntity.toDomain(): SetEntry = SetEntry(
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    targetRepsMax = targetRepsMax,
    weightKg = weightKg,
    status = status.toEnumOrDefault(SetStatus.Pending),
    position = position,
)

fun SetEntry.toEntity(sessionId: Long): SetEntryEntity = SetEntryEntity(
    sessionId = sessionId,
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    targetRepsMax = targetRepsMax,
    weightKg = weightKg,
    status = status.name,
    position = position,
)

fun SessionEntity.toDomain(sets: List<SetEntryEntity>): Session = Session(
    date = LocalDate.parse(date),
    dayKey = dayKey,
    feedback = feedback.toEnumOrDefault(Feedback.Green),
    circuitWeightKg = circuitWeightKg,
    // @Relation gives no ordering guarantee, so restore the order the session was
    // performed in: movement by movement, lead-ins ahead of working sets.
    sets = sets.sortedWith(
        compareBy({ it.position }, { !it.isPriming }, { it.setIndex }),
    ).map { it.toDomain() },
    bodyweightKg = bodyweightKg,
)

fun SessionWithSets.toDomain(): Session = session.toDomain(sets)

fun Session.toEntity(completedAt: Long): SessionEntity = SessionEntity(
    date = date.toString(),
    dayKey = dayKey,
    feedback = feedback.name,
    circuitWeightKg = circuitWeightKg,
    completedAt = completedAt,
    bodyweightKg = bodyweightKg,
)
