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

/**
 * 读取缓存时实际使用的账号键。
 *
 * 学号已知时只能读当前账号；学号未知（例如刚启动、升级后尚未解析身份）
 * 才允许回退到上次登录账号的分区，用于离线查看同一账号的旧课表。
 * 新登录时仓库会先清空 lastAccountKey，因此不会读到上一个账号。
 */
fun readableAccountKey(studentId: String?, lastAccountKey: String?): String? {
    val current = studentId?.takeIf { it.isNotBlank() }
    if (current != null) return accountCacheKey(current)
    return lastAccountKey?.trim()?.takeIf { it.isNotEmpty() }
}
