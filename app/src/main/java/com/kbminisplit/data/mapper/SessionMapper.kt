package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.SessionEntity
import com.kbminisplit.data.entity.SessionWithSets
import com.kbminisplit.data.entity.SetEntryEntity
import com.kbminisplit.data.util.toEnumOrDefault
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import java.time.LocalDate

fun SetEntryEntity.toDomain(): SetEntry = SetEntry(
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    weightKg = weightKg,
    status = status.toEnumOrDefault(SetStatus.Pending),
)

fun SetEntry.toEntity(sessionId: Long): SetEntryEntity = SetEntryEntity(
    sessionId = sessionId,
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    weightKg = weightKg,
    status = status.name,
)

fun SessionEntity.toDomain(sets: List<SetEntryEntity>): Session = Session(
    date = LocalDate.parse(date),
    split = split.toEnumOrDefault(Split.A),
    feedback = feedback.toEnumOrDefault(Feedback.Green),
    kbWeightKg = kbWeightKg,
    sets = sets.map { it.toDomain() },
    bodyweightKg = bodyweightKg,
)

fun SessionWithSets.toDomain(): Session = session.toDomain(sets)

fun Session.toEntity(completedAt: Long): SessionEntity = SessionEntity(
    date = date.toString(),
    split = split.name,
    feedback = feedback.name,
    kbWeightKg = kbWeightKg,
    completedAt = completedAt,
    bodyweightKg = bodyweightKg,
)
