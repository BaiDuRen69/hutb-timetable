package com.hutb.timetable.data.remote

import android.content.Context
import android.util.Log
import com.hutb.timetable.BuildConfig
import com.hutb.timetable.core.CoreLog
import com.hutb.timetable.core.HutbError
import com.hutb.timetable.core.JsxsdScheduleParser
import com.hutb.timetable.core.RoomOption
import com.hutb.timetable.core.ScheduleQuery
import com.hutb.timetable.core.Semester
import com.hutb.timetable.core.WeekSchedule
import com.hutb.timetable.core.isOpaqueRoomName
import com.hutb.timetable.core.roomBelongsToBuilding
import com.hutb.timetable.data.local.AppPrefs
import com.hutb.timetable.data.local.CredentialStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 强智 jsxsd 教务系统数据源。
 *
 * 站点结构（实测）：
 * - 站点根   `http://jwgl.hutb.edu.cn/`
 * - 学生端   `http://jwgl.hutb.edu.cn/jsxsd/`
 * - 课表页   `/jsxsd/kszx/cxks`（学生课表）、`/jsxsd/xskb/xskb_list.do`（学期理论课表）
 * - 学期选项 `/jsxsd/framework/xsMain.jsp`
 * - JSON API `/app.do?method=xxx`（智校园 APP 接口，需先持有 web 会话）
 *
 * 所有查询都需要携带登录后的 JSESSIONID，由 [SessionCookieStore] 自动注入。
 */
