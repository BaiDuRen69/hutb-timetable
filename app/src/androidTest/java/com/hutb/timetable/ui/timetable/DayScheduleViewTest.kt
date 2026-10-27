package com.hutb.timetable.ui.timetable

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayScheduleViewTest {

    @get:Rule
    val compose = createComposeRule()

    private val mondayCourse = Course(
        courseName = "高等数学",
        teacher = "张老师",
        classroom = "至诚楼A201",
        weekday = 1,
        startPeriod = 1,
        endPeriod = 2
    )
    private val tuesdayCourse = Course(
        courseName = "大学物理",
        teacher = "李老师",
        classroom = "至诚楼B405",
        weekday = 2,
        startPeriod = 3,
        endPeriod = 4
    )

    @Test
    fun rendersSelectedDayAndSwitchesByChip() {
        compose.setContent {
            HutbTimetableTheme {
                DayScheduleView(
                    week = 8,
                    semesterStartDate = "2026-09-07",
                    courses = listOf(mondayCourse, tuesdayCourse),
                    filter = ScheduleFilter(),
                    timetable = ClassTimetable.defaultTimetables().first(),
                    isCurrentWeek = false,
                    onCourseClick = {},
                    onClearFilter = {}
                )
            }
        }

        compose.onNodeWithText("高等数学").assertIsDisplayed()
        compose.onNodeWithText("10月26日 · 周一").assertIsDisplayed()
        compose.onNodeWithText("二").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("大学物理").assertIsDisplayed()
    }

    @Test
    fun filterExcludedDayShowsClearAction() {
        compose.setContent {
            HutbTimetableTheme {
                DayScheduleView(
                    week = 8,
                    semesterStartDate = "2026-09-07",
                    courses = listOf(mondayCourse, tuesdayCourse),
                    filter = ScheduleFilter(weekdays = setOf(2)),
                    timetable = ClassTimetable.defaultTimetables().first(),
                    isCurrentWeek = false,
                    onCourseClick = {},
                    onClearFilter = {}
                )
            }
        }

        compose.onNodeWithText("清除筛选").assertIsDisplayed()
    }
}
