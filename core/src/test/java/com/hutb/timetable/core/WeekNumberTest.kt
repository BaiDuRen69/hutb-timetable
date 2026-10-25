package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeekNumberTest {

    private fun date(s: String): LocalDate = LocalDate.parse(s)

    @Test
    fun `开学第一天是第1周`() {
        assertEquals(1, computeCurrentWeek("2026-09-07", date("2026-09-07")))
    }

    @Test
    fun `开学当周日仍是第1周`() {
        // 2026-09-07 是周一，同周周日 2026-09-13 应仍算第 1 周
        assertEquals(1, computeCurrentWeek("2026-09-07", date("2026-09-13")))
    }

    @Test
    fun `下周一起进入第2周`() {
        assertEquals(2, computeCurrentWeek("2026-09-07", date("2026-09-14")))
    }

    @Test
    fun `开学日不是周一时按所在周的周一算`() {
        // 2026-09-09 是周三，把它当第一周；同周日 09-13 仍是第 1 周
        assertEquals(1, computeCurrentWeek("2026-09-09", date("2026-09-13")))
        // 下周日 09-20 进入第 2 周
        assertEquals(2, computeCurrentWeek("2026-09-09", date("2026-09-20")))
    }

    @Test
    fun `开学日在周中、当天是周一时为第1周`() {
        assertEquals(1, computeCurrentWeek("2026-09-09", date("2026-09-07")))
    }

    @Test
    fun `超过30周时封顶`() {
        // 2026-09-07 起第 40 周的周一，应被截到 30
        assertEquals(30, computeCurrentWeek("2026-09-07", date("2027-06-14")))
    }

    @Test
    fun `空日期返回null`() {
        assertNull(computeCurrentWeek("", date("2026-09-07")))
    }

    @Test
    fun `非法日期返回null而不抛异常`() {
        assertNull(computeCurrentWeek("not-a-date", date("2026-09-07")))
        assertNull(computeCurrentWeek("2026/09/07", date("2026-09-07")))
    }
}
