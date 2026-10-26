package com.hutb.timetable.ui.timetable

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmptyRoomsViewTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clearFilterActionIsAvailable() {
        var cleared = false
        compose.setContent {
            HutbTimetableTheme {
                EmptyRoomsView(
                    rooms = listOf("至诚楼A101"),
                    shortNames = true,
                    buildingName = "至诚楼",
                    onClearFilter = { cleared = true }
                )
            }
        }

        compose.onNodeWithText("清除筛选").assertIsDisplayed()
        compose.onNodeWithText("清除筛选").performClick()
        compose.runOnIdle { assertTrue(cleared) }
    }
}