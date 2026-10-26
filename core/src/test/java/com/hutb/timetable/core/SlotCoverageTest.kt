package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotCoverageTest {

    @Test
    fun `完整覆盖两节格子`() {
        assertEquals(SlotCoverage.FULL, slotCoverage(1, 4, 1, 2))
        assertEquals(SlotCoverage.FULL, slotCoverage(1, 4, 3, 4))
    }

    @Test
    fun `一节到三节只占三到四节格子的上半格`() {
        assertEquals(SlotCoverage.TOP_HALF, slotCoverage(1, 3, 3, 4))
    }

    @Test
    fun `二节到三节在一到二节格子里只占下半格`() {
        assertEquals(SlotCoverage.BOTTOM_HALF, slotCoverage(2, 3, 1, 2))
        assertEquals(SlotCoverage.TOP_HALF, slotCoverage(2, 3, 3, 4))
    }

    @Test
    fun `四节到五节跨两个格子各占一半`() {
        assertEquals(SlotCoverage.BOTTOM_HALF, slotCoverage(4, 5, 3, 4))
        assertEquals(SlotCoverage.TOP_HALF, slotCoverage(4, 5, 5, 6))
    }

    @Test
    fun `单节课程也能正确裁剪`() {
        assertEquals(SlotCoverage.TOP_HALF, slotCoverage(3, 3, 3, 4))
        assertEquals(SlotCoverage.BOTTOM_HALF, slotCoverage(2, 2, 1, 2))
    }

    @Test
    fun `完全不相交返回 NONE`() {
        assertEquals(SlotCoverage.NONE, slotCoverage(1, 2, 5, 6))
        assertEquals(SlotCoverage.NONE, slotCoverage(7, 8, 1, 2))
    }

    @Test
    fun `非法节次不参与绘制`() {
        assertEquals(SlotCoverage.NONE, slotCoverage(0, 0, 1, 2))
        assertEquals(SlotCoverage.NONE, slotCoverage(4, 3, 3, 4))
    }
}
