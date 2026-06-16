package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "session",
    indices = [Index(value = ["date"])],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val split: String,
    val feedback: String,
    val kbWeightKg: Double,
    val completedAt: Long,
)
