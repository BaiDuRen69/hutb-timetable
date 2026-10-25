package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomSelectionTest {

    @Test
    fun `哈希楼名用教室名反推可读名称`() {
        val rooms = listOf(
            RoomOption("a", "风华楼302"),
            RoomOption("b", "风华楼401"),
            RoomOption("c", "日新楼201")
        )
        assertEquals("风华楼", resolveBuildingName("6F57A89B0A67435AB9E528529A6F2840", rooms))
    }

    @Test
    fun `可读楼名原样返回`() {
        assertEquals("乐知楼", resolveBuildingName("乐知楼", emptyList()))
        assertEquals(null, resolveBuildingName(null, emptyList()))
    }

    @Test
    fun `教室列表按教学楼收窄`() {
        val rooms = listOf(RoomOption("1", "乐知楼E101"), RoomOption("2", "日新楼C205"))
        assertEquals(listOf("乐知楼E101"), scopeToBuilding(rooms, "乐知楼").map { it.name })
    }

    @Test
    fun `收窄为空时保留原列表`() {
        val rooms = listOf(RoomOption("1", "乐知楼E101"))
        assertEquals(rooms, scopeToBuilding(rooms, "不存在的楼"))
        assertEquals(rooms, scopeToBuilding(rooms, null))
    }
}
