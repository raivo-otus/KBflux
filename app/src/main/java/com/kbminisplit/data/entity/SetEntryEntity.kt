package com.kbminisplit.data.entity

import androidx.room.ColumnInfo
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
    /** Low end of the rep range; null for circuit rounds. */
    val targetReps: Int?,
    /**
     * High end of the rep range. Null on sessions logged before rep ranges
     * existed, which render as the single [targetReps] number.
     */
    @ColumnInfo(defaultValue = "NULL")
    val targetRepsMax: Int? = null,
    val weightKg: Double,
    val status: String,
    /**
     * The movement's ordinal within the session as actually performed, i.e. after
     * group rotation. The Log orders by this rather than by a fixed catalog order.
     */
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,
)
