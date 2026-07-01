package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.Session
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
    fun `aux movement with no history falls back to its default starting weight`() {
        val fly = ExerciseCatalog.SideDeltFly

        // DEFAULT_ONBOARDING has no aux slug, so getPrescription must use the
        // movement's own defaultStartingWeightKg rather than erroring.
        val rx = getPrescription(emptyList(), fly, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(6.0)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `aux movement double-progresses from its own history`() {
        val fly = ExerciseCatalog.SideDeltFly
        val session = Session(
            date = LocalDate.of(2026, 5, 1),
            split = Split.A,
            feedback = Feedback.Green,
            kbWeightKg = 16.0,
            sets = buildList {
                add(primingSet(fly, 6.0))
                addAll(workingSets(fly, 6.0, 10))
            },
        )

        val rx = getPrescription(listOf(session), fly, DEFAULT_ONBOARDING)

        // All working sets completed and reps below standard max → bump reps only.
        assertThat(rx.weightKg).isEqualTo(6.0)
        assertThat(rx.targetReps).isEqualTo(11)
    }

    // --- Assisted movements (inverted double progression) ---

    private val dip = ExerciseCatalog.AssistedDip

    private fun assistedDipSession(
        date: LocalDate,
        weight: Double,
        reps: Int,
        statuses: List<SetStatus> = List(3) { SetStatus.Completed },
        feedback: Feedback = Feedback.Green,
    ): Session = Session(
        date = date,
        split = Split.B,
        feedback = feedback,
        kbWeightKg = 16.0,
        sets = buildList {
            add(primingSet(dip, weight))
            addAll(workingSets(dip, weight, reps, statuses))
        },
    )

    @Test
    fun `assisted dip with no history falls back to its default assist weight`() {
        val rx = getPrescription(emptyList(), dip, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(40.0)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `assisted graduation drops the assistance pin by one step`() {
        val history = listOf(
            assistedDipSession(LocalDate.of(2026, 5, 1), weight = 40.0, reps = 12), // at max reps
        )

        val rx = getPrescription(history, dip, DEFAULT_ONBOARDING)

        // Less assistance next time, reps reset to min.
        assertThat(rx.weightKg).isEqualTo(37.5)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `assisted reps still climb one at a time below max`() {
        val history = listOf(
            assistedDipSession(LocalDate.of(2026, 5, 1), weight = 40.0, reps = 8),
        )

        val rx = getPrescription(history, dip, DEFAULT_ONBOARDING)

        // Same assistance, one more rep — rep logic is unchanged for assisted.
        assertThat(rx.weightKg).isEqualTo(40.0)
        assertThat(rx.targetReps).isEqualTo(9)
    }

    @Test
    fun `assisted pin floors at zero rather than going negative`() {
        val history = listOf(
            assistedDipSession(LocalDate.of(2026, 5, 1), weight = 2.0, reps = 12),
        )

        val rx = getPrescription(history, dip, DEFAULT_ONBOARDING)

        assertThat(rx.weightKg).isEqualTo(0.0)
        assertThat(rx.targetReps).isEqualTo(8)
    }

    @Test
    fun `assisted deload adds assistance instead of removing load`() {
        // Three consecutive red Split B sessions trigger a deload.
        val history = listOf(
            assistedDipSession(LocalDate.of(2026, 5, 1), weight = 30.0, reps = 8, feedback = Feedback.Red),
            assistedDipSession(LocalDate.of(2026, 5, 3), weight = 30.0, reps = 8, feedback = Feedback.Red),
            assistedDipSession(LocalDate.of(2026, 5, 5), weight = 30.0, reps = 8, feedback = Feedback.Red),
        )

        val rx = getPrescription(history, dip, DEFAULT_ONBOARDING)

        // Easier = more assistance (higher pin), reps reset to min.
        assertThat(rx.weightKg).isEqualTo(32.5)
        assertThat(rx.targetReps).isEqualTo(8)
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
