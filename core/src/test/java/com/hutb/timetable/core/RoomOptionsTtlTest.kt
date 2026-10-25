package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOptionsTtlTest {

    private val ttl = RoomOptionsTtl.TTL_MS

    @Test
    fun `TTL 内视为新鲜`() {
        val now = 1_700_000_000_000L
        assertTrue(RoomOptionsTtl.isFresh(now - 1, now))
        assertTrue(RoomOptionsTtl.isFresh(now - ttl, now))
    }

    @Test
    fun `超过 TTL 视为过期`() {
        val now = 1_700_000_000_000L
        assertFalse(RoomOptionsTtl.isFresh(now - ttl - 1, now))
    }

    @Test
    fun `缺少写入时间视为无效`() {
        assertFalse(RoomOptionsTtl.isFresh(0L, 1_700_000_000_000L))
    }

    @Test
    fun `时钟回拨导致写入时间在未来时仍可用`() {
        val now = 1_700_000_000_000L
        assertTrue(RoomOptionsTtl.isFresh(now + 60_000, now))
    }

    @Test
    fun `教学楼缓存 key 含学期与校区`() {
        assertEquals("buildings_2026-2027-1_00001", RoomOptionsTtl.buildingsKey("2026-2027-1", "00001"))
        assertEquals("buildings__00001", RoomOptionsTtl.buildingsKey(null, "00001"))
        // 换学期或换校区必须得到不同 key，否则会读到旧数据
        assertNotEquals(
            RoomOptionsTtl.buildingsKey("2026-2027-1", "00001"),
            RoomOptionsTtl.buildingsKey("2026-2027-2", "00001")
        )
        assertNotEquals(
            RoomOptionsTtl.buildingsKey("2026-2027-1", "00001"),
            RoomOptionsTtl.buildingsKey("2026-2027-1", "00002")
        )
        assertEquals("campuses", RoomOptionsTtl.campusesKey())
    }
}
