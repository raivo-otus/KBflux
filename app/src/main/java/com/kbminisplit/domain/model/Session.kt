package com.kbminisplit.domain.model

import java.time.LocalDate

data class Session(
    val date: LocalDate,
    val split: Split,
    val feedback: Feedback,
    val kbWeightKg: Double,
    val sets: List<SetEntry>,
)
