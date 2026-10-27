package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DailyReminderTest {

    @Test
    fun laterTodayKeepsSameDay() {
        val now = LocalDateTime.of(2026, 10, 27, 7, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 27, 20, 30),
            nextDailyTrigger(now, 20 * 60 + 30)
        )
    }

    @Test
    fun passedOrExactTimeRollsToTomorrow() {
        val now = LocalDateTime.of(2026, 10, 27, 20, 30)
        assertEquals(
            LocalDateTime.of(2026, 10, 28, 20, 30),
            nextDailyTrigger(now, 20 * 60 + 30)
        )
    }

    @Test
    fun quietHoursWithinSameDay() {
        assertTrue(isWithinQuietHours(12 * 60, 12 * 60, 14 * 60))
        assertTrue(isWithinQuietHours(13 * 60 + 59, 12 * 60, 14 * 60))
        assertFalse(isWithinQuietHours(14 * 60, 12 * 60, 14 * 60))
    }

    @Test
    fun quietHoursAcrossMidnight() {
        assertTrue(isWithinQuietHours(23 * 60, 22 * 60, 7 * 60))
        assertTrue(isWithinQuietHours(6 * 60 + 59, 22 * 60, 7 * 60))
        assertFalse(isWithinQuietHours(7 * 60, 22 * 60, 7 * 60))
        assertFalse(isWithinQuietHours(12 * 60, 22 * 60, 7 * 60))
    }

    @Test
    fun equalBoundsDisableQuietHours() {
        assertFalse(isWithinQuietHours(0, 8 * 60, 8 * 60))
        assertFalse(isWithinQuietHours(8 * 60, 8 * 60, 8 * 60))
    }
}
