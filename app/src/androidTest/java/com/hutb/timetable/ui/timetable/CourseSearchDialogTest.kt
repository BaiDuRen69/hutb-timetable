package com.hutb.timetable.ui.timetable

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseSearchDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val timetable = ClassTimetable.defaultTimetables().first()
    private val math = Course(
        courseName = "高等数学",
        teacher = "张老师",
        className = "计科2301",
        classroom = "至诚楼A201",
        weekday = 1,
        startPeriod = 1,
        endPeriod = 2
    )
    private val physics = Course(
        courseName = "大学物理",
        teacher = "李老师",
        className = "计科2301",
        classroom = "至诚楼B405",
        weekday = 3,
        startPeriod = 3,
        endPeriod = 4
    )

    @Test
    fun filtersByTeacherAndOpensCourse() {
        var selected: Course? = null
        compose.setContent {
            HutbTimetableTheme {
                CourseSearchDialog(
                    courses = listOf(math, physics),
                    timetable = timetable,
                    week = 8,
                    onDismiss = {},
                    onSelect = { selected = it }
                )
            }
        }

        compose.onNodeWithTag("courseSearchField").performTextInput("张老师")
        compose.onNodeWithText("高等数学").assertExists()
        compose.onNodeWithText("大学物理").assertDoesNotExist()
        compose.onNodeWithText("高等数学").performClick()
        compose.runOnIdle { assertEquals(math, selected) }
    }

    @Test
    fun emptyQueryShowsHint() {
        compose.setContent {
            HutbTimetableTheme {
                CourseSearchDialog(
                    courses = listOf(math, physics),
                    timetable = timetable,
                    week = 8,
                    onDismiss = {},
                    onSelect = {}
                )
            }
        }

        compose.onNodeWithText("输入关键词开始搜索；多个关键词用空格分隔").assertExists()
    }
}
