package com.kbminisplit.domain.model

import java.time.LocalDate

data class Session(
    val date: LocalDate,
    /**
     * The [ProgramDay.key] this session was logged against. Free text rather than
     * an id so history survives a day being renamed, reordered or deleted.
     */
    val dayKey: String,
    val feedback: Feedback,
    /**
     * Weight of the day's first ladder circuit at commit time, kept as a snapshot
     * for the Log. Zero when the day had no circuit group.
     */
    val circuitWeightKg: Double,
    val sets: List<SetEntry>,
    /**
     * Bodyweight snapshot at commit time, mirroring [circuitWeightKg]. Null for
     * sessions logged before bodyweight tracking (or when never entered). Used to
     * derive effective load for assisted movements without re-joining history.
     */
    val bodyweightKg: Double? = null,
)
