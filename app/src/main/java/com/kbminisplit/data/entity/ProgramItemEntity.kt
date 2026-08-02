package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One movement as programmed on a given day. Every parameter lives here rather
 * than on the shared exercise row, so the same movement can be programmed
 * differently on different days.
 *
 * [currentWeightKg] is the live working weight and the only place it is stored.
 * Rows must be updated in place rather than deleted and re-inserted, or the
 * user's accumulated weight is lost.
 *
 * [exerciseSlug] points at the exercise registry, which supplies the display name
 * and keeps `set_entry`'s foreign key resolvable after a movement is dropped from
 * the program.
 */
@Serializable
@Entity(
    tableName = "program_item",
    foreignKeys = [
        ForeignKey(
            entity = ProgramGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["slug"],
            childColumns = ["exerciseSlug"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("groupId"), Index("exerciseSlug")],
)
data class ProgramItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val exerciseSlug: String,
    val position: Int,
    val sets: Int = 3,
    val minReps: Int = 8,
    val maxReps: Int = 12,
    /** 0 none, 1 warm-up, 2 prime + warm-up. */
    val leadInSets: Int = 2,
    val weightStepKg: Double = 2.5,
    val isAssisted: Boolean = false,
    val isPerSide: Boolean = false,
    val currentWeightKg: Double = 0.0,
)
