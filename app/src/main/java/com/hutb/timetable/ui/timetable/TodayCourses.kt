package com.hutb.timetable.ui.timetable

import com.hutb.timetable.core.Course
import com.hutb.timetable.data.local.ClassTimetable
import java.time.LocalDateTime

/**
 * 今天课程的即时状态，供首屏卡片和桌面小组件共用。
 *
 * 只读取已加载到内存或本地缓存的课程，不触发网络请求。
 */
internal data class TodayCourseStatus(
    val todayCount: Int = 0,
    val nowMinutes: Int = 0,
    val current: Course? = null,
    val currentRange: IntRange? = null,
    val next: Course? = null,
    val nextRange: IntRange? = null,
    /** 从现在起还没结束的课程（含正在上的）。 */
    val remaining: List<Course> = emptyList()
)

internal fun computeTodayCourseStatus(
    courses: List<Course>,
    timetable: ClassTimetable?,
    now: LocalDateTime
): TodayCourseStatus {
    val today = now.dayOfWeek.value
    val nowMinutes = now.hour * 60 + now.minute
    val todayCourses = courses
        .asSequence()
        .filter { it.weekday == today }
        .mapNotNull { course ->
            courseRangeMinutes(timetable, course)?.let { course to it }
        }
        .sortedBy { it.second.first }
        .toList()
    val current = todayCourses.firstOrNull { nowMinutes in it.second }
    val next = todayCourses.firstOrNull { it.second.first > nowMinutes }
    return TodayCourseStatus(
        todayCount = todayCourses.size,
        nowMinutes = nowMinutes,
        current = current?.first,
        currentRange = current?.second,
        next = next?.first,
        nextRange = next?.second,
        remaining = todayCourses.filter { it.second.last >= nowMinutes }.map { it.first }
    )
}

internal fun courseRangeMinutes(timetable: ClassTimetable?, course: Course): IntRange? {
    if (timetable == null) return null
    if (course.startPeriod <= 0 || course.endPeriod < course.startPeriod) return null
    val start = parseMinutes(timetable.periods.getOrNull(course.startPeriod - 1)?.start)
        ?: return null
    val end = parseMinutes(timetable.periods.getOrNull(course.endPeriod - 1)?.end)
        ?: return null
    return start..end
}

internal fun parseMinutes(text: String?): Int? {
    val parts = text?.trim()?.split(":") ?: return null
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

internal fun formatMinutes(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)
