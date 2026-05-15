package com.kbminisplit.ui.tracker

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
                        ExerciseCatalog.Deadlift.slug to 60.0,
                    ),
                    startingTargetReps = 8,
                )
            )
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

        // 3. Complete Strength 1 (Lat Pulldown in Split A)
        val m1 = "Lat Pulldown"
        val m1Prime = "$m1 priming set"
        composeTestRule.onNodeWithContentDescription(m1Prime).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule.onAllNodes(
                hasContentDescription(m1Prime) and hasStateDescription("Completed")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        for (i in 1..3) {
            val desc = "$m1 working set $i"
            composeTestRule.onNodeWithContentDescription(desc).performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                composeTestRule.onAllNodes(
                    hasContentDescription(desc) and hasStateDescription("Completed")
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // 4. Complete Strength 2 (Barbell Row in Split A)
        val m2 = "Barbell Row"
        val m2Prime = "$m2 priming set"
        composeTestRule.onNodeWithContentDescription(m2Prime).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule.onAllNodes(
                hasContentDescription(m2Prime) and hasStateDescription("Completed")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        for (i in 1..3) {
            val desc = "$m2 working set $i"
            composeTestRule.onNodeWithContentDescription(desc).performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 2000) {
                composeTestRule.onAllNodes(
                    hasContentDescription(desc) and hasStateDescription("Completed")
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // 5. Verify Feedback sheet appears
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasTestTag("feedback_sheet")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("feedback_sheet").assertIsDisplayed()

        // 6. Tap a feedback color (e.g., Green)
        composeTestRule.onNodeWithContentDescription("Feedback green").performClick()

        // 7. Verify we are back on Tracker, now showing Split B
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasContentDescription("Bench Press priming set")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
