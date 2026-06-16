package com.kbminisplit.data.mapper

import com.kbminisplit.data.entity.ExerciseEntity
import com.kbminisplit.data.util.toEnumOrDefault
import com.kbminisplit.domain.model.Category
import com.kbminisplit.domain.model.Exercise

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    slug = slug,
    displayName = displayName,
    category = category.toEnumOrDefault(Category.KB),
    isPerSide = isPerSide,
    weightStepKg = weightStepKg,
    minReps = minReps,
    maxReps = maxReps,
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    slug = slug,
    displayName = displayName,
    category = category.name,
    isPerSide = isPerSide,
    weightStepKg = weightStepKg,
    minReps = minReps,
    maxReps = maxReps,
)
