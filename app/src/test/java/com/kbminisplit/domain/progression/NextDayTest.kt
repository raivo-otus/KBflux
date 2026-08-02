package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.Program
import org.junit.Test
import java.time.LocalDate

class NextDayTest {

    private val start = LocalDate.of(2026, 5, 1)
    private val threeDays = program(day(1, "A"), day(2, "B"), day(3, "C"))

    @Test
    fun `empty history starts at the first day`() {
        assertThat(nextDay(emptyList(), threeDays)?.key).isEqualTo("A")
    }

    @Test
    fun `each day leads to the next in program order`() {
        assertThat(nextDay(listOf(session(start, "A")), threeDays)?.key).isEqualTo("B")
        assertThat(nextDay(listOf(session(start, "B")), threeDays)?.key).isEqualTo("C")
    }

    @Test
    fun `the last day wraps back to the first`() {
        assertThat(nextDay(listOf(session(start, "C")), threeDays)?.key).isEqualTo("A")
    }

    @Test
    fun `only the most recent session decides the next day`() {
        val history = listOf(
            session(start, "C"),
            session(start.plusDays(1), "A"),
        )

        assertThat(nextDay(history, threeDays)?.key).isEqualTo("B")
    }

    @Test
    fun `a program of one day always returns that day`() {
        val single = program(day(1, "A"))

        assertThat(nextDay(listOf(session(start, "A")), single)?.key).isEqualTo("A")
    }

    @Test
    fun `wraps correctly for a program longer than three days`() {
        val fiveDays = program(day(1, "A"), day(2, "B"), day(3, "C"), day(4, "D"), day(5, "E"))

        assertThat(nextDay(listOf(session(start, "D")), fiveDays)?.key).isEqualTo("E")
        assertThat(nextDay(listOf(session(start, "E")), fiveDays)?.key).isEqualTo("A")
    }

    @Test
    fun `history on a deleted day falls back to the first day`() {
        val history = listOf(session(start, "GONE"))

        assertThat(nextDay(history, threeDays)?.key).isEqualTo("A")
    }

    @Test
    fun `an empty program has no next day`() {
        assertThat(nextDay(listOf(session(start, "A")), Program.EMPTY)).isNull()
    }
}
