package com.kbminisplit.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "session",
    indices = [Index(value = ["date"])],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    /**
     * The program day this session was logged against. Still stored in the column
     * named `split` — the values are unchanged ("A"/"B"/"C" for the seeded
     * program), so renaming the column would mean rebuilding a table that
     * `set_entry` has a foreign key into, for no gain.
     */
    @ColumnInfo(name = "split")
    @SerialName("split")
    val dayKey: String,
    val feedback: String,
    /** Snapshot of the day's first ladder circuit weight; 0 when there was none. */
    @ColumnInfo(name = "kbWeightKg")
    @SerialName("kbWeightKg")
    val circuitWeightKg: Double,
    val completedAt: Long,
    @ColumnInfo(defaultValue = "NULL")
    val bodyweightKg: Double? = null,
)
