package com.hutb.timetable.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Cookie as OkCookie

/**
 * 会话 Cookie 存储。
 *
 * 教务系统为多套 Cookie 并存的结构：
 * - JSESSIONID（强智 jsxsd 会话，HttpOnly）
 * - SERVERID（强智负载均衡节点标识）
 * - CAS 侧的 TGC / CASTGC（统一认证票据）
 *
 * [persistentCookies] 只保留需要跨进程/重启复用的凭据（JSESSIONID、SERVERID），
 * CAS 票据属于浏览器会话态，不落盘。
 */
class SessionCookieStore(
    private val onSessionChanged: (jsessionId: String, serverId: String) -> Unit = { _, _ -> }
) : CookieJar {

    /** 内存中的完整 Cookie 池（含 CAS 票据）。 */
    private val memory = LinkedHashMap<String, OkCookie>()

    /** 需要持久化的 Cookie 键。 */
    private val persistentKeys = setOf("JSESSIONID", "SERVERID")

    @Volatile
    var jsessionId: String = ""
        private set

    @Volatile
    var serverId: String = ""
        private set

    /** 是否已经拿到有效的 jsxsd 会话。 */
    val hasSession: Boolean get() = jsessionId.isNotBlank()

    override fun saveFromResponse(url: HttpUrl, cookies: List<OkCookie>) {
        var changed = false
        synchronized(memory) {
            cookies.forEach { c ->
                // 统一按 name 存，同名 Cookie 后写覆盖先写
                memory[c.name] = c
                if (c.name in persistentKeys) {
                    when (c.name) {
                        "JSESSIONID" -> jsessionId = c.value
                        "SERVERID" -> serverId = c.value
                    }
                    changed = true
                }
            }
        }
        if (changed) onSessionChanged(jsessionId, serverId)
    }

    override fun loadForRequest(url: HttpUrl): List<OkCookie> =
        synchronized(memory) {
            memory.values.filter { it.matches(url) }
        }

    /**
     * 用持久化凭据恢复会话（App 启动时调用）。
     */
    fun restore(jsessionId: String, serverId: String) {
        this.jsessionId = jsessionId
        this.serverId = serverId
        synchronized(memory) {
            if (jsessionId.isNotBlank()) {
                memory["JSESSIONID"] = OkCookie.Builder()
                    .name("JSESSIONID").value(jsessionId)
                    .domain("jwgl.hutb.edu.cn").path("/")
                    .build()
            }
            if (serverId.isNotBlank()) {
                memory["SERVERID"] = OkCookie.Builder()
                    .name("SERVERID").value(serverId)
                    .domain("jwgl.hutb.edu.cn").path("/")
                    .build()
            }
        }
    }
}
