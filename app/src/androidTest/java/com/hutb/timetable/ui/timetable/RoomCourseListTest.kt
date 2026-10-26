package com.hutb.timetable.ui.timetable

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCourseListTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersCourseAndReactsToClick() {
        val course = Course(
            courseName = "高等数学",
            teacher = "张老师",
            classroom = "至诚楼A201",
            weekday = 1,
            startPeriod = 1,
            endPeriod = 2
        )
        var clicked: Course? = null
        compose.setContent {
            HutbTimetableTheme {
                RoomCourseList(courses = listOf(course), onCourseClick = { clicked = it })
            }
        }

        compose.onNodeWithText("周一").assertIsDisplayed()
        compose.onNodeWithText("第1-2节").assertIsDisplayed()
        compose.onNodeWithText("高等数学").assertIsDisplayed()
        compose.onNodeWithText("至诚楼A201 · 张老师").assertIsDisplayed()

        compose.onNodeWithText("高等数学").performClick()
        compose.runOnIdle { assertEquals(course, clicked) }
    }

    @Test
    fun showsEmptyStateWhenFilterHasNoCourse() {
        compose.setContent {
            HutbTimetableTheme {
                RoomCourseList(courses = emptyList(), onCourseClick = {})
            }
        }
        compose.onNodeWithText("当前筛选条件下没有课程").assertIsDisplayed()
    }
}
