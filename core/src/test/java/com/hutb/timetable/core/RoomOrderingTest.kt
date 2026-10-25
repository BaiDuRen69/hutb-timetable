package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOrderingTest {

    private val rooms = listOf(
        "乐知楼F101",
        "乐知楼E202",
        "乐知楼E101",
        "乐知楼E102",
        "乐知楼F102",
        "乐知楼E201"
    )

    @Test
    fun `楼栋优先：同一栋楼排在一起，楼内按编号`() {
        assertEquals(
            listOf(
                "乐知楼E101", "乐知楼E102", "乐知楼E201", "乐知楼E202",
                "乐知楼F101", "乐知楼F102"
            ),
            sortRoomLabels(rooms, RoomSortMode.BY_BUILDING)
        )
    }

    @Test
    fun `编号优先：先按编号再按楼栋字母`() {
        assertEquals(
            listOf(
                "乐知楼E101", "乐知楼F101",
                "乐知楼E102", "乐知楼F102",
                "乐知楼E201", "乐知楼E202"
            ),
            sortRoomLabels(rooms, RoomSortMode.BY_NUMBER)
        )
    }

    @Test
    fun `楼层优先：先按楼层，同层按楼栋再按编号`() {
        assertEquals(
            listOf(
                "乐知楼E101", "乐知楼E102", "乐知楼F101", "乐知楼F102",
                "乐知楼E201", "乐知楼E202"
            ),
            sortRoomLabels(rooms, RoomSortMode.BY_FLOOR)
        )
    }

    @Test
    fun `带括号注记和星号的教室名也能解析`() {
        val labels = listOf("至诚楼A102*(智慧)", "至诚楼A101*(智慧)")
        assertEquals(
            listOf("至诚楼A101*(智慧)", "至诚楼A102*(智慧)"),
            sortRoomLabels(labels, RoomSortMode.BY_BUILDING)
        )
    }

    @Test
    fun `座与栋的写法能提取楼栋字母`() {
        val labels = listOf("湘江楼B座202(机房)", "湘江楼B座201(机房)", "湘江楼C栋101")
        assertEquals(
            listOf("湘江楼B座201(机房)", "湘江楼B座202(机房)", "湘江楼C栋101"),
            sortRoomLabels(labels, RoomSortMode.BY_BUILDING)
        )
    }

    @Test
    fun `连字符编号按房间号排序而不是后缀`() {
        val labels = listOf("J1-102", "J1-101")
        assertEquals(
            listOf("J1-101", "J1-102"),
            sortRoomLabels(labels, RoomSortMode.BY_BUILDING)
        )
    }

    @Test
    fun `识别不出编号的教室排在最后`() {
        val labels = listOf("未标注教室", "乐知楼E202", "体育馆")
        assertEquals(
            listOf("乐知楼E202", "体育馆", "未标注教室"),
            sortRoomLabels(labels, RoomSortMode.BY_BUILDING)
        )
    }

    @Test
    fun `空列表与单元素列表原样返回`() {
        assertEquals(emptyList<String>(), sortRoomLabels(emptyList(), RoomSortMode.BY_FLOOR))
        assertEquals(listOf("E101"), sortRoomLabels(listOf("E101"), RoomSortMode.BY_NUMBER))
    }

    @Test
    fun `教室身份键忽略注记的括号形态与空白`() {
        assertEquals(
            roomIdentity("乐知楼E101*(智慧)"),
            roomIdentity("乐知楼E101*（智慧）")
        )
        assertEquals(
            roomIdentity("乐知楼E101"),
            roomIdentity("乐知楼 E101 *(智慧)")
        )
    }

    @Test
    fun `教室身份键保留楼栋信息，不同教室不会被合并`() {
        assertTrue(roomIdentity("乐知楼E101*(智慧)") != roomIdentity("乐知楼E102*(智慧)"))
        assertTrue(roomIdentity("乐知楼E101") != roomIdentity("至诚楼E101"))
    }
}
