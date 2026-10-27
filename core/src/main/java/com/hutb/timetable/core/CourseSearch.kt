package com.hutb.timetable.core

import java.text.Normalizer
import java.util.Locale

/**
 * 本周课程搜索。
 *
 * 匹配课程名、教师、教室、班级、课程性质、周次文本与星期；
 * 多个关键词按“同时包含”处理，空查询返回空列表。
 */
fun searchCourses(courses: List<Course>, query: String, limit: Int = 50): List<Course> {
    if (limit <= 0) return emptyList()
    val terms = normalizeSearchQuery(query)
    if (terms.isEmpty()) return emptyList()
    return courses
        .asSequence()
        .filter { course ->
            val haystack = normalizeSearchText(
                buildString {
                    append(course.courseName).append(' ')
                    append(course.teacher).append(' ')
                    append(course.classroom).append(' ')
                    append(course.className).append(' ')
                    append(course.courseType).append(' ')
                    append(course.weekText).append(' ')
                    append(weekdaySearchLabel(course.weekday))
                }
            )
            terms.all { haystack.contains(it) }
        }
        .sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.endPeriod }, { it.courseName }))
        .take(limit)
        .toList()
}

internal fun normalizeSearchQuery(query: String): List<String> =
    normalizeSearchText(query).split(' ').filter { it.isNotBlank() }.distinct()

/** 全角/半角统一后再忽略大小写，避免“Ａ201”搜不到“A201”。 */
internal fun normalizeSearchText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

private fun weekdaySearchLabel(weekday: Int): String {
    val day = when (weekday) {
        1 -> "周一 星期一"
        2 -> "周二 星期二"
        3 -> "周三 星期三"
        4 -> "周四 星期四"
        5 -> "周五 星期五"
        6 -> "周六 星期六"
        7 -> "周日 星期日"
        else -> ""
    }
    return day
}
