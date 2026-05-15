package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import org.junit.Test
import java.time.LocalDate

class PrescriptionTest {

    private val pulldown = ExerciseCatalog.LatPulldown

    @Test
    fun `never logged yields onboarding starting values`() {
        val rx = prescription(emptyList(), pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.exercise).isEqualTo(pulldown)
        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `all working sets completed and reps below 16 bumps reps by one`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 8,
            ),
        )

        val rx = prescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(9)
    }

    @Test
    fun `any failed working set repeats the same weight and reps`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 12,
                m1Statuses = listOf(SetStatus.Completed, SetStatus.Failed, SetStatus.Completed),
            ),
        )

        val rx = prescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(12)
    }

    @Test
    fun `success at reps 16 rolls weight by step and resets to 8`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 16,
            ),
        )

        val rx = prescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(52.5)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `success at reps 15 bumps to 16 without weight change`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 15,
            ),
        )

        val rx = prescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(16)
    }

    @Test
    fun `uses the most recent session containing this movement`() {
        val history = listOf(
            // Older A — pulldown at 50/8
            strengthSession(LocalDate.of(2026, 5, 1), Split.A, m1Weight = 50.0, m1Reps = 8),
            // B and C in between (no pulldown)
            strengthSession(LocalDate.of(2026, 5, 2), Split.B, m1Weight = 60.0, m1Reps = 8),
            strengthSession(LocalDate.of(2026, 5, 3), Split.C, m1Weight = 80.0, m1Reps = 8),
            // Newer A — pulldown at 50/9
            strengthSession(LocalDate.of(2026, 5, 4), Split.A, m1Weight = 50.0, m1Reps = 9),
        )

        val rx = prescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(10)
    }

    @Test
    fun `priming sets are ignored when judging progression`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 10,
            ),
        )

        val rx = prescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.targetReps).isEqualTo(11)
    }

    @Test
    fun `progression rolls forward across many cycles`() {
        // Walk 8 to 16 with one completed cycle each, then weight bump.
        var history = emptyList<com.kbminisplit.domain.model.Session>()
        var weight = 50.0
        var reps = 8

        repeat(9) { i ->
            history = history + strengthSession(
                date = LocalDate.of(2026, 5, 1).plusDays(i.toLong()),
                split = Split.A,
                m1Weight = weight,
                m1Reps = reps,
            )
            val rx = prescription(history, pulldown, DEFAULT_ONBOARDING)
            weight = rx.weightKg
            reps = rx.targetReps
        }

        // After 9 successful 8→…→16 sessions, the 10th prescription is 52.5kg @ 8 reps.
        assertThat(weight).isEqualTo(52.5)
        assertThat(reps).isEqualTo(8)
    }

    @Test
    fun `each strength movement progresses independently`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0, // pulldown
                m1Reps = 16,     // ready to roll over
                m2Weight = 40.0, // row
                m2Reps = 8,
                m2Statuses = listOf(SetStatus.Completed, SetStatus.Failed, SetStatus.Completed),
            ),
        )

        val pulldownRx = prescription(history, ExerciseCatalog.LatPulldown, DEFAULT_ONBOARDING)
        val rowRx = prescription(history, ExerciseCatalog.BarbellRow, DEFAULT_ONBOARDING)

        assertThat(pulldownRx.weightKg).isEqualTo(52.5)
        assertThat(pulldownRx.targetReps).isEqualTo(8)
        assertThat(rowRx.weightKg).isEqualTo(40.0)
        assertThat(rowRx.targetReps).isEqualTo(8)
    }
}
