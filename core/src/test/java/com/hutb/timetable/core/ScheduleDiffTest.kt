package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDiffTest {

    private fun course(
        name: String = "高等数学",
        weekday: Int = 1,
        start: Int = 1,
        end: Int = 2,
        room: String = "至诚楼A201",
        teacher: String = "张老师",
        weekText: String = "1-16周"
    ) = Course(
        courseName = name,
        teacher = teacher,
        classroom = room,
        weekday = weekday,
        startPeriod = start,
        endPeriod = end,
        weekText = weekText
    )

    @Test
    fun identicalSchedulesProduceNoChange() {
        val courses = listOf(course())
        assertEquals(emptyList<CourseChange>(), diffCourses(courses, courses))
    }

    @Test
    fun classroomChangeIsOneUpdate() {
        val changes = diffCourses(
            listOf(course(room = "至诚楼A201")),
            listOf(course(room = "至诚楼B202"))
        )
        assertEquals(1, changes.size)
        assertEquals(CourseChangeKind.UPDATED, changes[0].kind)
        assertTrue(changes[0].details.contains("教室：至诚楼A201 → 至诚楼B202"))
    }

    @Test
    fun timeChangeIsReportedAsUpdateNotRemoveAdd() {
        val changes = diffCourses(
            listOf(course(start = 1, end = 2)),
            listOf(course(start = 3, end = 4))
        )
        assertEquals(1, changes.size)
        assertEquals(CourseChangeKind.UPDATED, changes[0].kind)
        assertTrue(changes[0].details.contains("时间：第1-2节 → 第3-4节"))
    }

    @Test
    fun teacherAndWeekChangeAreReported() {
        val changes = diffCourses(
            listOf(course(teacher = "张老师", weekText = "1-16周")),
            listOf(course(teacher = "李老师", weekText = "1-8周"))
        )
        assertEquals(1, changes.size)
        assertTrue(changes[0].details.contains("教师：张老师 → 李老师"))
        assertTrue(changes[0].details.contains("周次：1-16周 → 1-8周"))
    }

    @Test
    fun addedAndRemovedCoursesAreBothReported() {
        val changes = diffCourses(
            listOf(course(name = "大学物理")),
            listOf(course(name = "数据结构"))
        )
        assertEquals(2, changes.size)
        assertEquals(CourseChangeKind.ADDED, changes[0].kind)
        assertEquals("数据结构", changes[0].courseName)
        assertEquals(CourseChangeKind.REMOVED, changes[1].kind)
        assertEquals("大学物理", changes[1].courseName)
    }

    @Test
    fun sameNameSameDaySessionsPairByNearestPeriod() {
        val changes = diffCourses(
            listOf(
                course(name = "实验课", start = 1, end = 2, room = "A101"),
                course(name = "实验课", start = 5, end = 6, room = "A102")
            ),
            listOf(
                course(name = "实验课", start = 1, end = 2, room = "A101"),
                course(name = "实验课", start = 5, end = 6, room = "B201")
            )
        )
        assertEquals(1, changes.size)
        assertTrue(changes[0].details.contains("教室：A102 → B201"))
    }
}
