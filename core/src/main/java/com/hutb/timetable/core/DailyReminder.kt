package com.hutb.timetable.core

import java.time.LocalDateTime

/**
 * 每日提醒的时间计算。
 *
 * 只处理"分钟数"与日期，不依赖 Android，便于直接单测。
 */
fun nextDailyTrigger(now: LocalDateTime, minutesOfDay: Int): LocalDateTime {
    val minute = minutesOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
    val candidate = now.toLocalDate().atTime(minute / 60, minute % 60)
    // 正好到点也算"已过"：避免同一分钟内反复安排到过去
    return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
}

/**
 * 是否处于免打扰时段，支持跨午夜（如 22:00–07:00）。
 *
 * 起止相同视为未启用；起点包含、终点不包含。
 */
fun isWithinQuietHours(minutesOfDay: Int, startMinutes: Int, endMinutes: Int): Boolean {
    val now = minutesOfDay.mod(MINUTES_PER_DAY)
    val start = startMinutes.mod(MINUTES_PER_DAY)
    val end = endMinutes.mod(MINUTES_PER_DAY)
    if (start == end) return false
    return if (start < end) {
        now >= start && now < end
    } else {
        now >= start || now < end
    }
}

const val MINUTES_PER_DAY = 24 * 60
