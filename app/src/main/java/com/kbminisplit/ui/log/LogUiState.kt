package com.kbminisplit.ui.log

import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import java.time.LocalDate

/**
 * Visual state of one calendar cell in the Log grid (spec §5.2).
 *
 * `Outside` represents a day that falls in an adjacent calendar month but is
 * rendered as blank padding so the Mon-Sun week stays aligned within the
 * current month's grid.
 */
sealed interface DayCellState {
    data object Outside : DayCellState
    data object PastEmpty : DayCellState
    data object Future : DayCellState
    data class Logged(val feedback: Feedback) : DayCellState
}

data class DayCell(
    val date: LocalDate,
    val state: DayCellState,
    val isToday: Boolean,
)

/** One row in the Log list. Either a week of seven [DayCell]s or a gap between months. */
sealed interface LogRow {
    val key: String

    data class Week(
        override val key: String,
        val monthLabel: String?,
        val days: List<DayCell>,
    ) : LogRow

    data class MonthGap(override val key: String) : LogRow
}

sealed interface LogUiState {
    data object Loading : LogUiState

    data class Ready(
        val rows: List<LogRow>,
        val todayRowIndex: Int,
    ) : LogUiState
}

/** Read-only session card shown when a colored cell is tapped (spec §5.2). */
data class SessionDetail(
    val date: LocalDate,
    val split: Split,
    val feedback: Feedback,
    val kbWeightKg: Double,
    val kbCircuits: List<SetStatus>,
    val strength: List<StrengthDetail>,
)

data class StrengthDetail(
    val exerciseDisplayName: String,
    val weightKg: Double,
    val targetReps: Int,
    val primeStatus: SetStatus,
    /** Null for historical sessions logged before the warm-up set existed. */
    val warmupStatus: SetStatus?,
    val workingStatuses: List<SetStatus>,
)
