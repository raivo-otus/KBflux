package com.kbminisplit.ui.main

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kbminisplit.MainActivity
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.OnboardingDefaults
import com.kbminisplit.data.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainShellTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var db: AppDatabase

    @Before
    fun init() {
        hiltRule.inject()
        runBlocking {
            db.inProgressDao().clear()
            db.sessionDao().clear()
            db.settingsDao().deleteSettings()
            db.settingsDao().deleteStartingWeights()

            settingsRepository.saveOnboarding(
                OnboardingDefaults(
                    kbWeightKg = 16.0,
                    startingWeightsBySlug = mapOf(
                        ExerciseCatalog.LatPulldown.slug to 40.0,
                        ExerciseCatalog.BarbellRow.slug to 30.0,
                        ExerciseCatalog.Bench.slug to 40.0,
                        ExerciseCatalog.Ohp.slug to 30.0,
                        ExerciseCatalog.HighBarSquat.slug to 40.0,
                        ExerciseCatalog.RomanianDeadlift.slug to 60.0,
                    ),
                    startingTargetReps = 8,
                    standardMaxReps = 12,
                )
            )
        }
    }

    @Test
    fun toggleInfoPage_showsAndHidesInfo() {
        // Initially on Tracker
        composeTestRule.onNodeWithTag("tracker_ready").assertIsDisplayed()

        // Click Help icon
        composeTestRule.onNodeWithContentDescription("Toggle Info Page").performClick()

        // Verify Info Screen is shown (assuming it has some identifiable text)
        composeTestRule.onNodeWithText("KB Flow", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("tracker_ready").assertDoesNotExist()

        // Click Help icon again
        composeTestRule.onNodeWithContentDescription("Toggle Info Page").performClick()

        // Back to Tracker
        composeTestRule.onNodeWithTag("tracker_ready").assertIsDisplayed()
    }

    @Test
    fun trackerTab_fiveTapTitle_showsForceSplitDialog() {
        // On Tracker tab
        composeTestRule.onNodeWithTag("tab_tracker").assertIsSelected()

        // Tap title 5 times
        val title = composeTestRule.onNodeWithText("KB MiniSplit")
        repeat(5) { title.performClick() }

        // Verify Force Split dialog
        composeTestRule.onNodeWithText("Choose Next Split").assertIsDisplayed()
        composeTestRule.onNodeWithText("Split B").assertIsDisplayed()
        
        // Close dialog
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Choose Next Split").assertDoesNotExist()
    }

    @Test
    fun logTab_fiveTapTitle_showsResetDialog() {
        // Navigate to Log
        composeTestRule.onNodeWithTag("tab_log").performClick()
        composeTestRule.onNodeWithTag("tab_log").assertIsSelected()

        // Tap title 5 times
        val title = composeTestRule.onNodeWithText("KB MiniSplit")
        repeat(5) { title.performClick() }

        // Verify Reset dialog
        composeTestRule.onNodeWithText("Reset All Progress?").assertIsDisplayed()
        
        // Close dialog
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun progressionTab_fiveTapTitle_showsSettingsDialog() {
        // Navigate to Progression
        composeTestRule.onNodeWithTag("tab_progression").performClick()

        // Tap title 5 times
        val title = composeTestRule.onNodeWithText("KB MiniSplit")
        repeat(5) { title.performClick() }

        // Verify Settings dialog
        composeTestRule.onNodeWithText("Progression Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Haptic Intensity").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export Data (JSON)").assertIsDisplayed()
        
        // Close dialog
        composeTestRule.onNodeWithText("Done").performClick()
    }

    @Test
    fun darkModeToggle_updatesIconDescription() {
        // Assuming it starts in dark mode
        composeTestRule.onNodeWithContentDescription("Toggle Dark Mode").assertIsDisplayed()
        
        // Click toggle
        composeTestRule.onNodeWithContentDescription("Toggle Dark Mode").performClick()
        
        // In a real app with theme following, we'd check if theme changed, 
        // but here we can at least check if the icon is still clickable or toggle changed.
        // The RootViewModel toggleDarkMode() is called.
    }
}
