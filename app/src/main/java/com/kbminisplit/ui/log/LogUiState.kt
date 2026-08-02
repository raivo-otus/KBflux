package com.kbminisplit.ui.log

import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.SetStatus
import java.time.LocalDate

/**
 * Visual state of one calendar cell in the Log grid.
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

/** Read-only session card shown when a colored cell is tapped. */
data class SessionDetail(
    val date: LocalDate,
    /** The day's name from the program, or its raw key if that day is long gone. */
    val dayLabel: String,
    val feedback: Feedback,
    val movements: List<MovementDetail>,
)

/**
 * One movement as it was performed, in session order.
 *
 * [repsLabel] is null for circuit rounds, which record completion rather than
 * reps, and is a single number for sessions logged before rep ranges existed.
 */
data class MovementDetail(
    val name: String,
    val weightKg: Double,
    val repsLabel: String?,
    val statuses: List<SetStatus>,
)
