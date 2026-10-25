package com.hutb.timetable.data.remote

import com.hutb.timetable.core.HutbError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 教务系统 HTTP 客户端。
 *
 * 统一封装：
 * - 会话 Cookie 保持（[SessionCookieStore]）
 * - 强智/凌育的 Referer 与 UA 伪装（服务端会校验）
 * - 错误归类（网络异常 / 会话失效 / 业务错误）
 * - IOException 的指数退避重试（全部请求入口统一走 [executeWithRetry]）
 */
class EduHttpClient(
    val cookieStore: SessionCookieStore,
    /** 共享的 OkHttp 实例，CookieJar 已绑定 [cookieStore]。 */
    val client: OkHttpClient
) {

    companion object {
        /**
         * 登录页的结构特征。
         *
         * 命中任意一个即认为当前不是已登录的数据页。
         * 覆盖强智 jsxsd 的 `LoginToXk` 与常见 CAS 登录入口。
         */
        private val LOGIN_FORM_MARKERS = listOf(
            "LoginToXk",
            "jsxsd/xk/",
            "id=\"Form1\""
        )

        /** IOException 额外重试次数。 */
        private const val RETRY_COUNT = 2

        /** 重试退避基数（毫秒），按 2 的幂递增：300ms / 600ms。 */
        private const val RETRY_BACKOFF_BASE_MS = 300L

        /**
         * 长任务（教室课表，读超时 90s）的最多尝试次数。
         *
         * 不能套用"90s × 3"：最坏会卡 4 分钟以上。这里只允许再试一次，
         * 且由 totalBudgetMs 兜底——第一次就耗满 90s 时不会再发起第二次。
         */
        private const val LONG_REQUEST_ATTEMPTS = 2
    }

    /** 统一 UA，模拟常见桌面浏览器，避免被服务端拒绝。 */
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * 同步 GET（内部切到 IO 线程）。
     */
    suspend fun get(url: String, referer: String? = null): Response =
        withContext(Dispatchers.IO) {
            executeWithRetry { rawGet(url, referer) }
        }

    /**
     * 同步 GET，模拟页面里 jQuery `$.ajax` 的请求头。
     *
     * 强智的部分级联接口（如 `/jsxsd/kbxx/class_query`）会校验
     * `X-Requested-With`，缺这个头直接回"提示：非法访问！"。
     *
     * @param timeoutSeconds 单独指定读超时（探测类请求用较短超时）
     */
    suspend fun getAjax(
        url: String,
        referer: String? = null,
        timeoutSeconds: Long? = null
    ): Response = withContext(Dispatchers.IO) {
        executeWithRetry { rawGetAjax(url, referer, timeoutSeconds) }
    }

    /**
     * 同步 POST 表单。
     */
    suspend fun postForm(url: String, form: Map<String, String>, referer: String? = null): Response =
        postForm(url, form, referer, ajaxHeaders = false)

    /**
     * 同步 POST 表单，可选模拟 jQuery `$.ajax` 的请求头。
     */
    suspend fun postForm(
        url: String,
        form: Map<String, String>,
        referer: String? = null,
        ajaxHeaders: Boolean = false,
        timeoutSeconds: Long? = null
    ): Response = withContext(Dispatchers.IO) {
        executeWithRetry { rawPostForm(url, form, referer, ajaxHeaders, timeoutSeconds) }
    }

    /**
     * 同步 POST 表单，可指定读超时。
     *
     * 教室课表结果页动辄 300KB+，服务端生成很慢（实测 8~90 秒），
     * 默认 30 秒读超时会在生成途中断连，因此这类请求单独放宽。
     *
     * 重试策略：最多两次尝试，且**总预算严格等于单次读超时**——
     * 第二次尝试的读超时按剩余预算分配，第一次就耗满预算时不会再发起第二次。
     */
    suspend fun postFormWithTimeout(
        url: String,
        form: Map<String, String>,
        referer: String? = null,
        readTimeoutSeconds: Long = 90
    ): Response = withContext(Dispatchers.IO) {
        executeWithBudget(
            totalBudgetMs = readTimeoutSeconds * 1000,
            maxAttempts = LONG_REQUEST_ATTEMPTS
        ) { attemptTimeoutSeconds ->
            rawPostForm(
                url = url,
                form = form,
                referer = referer,
                ajaxHeaders = false,
                timeoutSeconds = attemptTimeoutSeconds
            )
        }
    }

    private fun rawGet(url: String, referer: String?): Response {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
        referer?.let { builder.header("Referer", it) }
        return client.newCall(builder.build()).execute()
    }

    private fun rawGetAjax(url: String, referer: String?, timeoutSeconds: Long?): Response {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("X-Requested-With", "XMLHttpRequest")
        referer?.let { builder.header("Referer", it) }
        return clientWithTimeout(timeoutSeconds).newCall(builder.build()).execute()
    }

    private fun rawPostForm(
        url: String,
        form: Map<String, String>,
        referer: String?,
        ajaxHeaders: Boolean,
        timeoutSeconds: Long?
    ): Response {
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        val builder = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", userAgent)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", if (ajaxHeaders) "application/json, text/javascript, */*; q=0.01" else "*/*")
        if (ajaxHeaders) builder.header("X-Requested-With", "XMLHttpRequest")
        referer?.let { builder.header("Referer", it) }
        return clientWithTimeout(timeoutSeconds).newCall(builder.build()).execute()
    }

    /** 单独指定读超时时复制一个共享连接池的客户端。 */
    private fun clientWithTimeout(seconds: Long?): OkHttpClient =
        if (seconds == null) client
        else client.newBuilder().readTimeout(seconds, TimeUnit.SECONDS).build()

    /**
     * 所有请求入口的统一重试：仅对 IOException / 超时重试，
     * [HutbError]（含 NotLoggedIn）与取消异常立即向上抛。
     *
     * 退避用协程 `delay` 挂起而非 `Thread.sleep`，不占用 IO 线程。
     */
    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = RETRY_COUNT + 1,
        action: () -> T
    ): T {
        var lastError: IOException? = null
        repeat(maxAttempts) { attempt ->
            if (attempt > 0) {
                delay(RETRY_BACKOFF_BASE_MS * (1L shl (attempt - 1)))
            }
            try {
                return action()
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw HutbError.Network(lastError ?: IOException("网络请求失败"))
    }

    /**
     * 带总预算的重试：每次尝试的超时按剩余预算分配，
     * 保证所有尝试的**实际耗时之和**不超过 [totalBudgetMs]。
     */
    private suspend fun <T> executeWithBudget(
        totalBudgetMs: Long,
        maxAttempts: Int,
        action: (attemptTimeoutSeconds: Long) -> T
    ): T {
        require(totalBudgetMs > 0) { "总预算必须为正数" }
        var lastError: IOException? = null
        val startedAt = System.currentTimeMillis()
        repeat(maxAttempts) { attempt ->
            if (attempt > 0) delay(RETRY_BACKOFF_BASE_MS)
            val remainingMs = totalBudgetMs - (System.currentTimeMillis() - startedAt)
            if (remainingMs <= 0) {
                throw HutbError.Network(lastError ?: IOException("请求超出时间预算"))
            }
            val attemptTimeoutSeconds = maxOf(1L, remainingMs / 1000)
            try {
                return action(attemptTimeoutSeconds)
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw HutbError.Network(lastError ?: IOException("请求超出时间预算"))
    }

    /**
     * 读取响应文本，并把已知的登录失效特征转换为 [HutbError.NotLoggedIn]。
     *
     * 网络抖动（SocketTimeout / 连接重置）时由 [executeWithRetry] 做退避重试，
     * 弱网下的瞬时失败不至于直接变成报错。
     */
    suspend fun getText(url: String, referer: String? = null): String =
        withContext(Dispatchers.IO) {
            executeWithRetry {
                rawGet(url, referer).use { resp ->
                    val text = resp.readText()
                    if (isLoginRequired(text)) throw HutbError.NotLoggedIn()
                    text
                }
            }
        }

    /**
     * 判断响应体是否表示"未登录 / 会话失效"。
     *
     * 强智 jsxsd 的特征文案（实测）：
     * - "用户没有登录，请重新登录！"（app.do 接口）
     * - "请先登录系统"（jsxsd 页面）
     * - "该帐号不存在或密码错误"（CAS 侧提示）
     *
     * 教务系统改版后登录页文案也可能变化，因此这里同时用**结构特征**兜底：
     * 页面里出现指向 `LoginToXk` 的登录表单，说明这是登录页而非数据页。
     */
    fun isLoginRequired(body: String): Boolean {
        val sample = body.take(4000)
        val byText = sample.contains("用户没有登录") ||
            sample.contains("请先登录系统") ||
            sample.contains("请重新登录") ||
            sample.contains("该帐号不存在或密码错误") ||
            sample.contains("登录超时") ||
            sample.contains("loginCheck") ||
            // 改版后可能出现的新文案
            sample.contains("尚未登录") ||
            sample.contains("未登录") ||
            sample.contains("会话已过期") ||
            sample.contains("请先登录") ||
            sample.contains("重新登陆")
        if (byText) return true

        // 结构特征：登录表单
        return LOGIN_FORM_MARKERS.any { sample.contains(it) }
    }

    /** 归类网络异常。 */
    fun toHutbError(e: Throwable): HutbError = when (e) {
        is HutbError -> e
        is UnknownHostException -> HutbError.Network(e)
        is SocketTimeoutException -> HutbError.Network(e)
        is IOException -> HutbError.Network(e)
        else -> HutbError.Unknown(e.message ?: "未知错误", e)
    }
}

/**
     * 按响应/页面声明的字符集读取文本。
     *
     * 教务系统页面是 GBK，但响应头有时不带 charset；
     * 直接 `body.string()` 会按 UTF-8 解码成乱码，导致解析全失败。
     */
internal fun Response.readText(): String {
        val body = body ?: return ""
        val bytes = body.bytes()
        if (bytes.isEmpty()) return ""
        val fromHeader = body.contentType()?.charset()?.name()
        val charsetName = fromHeader ?: detectMetaCharset(bytes) ?: "GB18030"
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        return String(bytes, charset)
    }

private fun detectMetaCharset(bytes: ByteArray): String? {
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val match = Regex("""charset\s*=\s*["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(head) ?: return null
        val name = match.groupValues[1].lowercase()
        return if (name.startsWith("gb")) "GB18030" else name
    }
