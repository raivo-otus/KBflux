package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A block of movements inside a day.
 *
 * The circuit columns ([rounds], [circuitSlug], [weightKg], [usesLadder],
 * [weightChangedAt], [bumpSnoozedAt]) only carry meaning when [kind] is
 * `CIRCUIT`; a STANDARD group leaves them at their defaults. [circuitSlug] is the
 * sentinel exercise the round rows are stored under — the seeded kettlebell
 * groups reuse `kb_flow` so existing sessions keep resolving.
 */
@Serializable
@Entity(
    tableName = "program_group",
    foreignKeys = [
        ForeignKey(
            entity = ProgramDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dayId")],
)
data class ProgramGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val name: String,
    val kind: String,
    val position: Int,
    /** Shift the movement order by one every time this day comes around. */
    val rotates: Boolean = true,
    /** Hold the group back until every earlier group is resolved. */
    val isDeferred: Boolean = false,
    val rounds: Int = 3,
    val circuitSlug: String? = null,
    val weightKg: Double? = null,
    val usesLadder: Boolean = false,
    /** Anchors the three-month ladder-bump clock. */
    val weightChangedAt: Long? = null,
    val bumpSnoozedAt: Long? = null,
)
