package com.hutb.timetable.notify

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.ui.timetable.courseEntriesForDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailySummaryContentTest {

    private val timetable = ClassTimetable.defaultTimetables().first()

    private val math = Course(
        courseName = "高等数学",
        classroom = "至诚楼A201",
        weekday = 1,
        startPeriod = 1,
        endPeriod = 2
    )
    private val physics = Course(
        courseName = "大学物理",
        classroom = "至诚楼B405",
        weekday = 1,
        startPeriod = 3,
        endPeriod = 4
    )

    @Test
    fun emptyDayUsesNoCourseCopy() {
        val content = buildDailySummaryContent(emptyList(), nowMinutes = 7 * 60)

        assertFalse(content.hasCourses)
        assertEquals("今天没有课", content.title)
        assertEquals("第8周 · 仅供参考", summarySubtitle(8))
    }

    @Test
    fun listsCoursesWithTimeAndRoom() {
        val entries = courseEntriesForDay(listOf(math, physics), timetable, weekday = 1)
        val content = buildDailySummaryContent(entries, nowMinutes = 7 * 60)

        assertTrue(content.hasCourses)
        assertEquals("今天 2 门课 · 08:00 开始", content.title)
        assertTrue(content.body.contains("08:00-09:40 高等数学 · 至诚楼A201"))
        assertTrue(content.body.contains("10:05-11:45 大学物理 · 至诚楼B405"))
    }

    @Test
    fun afterLastCourseTheTitleSaysFinished() {
        val entries = courseEntriesForDay(listOf(math, physics), timetable, weekday = 1)
        val content = buildDailySummaryContent(entries, nowMinutes = 18 * 60)

        assertEquals("今天 2 门课 · 已结束", content.title)
    }

    @Test
    fun missingClassroomFallsBackToPlaceholder() {
        val course = physics.copy(courseName = "体育", classroom = "")
        val entries = courseEntriesForDay(listOf(course), timetable, weekday = 1)
        val content = buildDailySummaryContent(entries, nowMinutes = 7 * 60)

        assertEquals("今天 1 门课 · 10:05 开始", content.title)
        assertTrue(content.body.contains("体育 · 教室待定"))
    }
}
