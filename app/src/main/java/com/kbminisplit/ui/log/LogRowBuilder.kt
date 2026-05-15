package com.kbminisplit.ui.log

import com.kbminisplit.domain.model.Session
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val MONTH_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

/** Container returned by [buildLogRows]: the row list plus the index to auto-scroll to. */
data class LogContent(
    val rows: List<LogRow>,
    val todayRowIndex: Int,
)

/**
 * Build the calendar row list shown on the Log tab (spec §5).
 *
 * The grid renders one month at a time as a stack of Mon-Sun weeks. Days from
 * adjacent months in the same calendar week become [DayCellState.Outside] so
 * each month's grid stays aligned without double-counting boundary days. An
 * empty [LogRow.MonthGap] separates consecutive months.
 *
 * The lower bound follows the earliest session (or today, if no history). The
 * upper bound extends `futureBufferDays` past today so the user sees a handful
 * of outlined future cells.
 */
fun buildLogRows(
    sessions: List<Session>,
    today: LocalDate,
    futureBufferDays: Int = DEFAULT_FUTURE_BUFFER_DAYS,
): LogContent {
    val sessionsByDate = sessions.associateBy { it.date }
    val earliest = sessions.minByOrNull { it.date }?.date ?: today
    val startMonth = YearMonth.from(minOf(earliest, today))
    val endMonth = YearMonth.from(today.plusDays(futureBufferDays.toLong()))

    val rows = mutableListOf<LogRow>()
    var todayRowIndex = -1
    var monthCursor = startMonth
    while (!monthCursor.isAfter(endMonth)) {
        val month = monthCursor
        val firstOfMonth = month.atDay(1)
        val lastOfMonth = month.atEndOfMonth()
        val firstMonday = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastSunday = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

        var weekStart = firstMonday
        var isFirstWeekOfMonth = true
        while (!weekStart.isAfter(lastSunday)) {
            val days = (0..6).map { offset ->
                buildDayCell(
                    date = weekStart.plusDays(offset.toLong()),
                    currentMonth = month,
                    today = today,
                    sessionsByDate = sessionsByDate,
                )
            }
            // Pick the row where today's cell belongs to the *current* month's
            // grid — the same week appears in two adjacent months' grids when
            // today straddles a month boundary, and we only want one match.
            val isTodayWeek = days.any { it.isToday && it.state !is DayCellState.Outside }
            if (isTodayWeek) todayRowIndex = rows.size
            rows += LogRow.Week(
                key = "w-$weekStart-$month",
                monthLabel = if (isFirstWeekOfMonth) month.format(MONTH_LABEL_FORMATTER) else null,
                days = days,
            )
            isFirstWeekOfMonth = false
            weekStart = weekStart.plusDays(7)
        }
        if (monthCursor != endMonth) {
            rows += LogRow.MonthGap(key = "gap-$monthCursor")
        }
        monthCursor = monthCursor.plusMonths(1)
    }

    // Defensive fallback: if today somehow didn't fall into any month grid
    // (shouldn't happen given endMonth is derived from today), point at the
    // last week so the screen still scrolls somewhere sensible.
    if (todayRowIndex == -1) {
        todayRowIndex = rows.indexOfLast { it is LogRow.Week }.coerceAtLeast(0)
    }
    return LogContent(rows = rows, todayRowIndex = todayRowIndex)
}

private fun buildDayCell(
    date: LocalDate,
    currentMonth: YearMonth,
    today: LocalDate,
    sessionsByDate: Map<LocalDate, Session>,
): DayCell {
    val isToday = date == today
    val state = when {
        YearMonth.from(date) != currentMonth -> DayCellState.Outside
        sessionsByDate.containsKey(date) -> DayCellState.Logged(sessionsByDate.getValue(date).feedback)
        date.isAfter(today) -> DayCellState.Future
        else -> DayCellState.PastEmpty
    }
    return DayCell(date = date, state = state, isToday = isToday)
}

private const val DEFAULT_FUTURE_BUFFER_DAYS = 14
