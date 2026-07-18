package com.kbminisplit.ui.tracker

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kbminisplit.MainActivity
import com.kbminisplit.data.repository.InProgressRepository
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.data.repository.SettingsRepository
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.OnboardingDefaults
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
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var inProgressRepository: InProgressRepository

    @Inject
    lateinit var db: com.kbminisplit.data.db.AppDatabase

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
            // Log a current bodyweight so the assisted-lift bodyweight check-in (which
            // now auto-opens on Split B) stays dormant during the flow.
            settingsRepository.updateBodyweight(80.0)
        }
    }

    @Test
    fun tapSet_changesState() {
        // 1. Wait for Tracker
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("tracker_ready")).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Initial state: Pending
        composeTestRule.onNode(
            hasContentDescription("KB Flow circuit 1") and hasStateDescription("Pending")
        ).assertIsDisplayed()

        // 3. Tap it
        composeTestRule.onNodeWithContentDescription("KB Flow circuit 1").performClick()

        // 4. State should be Completed
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(
                hasContentDescription("KB Flow circuit 1") and hasStateDescription("Completed")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(
            hasContentDescription("KB Flow circuit 1") and hasStateDescription("Completed")
        ).assertIsDisplayed()

        // 5. Completing a set starts the rest guide.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("rest_timer")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rest_timer").assertIsDisplayed()
    }

    @Test
    fun completeSessionFlow() {
        // 1. Verify Tracker is shown (Wait for bootstrap)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("tracker_ready")).fetchSemanticsNodes().isNotEmpty()
        }

        // 2. Complete KB circuits (3 circuits)
        for (i in 1..3) {
            val desc = "KB Flow circuit $i"
            composeTestRule.onNodeWithContentDescription(desc).performClick()
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                composeTestRule.onAllNodes(
                    hasContentDescription(desc) and hasStateDescription("Completed")
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // 3. Complete the two strength movements of Split A
        completeMovement("Lat Pulldown")
        completeMovement("Barbell Row")

        // 4. Main resolved → aux block is appended automatically (no prompt).
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("aux_block")).fetchSemanticsNodes().isNotEmpty()
        }
        completeMovement("Side-Delt Flyes")
        completeMovement("Tricep Extensions")
        completeMovement("Back Extensions")

        // 5. Verify Feedback sheet appears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("feedback_sheet")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("feedback_sheet").assertIsDisplayed()

        // 6. Tap a feedback color (e.g., Green)
        composeTestRule.onNodeWithContentDescription("Feedback green").performClick()

        // 7. Verify Navigation to Log
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("tab_log")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("tab_log").assertIsSelected()

        // 8. Click Tracker tab to see next prescription
        composeTestRule.onNodeWithTag("tab_tracker").performClick()

        // 9. Verify we are back on Tracker, now showing Split B
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodes(hasContentDescription("Bench Press priming set")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Bench Press priming set").assertIsDisplayed()
    }

    /** Prime, warm-up, then the three working sets of [name], waiting out each state change. */
    private fun completeMovement(name: String) {
        val descriptions = listOf("$name priming set", "$name warm-up set") +
            (1..3).map { "$name working set $it" }
        descriptions.forEach { desc ->
            composeTestRule.onNodeWithContentDescription(desc).performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                composeTestRule.onAllNodes(
                    hasContentDescription(desc) and hasStateDescription("Completed")
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
