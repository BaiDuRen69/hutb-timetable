package com.hutb.timetable.data.repo

import com.hutb.timetable.data.local.AppPrefs
import com.hutb.timetable.data.local.CachedSchedule
import com.hutb.timetable.data.local.CredentialStore
import com.hutb.timetable.data.local.RoomOptionsCache
import com.hutb.timetable.data.local.ScheduleCache
import com.hutb.timetable.data.model.AuthSession
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.accountCacheKey
import com.hutb.timetable.core.HutbError
import com.hutb.timetable.core.RoomOption
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleQuery
import com.hutb.timetable.core.Semester
import com.hutb.timetable.core.WeekSchedule
import com.hutb.timetable.data.remote.JsxsdApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 课表仓库：网络 + 缓存的统一入口。
 *
 * 策略：
 * - 在线：请求教务系统，成功后写入本地缓存；
 * - 离线 / 网络异常：回退到本地缓存，并标记 [WeekSchedule.isFromCache]；
 * - 会话失效：抛出 [HutbError.NotLoggedIn]，由 UI 引导重新登录。
 */
class TimetableRepository(
    private val api: JsxsdApi,
    private val cache: ScheduleCache,
    private val credentials: CredentialStore,
    private val prefs: AppPrefs,
    private val optionsCache: RoomOptionsCache? = null
) {

    /**
     * 查询课表。
     *
     * @param allowCache 是否允许回退到本地缓存（离线查看）
     * @param fresh 跳过内存级页面缓存，强制从服务端重新拉取（刷新按钮用）
     */
    suspend fun loadSchedule(
        query: ScheduleQuery,
        allowCache: Boolean = true,
        fresh: Boolean = false
    ): Result<WeekSchedule> = withContext(Dispatchers.IO) {
        try {
            val schedule = if (query.isRoomQuery) {
                api.fetchRoomSchedule(
                    semesterId = query.semesterId,
                    week = query.week,
                    campusId = query.campusId,
                    buildingId = query.buildingId,
                    classroomId = query.roomCode,
                    building = query.building,
                    ignoreMemoryCache = fresh
                )
            } else {
                api.fetchSchedule(query)
            }
            putCache(schedule, query.cacheScope)
            Result.success(schedule)
        } catch (e: HutbError) {
            if (e is HutbError.NotLoggedIn) {
                // 会话失效：清掉本地会话凭据，但保留课表缓存供离线查看
                credentials.jsessionId = ""
                Result.failure(e)
            } else if (allowCache) {
                readCache(query)?.let { Result.success(it) } ?: Result.failure(e)
            } else {
                Result.failure(e)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val wrapped = HutbError.Unknown(e.message ?: "查询失败", e)
            if (allowCache) {
                readCache(query)?.let { Result.success(it) } ?: Result.failure(wrapped)
            } else {
                Result.failure(wrapped)
            }
        }
    }

    /**
     * 读取缓存。**只接受与请求周次一致的缓存**：
     * 别的周次的数据不能当作当前周渲染（断网时尤其致命）。
     *
     * 别的周次有缓存时由 [readLatestCache] 单独返回，供 UI 提示"可跳转查看"。
     */
    suspend fun readCache(query: ScheduleQuery): WeekSchedule? = withContext(Dispatchers.IO) {
        val scope = scoped(query.cacheScope) ?: return@withContext null
        cache.getSchedule(query.semesterId, query.week, scope)
            ?.takeIf { it.week == query.week }
            ?.toSchedule()
    }

    /**
     * 读取该分区最近一次缓存（可能不是请求周次）。
     *
     * 仅用于"第 N 周有缓存，点此查看"的提示，调用方不得把它当当前周渲染。
     */
    suspend fun readLatestCache(query: ScheduleQuery): WeekSchedule? = withContext(Dispatchers.IO) {
        val scope = scoped(query.cacheScope) ?: return@withContext null
        cache.getLatestOfSemester(query.semesterId, scope)?.toSchedule()
    }

    /** 清除本地课表/教室选项/学期缓存（保留登录凭据）。 */
    suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        cache.clearAll()
        optionsCache?.clear()
    }

    /** 取消仍在进行的教室页面请求（页面销毁时调用）。 */
    fun cancelRoomQueries() {
        api.cancelInFlightRoomRequests()
    }

    /**
     * 读取精确 (学期, 周次, 分区) 的缓存，用于"先上屏再刷新"。
     * 周次不匹配的缓存不上屏，避免显示错误周次的数据。
     */
    suspend fun readExactCache(query: ScheduleQuery): WeekSchedule? = withContext(Dispatchers.IO) {
        val scope = scoped(query.cacheScope) ?: return@withContext null
        cache.getSchedule(query.semesterId, query.week, scope)
            ?.takeIf { it.week == query.week }
            ?.toSchedule()
    }

    /** 加载校区列表：命中持久缓存直接返回，否则请求服务端并写缓存。 */
    suspend fun loadCampuses(semesterId: String?): Result<List<RoomOption>> =
        withContext(Dispatchers.IO) {
            optionsCache?.getCampuses()?.let { return@withContext Result.success(it) }
            try {
                val value = api.fetchCampuses(semesterId)
                optionsCache?.putCampuses(value)
                Result.success(value)
            } catch (e: HutbError.NotLoggedIn) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(roomOptionFailure(e))
            }
        }

    /** 加载指定校区的教学楼列表：命中持久缓存直接返回，否则请求服务端并写缓存。 */
    suspend fun loadBuildings(campusId: String, semesterId: String?): Result<List<RoomOption>> =
        withContext(Dispatchers.IO) {
            optionsCache?.getBuildings(semesterId, campusId)?.let { return@withContext Result.success(it) }
            try {
                val value = api.fetchBuildings(campusId, semesterId)
                if (value.isNotEmpty()) optionsCache?.putBuildings(semesterId, campusId, value)
                Result.success(value)
            } catch (e: HutbError.NotLoggedIn) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(roomOptionFailure(e))
            }
        }

    /** 加载指定教学楼下的教室列表。 */
    suspend fun loadClassrooms(
        campusId: String,
        buildingId: String,
        semesterId: String?,
        week: Int = 1,
        buildingName: String? = null
    ): Result<List<RoomOption>> = withContext(Dispatchers.IO) {
        try {
            Result.success(api.fetchClassrooms(campusId, buildingId, semesterId, week, buildingName))
        } catch (e: HutbError.NotLoggedIn) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(roomOptionFailure(e))
        }
    }

    /** 统一把网络/解析异常转成可展示的领域错误，避免 UI 收到裸异常。 */
    private fun roomOptionFailure(e: Exception): HutbError =
        e as? HutbError ?: HutbError.Unknown(e.message ?: "加载教室选项失败", e)

    /**
     * 账号缓存分区键；学号未知（首次登录尚未解析成功）时返回 null。
     *
     * 未知身份时**读写缓存一律跳过**：所有账号共用一个 `anon` 分区，
     * 会让换账号后读到上一个人的课表。
     */
    private fun accountKey(): String? =
        credentials.studentId.takeIf { it.isNotBlank() }?.let(::accountCacheKey)

    private fun scoped(scope: String): String? = accountKey()?.let { "$scope|u=$it" }

    private fun putCache(schedule: WeekSchedule, scope: String) {
        val scopedScope = scoped(scope) ?: return
        cache.putSchedule(
            semesterId = schedule.semesterId,
            week = schedule.week,
            scope = scopedScope,
            semesterName = schedule.semesterName,
            payload = encodeCourses(schedule.courses)
        )
    }

    /** 读取缓存的学期列表（启动时先上屏用，可能为空）。 */
    suspend fun readSemestersCache(): List<Semester> = withContext(Dispatchers.IO) {
        cache.getSemesters().map { Semester(it.first, it.second) }
    }

    /**
     * 学期列表：优先网络，失败回退缓存。
     */
    suspend fun loadSemesters(): Result<List<Semester>> = withContext(Dispatchers.IO) {
        try {
            val semesters = api.fetchSemesters()
            cache.putSemesters(semesters.map { it.id to it.name })
            Result.success(semesters)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HutbError.NotLoggedIn) {
            // 会话失效必须让 UI 引导重新登录，不能被旧缓存掩盖
            Result.failure(e)
        } catch (e: Exception) {
            val cached = cache.getSemesters()
            if (cached.isNotEmpty()) {
                Result.success(cached.map { Semester(it.first, it.second) })
            } else {
                Result.failure(e)
            }
        }
    }

    // ---------------- 会话 ----------------

    /** 当前是否有本地保存的会话。 */
    fun hasStoredSession(): Boolean = credentials.hasSession

    /** 用已保存的凭据恢复会话。 */
    fun restoreSession(): AuthSession {
        val session = AuthSession(
            jsessionId = credentials.jsessionId,
            serverId = credentials.serverId,
            displayName = credentials.displayName
        )
        api.syncSessionFromCookie(session.jsessionId, session.serverId)
        return session
    }

    /** 保存登录成功后拿到的会话。 */
    fun persistSession(session: AuthSession) {
        // 新会话可能属于另一个账号：旧学号立即作废，等 resolveIdentity 回填；
        // 解析不到时缓存读写会被跳过，绝不把新账号的数据记到旧账号分区。
        credentials.studentId = ""
        credentials.jsessionId = session.jsessionId
        credentials.serverId = session.serverId
        credentials.displayName = session.displayName
        api.syncSessionFromCookie(session.jsessionId, session.serverId)
    }

    /** 退出登录：清空全部凭据（含学号），课表缓存按账号隔离保留。 */
    fun logout() {
        credentials.clearCredentials()
        api.syncSessionFromCookie("", "")
    }

    /**
     * 登录后解析学号/姓名，供账号级缓存与 JSON API 使用。
     *
     * 会话失效与协程取消必须向上传播：前者要触发重新登录，
     * 后者不能被当成"解析失败"继续走缓存路径。
     */
    suspend fun resolveIdentity(): Boolean = withContext(Dispatchers.IO) {
        val identity = try {
            api.fetchStudentIdentity()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HutbError.NotLoggedIn) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return@withContext false
        updateIdentity(identity.first, identity.second)
        true
    }

    /** 记录学号与显示名（登录后由用户名解析得到）。 */
    fun updateIdentity(studentId: String, displayName: String) {
        if (studentId.isNotBlank()) credentials.studentId = studentId
        if (displayName.isNotBlank()) credentials.displayName = displayName
    }

    /** 记录最近一次浏览的学期与周次。 */
    fun rememberPosition(semesterId: String, week: Int) {
        val key = accountKey() ?: return
        prefs.setLastSemesterIdFor(key, semesterId)
        prefs.setLastWeekFor(key, week)
    }

    val lastSemesterId: String
        get() = accountKey()?.let { prefs.lastSemesterIdFor(it) }.orEmpty()
    val lastWeek: Int
        get() = accountKey()?.let { prefs.lastWeekFor(it) } ?: 0
    val studentId: String get() = credentials.studentId

    // ---------------- 教室查询的选择记忆 ----------------

    /** 记录教室查询的校区 / 教学楼范围。 */
    fun rememberRoomScope(campusId: String?, buildingId: String?, buildingName: String?) {
        prefs.lastCampusId = campusId.orEmpty()
        prefs.lastBuildingId = buildingId.orEmpty()
        prefs.lastBuildingName = buildingName.orEmpty()
    }

    /** 记录教室查询选中的教室，空表示不指定教室。 */
    fun rememberRoomCode(roomCode: String?) {
        prefs.lastRoomCode = roomCode.orEmpty()
    }

    val lastCampusId: String get() = prefs.lastCampusId
    val lastBuildingId: String get() = prefs.lastBuildingId
    val lastBuildingName: String get() = prefs.lastBuildingName
    val lastRoomCode: String get() = prefs.lastRoomCode

    /** 教室课表是否精简教室名显示。 */
    var shortRoomNames: Boolean
        get() = prefs.shortRoomNames
        set(value) {
            prefs.shortRoomNames = value
        }

    /** 教室行的排序方式；未设置或存储值失效时退回楼栋优先。 */
    var roomSortMode: RoomSortMode
        get() = runCatching { RoomSortMode.valueOf(prefs.roomSortMode) }
            .getOrDefault(RoomSortMode.BY_BUILDING)
        set(value) {
            prefs.roomSortMode = value.name
        }

    // ---------------- 序列化 ----------------

    /** Course 列表 → JSON 字符串。 */
    private fun encodeCourses(courses: List<Course>): String {
        val array = JSONArray()
        courses.forEach { course ->
            array.put(
                JSONObject().apply {
                    put("n", course.courseName)
                    put("t", course.teacher)
                    put("c", course.className)
                    put("r", course.classroom)
                    put("w", course.weekday)
                    put("s", course.startPeriod)
                    put("e", course.endPeriod)
                    put("wt", course.weekText)
                    put("ws", JSONArray(course.weeks))
                    put("ty", course.courseType)
                }
            )
        }
        return array.toString()
    }

    /** JSON 字符串 → Course 列表。 */
    private fun decodeCourses(payload: String): List<Course> {
        val array = JSONArray(payload)
        val result = mutableListOf<Course>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val weeksArray = obj.optJSONArray("ws")
            val weeks = mutableListOf<Int>()
            if (weeksArray != null) {
                for (j in 0 until weeksArray.length()) {
                    weeksArray.optInt(j).takeIf { it > 0 }?.let { weeks.add(it) }
                }
            }
            result.add(
                Course(
                    courseName = obj.optString("n"),
                    teacher = obj.optString("t"),
                    className = obj.optString("c"),
                    classroom = obj.optString("r"),
                    weekday = obj.optInt("w", 1),
                    startPeriod = obj.optInt("s", 1),
                    endPeriod = obj.optInt("e", 1),
                    weekText = obj.optString("wt"),
                    weeks = weeks,
                    courseType = obj.optString("ty")
                )
            )
        }
        return result
    }

    private fun CachedSchedule.toSchedule(): WeekSchedule = WeekSchedule(
        semesterId = semesterId,
        semesterName = semesterName,
        week = week,
        courses = runCatching { decodeCourses(payload) }.getOrDefault(emptyList()),
        isFromCache = true
    )
}
