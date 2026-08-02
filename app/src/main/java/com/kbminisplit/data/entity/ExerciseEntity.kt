package com.kbminisplit.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * The exercise registry: a stable slug and the name to show for it.
 *
 * Every programming parameter moved to `program_item`, but this table stays as
 * the foreign-key target for `set_entry`, so a movement dropped from the program
 * still renders a name in the Log. Rows are never deleted.
 *
 * The remaining columns are vestigial. They are kept, with defaults, so the table
 * needs no rebuild — rebuilding a table three foreign keys point into buys
 * nothing.
 */
@Serializable
@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val slug: String,
    val displayName: String,
    val category: String = "",
    val isPerSide: Boolean = false,
    val weightStepKg: Double = 2.5,
    @ColumnInfo(defaultValue = "8")
    val minReps: Int = 8,
    @ColumnInfo(defaultValue = "16")
    val maxReps: Int = 16,
)
