package com.hutb.timetable.data.repo

import android.content.Context
import com.hutb.timetable.HutbApp
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.ScheduleQuery
import com.hutb.timetable.data.local.ClassTimetable

/**
 * 本地缓存的当前个人课表，供桌面小组件与每日提醒共用。
 *
 * 只读 SQLite 缓存与设置：不发起网络请求、不读取账号密码。
 */
data class LocalTodaySchedule(
    val week: Int,
    /** true 表示缓存与当前周次一致；false 表示是最近一周的旧缓存。 */
    val currentWeekOnly: Boolean,
    val courses: List<Course>,
    val timetable: ClassTimetable
)

suspend fun loadLocalTodaySchedule(context: Context): LocalTodaySchedule? {
    val container = (context.applicationContext as? HutbApp)?.container ?: return null
    val repository = container.repository
    val settings = container.settings

    var semesterId = repository.lastSemesterId
    if (semesterId.isBlank()) {
        semesterId = repository.readSemestersCache().firstOrNull()?.id.orEmpty()
    }
    if (semesterId.isBlank()) return null

    val week = settings.currentWeekNumber(semesterId)
        ?: repository.lastWeek.takeIf { it in 1..30 }
        ?: 1
    val query = ScheduleQuery(semesterId = semesterId, week = week)
    val exact = repository.readExactCache(query)
    val schedule = exact ?: repository.readLatestCache(query) ?: return null

    return LocalTodaySchedule(
        week = schedule.week,
        currentWeekOnly = exact != null,
        courses = schedule.courses,
        timetable = settings.activeTimetable
    )
}
