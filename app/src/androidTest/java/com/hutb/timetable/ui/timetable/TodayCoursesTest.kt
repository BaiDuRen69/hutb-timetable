package com.hutb.timetable.ui.timetable

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hutb.timetable.core.Course
import com.hutb.timetable.data.local.ClassTimetable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TodayCoursesTest {

    private val timetable = ClassTimetable.defaultTimetables().first()

    private val first = Course(
        courseName = "高等数学",
        classroom = "至诚楼A201",
        weekday = 1,
        startPeriod = 1,
        endPeriod = 2
    )
    private val second = Course(
        courseName = "大学物理",
        classroom = "至诚楼B405",
        weekday = 1,
        startPeriod = 3,
        endPeriod = 4
    )

    @Test
    fun currentAndNextCourseAreDerivedFromActiveTimetable() {
        val now = LocalDateTime.of(2026, 10, 26, 9, 0) // 周一 09:00
        val status = computeTodayCourseStatus(listOf(first, second), timetable, now)

        assertEquals(2, status.todayCount)
        assertEquals(first, status.current)
        assertEquals(40, status.currentRange!!.last - status.nowMinutes)
        assertEquals(second, status.next)
        assertEquals(65, status.nextRange!!.first - status.nowMinutes)
        assertEquals(listOf(first, second), status.remaining)
    }

    @Test
    fun afterLastCourseShowsNoActiveOrUpcoming() {
        val now = LocalDateTime.of(2026, 10, 26, 12, 0) // 周一 12:00
        val status = computeTodayCourseStatus(listOf(first, second), timetable, now)

        assertEquals(2, status.todayCount)
        assertNull(status.current)
        assertNull(status.next)
        assertEquals(emptyList<Course>(), status.remaining)
    }
}
