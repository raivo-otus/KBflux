package com.kbminisplit.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "in_progress_session")
data class InProgressSessionEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val date: String,
    val dayKey: String,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
