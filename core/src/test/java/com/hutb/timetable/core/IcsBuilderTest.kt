package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class IcsBuilderTest {

    private val now = LocalDateTime.of(2026, 10, 26, 12, 0, 0)

    private fun sampleEvent(
        title: String = "高等数学",
        location: String = "至诚楼A201",
        description: String = "张老师",
        start: LocalDateTime = LocalDateTime.of(2026, 10, 26, 8, 0),
        end: LocalDateTime = LocalDateTime.of(2026, 10, 26, 9, 40)
    ) = IcsEvent("uid-1@hutb", title, location, description, start, end)

    @Test
    fun `生成完整日历结构`() {
        val text = buildIcs("2026-2027-1 第8周", listOf(sampleEvent()), now)
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        assertTrue(text.contains("BEGIN:VEVENT\r\n"))
        assertTrue(text.contains("SUMMARY:高等数学\r\n"))
        assertTrue(text.contains("LOCATION:至诚楼A201\r\n"))
        assertTrue(text.contains("DTSTART:20261026T080000\r\n"))
        assertTrue(text.contains("DTEND:20261026T094000\r\n"))
        assertTrue(text.contains("DTSTAMP:20261026T120000Z\r\n"))
    }

    @Test
    fun `空事件列表仍是合法日历`() {
        val text = buildIcs("空课表", emptyList(), now)
        assertTrue(text.contains("BEGIN:VCALENDAR"))
        assertTrue(text.contains("END:VCALENDAR"))
        assertTrue(!text.contains("BEGIN:VEVENT"))
    }

    @Test
    fun `逗号分号和换行会被转义`() {
        assertEquals("a\\,b\\;c\\nd", escapeIcs("a,b;c\nd"))
        val text = buildIcs(
            "名字,含分号;",
            listOf(sampleEvent(title = "课程,一;二\n三")),
            now
        )
        assertTrue(text.contains("SUMMARY:课程\\,一\\;二\\n三"))
        assertTrue(text.contains("X-WR-CALNAME:名字\\,含分号\\;"))
    }

    @Test
    fun `长中文行按字节折行且不超过七十五字节`() {
        val longTitle = "这是一门名字非常非常长的课程".repeat(8)
        val text = buildIcs("长标题", listOf(sampleEvent(title = longTitle)), now)
        val lines = text.split("\r\n")
        assertTrue(lines.any { it.startsWith(" ") })
        assertTrue(lines.all { it.toByteArray(StandardCharsets.UTF_8).size <= 75 })
    }

    @Test
    fun `同一事件重复导出保持确定性`() {
        val first = buildIcs("课表", listOf(sampleEvent()), now)
        val second = buildIcs("课表", listOf(sampleEvent()), now)
        assertEquals(first, second)
    }
}
