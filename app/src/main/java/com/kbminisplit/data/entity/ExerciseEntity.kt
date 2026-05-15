package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val slug: String,
    val displayName: String,
    val category: String,
    val isPerSide: Boolean,
    val weightStepKg: Double,
)
