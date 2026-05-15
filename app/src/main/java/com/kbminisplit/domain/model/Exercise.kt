package com.kbminisplit.domain.model

data class Exercise(
    val slug: String,
    val displayName: String,
    val category: Category,
    val isPerSide: Boolean,
    val weightStepKg: Double,
)
