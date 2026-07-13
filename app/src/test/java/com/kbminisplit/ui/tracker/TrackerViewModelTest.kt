package com.kbminisplit.ui.tracker

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.repository.InProgressRepository
import com.kbminisplit.data.repository.InProgressSnapshot
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Feedback
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import com.kbminisplit.domain.progression.KbBumpSnooze
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TrackerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixedClock: Clock =
        Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneId.of("UTC"))
    private val today: LocalDate = LocalDate.now(fixedClock)

    private val defaults = OnboardingDefaults(
        kbWeightKg = 16.0,
        startingWeightsBySlug = mapOf(
            ExerciseCatalog.LatPulldown.slug to 50.0,
            ExerciseCatalog.BarbellRow.slug to 40.0,
            ExerciseCatalog.Bench.slug to 60.0,
            ExerciseCatalog.Ohp.slug to 35.0,
            ExerciseCatalog.HighBarSquat.slug to 70.0,
            ExerciseCatalog.RomanianDeadlift.slug to 80.0,
        ),
        startingTargetReps = 8,
        standardMaxReps = 12,
    )

    private lateinit var sessionRepository: SessionRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var inProgressRepository: InProgressRepository

    private val inProgressFlow = MutableStateFlow<InProgressSnapshot?>(null)
    private val historyFlow = MutableStateFlow<List<Session>>(emptyList())
    private val defaultsFlow = MutableStateFlow<OnboardingDefaults?>(defaults)
    private val snoozeFlow = MutableStateFlow<KbBumpSnooze?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionRepository = mockk(relaxed = true) {
            every { observeAll() } returns historyFlow
            coEvery { getAll() } answers { historyFlow.value }
            coEvery { addSession(any()) } answers {
                historyFlow.value = historyFlow.value + firstArg<Session>()
                1L
            }
        }
        settingsRepository = mockk(relaxed = true) {
            every { observeOnboardingDefaults() } returns defaultsFlow
            every { observeKbBumpSnooze() } returns snoozeFlow
            coEvery { getOnboardingDefaults() } answers { defaultsFlow.value }
        }
        inProgressRepository = mockk(relaxed = true) {
            every { observe() } returns inProgressFlow
            coEvery { get() } answers {
                inProgressFlow.value
            }
            coEvery { start(any(), any(), any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                val sets = args[3] as List<SetEntry>
                inProgressFlow.value = InProgressSnapshot(
                    date = firstArg(),
                    split = secondArg(),
                    kbWeightKg = thirdArg(),
                    sets = sets,
                )
            }
            coEvery { updateKbWeight(any()) } answers {
                val newKg: Double = firstArg()
                val current = inProgressFlow.value ?: return@answers
                inProgressFlow.value = current.copy(kbWeightKg = newKg)
            }
            coEvery { updateExerciseWeight(any(), any(), any()) } answers {
                val slug: String = firstArg()
                val newKg: Double = secondArg()
                val newReps: Int? = thirdArg()
                val current = inProgressFlow.value ?: return@answers
                inProgressFlow.value = current.copy(
                    sets = current.sets.map {
                        if (it.exerciseSlug == slug) {
                            it.copy(weightKg = newKg, targetReps = if (it.isPriming) it.targetReps else newReps)
                        } else it
                    },
                )
            }
            coEvery { addSets(any()) } answers {
                @Suppress("UNCHECKED_CAST")
                val newSets = firstArg<List<SetEntry>>()
                val current = inProgressFlow.value ?: return@answers
                inProgressFlow.value = current.copy(sets = current.sets + newSets)
            }
            coEvery { clear() } answers { inProgressFlow.value = null }
            coEvery { updateSetState(any(), any(), any(), any()) } answers {
                val slug: String = firstArg()
                val idx: Int = secondArg()
                val priming: Boolean = thirdArg()
                val newStatus = args[3] as SetStatus
                val current = inProgressFlow.value ?: return@answers
                inProgressFlow.value = current.copy(
                    sets = current.sets.map {
                        if (it.exerciseSlug == slug && it.setIndex == idx && it.isPriming == priming) {
                            it.copy(status = newStatus)
                        } else it
                    },
                )
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = TrackerViewModel(
        sessionRepository = sessionRepository,
        settingsRepository = settingsRepository,
        inProgressRepository = inProgressRepository,
        clock = fixedClock,
    )

    // ---- Bootstrap ----

    @Test
    fun `bootstrap writes fresh in-progress when none exists`() {
        runTest(testDispatcher) {
            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { inProgressRepository.start(any(), any(), any(), any()) }
            val snapshot = inProgressFlow.value!!
            assertThat(snapshot.date).isEqualTo(today)
            assertThat(snapshot.split).isEqualTo(Split.A)
            assertThat(snapshot.kbWeightKg).isEqualTo(16.0)

            // 3 KB circuits + 2 strength × (1 prime + 1 warm-up + 3 working) = 3 + 10 = 13
            assertThat(snapshot.sets).hasSize(13)
            assertThat(snapshot.sets.count { it.exerciseSlug == ExerciseCatalog.KbFlow.slug })
                .isEqualTo(3)
        }
    }

    @Test
    fun `bootstrap is a no-op when in-progress matches today's split`() {
        inProgressFlow.value = InProgressSnapshot(
            date = today,
            split = Split.A,
            kbWeightKg = 16.0,
            sets = listOf(
                SetEntry(
                    exerciseSlug = ExerciseCatalog.KbFlow.slug,
                    setIndex = 0,
                    isPriming = false,
                    targetReps = null,
                    weightKg = 16.0,
                    status = SetStatus.Pending,
                ),
                SetEntry(
                    exerciseSlug = ExerciseCatalog.LatPulldown.slug,
                    setIndex = 0,
                    isPriming = true,
                    targetReps = null,
                    weightKg = 50.0,
                    status = SetStatus.Pending,
                ),
                // Warm-up row (setIndex 1) — required since the freshness guard
                // rebuilds any in-progress that predates the warm-up set.
                SetEntry(
                    exerciseSlug = ExerciseCatalog.LatPulldown.slug,
                    setIndex = 1,
                    isPriming = true,
                    targetReps = null,
                    weightKg = 50.0,
                    status = SetStatus.Pending,
                ),
                SetEntry(
                    exerciseSlug = ExerciseCatalog.BarbellRow.slug,
                    setIndex = 0,
                    isPriming = true,
                    targetReps = null,
                    weightKg = 40.0,
                    status = SetStatus.Pending,
                ),
                SetEntry(
                    exerciseSlug = ExerciseCatalog.BarbellRow.slug,
                    setIndex = 1,
                    isPriming = true,
                    targetReps = null,
                    weightKg = 40.0,
                    status = SetStatus.Pending,
                ),
            ),
        )
        runTest(testDispatcher) {
            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { inProgressRepository.start(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `bootstrap replaces in-progress that predates the kb_flow sentinel`() {
        // Legacy snapshot from before the per-circuit refactor — no kb_flow row.
        inProgressFlow.value = InProgressSnapshot(
            date = today,
            split = Split.A,
            kbWeightKg = 16.0,
            sets = listOf(
                SetEntry(
                    exerciseSlug = ExerciseCatalog.Swings.slug,
                    setIndex = 0,
                    isPriming = false,
                    targetReps = null,
                    weightKg = 16.0,
                    status = SetStatus.Pending,
                ),
            ),
        )
        runTest(testDispatcher) {
            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { inProgressRepository.start(any(), any(), any(), any()) }
            assertThat(inProgressFlow.value!!.sets.any { it.exerciseSlug == ExerciseCatalog.KbFlow.slug })
                .isTrue()
        }
    }

    @Test
    fun `bootstrap replaces stale in-progress from a different date`() {
        inProgressFlow.value = InProgressSnapshot(
            date = today.minusDays(2),
            split = Split.A,
            kbWeightKg = 16.0,
            sets = emptyList(),
        )
        runTest(testDispatcher) {
            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { inProgressRepository.start(any(), any(), any(), any()) }
            assertThat(inProgressFlow.value!!.date).isEqualTo(today)
        }
    }

    @Test
    fun `bootstrap replaces in-progress whose split disagrees with expected`() {
        // History ends on Split.A → expected next is Split.B; but in-progress says C.
        historyFlow.value = listOf(
            sessionAt(today.minusDays(3), Split.A),
        )
        inProgressFlow.value = InProgressSnapshot(
            date = today,
            split = Split.C,
            kbWeightKg = 16.0,
            sets = emptyList(),
        )
        runTest(testDispatcher) {
            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { inProgressRepository.start(any(), any(), any(), any()) }
            assertThat(inProgressFlow.value!!.split).isEqualTo(Split.B)
        }
    }

    @Test
    fun `bootstrap waits if onboarding defaults are not yet ready`() {
        defaultsFlow.value = null
        coEvery { settingsRepository.getOnboardingDefaults() } returns null

        runTest(testDispatcher) {
            newViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { inProgressRepository.start(any(), any(), any(), any()) }
        }
    }

    // ---- State derivation ----

    @Test
    fun `state becomes Ready after bootstrap with all sets Pending`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.split).isEqualTo(Split.A)
            assertThat(ready.kbBlock.movements).hasSize(3)
            assertThat(ready.kbBlock.circuits).hasSize(3)
            assertThat(ready.strength).hasSize(2)
            assertThat(ready.strength.map { it.exercise.slug })
                .containsExactly(ExerciseCatalog.LatPulldown.slug, ExerciseCatalog.BarbellRow.slug)
                .inOrder()
            assertThat(ready.mainResolved).isFalse()
        }
    }

    @Test
    fun `strength row carries weight and target reps from prescription`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            val pulldown = ready.strength.first { it.exercise.slug == ExerciseCatalog.LatPulldown.slug }
            assertThat(pulldown.weightKg).isEqualTo(50.0)
            assertThat(pulldown.targetReps).isEqualTo(8)
            assertThat(pulldown.working).hasSize(3)
        }
    }

    @Test
    fun `strength row exposes prime and warm-up acclimatization loads`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            val pulldown = ready.strength.first { it.exercise.slug == ExerciseCatalog.LatPulldown.slug }
            // Working 50 kg, traditional main lift (floor 20): prime 25, warm-up 37.5.
            assertThat(pulldown.prime.weightKg).isEqualTo(25.0)
            assertThat(pulldown.warmup).isNotNull()
            assertThat(pulldown.warmup!!.weightKg).isEqualTo(37.5)
        }
    }

    @Test
    fun `kbBump is non-null when prompt is due and no KB sets touched`() {
        // The current weight has been in use for 3 months → prompt due.
        historyFlow.value = listOf(sessionAt(today.minusMonths(3), Split.C))
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.kbBump).isNotNull()
            assertThat(ready.kbBump!!.currentKg).isEqualTo(16.0)
            // Target is the next kettlebell on the ladder, not a fixed step.
            assertThat(ready.kbBump!!.targetKg).isEqualTo(20.0)
        }
    }

    @Test
    fun `kbBump is null once any KB set has been touched`() {
        historyFlow.value = listOf(sessionAt(today.minusMonths(3), Split.C))
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            val firstCircuit = ready.kbBlock.circuits.first()
            vm.onSetTap(firstCircuit)
            advanceUntilIdle()

            val updated = vm.state.value as TrackerUiState.Ready
            assertThat(updated.kbBump).isNull()
        }
    }

    @Test
    fun `kb flow movements are themed to the split with positional rep labels`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            // Fresh install bootstraps split A; weight never changed → full scheme.
            val readyA = vm.state.value as TrackerUiState.Ready
            assertThat(readyA.kbBlock.movements.map { it.exercise.slug }).containsExactly(
                ExerciseCatalog.Swings.slug,
                ExerciseCatalog.HighPull.slug,
                ExerciseCatalog.GobletSquat.slug,
            ).inOrder()
            assertThat(readyA.kbBlock.movements.map { it.repsLabel })
                .containsExactly("32", "16/side", "8").inOrder()

            vm.forceSplit(Split.C)
            advanceUntilIdle()

            val readyC = vm.state.value as TrackerUiState.Ready
            assertThat(readyC.kbBlock.movements.map { it.exercise.slug }).containsExactly(
                ExerciseCatalog.Swings.slug,
                ExerciseCatalog.GobletSquat.slug,
                ExerciseCatalog.Snatch.slug,
            ).inOrder()
            assertThat(readyC.kbBlock.movements.map { it.repsLabel })
                .containsExactly("32", "16", "8/side").inOrder()
        }
    }

    @Test
    fun `kb rep labels ramp while a recent weight change settles in`() {
        // Two sessions at 12 kg, then one at the current 16 kg → lowest ramp stage.
        historyFlow.value = listOf(
            sessionAt(today.minusDays(9), Split.A, kbWeight = 12.0),
            sessionAt(today.minusDays(7), Split.B, kbWeight = 12.0),
            sessionAt(today.minusDays(5), Split.C, kbWeight = 16.0),
        )
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.split).isEqualTo(Split.A)
            assertThat(ready.kbBlock.movements.map { it.repsLabel })
                .containsExactly("20", "10/side", "5").inOrder()
        }
    }

    // ---- Gesture handlers ----

    @Test
    fun `tap completes a set`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            val pulldownPrime = ready.strength.first { it.exercise.slug == ExerciseCatalog.LatPulldown.slug }.prime

            vm.onSetTap(pulldownPrime)
            advanceUntilIdle()

            coVerify {
                inProgressRepository.updateSetState(
                    pulldownPrime.exerciseSlug,
                    pulldownPrime.setIndex,
                    pulldownPrime.isPriming,
                    SetStatus.Completed,
                )
            }
        }
    }

    @Test
    fun `double-tap fails a set`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            val cell = (vm.state.value as TrackerUiState.Ready).strength.first().working.first()

            vm.onSetDoubleTap(cell)
            advanceUntilIdle()

            coVerify {
                inProgressRepository.updateSetState(
                    cell.exerciseSlug,
                    cell.setIndex,
                    cell.isPriming,
                    SetStatus.Failed,
                )
            }
        }
    }

    @Test
    fun `long-press reverts a set to Pending`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            val cell = (vm.state.value as TrackerUiState.Ready).strength.first().working.first()

            vm.onSetLongPress(cell)
            advanceUntilIdle()

            coVerify {
                inProgressRepository.updateSetState(
                    cell.exerciseSlug,
                    cell.setIndex,
                    cell.isPriming,
                    SetStatus.Pending,
                )
            }
        }
    }

    // ---- Commit flow ----

    @Test
    fun `feedback commits session and re-bootstraps next split`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllSets()
            advanceUntilIdle()

            // After commit the history grows, which rotates today's expected split.
            val captured = slot<Session>()
            coEvery { sessionRepository.addSession(capture(captured)) } answers {
                historyFlow.value = historyFlow.value + captured.captured
                42L
            }

            vm.onFeedback(Feedback.Green)
            advanceUntilIdle()

            assertThat(captured.captured.feedback).isEqualTo(Feedback.Green)
            assertThat(captured.captured.split).isEqualTo(Split.A)
            // After commit + re-bootstrap, the in-progress now reflects Split.B.
            assertThat(inProgressFlow.value!!.split).isEqualTo(Split.B)
        }
    }

    @Test
    fun `feedback is a no-op while any set is still Pending`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            // Don't resolve sets — they remain Pending.
            vm.onFeedback(Feedback.Green)
            advanceUntilIdle()

            coVerify(exactly = 0) { sessionRepository.addSession(any()) }
        }
    }

    // ---- KB bump actions ----

    @Test
    fun `accepting KB bump persists the next ladder weight and hides the prompt`() {
        historyFlow.value = listOf(sessionAt(today.minusMonths(3), Split.C))
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            coEvery { settingsRepository.bumpKbWeight(any()) } answers {
                defaultsFlow.value = defaultsFlow.value!!.copy(kbWeightKg = firstArg()); testDispatcher.scheduler.runCurrent()
            }

            vm.onKbBumpAccept()
            advanceUntilIdle()

            coVerify { settingsRepository.bumpKbWeight(20.0) }
            // No snooze stamp: the new weight has no completed sessions, which
            // suppresses the prompt structurally.
            coVerify(exactly = 0) { settingsRepository.saveKbBumpSnooze(any()) }
            // Bootstrap re-ran and the new in-progress carries the bumped weight.
            assertThat(inProgressFlow.value!!.kbWeightKg).isEqualTo(20.0)

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.kbBump).isNull()
            // Freshly bumped weight starts the rep ramp at its lowest stage.
            assertThat(ready.kbBlock.movements.map { it.repsLabel })
                .containsExactly("20", "10/side", "5").inOrder()
        }
    }

    @Test
    fun `snoozing KB bump records snooze with current history size`() {
        historyFlow.value = listOf(
            sessionAt(today.minusMonths(1).withDayOfMonth(5), Split.A),
            sessionAt(today.minusMonths(1).withDayOfMonth(12), Split.B),
        )
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            var capturedSnooze: KbBumpSnooze? = null
            coEvery { settingsRepository.saveKbBumpSnooze(any()) } answers {
                capturedSnooze = firstArg()
            }

            vm.onKbBumpSnooze()
            advanceUntilIdle()

            assertThat(capturedSnooze?.snoozedAtMonth).isEqualTo(YearMonth.from(today))
            assertThat(capturedSnooze?.sessionCountAtSnooze).isEqualTo(2)
        }
    }

    @Test
    fun `forceSplit replaces in-progress with specified split`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            assertThat(inProgressFlow.value!!.split).isEqualTo(Split.A)

            vm.forceSplit(Split.C)
            advanceUntilIdle()

            assertThat(inProgressFlow.value!!.split).isEqualTo(Split.C)
        }
    }

    // ---- Manual weight overrides ----

    @Test
    fun `onExerciseWeightChange updates both in-progress and persisted settings and resets reps`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            val slug = ExerciseCatalog.LatPulldown.slug
            val minReps = ExerciseCatalog.LatPulldown.minReps

            vm.onExerciseWeightChange(slug, 55.0)
            advanceUntilIdle()

            coVerify { inProgressRepository.updateExerciseWeight(slug, 55.0, minReps) }
            coVerify { settingsRepository.updateStartingWeight(slug, 55.0) }

            val ready = vm.state.value as TrackerUiState.Ready
            val pulldown = ready.strength.first { it.exercise.slug == slug }
            assertThat(pulldown.weightKg).isEqualTo(55.0)
            assertThat(pulldown.targetReps).isEqualTo(minReps)
        }
    }

    @Test
    fun `onKbWeightChange updates both in-progress and persisted settings and updates UI`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onKbWeightChange(20.0)
            advanceUntilIdle()

            coVerify { inProgressRepository.updateKbWeight(20.0) }
            coVerify { inProgressRepository.updateExerciseWeight(ExerciseCatalog.KbFlow.slug, 20.0, null) }
            coVerify { settingsRepository.updateKbWeight(20.0) }

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.kbWeightKg).isEqualTo(20.0)
        }
    }

    // ---- Auxiliary flow ----

    @Test
    fun `aux prompt appears once main work is resolved`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllSets()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.mainResolved).isTrue()
            assertThat(ready.showAuxPrompt).isTrue()
            assertThat(ready.feedbackReady).isFalse()
            assertThat(ready.phase).isEqualTo(TrackerPhase.MAIN)
        }
    }

    @Test
    fun `declining aux moves straight to feedback`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllSets()
            advanceUntilIdle()

            vm.onSkipAux()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.showAuxPrompt).isFalse()
            assertThat(ready.feedbackReady).isTrue()
            assertThat(ready.phase).isEqualTo(TrackerPhase.MAIN)
        }
    }

    @Test
    fun `starting aux appends aux rows and enters AUX phase`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllSets()
            advanceUntilIdle()

            vm.onStartAux()
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.phase).isEqualTo(TrackerPhase.AUX)
            assertThat(ready.aux.map { it.exercise.slug }).containsExactly(
                ExerciseCatalog.SideDeltFly.slug,
                ExerciseCatalog.TricepExtension.slug,
                ExerciseCatalog.BackExtension.slug,
            ).inOrder()
            ready.aux.forEach { row -> assertThat(row.working).hasSize(3) }
            // Falls back to the movement's default starting weight (no history yet).
            val fly = ready.aux.first { it.exercise.slug == ExerciseCatalog.SideDeltFly.slug }
            assertThat(fly.weightKg).isEqualTo(6.0)
            // Aux not yet resolved → no feedback sheet.
            assertThat(ready.feedbackReady).isFalse()
        }
    }

    @Test
    fun `feedback after aux commits main and aux sets in one session`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            resolveAllSets()
            advanceUntilIdle()
            vm.onStartAux()
            advanceUntilIdle()
            resolveAllSets() // now resolves main + aux rows together
            advanceUntilIdle()

            val ready = vm.state.value as TrackerUiState.Ready
            assertThat(ready.feedbackReady).isTrue()

            val captured = slot<Session>()
            coEvery { sessionRepository.addSession(capture(captured)) } answers {
                historyFlow.value = historyFlow.value + captured.captured
                7L
            }

            vm.onFeedback(Feedback.Green)
            advanceUntilIdle()

            val auxSlugs = ExerciseCatalog.auxForSplit(Split.A).map { it.slug }.toSet()
            // 3 aux movements × (1 prime + 1 warm-up + 3 working) = 15 aux sets.
            assertThat(captured.captured.sets.count { it.exerciseSlug in auxSlugs }).isEqualTo(15)
            // 13 main (3 KB + 2×5) + 15 aux = 28.
            assertThat(captured.captured.sets).hasSize(28)
        }
    }

    // ---- helpers ----

    private fun sessionAt(date: LocalDate, split: Split, kbWeight: Double = 16.0) = Session(
        date = date,
        split = split,
        feedback = Feedback.Green,
        kbWeightKg = kbWeight,
        sets = emptyList(),
    )

    private fun resolveAllSets() {
        val current = inProgressFlow.value!!
        inProgressFlow.value = current.copy(
            sets = current.sets.map { it.copy(status = SetStatus.Completed) },
        )
    }
}
