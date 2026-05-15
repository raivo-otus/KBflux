package com.kbminisplit.domain.model

import java.time.LocalDate

data class DayPlan(
    val date: LocalDate,
    val split: Split,
    val kbWeightKg: Double,
    val strength: List<Prescription>,
)
