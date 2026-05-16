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
        val rx = getPrescription(emptyList(), pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.exercise).isEqualTo(pulldown)
        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `all working sets completed and reps below max bumps reps by one`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 8,
            ),
        )

        val rx = getPrescription(history, pulldown, DEFAULT_ONBOARDING)

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
                m1Reps = 10,
                m1Statuses = listOf(SetStatus.Completed, SetStatus.Failed, SetStatus.Completed),
            ),
        )

        val rx = getPrescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(10)
    }

    @Test
    fun `success at standard max reps rolls weight by step and resets to 8`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 12, // Default standardMaxReps in Fixtures
            ),
        )

        val rx = getPrescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(52.5)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `success just below standard max bumps without weight change`() {
        val history = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.A,
                m1Weight = 50.0,
                m1Reps = 11,
            ),
        )

        val rx = getPrescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(50.0)
        assertThat(rx.targetReps).isEqualTo(12)
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

        val rx = getPrescription(history, pulldown, DEFAULT_ONBOARDING)

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
                m1Reps = 9,
            ),
        )

        val rx = getPrescription(history, pulldown, DEFAULT_ONBOARDING)

        assertThat(rx.targetReps).isEqualTo(10)
    }

    @Test
    fun `progression rolls forward across many cycles`() {
        // Walk 8 to 12 with one completed cycle each, then weight bump.
        var history = emptyList<com.kbminisplit.domain.model.Session>()
        var weight = 50.0
        var reps = 8

        repeat(5) { i -> // 8, 9, 10, 11, 12
            history = history + strengthSession(
                date = LocalDate.of(2026, 5, 1).plusDays(i.toLong()),
                split = Split.A,
                m1Weight = weight,
                m1Reps = reps,
            )
            val rx = getPrescription(history, pulldown, DEFAULT_ONBOARDING)
            weight = rx.weightKg
            reps = rx.targetReps
        }

        // After 5 successful sessions (8 to 12), the next prescription is 52.5kg @ 8 reps.
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
                m1Reps = 12,     // ready to roll over
                m2Weight = 40.0, // row
                m2Reps = 8,
                m2Statuses = listOf(SetStatus.Completed, SetStatus.Failed, SetStatus.Completed),
            ),
        )

        val pulldownRx = getPrescription(history, ExerciseCatalog.LatPulldown, DEFAULT_ONBOARDING)
        val rowRx = getPrescription(history, ExerciseCatalog.BarbellRow, DEFAULT_ONBOARDING)

        assertThat(pulldownRx.weightKg).isEqualTo(52.5)
        assertThat(pulldownRx.targetReps).isEqualTo(8)
        assertThat(rowRx.weightKg).isEqualTo(40.0)
        assertThat(rowRx.targetReps).isEqualTo(8)
    }

    @Test
    fun `romanian deadlift follows standard rules`() {
        val rdl = ExerciseCatalog.RomanianDeadlift
        val onboarding = DEFAULT_ONBOARDING.copy(startingTargetReps = 8, standardMaxReps = 12)

        // 1. Success at 12 reps rolls weight by 2.5kg and resets to 8
        val history1 = listOf(
            strengthSession(
                date = LocalDate.of(2026, 5, 1),
                split = Split.C,
                m1Weight = 80.0,
                m1Reps = 8,
                m2Weight = 100.0,
                m2Reps = 12,
            ),
        )
        val rx1 = getPrescription(history1, rdl, onboarding)
        assertThat(rx1.weightKg).isEqualTo(102.5)
        assertThat(rx1.targetReps).isEqualTo(8)
    }
}
