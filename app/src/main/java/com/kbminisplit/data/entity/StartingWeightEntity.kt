package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "starting_weight",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["slug"],
            childColumns = ["exerciseSlug"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class StartingWeightEntity(
    @PrimaryKey val exerciseSlug: String,
    val weightKg: Double,
)
