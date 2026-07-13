package com.kbminisplit.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.SetStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun singleTapFromPending_invokesOnCompleteExactlyOnce() {
        var status by mutableStateOf(SetStatus.Pending)
        var completeCount = 0
        var failCount = 0
        var revertCount = 0
        composeTestRule.setContent {
            SetButton(
                status = status,
                contentDescription = "Set",
                onComplete = {
                    completeCount++
                    status = SetStatus.Completed
                },
                onFail = {
                    failCount++
                    status = SetStatus.Failed
                },
                onRevert = {
                    revertCount++
                    status = SetStatus.Pending
                },
            )
        }

        composeTestRule.onNodeWithContentDescription("Set").performTouchInput { click() }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()

        assertThat(completeCount).isEqualTo(1)
        assertThat(failCount).isEqualTo(0)
        assertThat(revertCount).isEqualTo(0)
    }

    @Test
    fun doubleTapFromPending_invokesOnFailOnceAndNotOnComplete() {
        var status by mutableStateOf(SetStatus.Pending)
        var completeCount = 0
        var failCount = 0
        composeTestRule.setContent {
            SetButton(
                status = status,
                contentDescription = "Set",
                onComplete = {
                    completeCount++
                    status = SetStatus.Completed
                },
                onFail = {
                    failCount++
                    status = SetStatus.Failed
                },
                onRevert = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Set").performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()

        assertThat(failCount).isEqualTo(1)
        assertThat(completeCount).isEqualTo(0)
    }

    @Test
    fun longPressFromPending_doesNotInvokeOnRevert() {
        var revertCount = 0
        composeTestRule.setContent {
            SetButton(
                status = SetStatus.Pending,
                contentDescription = "Set",
                onComplete = {},
                onFail = {},
                onRevert = { revertCount++ },
            )
        }

        composeTestRule.onNodeWithContentDescription("Set").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertThat(revertCount).isEqualTo(0)
    }

    @Test
    fun longPressAfterStatusFlipsToCompleted_invokesOnRevert() {
        var status by mutableStateOf(SetStatus.Pending)
        var revertCount = 0
        composeTestRule.setContent {
            SetButton(
                status = status,
                contentDescription = "Set",
                onComplete = { status = SetStatus.Completed },
                onFail = {},
                onRevert = {
                    revertCount++
                    status = SetStatus.Pending
                },
            )
        }

        composeTestRule.onNodeWithContentDescription("Set").performTouchInput { click() }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Set").performTouchInput { longClick() }
        composeTestRule.waitForIdle()

        assertThat(revertCount).isEqualTo(1)
    }

    @Test
    fun centerText_showsWhilePendingAndIsReplacedByGlyphOnceResolved() {
        var status by mutableStateOf(SetStatus.Pending)
        composeTestRule.setContent {
            SetButton(
                status = status,
                contentDescription = "Set",
                onComplete = { status = SetStatus.Completed },
                onFail = {},
                onRevert = {},
                centerText = "25",
            )
        }

        // Pending Prime/Warm-up: the acclimatization load is shown inside the circle.
        composeTestRule.onNodeWithText("25").assertIsDisplayed()

        // Once resolved, the status glyph replaces the number.
        composeTestRule.onNodeWithContentDescription("Set").performTouchInput { click() }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("25").assertDoesNotExist()
    }
}
