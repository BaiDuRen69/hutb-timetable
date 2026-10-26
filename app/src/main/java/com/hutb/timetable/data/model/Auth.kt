package com.hutb.timetable.data.model

import com.hutb.timetable.core.HutbError

/**
 * 登录态。
 */
data class AuthSession(
    /** jsxsd 会话 Cookie（JSESSIONID） */
    val jsessionId: String,
    /** 强智 SERVERID，缺失时部分接口会要求 */
    val serverId: String = "",
    /** 登录显示名（姓名 / 账号） */
    val displayName: String = ""
) {
    val isValid: Boolean get() = jsessionId.isNotBlank()
}
