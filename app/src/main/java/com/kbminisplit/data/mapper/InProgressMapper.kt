package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.util.toEnumOrDefault
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus

fun InProgressSetEntity.toDomain(): SetEntry = SetEntry(
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    weightKg = weightKg,
    status = state.toEnumOrDefault(SetStatus.Pending),
)

fun SetEntry.toInProgressEntity(): InProgressSetEntity = InProgressSetEntity(
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    weightKg = weightKg,
    state = status.name,
)
