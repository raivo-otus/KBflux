package com.kbminisplit.domain.model

import java.time.LocalDate

data class Session(
    val date: LocalDate,
    val split: Split,
    val feedback: Feedback,
    val kbWeightKg: Double,
    val sets: List<SetEntry>,
    /**
     * Bodyweight snapshot at commit time, mirroring [kbWeightKg]. Null for
     * sessions logged before bodyweight tracking (or when never entered). Used to
     * derive effective load for assisted movements without re-joining history.
     */
    val bodyweightKg: Double? = null,
)
