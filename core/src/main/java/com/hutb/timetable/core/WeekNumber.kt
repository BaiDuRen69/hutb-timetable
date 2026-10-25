package com.hutb.timetable.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 由开学日期推算当前周次。
 *
 * 以周一为一周的起点：开学日是周一、当天是周日时，
 * 直接按天求差会把周日算进下一周，因此两端都归到所在周的周一。
 *
 * @param semesterStartDate 开学（第一周周一）日期，"yyyy-MM-dd"
 * @param today 计算基准日（生产代码传 LocalDate.now()，测试传固定日期）
 * @return 周次，落在 1..30；日期非法或为空时返回 null
 */
fun computeCurrentWeek(semesterStartDate: String, today: LocalDate): Int? {
    if (semesterStartDate.isBlank()) return null
    return runCatching {
        val start = LocalDate.parse(semesterStartDate, DateTimeFormatter.ISO_LOCAL_DATE)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeks = ChronoUnit.WEEKS.between(start, monday) + 1
        weeks.toInt().coerceIn(1, 30)
    }.getOrNull()
}
