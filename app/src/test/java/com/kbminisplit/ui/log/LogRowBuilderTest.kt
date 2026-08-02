package com.kbminisplit.ui.log

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Session
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class LogRowBuilderTest {

    // Friday in mid-month — gives both a leading partial week (Mon-Thu in Apr)
    // and a trailing partial week (Sat-Sun spilling into Jun).
    private val today: LocalDate = LocalDate.of(2026, 5, 15)

    @Test
    fun `empty history renders the current month grid with month label on first row`() {
        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 0)

        val weeks = content.rows.filterIsInstance<LogRow.Week>()
        assertThat(weeks).isNotEmpty()
        assertThat(content.rows.filterIsInstance<LogRow.MonthGap>()).isEmpty()
        assertThat(weeks.first().monthLabel).isNotNull()
        assertThat(weeks.drop(1).all { it.monthLabel == null }).isTrue()
    }

    @Test
    fun `each week row has exactly seven Monday-anchored day cells`() {
        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 0)

        content.rows.filterIsInstance<LogRow.Week>().forEach { week ->
            assertThat(week.days).hasSize(7)
            assertThat(week.days.first().date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
            assertThat(week.days.last().date.dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
        }
    }

    @Test
    fun `today cell is marked is_today and is not Outside`() {
        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 0)

        val todayRow = content.rows.filterIsInstance<LogRow.Week>().single { week ->
            week.days.any { it.date == today && it.state !is DayCellState.Outside }
        }
        val cell = todayRow.days.single { it.date == today }
        assertThat(cell.isToday).isTrue()
        assertThat(cell.state).isEqualTo(DayCellState.PastEmpty)
    }

    @Test
    fun `today cell renders as Logged when a session exists for today`() {
        val content = buildLogRows(
            sessions = listOf(sessionAt(today, Feedback.Green)),
            today = today,
            futureBufferDays = 0,
        )

        val cell = content.rows.filterIsInstance<LogRow.Week>()
            .flatMap { it.days }
            .single { it.date == today && it.state !is DayCellState.Outside }
        assertThat(cell.state).isEqualTo(DayCellState.Logged(Feedback.Green))
        assertThat(cell.isToday).isTrue()
    }

    @Test
    fun `past day with no session is PastEmpty`() {
        val past = today.minusDays(3)

        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 0)

        val cell = content.rows.filterIsInstance<LogRow.Week>()
            .flatMap { it.days }
            .single { it.date == past && it.state !is DayCellState.Outside }
        assertThat(cell.state).isEqualTo(DayCellState.PastEmpty)
        assertThat(cell.isToday).isFalse()
    }

    @Test
    fun `future days within buffer are Future`() {
        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 14)
        val tomorrow = today.plusDays(1)

        val cell = content.rows.filterIsInstance<LogRow.Week>()
            .flatMap { it.days }
            .single { it.date == tomorrow && it.state !is DayCellState.Outside }
        assertThat(cell.state).isEqualTo(DayCellState.Future)
    }

    @Test
    fun `days from an adjacent month appear Outside in the current month's grid`() {
        // May 1 2026 is Friday; Mon-Thu of that week are Apr 27-30 — must be Outside
        // when rendered as part of May's grid.
        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 0)

        val mayFirstWeek = content.rows.filterIsInstance<LogRow.Week>().first()
        val apr30 = LocalDate.of(2026, 4, 30)
        val cell = mayFirstWeek.days.single { it.date == apr30 }
        assertThat(cell.state).isEqualTo(DayCellState.Outside)
    }

    @Test
    fun `month gap inserted between consecutive months`() {
        val priorSession = sessionAt(LocalDate.of(2026, 4, 15), Feedback.Yellow)

        val content = buildLogRows(
            sessions = listOf(priorSession),
            today = today,
            futureBufferDays = 0,
        )

        val gaps = content.rows.filterIsInstance<LogRow.MonthGap>()
        assertThat(gaps).hasSize(1)
        val labeledWeeks = content.rows.filterIsInstance<LogRow.Week>()
            .filter { it.monthLabel != null }
        assertThat(labeledWeeks).hasSize(2)
    }

    @Test
    fun `start month follows the earliest session date`() {
        // Jan, Feb, Mar, Apr, May = 5 months, with 4 gaps between them.
        val content = buildLogRows(
            sessions = listOf(sessionAt(LocalDate.of(2026, 1, 5), Feedback.Red)),
            today = today,
            futureBufferDays = 0,
        )

        val monthLabels = content.rows.filterIsInstance<LogRow.Week>()
            .mapNotNull { it.monthLabel }
        assertThat(monthLabels).hasSize(5)
        assertThat(content.rows.filterIsInstance<LogRow.MonthGap>()).hasSize(4)
    }

    @Test
    fun `todayRowIndex points at the row whose today cell is in its own month`() {
        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 0)

        val row = content.rows[content.todayRowIndex] as LogRow.Week
        val cell = row.days.single { it.date == today }
        assertThat(cell.state).isNotEqualTo(DayCellState.Outside)
        assertThat(cell.isToday).isTrue()
    }

    @Test
    fun `future buffer can extend the grid into the next month`() {
        // today is May 15; +21 days = Jun 5 → endMonth becomes June, so a gap
        // and a June grid appear even with no history.
        val content = buildLogRows(sessions = emptyList(), today = today, futureBufferDays = 21)

        val labels = content.rows.filterIsInstance<LogRow.Week>().mapNotNull { it.monthLabel }
        assertThat(labels).hasSize(2)
        assertThat(content.rows.filterIsInstance<LogRow.MonthGap>()).hasSize(1)
    }

    @Test
    fun `feedback color is preserved end-to-end`() {
        val redDate = LocalDate.of(2026, 5, 4)
        val yellowDate = LocalDate.of(2026, 5, 6)
        val greenDate = LocalDate.of(2026, 5, 8)

        val content = buildLogRows(
            sessions = listOf(
                sessionAt(redDate, Feedback.Red),
                sessionAt(yellowDate, Feedback.Yellow),
                sessionAt(greenDate, Feedback.Green),
            ),
            today = today,
            futureBufferDays = 0,
        )

        val byDate = content.rows.filterIsInstance<LogRow.Week>()
            .flatMap { it.days }
            .filter { it.state !is DayCellState.Outside }
            .associateBy { it.date }
        assertThat(byDate.getValue(redDate).state).isEqualTo(DayCellState.Logged(Feedback.Red))
        assertThat(byDate.getValue(yellowDate).state).isEqualTo(DayCellState.Logged(Feedback.Yellow))
        assertThat(byDate.getValue(greenDate).state).isEqualTo(DayCellState.Logged(Feedback.Green))
    }

    private fun sessionAt(date: LocalDate, feedback: Feedback): Session = Session(
        date = date,
        dayKey = "A",
        feedback = feedback,
        circuitWeightKg = 16.0,
        sets = emptyList(),
    )
}
