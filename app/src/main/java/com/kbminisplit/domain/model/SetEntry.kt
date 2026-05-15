package com.kbminisplit.domain.model

data class SetEntry(
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val targetReps: Int?,
    val weightKg: Double,
    val status: SetStatus,
)
