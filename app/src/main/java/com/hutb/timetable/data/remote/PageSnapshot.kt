package com.hutb.timetable.data.remote

import android.content.Context
import android.util.Log
import com.hutb.timetable.core.HtmlStructureProbe
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 页面快照工具。
 *
 * 教务系统改版时，解析失败的根本原因是"我们看到的 HTML 与开发者假设的 HTML 不一致"。
 * 这个类把抓到的原始 HTML 落盘到 App 私有目录，
 * 开发者可以用一条命令取回真实结构：
 *
 * ```bash
 * adb shell run-as com.hutb.timetable ls files/snapshots
 * adb shell run-as com.hutb.timetable cat files/snapshots/xxx.html > real.html
 * ```
 *
 * 快照可能含学号、会话 Cookie 等个人信息，落盘前先做脱敏；
 * 仅 debug 包启用，避免 release 包在用户设备上残留 HTML。
 */
object PageSnapshot {

    private const val TAG = "PageSnapshot"
    private const val DIR_NAME = "snapshots"

    /** 单个快照文件的大小上限，避免异常响应撑爆存储。 */
    private const val MAX_BYTES = 512 * 1024

    /** 最多保留的快照数量，超出后按时间删除最旧的。 */
    private const val MAX_KEEP = 10

    /** 学号形态：8~12 位连续数字（教务系统账号），整段打码。 */
    private val STUDENT_ID_REGEX = Regex("""(?<!\d)\d{8,12}(?!\d)""")

    /** 会话 Cookie 值：JSESSIONID / SERVERID 的 value 打码。 */
    private val COOKIE_VALUE_REGEX =
        Regex("""((?:JSESSIONID|SERVERID)\s*[=:]\s*)[A-Za-z0-9._~+-]{6,}""")

    /**
     * 保存一份页面快照，返回文件路径（失败返回 null）。
     *
     * @param tag 用途标识，如 "xsMain" / "cxks" / "topyfamx"
     */
    fun save(context: Context, tag: String, url: String, html: String): String? {
        return try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()

            // 精确到毫秒：同一秒内连续抓多个接口时不会互相覆盖
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val file = File(dir, "${tag}_$stamp.html")

            val sanitized = sanitize(html)
            // 记录原始大小：截断后的长度会谎报"这页到底多大"
            val originalBytes = html.toByteArray(Charsets.UTF_8).size

            // 结构指纹在**完整 HTML** 上跑：页面再大、再异常，指纹都基于真实结构。
            // 对截断副本做探针，恰恰会在最需要它的时候给出残缺结论。
            val header = buildString {
                appendLine("<!-- PageSnapshot")
                appendLine("     tag: $tag")
                appendLine("     url: $url")
                appendLine("     time: $stamp")
                appendLine("     size: $originalBytes bytes (原始大小；正文按预算截断)")
                appendLine("     ---- 结构指纹 ----")
                append(HtmlStructureProbe.probe(html).render())
                appendLine("-->")
            }

            // 预算式分配：头部固定额度，正文占剩余空间；拼接后不再二次截断
            val headerBytes = header.toByteArray(Charsets.UTF_8).size
            val bodyBudget = (MAX_BYTES - headerBytes).coerceAtLeast(0)
            file.writeText(header + truncate(sanitized, bodyBudget))

            trimOldSnapshots(dir)
            Log.i(TAG, "saved snapshot: ${file.absolutePath} ($originalBytes bytes)")
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "保存页面快照失败", e)
            null
        }
    }

    /** 清空全部快照（用户可在调试入口一键清除个人数据）。 */
    fun clearAll(context: Context) {
        val dir = File(context.filesDir, DIR_NAME)
        dir.listFiles { f -> f.name.endsWith(".html") }?.forEach { f ->
            runCatching { f.delete() }
        }
    }

    /** 脱敏个人信息：学号与会话 Cookie 值。 */
    fun sanitize(html: String): String =
        html.replace(COOKIE_VALUE_REGEX, "$1***")
            .replace(STUDENT_ID_REGEX) { m -> m.value.take(2) + "****" + m.value.takeLast(2) }

    /** 清理过旧的快照，只保留最近 [MAX_KEEP] 个。 */
    private fun trimOldSnapshots(dir: File) {
        val files = dir.listFiles { f -> f.name.endsWith(".html") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        if (files.size <= MAX_KEEP) return
        files.drop(MAX_KEEP).forEach { runCatching { it.delete() } }
    }

    /** 列出已有快照（供 App 内调试入口展示）。 */
    fun list(context: Context): List<File> {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.name.endsWith(".html") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * 截断过大的内容（按 UTF-8 字节计，中文字符占 3 字节）。
     *
     * @param maxBytes 预算字节数；头部已占用配额时由调用方扣减
     */
    fun truncate(html: String, maxBytes: Int = MAX_BYTES): String {
        if (maxBytes <= 0) return ""
        val bytes = html.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return html
        // 回退到完整 UTF-8 字符边界，避免截出半个汉字
        var cut = maxBytes
        while (cut > 0 && (bytes[cut].toInt() and 0xC0) == 0x80) cut--
        return String(bytes, 0, cut, Charsets.UTF_8) + "\n<!-- truncated -->"
    }
}