class JsxsdApi(
    private val http: EduHttpClient,
    private val cookieStore: SessionCookieStore,
    /** 可选：用于把抓到的页面落盘成快照（debug 排查改版问题）。 */
    private val appContext: Context? = null,
    /** 可选：提供学号，供 JSON API 使用。 */
    private val credentials: CredentialStore? = null,
    /** 可选：非敏感偏好（记住上次成功的学期来源路径）。 */
    private val prefs: AppPrefs? = null
) {

    companion object {
        private const val TAG = "JsxsdApi"

        const val BASE = "http://jwgl.hutb.edu.cn"

        /** 学生课表查询页（改版后主路径）。 */
        const val PATH_STUDENT_SCHEDULE = "/jsxsd/xskb/xskb_list.do"

        /** 学生课表查询页（老版路径，部分部署仍可用）。 */
        const val PATH_STUDENT_SCHEDULE_LEGACY = "/jsxsd/kszx/cxks"

        /** 学期理论课表（老版路径，部分部署仍可用）。 */
        const val PATH_SEMESTER_SCHEDULE = "/jsxsd/xskb/xskb_list.do"

        /** 学生主页框架，含学期下拉选项。 */
        const val PATH_MAIN_FRAME = "/jsxsd/framework/xsMain.jsp"

        /** 班级/教室课表查询（教学安排）。 */
        const val PATH_ROOM_SCHEDULE = "/jsxsd/kbcx/kbxx_classroom"

        /** 教室课表结果页（表单提交目标）。 */
        const val PATH_ROOM_SCHEDULE_IFR = "/jsxsd/kbcx/kbxx_classroom_ifr"

        /**
         * 教学楼/教室级联下拉框的 AJAX 接口。
         *
         * 教室课表页的 `select#jzwid`（教学楼）与 `select#classroomID`（教室）
         * 初始只有「请选择」，实际内容由页面脚本按上级选择动态加载：
         * - 教学楼：`POST /jsxsd/kbcx/getJxlByAjax?xqid=<校区>` → `[{"dm","dmmc"}]`
         * - 教室：  `GET  /jsxsd/kbxx/class_query?jzwid=<教学楼 dm>`
         *
         * 实测**只有教学楼那个接口存在**：教室接口与任意不存在的路径一样
         * 返回"提示：非法访问！"错误页，因此教室清单改由教室课表结果页反推。
         */
        const val PATH_ROOM_AJAX = "/jsxsd/kbcx/getJxlByAjax"

        /** 教室课表结果页的生成很慢，读超时单独放宽。 */
        private const val ROOM_TABLE_READ_TIMEOUT_SECONDS = 90L

        /**
         * 教室课表页的查询参数。
         *
         * 改版后页面下拉框含义（实测）：
         * - `xnxqh` 学年学期，如 `2026-2027-1`
         * - `xqid`  校区，如 `0t`=月亮岛校区、`00001`=南校区、`00002`=北校区
         * - `skyx`  上课学院
         * - `jzwid` 教学楼
         * - `classroomID` 教室
         * - `zc1`/`zc2` 周次起止；`jc1`/`jc2` 节次起止
         */
        const val PATH_APP_API = "/app.do"

        /**
         * 学期列表的候选来源页面，按优先级排列。
         *
         * 教务系统改版后学期下拉框的位置不固定，因此逐个尝试。
         */
        val SEMESTER_SOURCE_PATHS = listOf(
            PATH_STUDENT_SCHEDULE,
            PATH_MAIN_FRAME,
            PATH_STUDENT_SCHEDULE_LEGACY,
            "/jsxsd/framework/main.jsp",
            PATH_SEMESTER_SCHEDULE,
            "/jsxsd/kszx/xskb.jsp",
            // 改版后可能出现的入口
            "/jsxsd/framework/xsIndex.jsp",
            "/jsxsd/framework/xsTop.jsp",
            "/jsxsd/kszx/cxks_all",
            "/jsxsd/xskb/xskb_list"
        )

        /**
         * JSON API 的候选方法名。
         *
         * 强智各版本命名不一，从 `getXnxq` 开始逐个尝试。
         */
        val SEMESTER_API_METHODS = listOf(
            "getXnxq",
            "getXnxqList",
            "queryXnxq",
            "getXnxqh",
            "getXnxxq"
        )

        /** 单个 AJAX 探测请求的读超时（秒）：探测是试探性的，不能等满 30 秒。 */
        private const val AJAX_PROBE_TIMEOUT_SECONDS = 4L

        /** AJAX 探测阶段的总预算（毫秒）。 */
        private const val AJAX_PROBE_BUDGET_MS = 12_000L
    }

    /**
     * 获取可选学年学期列表。
     *
     * 教务系统改版后，学期下拉框所在页面会变，因此这里**依次尝试多个候选页面**，
     * 第一个能解析出学期列表的即返回。
     *
     * 每个候选页面都会落盘快照（debug 包），便于改版后快速定位真实结构。
     */
    suspend fun fetchSemesters(): List<Semester> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        // ---- 路径 1：强智 JSON API getXnxq（最稳，优先尝试）----
        // 该接口直接返回学年学期的 JSON 数组：
        //   [{"xnxq01id":"2026-2027-1","xqmc":"2026-2027学年第一学期"}, ...]
        // 需要已登录会话；未登录会返回"出错页面"HTML。
        fetchSemestersViaAppApi()?.let { apiSemesters ->
            Log.i(TAG, "学期列表来自 JSON API getXnxq，共 ${apiSemesters.size} 项")
            return@withContext apiSemesters
        }

        // ---- 路径 2：依次尝试多个候选页面 ----
        // 上次成功过的路径优先：学期列表候选页面有 10 个，
        // 逐个探测每次都要多打几次请求，记住成功路径可以省掉这些探测。
        val cachedPath = prefs?.lastSemesterSourcePath.orEmpty()
        val orderedPaths = if (cachedPath.isNotBlank() && cachedPath in SEMESTER_SOURCE_PATHS) {
            listOf(cachedPath) + (SEMESTER_SOURCE_PATHS - cachedPath)
        } else {
            SEMESTER_SOURCE_PATHS
        }
        for (path in orderedPaths) {
            val html = try {
                http.getText(BASE + path, referer = BASE + "/jsxsd/")
            } catch (e: HutbError) {
                // 会话失效必须一路抛给 UI，不能降级成"页面结构变化"
                if (e is HutbError.NotLoggedIn) throw e
                lastError = e
                continue
            } catch (e: Exception) {
                lastError = e
                continue
            }

            snapshot("semesters", BASE + path, html)

            val semesters = JsxsdScheduleParser.parseSemesters(html)
            if (semesters.isNotEmpty()) {
                prefs?.lastSemesterSourcePath = path
                Log.i(TAG, "学期列表来自 $path，共 ${semesters.size} 项")
                return@withContext semesters
            }
            Log.w(TAG, "$path 未解析出学期列表，尝试下一个候选")
        }

        // ---- 路径 3：兜底，从课表查询页反查（课表页通常也带学期下拉）----
        val fallback = try {
            val html = http.getText(
                BASE + PATH_STUDENT_SCHEDULE + "?zc=1",
                referer = BASE + "/jsxsd/"
            )
            snapshot("semesters_fallback", BASE + PATH_STUDENT_SCHEDULE, html)
            JsxsdScheduleParser.parseSemesters(html)
        } catch (e: Exception) {
            lastError = e
            emptyList()
        }

        if (fallback.isNotEmpty()) return@withContext fallback

        throw HutbError.Parse(
            "未能解析出学年学期列表。请点右上角 🐞 打开「页面快照」，" +
                "选中最新的一份点「复制指纹」，把内容发给我即可精准适配改版后的结构。",
            lastError
        )
    }

    /**
     * 通过强智 JSON API 获取学年学期列表。
     *
     * `/app.do?method=getXnxq&xh=<学号>` 返回：
     * ```json
     * [{"xnxq01id":"2026-2027-1","xqmc":"2026-2027学年第一学期"}, ...]
     * ```
     *
     * 该接口需要已登录会话；未登录时返回 HTML 错误页。
     * 返回 null 表示接口不可用，调用方应回退到 HTML 解析。
     */
    private suspend fun fetchSemestersViaAppApi(): List<Semester>? {
        val studentId = credentials?.studentId.orEmpty()
        for (method in SEMESTER_API_METHODS) {
            val semesters = fetchSemestersViaAppApi(method, studentId)
            if (semesters != null) return semesters
        }
        return null
    }

    /**
     * 通过强智 JSON API 获取学年学期列表（指定方法名）。
     *
     * `/app.do?method=getXnxq&xh=<学号>` 返回：
     * ```json
     * [{"xnxq01id":"2026-2027-1","xqmc":"2026-2027学年第一学期"}, ...]
     * ```
     *
     * 该接口需要已登录会话；未登录时返回 HTML 错误页。
     * 返回 null 表示接口不可用，调用方应回退到 HTML 解析。
     */
    private suspend fun fetchSemestersViaAppApi(method: String, studentId: String): List<Semester>? {
        return try {
            val url = "$BASE$PATH_APP_API?method=$method" +
                (if (studentId.isNotBlank()) "&xh=$studentId" else "")

            val text = http.get(url, referer = BASE + PATH_STUDENT_SCHEDULE)
                .use { resp -> resp.readText() }

            if (http.isLoginRequired(text) || !text.trimStart().startsWith("[")) {
                null
            } else {
                parseXnxqJson(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "$method 接口调用失败", e)
            null
        }
    }

    /**
     * 解析 `getXnxq` 的 JSON 返回。
     *
     * 字段：`xnxq01id` = 学期 ID，`xqmc` = 学期名称。
     */
    private fun parseXnxqJson(json: String): List<Semester>? {
        return try {
            val array = org.json.JSONArray(json)
            val result = mutableListOf<Semester>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("xnxq01id").trim()
                if (id.isEmpty()) continue
                val name = obj.optString("xqmc").trim().ifEmpty { id }
                result.add(Semester(id = id, name = name))
            }
            result.ifEmpty { null }
        } catch (e: Exception) {
            Log.w(TAG, "解析 getXnxq 返回失败", e)
            null
        }
    }

    /**
     * 登录后解析学号/姓名，用于账号级缓存与 JSON API。
     *
     * 首次登录时 App 没有任何身份信息（WebView 只带回会话），
     * 这里从教务首页文本里提取；解析不到时返回 null，由调用方降级。
     */
    suspend fun fetchStudentIdentity(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val pages = listOf(
            "/jsxsd/framework/xsMain.jsp",
            "/jsxsd/framework/xsTop.jsp"
        )
        for (path in pages) {
            val text = try {
                http.getText(BASE + path, referer = BASE + "/jsxsd/")
            } catch (e: HutbError) {
                if (e is HutbError.NotLoggedIn) throw e
                continue
            } catch (e: Exception) {
                continue
            }
            val all = org.jsoup.Jsoup.parse(text).text()
            val id = Regex("""学号[:：]?\s*(\d{6,12})""").find(all)?.groupValues?.get(1)
                ?: Regex("""xh\s*[=:]\s*["']?(\d{6,12})""").find(text)?.groupValues?.get(1)
            val name = Regex("""姓名[:：]?\s*([一-龥]{2,6})""").find(all)?.groupValues?.get(1)
            if (id != null) return@withContext id to name.orEmpty()
        }
        null
    }

    /** 保存页面快照，仅 debug 包生效。 */
    private fun snapshot(tag: String, url: String, html: String) {
        if (!BuildConfig.DEBUG) return
        val context = appContext ?: return
        PageSnapshot.save(context, tag, url, PageSnapshot.truncate(html))
    }

    /**
     * 查询某学期某周的个人课表。
     *
     * 强智的课表查询是 POST 表单：
     * - `zc`         教学周次
     * - `xnxq01id`   学年学期 ID
     */
    suspend fun fetchSchedule(query: ScheduleQuery): WeekSchedule = withContext(Dispatchers.IO) {
        val form = mapOf(
            "zc" to query.week.toString(),
            "xnxq01id" to query.semesterId
        )
        val html = try {
            http.postForm(
                BASE + PATH_STUDENT_SCHEDULE,
                form,
                referer = BASE + PATH_STUDENT_SCHEDULE
            ).use { resp ->
                val text = resp.readText()
                if (http.isLoginRequired(text)) {
                    // 记录状态码与最终 URL，便于区分"会话过期"与"接口改版返回登录页"
                    Log.w(
                        TAG,
                        "个人课表返回登录页: code=${resp.code} url=${resp.request.url} 长度=${text.length}"
                    )
                    throw HutbError.NotLoggedIn()
                }
                snapshot("cxks", BASE + PATH_STUDENT_SCHEDULE, text)
                text
            }
        } catch (e: HutbError) {
            throw e
        } catch (e: Exception) {
            throw http.toHutbError(e)
        }

        // HTML 解析是 CPU 密集活（300KB+ 页面做 Jsoup 全量解析），
        // 放 Default 线程池，别占用 IO 线程
        return@withContext withContext(Dispatchers.Default) {
            val semesterName = JsxsdScheduleParser.parseSemesters(html)
                .firstOrNull { it.id == query.semesterId }?.name
                ?: query.semesterId

            JsxsdScheduleParser.parseSchedule(
                html = html,
                semesterId = query.semesterId,
                semesterName = semesterName,
                week = query.week
            )
        }
    }

    /**
     * 查询教室（教学安排）课表。
     *
     * 改版后教室课表需要通过 POST 到 `/jsxsd/kbcx/kbxx_classroom_ifr` 获取，
     * 表单字段：
     * - `xnxqh`   学期 ID
     * - `xqid`    校区 ID（没指定教学楼时用来定位校区）
     * - `jzwid`   教学楼 ID（可选，单独就能定位教学楼）
     * - `zc1` / `zc2` 周次范围
     *
     * 注意：**不要提交 `classroomID`**。服务端只认教室的内部编码，
     * 而该编码只能由并不存在的 `class_query` 接口给出，
     * 提交教室名会得到一张空表。因此具体教室改为在客户端按教室名过滤。
     */
    suspend fun fetchRoomSchedule(
        semesterId: String,
        week: Int,
        campusId: String? = null,
        buildingId: String? = null,
        classroomId: String? = null,
        building: String? = null,
        ignoreMemoryCache: Boolean = false
    ): WeekSchedule = withContext(Dispatchers.IO) {
        val html = fetchRoomScheduleHtml(semesterId, week, campusId, buildingId, ignoreMemoryCache)
            ?: throw HutbError.Parse("未获取到教室课表数据")

        // 解析同上放 Default：大页面 Jsoup 解析不应阻塞 IO 线程池
        return@withContext withContext(Dispatchers.Default) {
            val semesterName = JsxsdScheduleParser.parseSemesters(html)
                .firstOrNull { it.id == semesterId }?.name
                ?: semesterId

            // 教室代码与教室名在这里是同一个值（从课表首列反推）
            val roomName = classroomId?.takeIf { it.isNotBlank() }

            JsxsdScheduleParser.parseRoomSchedule(
                html = html,
                semesterId = semesterId,
                semesterName = semesterName,
                week = week,
                roomCode = classroomId,
                roomName = roomName,
                building = building
            )
        }
    }

    /**
     * 抓取教室课表结果页的 HTML。
     *
     * 该页面体积大（300KB+）且服务端生成慢，因此读超时单独放宽，
     * 并把结果缓存起来：教室清单与课表内容来自同一个页面，
     * 没必要为"选教学楼"和"查课表"各拉一次。
     * 并发调用同一参数组合时共享同一次在途请求。
     *
     * 参数策略（2026-10-23 真机实测）：
     * - `jzwid` 单独就能定位教学楼，服务端返回该楼的全部教室，
     *   不会混入别的楼（实测至诚楼 57 间、日新楼 31 间、立德楼 22 间）。
     * - `xqid` 在有 `jzwid` 时被服务端忽略（两种提交方式返回的字节完全一致），
     *   只在没选教学楼时用来定位校区；因此两个都带上，与校方网页表单一致。
     */
    private suspend fun fetchRoomScheduleHtml(
        semesterId: String,
        week: Int,
        campusId: String?,
        buildingId: String?,
        ignoreMemoryCache: Boolean = false
    ): String? {
        val cacheKey = cacheKeyOf(semesterId, week, campusId, buildingId)
        if (!ignoreMemoryCache) {
            synchronized(roomTableCache) {
                roomTableCache[cacheKey]
            }?.let {
                Log.i(TAG, "教室课表页命中缓存：$cacheKey")
                return it
            }
        }

        // 进行中去重：并发的调用方共享同一个 Deferred，不会重复打服务端
        val deferred = synchronized(inFlightRoomPages) {
            inFlightRoomPages[cacheKey]?.let { return@synchronized it }
            scope.async {
                try {
                    fetchRoomScheduleHtmlRemote(semesterId, week, campusId, buildingId)
                } finally {
                    synchronized(inFlightRoomPages) { inFlightRoomPages.remove(cacheKey) }
                }
            }.also { inFlightRoomPages[cacheKey] = it }
        }
        return deferred.await()
    }

    private suspend fun fetchRoomScheduleHtmlRemote(
        semesterId: String,
        week: Int,
        campusId: String?,
        buildingId: String?
    ): String? {
        val form = buildMap {
            put("xnxqh", semesterId)
            put("zc1", week.toString())
            put("zc2", week.toString())
            campusId?.takeIf { it.isNotBlank() }?.let { put("xqid", it) }
            buildingId?.takeIf { it.isNotBlank() }?.let { put("jzwid", it) }
        }
        return try {
            http.postFormWithTimeout(
                BASE + PATH_ROOM_SCHEDULE_IFR,
                form,
                referer = BASE + PATH_ROOM_SCHEDULE,
                readTimeoutSeconds = ROOM_TABLE_READ_TIMEOUT_SECONDS
            ).use { resp ->
                val text = resp.readText()
                if (http.isLoginRequired(text)) throw HutbError.NotLoggedIn()
                snapshot("kbxx_classroom", BASE + PATH_ROOM_SCHEDULE, text)
                synchronized(roomTableCache) { roomTableCache[cacheKeyOf(semesterId, week, campusId, buildingId)] = text }
                text
            }
        } catch (e: HutbError) {
            throw e
        } catch (e: Exception) {
            throw http.toHutbError(e)
        }
    }

    private fun cacheKeyOf(semesterId: String, week: Int, campusId: String?, buildingId: String?) =
        listOf(semesterId, week, campusId.orEmpty(), buildingId.orEmpty()).joinToString("|")

    /** 教室课表结果页缓存（LRU）。翻周次来回切时同周直接复用。 */
    private val roomTableCache = object : LinkedHashMap<String, String>(4, 0.75f, true) {
        // 单份全校课表 HTML 可达 ~1.9MB，条数上限必须收紧，否则常驻内存过大
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 3
    }

    /** 在途的教室课表页请求（cacheKey → Deferred），用于并发去重。 */
    private val inFlightRoomPages = ConcurrentHashMap<String, Deferred<String?>>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 释放后台资源：取消在途请求、清空页面缓存并取消 scope。
     *
     * JsxsdApi 是进程级单例，正常运行不需要调用；
     * 保留显式入口，避免"scope 永不取消且无法取消"。
     */
    fun dispose() {
        cancelInFlightRoomRequests()
        synchronized(roomTableCache) { roomTableCache.clear() }
        scope.cancel()
    }

    /**
     * 获取校区列表。
     *
     * 校区下拉框 `select#xqid` 是服务端渲染的，直接从教室课表页解析。
     * 取值形如 `0t`=湖南工商大学月亮岛校区、`00001`=南校区、`00002`=北校区。
     */
    suspend fun fetchCampuses(semesterId: String? = null): List<RoomOption> {
        val html = fetchRoomPage(semesterId = semesterId) ?: return emptyList()
        return JsxsdScheduleParser.parseCampuses(html)
    }

    /**
     * 获取指定校区的教学楼列表。
     *
     * 优先用页面上的 `select#jzwid`；该下拉框初始为空时，
     * 退到 AJAX 接口 `getJxlByAjax`，按校区（`xqid`）逐级加载。
     */
    suspend fun fetchBuildings(
        campusId: String,
        semesterId: String? = null
    ): List<RoomOption> {
        val html = fetchRoomPage(semesterId = semesterId, campusId = campusId)
        val fromPage = html?.let { JsxsdScheduleParser.parseBuildingOptions(it) }.orEmpty()
        if (fromPage.isNotEmpty()) {
            Log.i(TAG, "教学楼来自页面 #jzwid，共 ${fromPage.size} 个")
            return fromPage
        }

        // 页面没渲染出来时，直接提交表单让服务端按校区填表
        val fromPost = fetchRoomPage(
            semesterId = semesterId,
            campusId = campusId,
            post = true
        )?.let { JsxsdScheduleParser.parseBuildingOptions(it) }.orEmpty()
        if (fromPost.isNotEmpty()) {
            Log.i(TAG, "教学楼来自 POST 后的 #jzwid，共 ${fromPost.size} 个")
            return fromPost
        }

        val buildings = probeRoomAjax(
            paths = listOf(PATH_ROOM_AJAX),
            paramSets = listOf(
                mapOf("xqid" to campusId),
                mapOf("xqid" to campusId, "xnxqh" to semesterId.orEmpty()),
                mapOf("xqid" to campusId, "xnxqh" to semesterId.orEmpty(), "zc1" to "1", "zc2" to "1"),
                emptyMap()
            ).filter { it.values.none { v -> v.isBlank() } }
        )

        Log.i(TAG, "教学楼来自 getJxlByAjax，共 ${buildings.size} 个")
        return buildings
    }

    /**
     * 获取指定教学楼下的教室列表。
     *
     * 教务系统页面脚本里的 `GET /jsxsd/kbxx/class_query?jzwid=…`
     * 在本校部署中**并不存在**（与任意不存在的路径一样返回"提示：非法访问！"），
     * 因此拿不到下拉框里的教室编码。
     *
     * 可行的替代：按教学楼查询整栋楼的教室课表，
     * 结果表格的**首列就是这栋楼下的教室**（如「至诚楼A101*(智慧)」），
     * 拿它当教室清单，编码与名称都用这个名字。
     *
     * 服务端按 `jzwid` 返回该教学楼的教室，但同一校区内的场馆会被顺带带出来
     * （实测「月亮岛立德楼」的结果里混着「月亮岛体育馆」），
     * 因此这里再按教学楼名收窄一次；名字对不上时保留原始列表，避免列表变空。
     */
    suspend fun fetchClassrooms(
        campusId: String,
        buildingId: String,
        semesterId: String? = null,
        week: Int = 1,
        buildingName: String? = null
    ): List<RoomOption> {
        val html = fetchRoomScheduleHtml(
            semesterId = semesterId.orEmpty(),
            week = week,
            campusId = campusId,
            buildingId = buildingId
        ) ?: return emptyList()

        // 大页面解析放 Default 线程池
        val labels = withContext(Dispatchers.Default) {
            JsxsdScheduleParser.parseRoomLabels(html)
        }
        val scoped = labels.filter { roomBelongsToBuilding(it, buildingName) }
        val result = if (scoped.isEmpty()) labels else scoped
        Log.i(
            TAG,
            "教室由课表首列反推：原始 ${labels.size} 个，按教学楼[$buildingName]收窄后 ${scoped.size} 个"
        )
        return result.map { RoomOption(code = it, name = it) }
    }

    /**
     * 抓取教室课表页。
     *
     * 带上课期与校区参数，服务端才可能把教学楼下拉框渲染出来。
     */
    private suspend fun fetchRoomPage(
        semesterId: String? = null,
        campusId: String? = null,
        post: Boolean = false
    ): String? {
        return try {
            val query = buildList {
                semesterId?.takeIf { it.isNotBlank() }?.let { add("xnxqh" to it) }
                campusId?.takeIf { it.isNotBlank() }?.let { add("xqid" to it) }
            }.joinToString("&") { (k, v) -> "$k=$v" }

            val url = if (query.isEmpty()) {
                BASE + PATH_ROOM_SCHEDULE
            } else {
                BASE + PATH_ROOM_SCHEDULE + "?" + query
            }

            val text = if (post) {
                val form = buildMap {
                    semesterId?.takeIf { it.isNotBlank() }?.let { put("xnxqh", it) }
                    campusId?.takeIf { it.isNotBlank() }?.let { put("xqid", it) }
                }
                http.postForm(url, form, referer = BASE + PATH_ROOM_SCHEDULE)
                    .use { resp -> resp.readText() }
            } else {
                http.get(url, referer = BASE + PATH_STUDENT_SCHEDULE)
                    .use { resp -> resp.readText() }
            }

            if (http.isLoginRequired(text)) {
                Log.w(TAG, "教室课表页未登录")
                throw HutbError.NotLoggedIn()
            }
            snapshot("rooms", url, text)
            text
        } catch (e: HutbError) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "抓取教室课表页失败", e)
            null
        }
    }

    /**
     * 探测级联下拉框的 AJAX 接口，返回**第一个**解析出内容的候选结果。
     *
     * 该接口的路径与参数组合随部署而异，而服务端对不存在的路径同样返回登录页，
     * 因此这里可以安全地多试几个候选路径与参数组。
     *
     * 性能：所有候选（路径 × 参数组 × GET/POST）**并发**发出，
     * 首个非空即返回并取消其余；单个请求 4 秒读超时 + 12 秒总预算，
     * 避免串行探测最坏等上几分钟。
     */
    private suspend fun probeRoomAjax(
        paths: List<String>,
        paramSets: List<Map<String, String>>
    ): List<RoomOption> = coroutineScope {
        val attempts = buildList {
            for (path in paths) {
                for (params in paramSets) {
                    for (asPost in listOf(false, true)) {
                        add(Triple(path, params, asPost))
                    }
                }
            }
        }
        if (attempts.isEmpty()) return@coroutineScope emptyList()

        val winner = CompletableDeferred<List<RoomOption>>()
        val snapshotted = ConcurrentHashMap.newKeySet<String>()
        val jobs = attempts.map { (path, params, asPost) ->
            async(Dispatchers.IO) {
                if (winner.isCompleted) return@async
                val query = params.entries.joinToString("&") { (k, v) -> "$k=$v" }
                val url = BASE + path + if (query.isEmpty()) "" else "?$query"

                val text = try {
                    if (asPost) {
                        http.postForm(
                            url,
                            params,
                            referer = BASE + PATH_ROOM_SCHEDULE,
                            ajaxHeaders = true,
                            timeoutSeconds = AJAX_PROBE_TIMEOUT_SECONDS
                        ).use { resp -> resp.readText() }
                    } else {
                        http.getAjax(
                            url,
                            referer = BASE + PATH_ROOM_SCHEDULE,
                            timeoutSeconds = AJAX_PROBE_TIMEOUT_SECONDS
                        ).use { resp -> resp.readText() }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AJAX 请求失败 path=$path params=$params post=$asPost", e)
                    return@async
                }

                if (text.isBlank() || http.isLoginRequired(text)) {
                    Log.w(
                        TAG,
                        "AJAX 无可用内容 path=$path params=$params post=$asPost " +
                            "blank=${text.isBlank()} 需登录=${http.isLoginRequired(text)} " +
                            "长度=${text.length} 开头=${text.take(80)}"
                    )
                    return@async
                }

                // 存快照：字段名各版本不一，需要原始响应才能精准适配
                if (snapshotted.add(text)) {
                    snapshot("rooms_ajax", "$path?$query", text)
                }

                val options = parseAjaxOptions(text)
                if (options.isNotEmpty()) {
                    Log.i(TAG, "AJAX 命中 path=$path params=$params post=$asPost，共 ${options.size} 项")
                    winner.complete(options)
                } else {
                    Log.w(
                        TAG,
                        "AJAX 响应无法解析 path=$path params=$params post=$asPost " +
                            "长度=${text.length} 开头=${text.take(120)}"
                    )
                }
            }
        }

        val result = withTimeoutOrNull(AJAX_PROBE_BUDGET_MS) { winner.await() } ?: emptyList()
        jobs.forEach { it.cancel() }
        if (result.isEmpty()) Log.w(TAG, "所有 AJAX 候选路径与参数组合均未解析出内容")
        result
    }

    /** 取消仍在进行的教室页面请求（页面销毁时调用）。 */
    fun cancelInFlightRoomRequests() {
        synchronized(inFlightRoomPages) {
            inFlightRoomPages.values.forEach { it.cancel() }
            inFlightRoomPages.clear()
        }
    }

    /**
     * 宽松解析 AJAX 返回的选项数组。
     *
     * 各版本字段名不一（`MC` / `jzwh` / `name` …），把键名写死总会漏，
     * 因此这里分三层取名：
     * 1. 命中已知名称键；
     * 2. 兜底取"含中文、不含数字、且不等于编码"的字段值——教学楼的名称一定是中文，
     *    这一层能兜住没见过的键名，但会记录命中的 key，字段错位时有迹可循；
     * 3. 都没有就用可读的编码兜底；编码是哈希等不可读形式时直接丢弃该条，不再猜。
     */
    private fun parseAjaxOptions(json: String): List<RoomOption> {
        val trimmed = json.trimStart()
        val array = try {
            when {
                trimmed.startsWith("[") -> org.json.JSONArray(json)
                trimmed.startsWith("{") -> {
                    val obj = org.json.JSONObject(json)
                    val wrapper = listOf("data", "list", "rows", "result", "items", "options")
                        .firstNotNullOfOrNull { key ->
                            obj.optJSONArray(key) ?: runCatching { obj.getJSONObject(key).optJSONArray("list") }.getOrNull()
                        }
                    wrapper
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析 AJAX 返回失败", e)
            null
        } ?: return emptyList()

        val codeKeys = setOf(
            "dm", "id", "value", "code", "jzid", "jzwhid", "classroomid", "zcdm", "zcbm", "roomid"
        )
        val nameKeys = setOf(
            "mc", "name", "text", "label", "title", "jzmc", "jzwh", "jzwhmc", "mcname",
            "roomname", "classname", "cname", "jzname", "xxmc", "dmmc", "dmmcid", "dmname"
        )

        val result = mutableListOf<RoomOption>()
        val seen = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val pairs = obj.keys().asSequence()
                .mapNotNull { key ->
                    obj.optString(key).trim().takeIf { it.isNotEmpty() }?.let { key to it }
                }
                .toList()
            if (pairs.isEmpty()) continue

            val code = pairs.firstNotNullOfOrNull { (key, value) ->
                value.takeIf { codeKeys.contains(key.lowercase()) }
            } ?: pairs.firstNotNullOfOrNull { (_, value) ->
                value.takeIf { !hasChinese(value) }
            } ?: continue

            val name = pairs.firstNotNullOfOrNull { (key, value) ->
                value.takeIf { nameKeys.contains(key.lowercase()) && !isOpaqueRoomName(value) }
            } ?: pairs.firstNotNullOfOrNull { (key, value) ->
                // 兜底猜测：只接受"含中文、不含数字、不等于编码"的值，并记录命中的 key
                value.takeIf {
                    hasChinese(it) && it.none { ch -> ch.isDigit() } &&
                        it != code && !isOpaqueRoomName(it)
                }?.also { guessed ->
                    CoreLog.d("AJAX选项名称兜底：code=$code key=$key name=$guessed")
                }
            } ?: code.takeIf { !isOpaqueRoomName(it) } ?: continue

            if (!seen.add(code)) continue
            result.add(RoomOption(code = code, name = name))
        }
        return result
    }

    /** 是否包含中文字符。 */
    private fun hasChinese(text: String): Boolean =
        text.any { it.code in 0x4E00..0x9FFF }

    /** 供 WebView 登录后同步 Cookie 使用。 */
    fun syncSessionFromCookie(jsessionId: String, serverId: String) {
        cookieStore.restore(jsessionId, serverId)
    }
}
