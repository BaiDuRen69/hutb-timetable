package com.hutb.timetable.ui.timetable

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.CourseChange
import com.hutb.timetable.core.CourseChangeKind
import com.hutb.timetable.core.Semester
import com.hutb.timetable.core.WeekSchedule
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimetableScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun expiredSessionStillRendersCachedSchedule() {
        val course = Course(
            courseName = "高等数学",
            teacher = "张老师",
            classroom = "至诚楼A201",
            weekday = 1,
            startPeriod = 1,
            endPeriod = 2
        )
        val state = TimetableUiState(
            schedule = ScheduleState(
                data = WeekSchedule(
                    semesterId = "2026-2027-1",
                    semesterName = "2026-2027-1",
                    week = 8,
                    courses = listOf(course),
                    isFromCache = true
                ),
                semesters = listOf(Semester("2026-2027-1", "2026-2027-1")),
                currentSemesterId = "2026-2027-1",
                currentWeek = 8,
                fromCache = true
            ),
            session = SessionState(loggedIn = false, expired = true),
            timetable = ClassTimetable.defaultTimetables().first(),
            semesterStartDate = "2026-09-07"
        )

        compose.setContent {
            HutbTimetableTheme {
                TimetableScreen(
                    state = state,
                    onRefresh = {},
                    onPrevWeek = {},
                    onNextWeek = {},
                    onSelectWeek = {},
                    onSelectSemester = {},
                    onLogin = {},
                    onLogout = {},
                    onSwitchQueryMode = {},
                    onPickRoom = {},
                    onShowSnapshots = {},
                    onFilterChange = {}
                )
            }
        }

        compose.onNodeWithText("登录已失效，当前显示的是本地缓存").assertIsDisplayed()
        // 重点是缓存课表被渲染而不是被整屏会话空态替代；测试窗口较矮时不要求首行在视口内
        compose.onNodeWithText("高等数学").assertExists()
    }

    @Test
    fun scheduleChangesExpandToDetails() {
        val course = Course(
            courseName = "高等数学",
            teacher = "张老师",
            classroom = "至诚楼A201",
            weekday = 1,
            startPeriod = 1,
            endPeriod = 2
        )
        val state = TimetableUiState(
            schedule = ScheduleState(
                data = WeekSchedule(
                    semesterId = "2026-2027-1",
                    semesterName = "2026-2027-1",
                    week = 8,
                    courses = listOf(course),
                    isFromCache = true
                ),
                semesters = listOf(Semester("2026-2027-1", "2026-2027-1")),
                currentSemesterId = "2026-2027-1",
                currentWeek = 8,
                fromCache = true,
                changes = listOf(
                    CourseChange(
                        kind = CourseChangeKind.UPDATED,
                        courseName = "高等数学",
                        weekday = 1,
                        details = listOf("教室：至诚楼A201 → 至诚楼B202")
                    )
                )
            ),
            session = SessionState(loggedIn = false, expired = true),
            timetable = ClassTimetable.defaultTimetables().first(),
            semesterStartDate = "2026-09-07"
        )

        compose.setContent {
            HutbTimetableTheme {
                TimetableScreen(
                    state = state,
                    onRefresh = {},
                    onPrevWeek = {},
                    onNextWeek = {},
                    onSelectWeek = {},
                    onSelectSemester = {},
                    onLogin = {},
                    onLogout = {},
                    onSwitchQueryMode = {},
                    onPickRoom = {},
                    onShowSnapshots = {},
                    onFilterChange = {}
                )
            }
        }

        compose.onNodeWithText("本周有 1 处课表变化").assertIsDisplayed()
        compose.onNodeWithText("查看").performClick()
        compose.onNodeWithText(
            "• 周一 高等数学：教室：至诚楼A201 → 至诚楼B202"
        ).assertIsDisplayed()
    }
}
