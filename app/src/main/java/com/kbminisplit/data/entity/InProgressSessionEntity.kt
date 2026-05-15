package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "in_progress_session")
data class InProgressSessionEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val date: String,
    val split: String,
    val kbWeightKg: Double,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
