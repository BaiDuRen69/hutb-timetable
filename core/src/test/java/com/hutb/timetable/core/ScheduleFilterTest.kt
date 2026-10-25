package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 星期 / 节次筛选的单元测试。
 */
class ScheduleFilterTest {

    private fun course(
        name: String,
        weekday: Int,
        start: Int,
        end: Int = start
    ) = Course(
        courseName = name,
        weekday = weekday,
        startPeriod = start,
        endPeriod = end
    )

    private val courses = listOf(
        course("高等数学", weekday = 1, start = 1, end = 2),
        course("大学英语", weekday = 2, start = 3, end = 4),
        course("数据结构", weekday = 2, start = 9, end = 10),
        course("体育", weekday = 5, start = 5, end = 6)
    )

    @Test
    fun `默认筛选不限制任何课程`() {
        val filter = ScheduleFilter()
        assertFalse(filter.isActive)
        assertEquals(courses, filter.apply(courses))
    }

    @Test
    fun `星期筛选只保留选中的星期`() {
        val filter = ScheduleFilter(weekdays = setOf(2))
        val filtered = filter.apply(courses)
        assertEquals(listOf("大学英语", "数据结构"), filtered.map { it.courseName })
        assertEquals(listOf(2), filter.visibleWeekdays())
    }

    @Test
    fun `节次区间按重叠判断 连堂课跨边界也算命中`() {
        val filter = ScheduleFilter(startPeriod = 4, endPeriod = 5)
        assertEquals(listOf("大学英语", "体育"), filter.apply(courses).map { it.courseName })
    }

    @Test
    fun `星期与节次同时筛选取交集`() {
        val filter = ScheduleFilter(weekdays = setOf(2), startPeriod = 1, endPeriod = 4)
        assertEquals(listOf("大学英语"), filter.apply(courses).map { it.courseName })
    }

    @Test
    fun `节次没解析出来的课程不会被节次筛选误伤`() {
        val unknown = course("未知节次", weekday = 3, start = 0, end = 0)
        val filter = ScheduleFilter(startPeriod = 5, endPeriod = 6)
        assertTrue(filter.matches(unknown))
    }

    @Test
    fun `切换星期至少保留一天`() {
        val single = ScheduleFilter(weekdays = setOf(3))
        assertEquals(setOf(3), single.toggleWeekday(3).weekdays)

        val two = ScheduleFilter(weekdays = setOf(3, 4))
        assertEquals(setOf(4), two.toggleWeekday(3).weekdays)
        assertEquals(setOf(3, 4, 5), two.toggleWeekday(5).weekdays)
    }

    @Test
    fun `设置节次区间时起始节次不大于结束节次`() {
        val filter = ScheduleFilter()
        assertEquals(3, filter.withPeriodRange(3, 2).startPeriod)
        assertEquals(3, filter.withPeriodRange(3, 2).endPeriod)
        assertEquals(1, filter.withPeriodRange(0, 20).startPeriod)
        assertEquals(ScheduleFilter.MAX_PERIOD, filter.withPeriodRange(0, 20).endPeriod)
    }

    @Test
    fun `筛选生效时列出要显示的星期`() {
        assertEquals((1..7).toList(), ScheduleFilter().visibleWeekdays())
        assertEquals(listOf(3, 5), ScheduleFilter(weekdays = setOf(5, 3)).visibleWeekdays())
    }

    @Test
    fun `精简教室名剥掉教学楼前缀与括号注记`() {
        assertEquals("E101", shortRoomName("乐知楼E101*(智慧)", "乐知楼"))
        assertEquals("A101", shortRoomName("至诚楼A101*(智慧)", "至诚楼"))
        assertEquals("A401", shortRoomName("立德楼A401", "月亮岛立德楼"))
        assertEquals("B405", shortRoomName("至诚楼B405*", "至诚楼"))
        assertEquals("D304", shortRoomName("日新楼D304*", "日新楼"))
        assertEquals("E602", shortRoomName("乐知楼E602(计信)", "乐知楼"))
    }

    @Test
    fun `精简教室名无法剥前缀时回退原名`() {
        // 没给教学楼名，无从剥前缀
        assertEquals("乐知楼E101", shortRoomName("乐知楼E101"))
        // 教学楼核心名（立德楼）不在教室名里
        assertEquals("月亮岛体育馆", shortRoomName("月亮岛体育馆", "月亮岛立德楼"))
        assertEquals("", shortRoomName(""))
        // 教室名本身就是教学楼名时不能剥成空串
        assertEquals("体育馆", shortRoomName("体育馆", "体育馆"))
    }

    @Test
    fun `查询快照比较包含学期周次与场地选择`() {
        val base = ScheduleQuery(
            semesterId = "2026-2027-1",
            week = 7,
            campusId = "00001",
            buildingId = "00003",
            roomMode = true
        )
        assertTrue(base.sameSnapshot(base))

        // 学期 / 周次不同就不是同一份数据，哪怕场地选择一样
        assertTrue(!base.sameSnapshot(base.copy(week = 8)))
        assertTrue(!base.sameSnapshot(base.copy(semesterId = "2025-2026-2")))

        assertTrue(!base.sameSnapshot(base.copy(buildingId = "00004")))
        assertTrue(!base.sameSnapshot(base.copy(roomCode = "E101")))
        assertTrue(!base.sameSnapshot(base.copy(roomMode = false, campusId = null, buildingId = null)))

        val personal = ScheduleQuery(semesterId = "2026-2027-1", week = 7)
        assertTrue(!personal.sameSnapshot(personal.copy(week = 9)))
    }
}
