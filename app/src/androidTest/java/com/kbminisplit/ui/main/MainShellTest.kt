package com.kbminisplit.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kbminisplit.MainActivity
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.seedDefaultProgram
import com.kbminisplit.data.db.seedExerciseRegistry
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
            db.programDao().clear()
            db.settingsDao().deleteSettings()

            seedExerciseRegistry(db)
            seedDefaultProgram(db, nowMillis = 0L)
            // Not a first launch, so the app opens on the Tracker.
            settingsRepository.markProgramSeen()
        }
    }

    private fun awaitTracker() {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("tracker_ready")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun toggleInfoPage_showsAndHidesInfo() {
        awaitTracker()
        composeTestRule.onNodeWithTag("tracker_ready").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Toggle Info Page").performClick()

        composeTestRule.onNodeWithText("Philosophy", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("tracker_ready").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Toggle Info Page").performClick()

        composeTestRule.onNodeWithTag("tracker_ready").assertIsDisplayed()
    }

    @Test
    fun trackerTab_fiveTapTitle_showsDayPicker() {
        awaitTracker()
        composeTestRule.onNodeWithTag("tab_tracker").assertIsSelected()

        val title = composeTestRule.onNodeWithText("KB MiniSplit")
        repeat(5) { title.performClick() }

        composeTestRule.onNodeWithText("Choose Next Day").assertIsDisplayed()
        composeTestRule.onNodeWithText("Push").assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Choose Next Day").assertDoesNotExist()
    }

    @Test
    fun logTab_fiveTapTitle_showsResetDialog() {
        composeTestRule.onNodeWithTag("tab_log").performClick()
        composeTestRule.onNodeWithTag("tab_log").assertIsSelected()

        val title = composeTestRule.onNodeWithText("KB MiniSplit")
        repeat(5) { title.performClick() }

        composeTestRule.onNodeWithText("Reset All Progress?").assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun programTab_fiveTapTitle_showsSettingsDialog() {
        composeTestRule.onNodeWithTag("tab_program").performClick()

        val title = composeTestRule.onNodeWithText("KB MiniSplit")
        repeat(5) { title.performClick() }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Haptic Intensity").assertIsDisplayed()
        composeTestRule.onNodeWithText("Export Data (JSON)").assertIsDisplayed()

        composeTestRule.onNodeWithText("Done").performClick()
    }

    @Test
    fun programTab_showsTheSeededDays() {
        composeTestRule.onNodeWithTag("tab_program").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("program_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Pull").assertIsDisplayed()
        composeTestRule.onNodeWithText("Push").assertIsDisplayed()
        composeTestRule.onNodeWithText("Legs").assertIsDisplayed()
    }

    @Test
    fun darkModeToggle_updatesIconDescription() {
        composeTestRule.onNodeWithContentDescription("Toggle Dark Mode").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Toggle Dark Mode").performClick()

        composeTestRule.onNodeWithContentDescription("Toggle Dark Mode").assertIsDisplayed()
    }
}
