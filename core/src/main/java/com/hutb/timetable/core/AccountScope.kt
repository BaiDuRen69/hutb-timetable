package com.hutb.timetable.core

import java.security.MessageDigest

/**
 * 账号维度的缓存键。
 *
 * 个人课表缓存必须按账号隔离：同一台设备上 A 退出、B 登录后，
 * 绝不能把 A 的缓存当 B 的课表先上屏。学号本身不写进缓存表，
 * 取 SHA-256 前 16 位作为不可逆标识。
 */
fun accountCacheKey(studentId: String): String {
    val clean = studentId.trim()
    if (clean.isEmpty()) return "anon"
    val digest = MessageDigest.getInstance("SHA-256").digest(clean.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(16)
}
