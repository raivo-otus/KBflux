package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A training day in the user's program.
 *
 * [dayKey] is the stable identity written to `session.split`; it is generated once
 * and never changes, so renaming or reordering a day leaves history intact. The
 * seeded default program uses "A", "B" and "C" so sessions logged before the
 * program became editable still resolve.
 */
@Serializable
@Entity(
    tableName = "program_day",
    indices = [Index(value = ["dayKey"], unique = true)],
)
data class ProgramDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayKey: String,
    val name: String,
    val position: Int,
)
