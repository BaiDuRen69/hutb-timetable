package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseSearchTest {

    private val math = Course(
        courseName = "高等数学",
        teacher = "张老师",
        className = "计科2301",
        classroom = "至诚楼A201",
        weekday = 1,
        startPeriod = 1,
        endPeriod = 2,
        weekText = "1-16周",
        courseType = "必修"
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
    private val programming = Course(
        courseName = "Python编程",
        teacher = "Zhang Wei",
        className = "软件2302",
        classroom = "乐知楼E101",
        weekday = 2,
        startPeriod = 5,
        endPeriod = 6
    )

    private val all = listOf(math, physics, programming)

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(searchCourses(all, "").isEmpty())
        assertTrue(searchCourses(all, "   ").isEmpty())
    }

    @Test
    fun matchesCourseTeacherRoomAndClass() {
        assertEquals(listOf(math), searchCourses(all, "高等"))
        assertEquals(listOf(math), searchCourses(all, "张老师"))
        assertEquals(listOf(programming), searchCourses(all, "e101"))
        assertEquals(listOf(math, physics), searchCourses(all, "计科2301"))
    }

    @Test
    fun multipleTermsMustAllMatch() {
        assertEquals(emptyList<Course>(), searchCourses(all, "高等 物理"))
        assertEquals(listOf(math), searchCourses(all, "计科 张老师 必修"))
    }

    @Test
    fun ignoresCaseAndFullWidthCharacters() {
        assertEquals(listOf(programming), searchCourses(all, "zhang wei"))
        assertEquals(listOf(programming), searchCourses(all, "Ｅ１０１"))
    }

    @Test
    fun weekdayKeywordsAreSearchable() {
        assertEquals(listOf(math), searchCourses(all, "周一"))
        assertEquals(listOf(physics), searchCourses(all, "星期三"))
    }

    @Test
    fun resultsAreOrderedByWeekdayThenPeriod() {
        assertEquals(listOf(math, physics), searchCourses(all, "计科"))
    }
}
