package com.hutb.timetable.ui.timetable

import com.hutb.timetable.core.Course
import com.hutb.timetable.core.IcsEvent
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.buildIcs
import com.hutb.timetable.data.local.ClassTimetable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 分享当前实际显示（已套用筛选）的课表文本。 */
internal fun buildScheduleShareText(
    semesterName: String,
    week: Int,
    courses: List<Course>,
    filter: ScheduleFilter,
    roomScope: String
): String = buildString {
    append("湖南工商大学课表\n")
    if (semesterName.isNotBlank()) append(semesterName).append('\n')
    append("第").append(week).append("周\n")
    if (roomScope.isNotBlank()) append("范围：").append(roomScope).append('\n')
    filterDescription(filter)?.let { append("筛选：").append(it).append('\n') }
    append('\n')

    if (courses.isEmpty()) {
        append("（当前没有课程）\n")
    } else {
        courses.sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.endPeriod }))
            .groupBy { it.weekday }
            .forEach { (weekday, dayCourses) ->
                append(WEEKDAY_NAMES.getOrNull(weekday - 1) ?: "周$weekday").append('\n')
                dayCourses.forEach { course ->
                    append("  ")
                    append(
                        listOf(
                            periodText(course.startPeriod, course.endPeriod),
                            course.courseName,
                            course.classroom,
                            course.teacher
                        ).filter { it.isNotBlank() }.joinToString(" · ")
                    )
                    append('\n')
                }
            }
    }
    append("\n课表仅供参考，请以教务系统及学校通知为准。")
}

/**
 * 把当前周课程导出为单次 VEVENT。
 *
 * 未设置开学日期或作息表时无法确定真实日期，返回 null 让 UI 提示用户先设置。
 */
internal fun buildWeekIcs(
    semesterStartDate: String,
    week: Int,
    semesterName: String,
    courses: List<Course>,
    timetable: ClassTimetable?
): String? {
    if (courses.isEmpty() || timetable == null) return null
    val monday = runCatching {
        LocalDate.parse(semesterStartDate).plusWeeks((week - 1).toLong())
    }.getOrNull() ?: return null

    val events = courses.mapIndexedNotNull { index, course ->
        val startTime = parseTime(timetable, course.startPeriod, end = false) ?: return@mapIndexedNotNull null
        val endTime = parseTime(timetable, course.endPeriod, end = true) ?: return@mapIndexedNotNull null
        if (!endTime.isAfter(startTime)) return@mapIndexedNotNull null
        val date = monday.plusDays((course.weekday - 1).toLong())
        IcsEvent(
            uid = "hutb-$semesterStartDate-w$week-${course.weekday}-${course.startPeriod}-$index@hutb.timetable",
            title = course.courseName,
            location = course.classroom,
            description = listOfNotNull(
                course.teacher.takeIf { it.isNotBlank() },
                course.className.takeIf { it.isNotBlank() },
                course.weekText.takeIf { it.isNotBlank() },
                "课表仅供参考，请以教务系统及学校通知为准"
            ).joinToString("\n"),
            start = LocalDateTime.of(date, startTime),
            end = LocalDateTime.of(date, endTime)
        )
    }
    if (events.isEmpty()) return null
    return buildIcs(
        calendarName = "${semesterName.ifBlank { "课表" }} 第${week}周",
        events = events
    )
}

internal fun icsFileName(semesterName: String, week: Int): String {
    val clean = semesterName
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .trim()
        .ifBlank { "课表" }
    return "$clean-第${week}周.ics"
}

private fun parseTime(timetable: ClassTimetable, period: Int, end: Boolean): LocalTime? {
    val value = timetable.periods.getOrNull(period - 1) ?: return null
    return runCatching { LocalTime.parse(if (end) value.end else value.start) }.getOrNull()
}

private fun periodText(start: Int, end: Int): String =
    if (start == end) "第${start}节" else "第${start}-${end}节"

private fun filterDescription(filter: ScheduleFilter): String? {
    if (!filter.isActive) return null
    val parts = mutableListOf<String>()
    if (filter.limitsWeekday) {
        parts += filter.weekdays.sorted()
            .mapNotNull { WEEKDAY_NAMES.getOrNull(it - 1) }
            .joinToString("、")
    }
    if (filter.limitsPeriod) {
        parts += periodText(filter.startPeriod, filter.endPeriod)
    }
    return parts.joinToString(" · ").ifBlank { null }
}
