package com.kbminisplit.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SessionWithSets(
    @Embedded val session: SessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val sets: List<SetEntryEntity>,
)
