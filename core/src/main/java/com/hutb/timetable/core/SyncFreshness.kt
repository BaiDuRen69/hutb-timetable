package com.hutb.timetable.core

/**
 * 把缓存时间格式化成便于阅读的“多久前”。
 *
 * 时间未知（<=0）返回 null；未来时间按“刚刚”处理，避免设备时间回拨显示负数。
 */
fun formatSyncAge(cachedAtMillis: Long, nowMillis: Long): String? {
    if (cachedAtMillis <= 0L) return null
    val ageMillis = (nowMillis - cachedAtMillis).coerceAtLeast(0L)
    val minutes = ageMillis / 60_000L
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        else -> "${minutes / (24 * 60)} 天前"
    }
}

/**
 * 是否应该自动同步：距上次尝试足够久，且数据已超过最小年龄（未知时间视为过期）。
 */
fun isAutoSyncDue(
    nowMillis: Long,
    cachedAtMillis: Long,
    lastAttemptAtMillis: Long,
    minAgeMillis: Long,
    minIntervalMillis: Long
): Boolean {
    if (nowMillis - lastAttemptAtMillis < minIntervalMillis) return false
    if (cachedAtMillis <= 0L) return true
    return nowMillis - cachedAtMillis >= minAgeMillis
}
