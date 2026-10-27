package com.hutb.timetable.notify

import com.hutb.timetable.ui.timetable.TodayCourseEntry
import com.hutb.timetable.ui.timetable.formatMinutes

/**
 * 每日摘要的文案，独立成纯函数便于测试。
 */
internal data class DailySummaryContent(
    val title: String,
    val body: String,
    val hasCourses: Boolean
)

internal fun buildDailySummaryContent(
    entries: List<TodayCourseEntry>,
    nowMinutes: Int
): DailySummaryContent {
    if (entries.isEmpty()) {
        return DailySummaryContent(
            title = "今天没有课",
            body = "今天没有安排课程，好好休息，或打开 App 看看本周安排。",
            hasCourses = false
        )
    }
    val distinctCourses = entries.map { it.course.courseName }.distinct().size
    val current = entries.firstOrNull { nowMinutes in it.range }
    val upcoming = entries.firstOrNull { it.range.first > nowMinutes }
    val title = when {
        current != null -> "今天 $distinctCourses 门课 · 正在上 ${current.course.courseName}"
        upcoming != null && nowMinutes < entries.first().range.first ->
            "今天 $distinctCourses 门课 · ${formatMinutes(upcoming.range.first)} 开始"
        upcoming != null ->
            "今天 $distinctCourses 门课 · 下一节 ${formatMinutes(upcoming.range.first)}"
        else -> "今天 $distinctCourses 门课 · 已结束"
    }
    val lines = entries.map { entry ->
        val course = entry.course
        val room = course.classroom.ifBlank { "教室待定" }
        "${formatMinutes(entry.range.first)}-${formatMinutes(entry.range.last)} " +
            "${course.courseName} · $room"
    }
    val shown = lines.take(MAX_LINES)
    val body = buildString {
        append(shown.joinToString("\n"))
        if (lines.size > shown.size) {
            append("\n等 ${lines.size - shown.size} 段课程")
        }
    }
    return DailySummaryContent(
        title = title,
        body = body,
        hasCourses = true
    )
}

private const val MAX_LINES = 8

/** 通知副标题里的周次说明；周次未知时只提示仅供参考。 */
internal fun summarySubtitle(week: Int): String =
    if (week > 0) "第${week}周 · 仅供参考" else "仅供参考"
