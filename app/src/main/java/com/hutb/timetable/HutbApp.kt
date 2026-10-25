package com.hutb.timetable

import android.app.Application
import android.content.Context
import android.util.Log
import com.hutb.timetable.core.CoreLog
import com.hutb.timetable.data.local.AppPrefs
import com.hutb.timetable.data.local.CredentialStore
import com.hutb.timetable.data.local.AppSettings
import com.hutb.timetable.data.local.RoomOptionsCache
import com.hutb.timetable.data.local.ScheduleCache
import com.hutb.timetable.data.remote.EduHttpClient
import com.hutb.timetable.data.remote.JsxsdApi
import com.hutb.timetable.data.remote.SessionCookieStore
import com.hutb.timetable.data.repo.TimetableRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * 应用入口与轻量依赖容器。
 *
 * 这个 App 规模不大，用手写单例代替 Hilt，
 * 减少依赖与构建复杂度，逻辑更直观。
 */
class HutbApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // core 是纯 JVM 模块，日志经此出口进入 logcat；
        // 解析策略命中/降级信息在"教务系统改版后学期拿不到"时是关键线索
        CoreLog.sink = { Log.d("HutbCore", it) }
        container = AppContainer(this)
    }
}

/**
 * 依赖容器。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** 本地凭据（学号 / JSESSIONID）。 */
    val credentials: CredentialStore by lazy { CredentialStore(appContext) }

    /** 非敏感偏好（最近学期/周次、校区选择、显示偏好）。 */
    val prefs: AppPrefs by lazy { AppPrefs(appContext) }

    /**
     * Cookie 存储：负责在 WebView 登录与 OkHttp 请求之间共享会话。
     *
     * 服务端会在会话中轮换 JSESSIONID；不持久化新值的话，
     * 用户"本次能用、重启就掉线"。这里把每次变化立即写回凭据存储。
     */
    val cookieStore: SessionCookieStore by lazy {
        SessionCookieStore { jsessionId, serverId ->
            if (jsessionId.isNotBlank()) credentials.jsessionId = jsessionId
            if (serverId.isNotBlank()) credentials.serverId = serverId
        }
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieStore)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            // 重试只保留 EduHttpClient.executeWithRetry 一层：
            // OkHttp 自己的重试与其叠加会放大成最多 9 次尝试，弱网下等待时间不可控
            .retryOnConnectionFailure(false)
            .apply {
                // DEBUG 下打印请求行，排查教务系统改版时定位用的是哪个路径
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
            .build()
    }

    val eduHttp: EduHttpClient by lazy { EduHttpClient(cookieStore, httpClient) }

    /**
     * 教务系统数据源。
     *
     * 依赖 [credentials] 以便 JSON API 能带上学号，
     * 但这里是 lazy 求值，不存在初始化顺序问题。
     */
    val api: JsxsdApi by lazy { JsxsdApi(eduHttp, cookieStore, appContext, credentials, prefs) }

    val scheduleCache: ScheduleCache by lazy { ScheduleCache(appContext) }

    val roomOptionsCache: RoomOptionsCache by lazy { RoomOptionsCache(appContext) }

    val settings: AppSettings by lazy { AppSettings(appContext) }

    val repository: TimetableRepository by lazy {
        TimetableRepository(api, scheduleCache, credentials, prefs, roomOptionsCache)
    }
}
