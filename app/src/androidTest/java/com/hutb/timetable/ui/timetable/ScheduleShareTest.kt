package com.hutb.timetable.ui.timetable

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.data.local.ClassTimetable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleShareTest {

    private val course = Course(
        courseName = "高等数学",
        teacher = "张老师",
        classroom = "至诚楼A201",
        weekday = 1,
        startPeriod = 1,
        endPeriod = 3,
        weekText = "1-16周"
    )

    @Test
    fun shareTextContainsFilterAndDisclaimer() {
        val text = buildScheduleShareText(
            semesterName = "2026-2027-1",
            week = 8,
            courses = listOf(course),
            filter = ScheduleFilter(weekdays = setOf(1), startPeriod = 1, endPeriod = 3),
            roomScope = ""
        )
        assertTrue(text.contains("第8周"))
        assertTrue(text.contains("筛选：周一 · 第1-3节"))
        assertTrue(text.contains("高等数学"))
        assertTrue(text.contains("课表仅供参考"))
    }

    @Test
    fun icsUsesRealDateAndLastPeriodEnd() {
        val timetable = ClassTimetable.defaultTimetables().first()
        val ics = buildWeekIcs(
            semesterStartDate = "2026-09-07",
            week = 8,
            semesterName = "2026-2027-1",
            courses = listOf(course),
            timetable = timetable
        )
        requireNotNull(ics)
        assertTrue(ics.contains("DTSTART:20261026T080000"))
        // 夏时制·南校区第 3 节 10:05-10:50；1-3 节课程必须结束在 10:50
        assertTrue(ics.contains("DTEND:20261026T105000"))
        assertTrue(ics.contains("SUMMARY:高等数学"))
    }

    @Test
    fun icsWithoutStartDateIsNotGenerated() {
        assertTrue(
            buildWeekIcs(
                semesterStartDate = "",
                week = 8,
                semesterName = "2026-2027-1",
                courses = listOf(course),
                timetable = ClassTimetable.defaultTimetables().first()
            ) == null
        )
    }
}
