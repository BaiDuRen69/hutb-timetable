package com.hutb.timetable.ui.timetable

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeekGridTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun threePeriodCourseOnlyAddsOneContinuationCell() {
        val course = Course(
            courseName = "高等数学",
            teacher = "张老师",
            classroom = "至诚楼A201",
            weekday = 1,
            startPeriod = 1,
            endPeriod = 3
        )
        compose.setContent {
            HutbTimetableTheme {
                WeekGrid(
                    courses = listOf(course),
                    filter = ScheduleFilter(),
                    onCourseClick = {},
                    isCurrentWeek = false
                )
            }
        }

        compose.onNodeWithText("高等数学").assertIsDisplayed()
        compose.onAllNodesWithText("续 · 高等数学").assertCountEquals(1)
        compose.onAllNodesWithText("高等数学").assertCountEquals(1)
    }
}
