package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["slug"],
            childColumns = ["exerciseSlug"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("exerciseSlug"),
    ],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val weightKg: Double,
    val status: String,
)
