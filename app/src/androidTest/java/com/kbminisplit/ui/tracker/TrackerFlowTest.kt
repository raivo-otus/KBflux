package com.kbminisplit.ui.tracker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kbminisplit.MainActivity
import com.kbminisplit.data.db.AppDatabase
import com.kbminisplit.data.db.seedDefaultProgram
import com.kbminisplit.data.db.seedExerciseRegistry
import com.kbminisplit.data.repository.ProgramRepository
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
class TrackerFlowTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var programRepository: ProgramRepository

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
            settingsRepository.markProgramSeen()
            // Log a current bodyweight so the assisted-lift check-in stays dormant.
            settingsRepository.updateBodyweight(80.0)
        }
    }

    private fun awaitTracker() {
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodes(hasTestTag("tracker_ready")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun tapSet_changesStateAndStartsTheRestGuide() {
        awaitTracker()

        composeTestRule.onNode(
            hasContentDescription("Kettlebell flow round 1") and hasStateDescription("Pending")
        ).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Kettlebell flow round 1").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(
                hasContentDescription("Kettlebell flow round 1") and hasStateDescription("Completed")
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("rest_timer")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rest_timer").assertIsDisplayed()
    }

    @Test
    fun completeSessionFlow() {
        awaitTracker()

        // 1. The circuit's three rounds.
        for (i in 1..3) {
            val desc = "Kettlebell flow round $i"
            composeTestRule.onNodeWithContentDescription(desc).performClick()
            awaitCompleted(desc)
        }

        // 2. The main block: two movements with prime + warm-up + three work sets.
        completeMovement("Lat Pulldown", leadIns = listOf("Prime", "Warm-up"), workingSets = 3)
        completeMovement("Barbell Row", leadIns = listOf("Prime", "Warm-up"), workingSets = 3)

        // 3. Completing every set of a movement offers the next weight up.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("bump_chip")).fetchSemanticsNodes().isNotEmpty()
        }

        // 4. The accessory block reveals itself once the main work is resolved.
        completeMovement("Side-Delt Flyes", leadIns = emptyList(), workingSets = 3)
        completeMovement("Tricep Extensions", leadIns = emptyList(), workingSets = 3)
        completeMovement("Back Extensions", leadIns = emptyList(), workingSets = 3)

        // 5. Feedback is mandatory and appears only once everything is resolved.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("feedback_sheet")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("feedback_sheet").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Feedback green").performClick()

        // 6. Committing navigates to the Log.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("tab_log")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("tab_log").assertIsSelected()

        // 7. The Tracker has already rolled over to the next day.
        composeTestRule.onNodeWithTag("tab_tracker").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodes(hasContentDescription("Bench Press Prime set"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Bench Press Prime set").assertIsDisplayed()
    }

    private fun awaitCompleted(description: String) {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(
                hasContentDescription(description) and hasStateDescription("Completed")
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Taps every button of a movement in order, waiting out each state change. */
    private fun completeMovement(name: String, leadIns: List<String>, workingSets: Int) {
        val descriptions = leadIns.map { "$name $it set" } +
            (1..workingSets).map { "$name working set $it" }
        descriptions.forEach { desc ->
            composeTestRule.waitUntil(timeoutMillis = 5000) {
                composeTestRule.onAllNodes(hasContentDescription(desc)).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription(desc).performScrollTo().performClick()
            awaitCompleted(desc)
        }
    }
}
