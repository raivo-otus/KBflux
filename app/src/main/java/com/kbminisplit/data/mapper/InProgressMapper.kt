package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.InProgressSetEntity
import com.kbminisplit.data.util.toEnumOrDefault
import com.kbminisplit.domain.model.InProgressSet
import com.kbminisplit.domain.model.SetStatus

fun InProgressSetEntity.toDomain(): InProgressSet = InProgressSet(
    id = id,
    programGroupId = programGroupId,
    programItemId = programItemId,
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    targetRepsMax = targetRepsMax,
    weightKg = weightKg,
    status = state.toEnumOrDefault(SetStatus.Pending),
    position = position,
)

/** The id is left at 0 so Room assigns one; built rows are always fresh. */
fun InProgressSet.toEntity(): InProgressSetEntity = InProgressSetEntity(
    programGroupId = programGroupId,
    programItemId = programItemId,
    exerciseSlug = exerciseSlug,
    setIndex = setIndex,
    isPriming = isPriming,
    targetReps = targetReps,
    targetRepsMax = targetRepsMax,
    weightKg = weightKg,
    state = status.name,
    position = position,
)
