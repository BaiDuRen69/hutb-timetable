package com.hutb.timetable.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisclaimerDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun requiresExplicitConfirmation() {
        var confirmed = false
        compose.setContent {
            HutbTimetableTheme {
                DisclaimerDialog(onConfirm = { confirmed = true })
            }
        }

        compose.onNodeWithText("课表仅供参考").assertIsDisplayed()
        compose.onNodeWithText("我已了解").assertIsDisplayed()
        compose.runOnIdle { assertFalse(confirmed) }

        compose.onNodeWithText("我已了解").performClick()
        compose.runOnIdle { assertTrue(confirmed) }
    }
}
