package com.hutb.timetable.core

import kotlin.math.abs

/** 一条课表变化，供顶部提示展开显示。 */
enum class CourseChangeKind { ADDED, REMOVED, UPDATED }

data class CourseChange(
    val kind: CourseChangeKind,
    val courseName: String,
    val weekday: Int,
    /** 教室/教师/时间等具体变化；新增和移除时为空，由 UI 用课程信息描述。 */
    val details: List<String> = emptyList(),
    val oldCourse: Course? = null,
    val newCourse: Course? = null
)

/**
 * 对比同一学期、同一周的新旧课程，产出可读的局部变化。
 *
 * 匹配键为“课程名 + 星期”，同一组内按起始节次就近配对，
 * 这样调时间/换教室是一条变化，而不是一条删除 + 一条新增。
 * 课程换到别的星期会表现为移出 + 新增，语义仍然正确。
 */
fun diffCourses(oldCourses: List<Course>, newCourses: List<Course>): List<CourseChange> {
    val identities = (oldCourses + newCourses)
        .map { it.courseName.trim() to it.weekday }
        .distinct()
        .sortedWith(compareBy({ it.second }, { it.first }))

    val changes = mutableListOf<CourseChange>()
    identities.forEach { (name, weekday) ->
        val oldGroup = oldCourses
            .filter { it.courseName.trim() == name && it.weekday == weekday }
            .sortedBy { it.startPeriod }
            .toMutableList()
        val newGroup = newCourses
            .filter { it.courseName.trim() == name && it.weekday == weekday }
            .sortedBy { it.startPeriod }

        val pairs = mutableListOf<Pair<Course?, Course?>>()
        newGroup.forEach { newCourse ->
            val nearest = oldGroup.minByOrNull {
                abs(it.startPeriod - newCourse.startPeriod)
            }
            if (nearest != null) {
                oldGroup.remove(nearest)
                pairs += nearest to newCourse
            } else {
                pairs += null to newCourse
            }
        }
        oldGroup.forEach { pairs += it to null }

        pairs.forEach { (oldCourse, newCourse) ->
            when {
                oldCourse == null && newCourse != null -> changes += CourseChange(
                    kind = CourseChangeKind.ADDED,
                    courseName = name.ifBlank { "未命名课程" },
                    weekday = weekday,
                    newCourse = newCourse
                )

                oldCourse != null && newCourse == null -> changes += CourseChange(
                    kind = CourseChangeKind.REMOVED,
                    courseName = name.ifBlank { "未命名课程" },
                    weekday = weekday,
                    oldCourse = oldCourse
                )

                oldCourse != null && newCourse != null -> {
                    val details = buildList {
                        if (oldCourse.startPeriod != newCourse.startPeriod ||
                            oldCourse.endPeriod != newCourse.endPeriod
                        ) {
                            add("时间：${periodText(oldCourse)} → ${periodText(newCourse)}")
                        }
                        if (oldCourse.classroom.trim() != newCourse.classroom.trim()) {
                            add(
                                "教室：${oldCourse.classroom.ifBlank { "待定" }} → " +
                                    newCourse.classroom.ifBlank { "待定" }
                            )
                        }
                        if (oldCourse.teacher.trim() != newCourse.teacher.trim()) {
                            add(
                                "教师：${oldCourse.teacher.ifBlank { "待定" }} → " +
                                    newCourse.teacher.ifBlank { "待定" }
                            )
                        }
                        if (oldCourse.weekText.trim() != newCourse.weekText.trim()) {
                            add(
                                "周次：${oldCourse.weekText.ifBlank { "未标注" }} → " +
                                    newCourse.weekText.ifBlank { "未标注" }
                            )
                        }
                    }
                    if (details.isNotEmpty()) {
                        changes += CourseChange(
                            kind = CourseChangeKind.UPDATED,
                            courseName = name.ifBlank { "未命名课程" },
                            weekday = weekday,
                            details = details,
                            oldCourse = oldCourse,
                            newCourse = newCourse
                        )
                    }
                }
            }
        }
    }
    return changes.sortedWith(
        compareBy(
            { it.weekday },
            { it.newCourse?.startPeriod ?: it.oldCourse?.startPeriod ?: 0 },
            { it.kind.ordinal },
            { it.courseName }
        )
    )
}

private fun periodText(course: Course): String =
    if (course.startPeriod == course.endPeriod) {
        "第${course.startPeriod}节"
    } else {
        "第${course.startPeriod}-${course.endPeriod}节"
    }
