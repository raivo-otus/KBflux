package com.kbminisplit.ui.info

import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.OnboardingDefaults
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
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
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class InfoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val defaultsFlow = MutableStateFlow<OnboardingDefaults?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsRepository.observeOnboardingDefaults() } returns defaultsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onboardingDefaults reflects repository state`() = runTest {
        val vm = InfoViewModel(settingsRepository)
        
        vm.onboardingDefaults.test {
            assertThat(awaitItem()).isNull()

            val defaults = OnboardingDefaults(16.0, emptyMap(), 8, 12)
            defaultsFlow.value = defaults
            assertThat(awaitItem()).isEqualTo(defaults)
        }
    }
}
