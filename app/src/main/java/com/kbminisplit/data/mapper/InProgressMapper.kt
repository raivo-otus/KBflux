package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus

fun InProgressSetEntity.toDomain(): SetEntry = SetEntry(
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    weightKg = weightKg,
    status = try { SetStatus.valueOf(state) } catch (e: IllegalArgumentException) { SetStatus.Pending },
)

fun SetEntry.toInProgressEntity(): InProgressSetEntity = InProgressSetEntity(
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    weightKg = weightKg,
    state = status.name,
)
