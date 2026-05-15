package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "in_progress_set",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["slug"],
            childColumns = ["exerciseSlug"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["exerciseSlug", "setIndex", "isPriming"], unique = true),
    ],
)
data class InProgressSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val weightKg: Double,
    val state: String,
)
