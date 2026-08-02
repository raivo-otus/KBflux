package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A live set button in the session currently being tracked.
 *
 * Rows are addressed by [id] rather than by their exercise, because a
 * user-defined day may legitimately contain the same movement twice. The unique
 * index over the owning group and item still guards against duplicate rows being
 * built for one slot; [programItemId] is 0 for a circuit group's round rows,
 * which belong to the group rather than to any single movement.
 */
@Serializable
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
        Index(
            value = ["programGroupId", "programItemId", "setIndex", "isPriming"],
            unique = true,
        ),
        Index("exerciseSlug"),
    ],
)
data class InProgressSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programGroupId: Long,
    /** 0 for a circuit group's round rows, which have no owning movement. */
    val programItemId: Long,
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val targetRepsMax: Int?,
    val weightKg: Double,
    val state: String,
    val position: Int,
)
