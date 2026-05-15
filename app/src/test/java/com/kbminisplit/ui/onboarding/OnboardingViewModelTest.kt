package com.kbminisplit.ui.onboarding

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.OnboardingDefaults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true) {
            coEvery { saveOnboarding(any()) } returns Unit
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): OnboardingViewModel = OnboardingViewModel(settingsRepository)

    @Test
    fun `initial state is on KB step with valid prefilled defaults`() {
        val vm = newViewModel()
        val s = vm.state.value

        assertThat(s.step).isEqualTo(OnboardingStep.Kb)
        assertThat(s.kbStepValid).isTrue()
        assertThat(s.strengthStepValid).isTrue()
        assertThat(s.repsStepValid).isTrue()
        assertThat(s.canSubmit).isTrue()
        assertThat(s.isComplete).isFalse()
    }

    @Test
    fun `next advances Kb to StrengthWeights when valid`() {
        val vm = newViewModel()

        vm.next()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.StrengthWeights)
    }

    @Test
    fun `next does not advance when KB is invalid`() {
        val vm = newViewModel()
        vm.onKbWeightChanged("")

        vm.next()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Kb)
    }

    @Test
    fun `next from StrengthWeights advances to TargetReps`() {
        val vm = newViewModel()
        vm.goToStep(OnboardingStep.StrengthWeights)

        vm.next()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.TargetReps)
    }

    @Test
    fun `next from StrengthWeights blocked when one weight is empty`() {
        val vm = newViewModel()
        vm.goToStep(OnboardingStep.StrengthWeights)
        vm.onStrengthWeightChanged(ExerciseCatalog.Bench.slug, "")

        vm.next()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.StrengthWeights)
    }

    @Test
    fun `next from TargetReps stays put`() {
        val vm = newViewModel()
        vm.goToStep(OnboardingStep.TargetReps)

        vm.next()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.TargetReps)
    }

    @Test
    fun `back walks the steps in reverse and stops at Kb`() {
        val vm = newViewModel()
        vm.goToStep(OnboardingStep.TargetReps)

        vm.back()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.StrengthWeights)

        vm.back()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Kb)

        vm.back()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Kb)
    }

    @Test
    fun `comma decimal separator parses as a positive weight`() {
        val vm = newViewModel()
        vm.onKbWeightChanged("18,5")

        assertThat(vm.state.value.kbWeightKg).isEqualTo(18.5)
        assertThat(vm.state.value.kbStepValid).isTrue()
    }

    @Test
    fun `non-numeric input is rejected`() {
        val vm = newViewModel()
        vm.onKbWeightChanged("heavy")

        assertThat(vm.state.value.kbWeightKg).isNull()
        assertThat(vm.state.value.kbStepValid).isFalse()
    }

    @Test
    fun `zero or negative weight is rejected`() {
        val vm = newViewModel()
        vm.onKbWeightChanged("0")
        assertThat(vm.state.value.kbStepValid).isFalse()

        vm.onKbWeightChanged("-4")
        assertThat(vm.state.value.kbStepValid).isFalse()
    }

    @Test
    fun `target reps must be in 1 to 16`() {
        val vm = newViewModel()
        vm.onTargetRepsChanged("0")
        assertThat(vm.state.value.repsStepValid).isFalse()

        vm.onTargetRepsChanged("17")
        assertThat(vm.state.value.repsStepValid).isFalse()

        vm.onTargetRepsChanged("8")
        assertThat(vm.state.value.repsStepValid).isTrue()
    }

    @Test
    fun `complete persists parsed defaults and flips isComplete`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.onKbWeightChanged("18")
            vm.onStrengthWeightChanged(ExerciseCatalog.Bench.slug, "55")
            vm.onTargetRepsChanged("9")

            val captured = slot<OnboardingDefaults>()
            coEvery { settingsRepository.saveOnboarding(capture(captured)) } returns Unit

            vm.complete()
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.saveOnboarding(any()) }
            assertThat(captured.captured.kbWeightKg).isEqualTo(18.0)
            assertThat(captured.captured.startingTargetReps).isEqualTo(9)
            assertThat(captured.captured.startingWeightsBySlug[ExerciseCatalog.Bench.slug])
                .isEqualTo(55.0)
            // The other strength movements still carry the prefilled defaults.
            assertThat(captured.captured.startingWeightsBySlug[ExerciseCatalog.Deadlift.slug])
                .isEqualTo(60.0)

            assertThat(vm.state.value.isComplete).isTrue()
            assertThat(vm.state.value.isSaving).isFalse()
        }
    }

    @Test
    fun `complete is a no-op when any field is invalid`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            vm.onKbWeightChanged("")

            vm.complete()
            advanceUntilIdle()

            coVerify(exactly = 0) { settingsRepository.saveOnboarding(any()) }
            assertThat(vm.state.value.isComplete).isFalse()
        }
    }

    @Test
    fun `complete is idempotent — second call after success does not re-save`() {
        runTest(testDispatcher) {
            val vm = newViewModel()

            vm.complete()
            advanceUntilIdle()
            vm.complete()
            advanceUntilIdle()

            coVerify(exactly = 1) { settingsRepository.saveOnboarding(any()) }
        }
    }
}
