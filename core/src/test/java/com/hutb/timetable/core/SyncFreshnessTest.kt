package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFreshnessTest {

    private val now = 1_800_000_000_000L

    @Test
    fun unknownTimestampHasNoLabel() {
        assertNull(formatSyncAge(0L, now))
        assertNull(formatSyncAge(-1L, now))
    }

    @Test
    fun justSyncedIsJustNow() {
        assertEquals("刚刚", formatSyncAge(now - 30_000L, now))
        assertEquals("刚刚", formatSyncAge(now - 59_999L, now))
        // 设备时间回拨不应出现负数
        assertEquals("刚刚", formatSyncAge(now + 5 * 60_000L, now))
    }

    @Test
    fun minutesHoursAndDays() {
        assertEquals("1 分钟前", formatSyncAge(now - 60_000L, now))
        assertEquals("59 分钟前", formatSyncAge(now - 59 * 60_000L, now))
        assertEquals("1 小时前", formatSyncAge(now - 60 * 60_000L, now))
        assertEquals("23 小时前", formatSyncAge(now - 23 * 60 * 60_000L, now))
        assertEquals("1 天前", formatSyncAge(now - 24 * 60 * 60_000L, now))
        assertEquals("3 天前", formatSyncAge(now - 3 * 24 * 60 * 60_000L, now))
    }

    @Test
    fun autoSyncRespectsAgeAndAttemptInterval() {
        val minAge = 15 * 60_000L
        val minInterval = 5 * 60_000L
        // 数据 15 分钟以内：不同步
        assertTrue(!isAutoSyncDue(now, now - 14 * 60_000L, 0L, minAge, minInterval))
        // 超过 15 分钟且从未尝试：同步
        assertTrue(isAutoSyncDue(now, now - 16 * 60_000L, 0L, minAge, minInterval))
        // 刚尝试过：即使数据很旧也先等 5 分钟
        assertTrue(!isAutoSyncDue(now, now - 60 * 60_000L, now - 4 * 60_000L, minAge, minInterval))
        assertTrue(isAutoSyncDue(now, now - 60 * 60_000L, now - 6 * 60_000L, minAge, minInterval))
        // 时间未知：视为过期，允许同步
        assertTrue(isAutoSyncDue(now, 0L, 0L, minAge, minInterval))
    }
}
