package com.hutb.timetable.ui.login

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hutb.timetable.HutbApp
import com.hutb.timetable.R

/**
 * 统一身份认证登录页（WebView 承载）。
 *
 * 登录动作在 WebView 中由用户手动完成（含滑块验证码）；
 * 登录成功后，服务端在 `jwgl.hutb.edu.cn` 域下发 `JSESSIONID` / `SERVERID`，
 * 这里读出 Cookie 并返回给调用方。会话是否仍然有效以首次数据请求为准，
 * 本页不额外发起服务端验证（那会与页面 UA/Cookie 路径产生误判）。
 */
class WebLoginActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WebLoginActivity"

        /** 统一认证入口（带 service 参数，登录后回跳教务系统）。 */
        private const val CAS_LOGIN_URL =
            "https://cas.hutb.edu.cn/lyuapServer/login?service=http%3A%2F%2Fjwgl.hutb.edu.cn%2F"

        /** 教务系统根地址：登录成功后会被重定向到这里。 */
        private const val EDU_ROOT = "http://jwgl.hutb.edu.cn/"

        /** 返回给调用方的会话标识。 */
        const val RESULT_JSESSION_ID = "result_jsession_id"
        const val RESULT_SERVER_ID = "result_server_id"

        /** 登录成功标志。 */
        const val RESULT_OK = Activity.RESULT_OK

        /** 与 [RESULT_OK] 配对的取消结果。 */
        const val RESULT_CANCELED = Activity.RESULT_CANCELED

        /** 构造登录页 Intent（供 Activity Result API 使用）。 */
        fun intent(context: Context): Intent =
            Intent(context, WebLoginActivity::class.java)
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var rootLayout: LinearLayout

    private var loginSucceeded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_login)

        rootLayout = findViewById(R.id.rootLayout)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        configureWebView()
        applyWindowInsets()
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        // 清空上一次遗留的全部 WebView Cookie（教务 + CAS）：
        // 只按名字/路径删容易漏，且 CAS 的 TGC 还在时会被 SSO 直接送回原账号。
        // removeAllCookies 是异步的，必须等回调后再加载登录页，
        // 否则可能出现"旧 Cookie 尚未删完、页面已经开始认证"。
        CookieManager.getInstance().removeAllCookies {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) webView.loadUrl(CAS_LOGIN_URL)
            }
        }

        // 返回键：优先回退认证页历史，回到起点后关闭登录页返回 App
        onBackPressedDispatcher.addCallback(this) {
            if (::webView.isInitialized && webView.canGoBack()) {
                webView.goBack()
            } else {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
    }

    /**
     * 处理系统栏边距。
     *
     * targetSdk 35+ 强制 edge-to-edge，窗口内容会画到状态栏/导航栏之下，
     * 需要手动把系统栏 inset 转成根布局的 padding，否则顶部提示条会被状态栏盖住。
     */
    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            // 统一认证是 HTTPS、教务系统是 HTTP，跳转需要放行混合内容；
            // 用兼容模式而不是 ALWAYS_ALLOW，让 WebView 对可疑子资源保持默认拦截
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            // 登录页不需要访问本地文件/内容
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                checkLoginSuccess(url)
            }

            // 证书错误一律取消加载：教务/认证链路本就有明文环节，
            // 不能再让 HTTPS 侧的证书问题被静默放行
            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.cancel()
                Log.w(TAG, "SSL 证书错误，已取消加载: ${error?.primaryError}")
            }

            // minSdk 24 起只会走这个带 WebResourceRequest 的重载
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString() ?: return false
                return handleUrl(target)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
    }

    /**
     * 处理跳转 URL。
     * 只放行学校域名；回到教务系统域时才尝试判定登录。
     */
    private fun handleUrl(target: String): Boolean {
        Log.d(TAG, "navigate: $target")
        // 域名白名单：登录流程只应涉及 cas / jwgl，其余一律拦截
        if (!isAllowedHost(target)) {
            Log.w(TAG, "拦截非学校跳转: ${safeUrlForLog(target)}")
            return true
        }
        checkLoginSuccess(target)
        return false
    }

    /**
     * 检查是否已登录成功。
     *
     * 必要条件：**当前页面已在教务系统域**，且教务域上存在 JSESSIONID。
     * 会话 Cookie 可能挂在根路径或 `/jsxsd/` 路径下，两者都要检查。
     *
     * 关键前提是进入本页时已清空全部 WebView Cookie——
     * 因此这里看到的 Cookie 只可能由本次认证流程签发。
     */
    private fun checkLoginSuccess(url: String?) {
        if (loginSucceeded) return
        if (!isEduHost(url)) return
        val cookieManager = CookieManager.getInstance()
        val cookies = buildList {
            cookieManager.getCookie(EDU_ROOT)?.let(::add)
            cookieManager.getCookie(EDU_ROOT.trimEnd('/') + "/jsxsd/")?.let(::add)
        }.joinToString(";")
        val jsession = parseCookie(cookies, "JSESSIONID")
        if (jsession.isNullOrBlank()) return
        val serverId = parseCookie(cookies, "SERVERID").orEmpty()
        Log.i(TAG, "登录成功：教务域已签发新会话")
        completeLogin(jsession, serverId)
    }

    /** 域名精确白名单：只允许 hutb.edu.cn 及其子域，且仅 http/https。 */
    private fun isAllowedHost(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "hutb.edu.cn" || host.endsWith(".hutb.edu.cn")
    }

    /** 日志只保留 host/path，避免把查询参数里的票据写进 logcat。 */
    private fun safeUrlForLog(url: String): String {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return "<invalid>"
        return "${uri.host.orEmpty()}${uri.path.orEmpty()}"
    }

    /** 当前 URL 是否属于教务系统（而不是统一认证）。 */
    private fun isEduHost(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
        return host.equals("jwgl.hutb.edu.cn", ignoreCase = true)
    }

    private fun completeLogin(jsession: String, serverId: String) {
        loginSucceeded = true

        // 同步到 OkHttp 的 CookieJar，后续纯 API 查询复用同一会话。
        // 用 runCatching 兜底：同步失败不应让整个登录页崩掉，
        // 否则用户已经验证通过的凭证会白白丢失。
        runCatching {
            val app = application as? HutbApp ?: return@runCatching
            app.container.cookieStore.restore(jsession, serverId)
        }.onFailure {
            Log.e(TAG, "同步会话到 CookieStore 失败", it)
        }

        statusText.text = getString(R.string.login_success)
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(RESULT_JSESSION_ID, jsession)
                putExtra(RESULT_SERVER_ID, serverId)
            }
        )
        finish()
    }

    private fun parseCookie(cookieHeader: String, name: String): String? =
        cookieHeader.split(";").firstOrNull { it.trim().startsWith("$name=") }
            ?.substringAfter("$name=")
            ?.trim()

    override fun onDestroy() {
        super.onDestroy()
        // onCreate 早期失败时 webView 可能尚未赋值，此时不能访问 lateinit
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }
}
