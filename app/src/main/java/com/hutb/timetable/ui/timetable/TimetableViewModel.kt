package com.hutb.timetable.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hutb.timetable.data.model.AuthSession
import com.hutb.timetable.core.HutbError
import com.hutb.timetable.core.RoomOption
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.ScheduleQuery
import com.hutb.timetable.core.Semester
import com.hutb.timetable.core.WeekSchedule
import com.hutb.timetable.core.resolveBuildingName
import com.hutb.timetable.core.scopeToBuilding
import com.hutb.timetable.data.local.AppSettings
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.data.repo.TimetableRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 查询模式。 */
enum class QueryMode {
    /** 个人课表 */
    PERSONAL,

    /** 教室（教学楼）课表 */
    ROOM
}

/**
 * 课表主体状态：学期 / 周次 / 当前数据 / 筛选。
 */
data class ScheduleState(
    val loading: Boolean = false,
    val data: WeekSchedule? = null,
    val semesters: List<Semester> = emptyList(),
    val currentSemesterId: String = "",
    val currentWeek: Int = 1,
    val fromCache: Boolean = false,
    val queryMode: QueryMode = QueryMode.PERSONAL,
    val filter: ScheduleFilter = ScheduleFilter(),
    /**
     * 网络失败且本周没有缓存时，该分区最近一次缓存所在周次。
     * 仅用于提示"第 N 周有缓存，点此查看"，绝不把别的周次当当前周渲染。
     */
    val staleCacheWeek: Int? = null
)

/**
 * 教室级联选择状态：校区 → 教学楼 → 教室。
 */
data class RoomPickerState(
    val campuses: List<RoomOption> = emptyList(),
    val selectedCampusId: String? = null,
    val buildingOptions: List<RoomOption> = emptyList(),
    val selectedBuildingId: String? = null,
    val selectedBuilding: String? = null,
    val classroomOptions: List<RoomOption> = emptyList(),
    val selectedRoomCode: String? = null,
    /** 选择器草稿：浏览选项只改这里，点"确定"才提交 */
    val draftBuildingOptions: List<RoomOption> = emptyList(),
    val draftClassroomOptions: List<RoomOption> = emptyList(),
    val draftBuildingsLoading: Boolean = false,
    val draftClassroomsLoading: Boolean = false,
    /** 最近一次教室选项加载失败的原因（可重试） */
    val errorMessage: String? = null
)

/**
 * 会话与错误状态。
 */
data class SessionState(
    val loggedIn: Boolean = false,
    val expired: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 课表页状态。
 */
data class TimetableUiState(
    val schedule: ScheduleState = ScheduleState(),
    val room: RoomPickerState = RoomPickerState(),
    val session: SessionState = SessionState(),
    /** 教室课表是否精简教室名（去掉教学楼前缀只留房间号） */
    val shortRoomNames: Boolean = true,
    /** 教室行的排序方式 */
    val roomSortMode: RoomSortMode = RoomSortMode.BY_BUILDING,
    /** 当前生效的作息时间表（决定课表网格上各节次显示的时间） */
    val timetable: ClassTimetable? = null,
    /** 开学日期 "yyyy-MM-dd"，用于推算当前周次；空表示未设置 */
    val semesterStartDate: String = ""
)

/**
 * 课表页 ViewModel。
 */
class TimetableViewModel(
    private val repository: TimetableRepository,
    private val settings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        TimetableUiState(
            shortRoomNames = repository.shortRoomNames,
            roomSortMode = repository.roomSortMode,
            timetable = settings.activeTimetable
        )
    )
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    /** 当前课表查询任务；新查询启动时取消旧查询，避免慢响应覆盖新数据。 */
    private var scheduleJob: Job? = null

    /** 教室选择器的预览请求；快速切换校区时只保留最后一次。 */
    private var previewJob: Job? = null

    /**
     * 屏幕上这份课表对应的查询快照（仅成功加载后设置）。
     *
     * 所有会把 [ScheduleState.data] 清空的操作都必须同步清掉它，
     * 否则"条件相同"的误判会让界面停在旧数据上。
     */
    private var loadedQuery: ScheduleQuery? = null

    init {
        // 尝试用本地保存的会话直接进入
        if (repository.hasStoredSession()) {
            // 只要登录过一次，后续启动不再强制登录（会话失效只提示）
            settings.firstLoginCompleted = true
            repository.restoreSession()
            _uiState.update { it.copy(session = it.session.copy(loggedIn = true)) }
            viewModelScope.launch {
                // 身份未知（升级/首次登录未解析）时先解析学号：
                // 课表缓存按账号分区，未知身份不能读写任何账号的缓存
                ensureIdentity()
                // 学期列表有持久缓存时先直接进课表，学期列表随后在后台刷新
                val cached = repository.readSemestersCache()
                if (currentSemesterOrDefault(cached) != null) {
                    applySemesters(cached)
                    loadSchedule()
                }
                loadSemesters()
            }
        } else {
            // 没有活动会话：只读上次登录账号的离线课表，不发起任何网络请求
            viewModelScope.launch { loadOfflinePersonal() }
        }
    }

    /**
     * 未登录时加载上次登录账号的离线个人课表。
     *
     * 没有缓存就保持空态（由界面提示登录）；这里绝不发网络请求。
     */
    private suspend fun loadOfflinePersonal() {
        if (repository.hasStoredSession()) return
        val semesters = repository.readSemestersCache()
        val current = currentSemesterOrDefault(semesters) ?: return
        applySemesters(semesters)
        val state = _uiState.value
        val query = ScheduleQuery(
            semesterId = state.schedule.currentSemesterId,
            week = state.schedule.currentWeek
        )
        val exact = repository.readExactCache(query)
        // 当前周没缓存时允许展示同分区最近一周，标题会显示实际周次，不能谎报为本周
        val latest = if (exact == null) repository.readLatestCache(query) else null
        val data = exact ?: latest
        // 加载期间可能已经登录成功，此时不要用离线数据覆盖在线结果
        if (repository.hasStoredSession()) return
        _uiState.update {
            it.copy(
                schedule = it.schedule.copy(
                    currentWeek = data?.week ?: it.schedule.currentWeek,
                    data = data,
                    fromCache = data != null,
                    loading = false,
                    staleCacheWeek = null
                ),
                session = it.session.copy(loggedIn = false, expired = false, errorMessage = null)
            )
        }
    }

    /**
     * 确保当前会话已解析出学号。
     *
     * @return 身份是否可用；false 时仍可联网查询，但缓存读写会被仓库层跳过
     */
    private suspend fun ensureIdentity(): Boolean {
        if (repository.studentId.isNotBlank()) return true
        return try {
            repository.resolveIdentity()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: HutbError.NotLoggedIn) {
            _uiState.update {
                it.copy(session = it.session.copy(loggedIn = false, expired = true))
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun currentSemesterOrDefault(semesters: List<Semester>): Semester? {
        val remembered = repository.lastSemesterId
        return semesters.firstOrNull { it.id == remembered } ?: semesters.firstOrNull()
    }

    /** 应用学期列表并设置默认周次。 */
    private fun applySemesters(semesters: List<Semester>) {
        val current = currentSemesterOrDefault(semesters) ?: return
        // 旧版全局开学日期归到当前学期，之后各学期互不影响
        settings.migrateLegacyStartDate(current.id)
        val startDate = settings.semesterStartDateFor(current.id)
        val remembered = repository.lastSemesterId
        val week = if (remembered == current.id) {
            settings.currentWeekNumber(current.id)
                ?: repository.lastWeek.takeIf { it in 1..30 }
                ?: 1
        } else {
            settings.currentWeekNumber(current.id) ?: 1
        }
        _uiState.update {
            it.copy(
                semesterStartDate = startDate,
                schedule = it.schedule.copy(
                    semesters = semesters,
                    currentSemesterId = current.id,
                    currentWeek = week
                )
            )
        }
    }

    /** 设置页修改后回调，重新读取当前学期的开学日期与作息时间表。 */
    fun reloadSettings() {
        val semesterId = _uiState.value.schedule.currentSemesterId
        val previousStartDate = _uiState.value.semesterStartDate
        val startDate = settings.semesterStartDateFor(semesterId)
        _uiState.update {
            it.copy(
                timetable = settings.activeTimetable,
                semesterStartDate = startDate
            )
        }
        // 只有"开学日期"变化才重算周次；
        // 改时间表名称/作息时不能把用户正在看的周次弹回当前周
        if (previousStartDate != startDate) {
            val computed = settings.currentWeekNumber(semesterId)
            if (computed != null && computed != _uiState.value.schedule.currentWeek) {
                switchWeek(computed)
            }
        }
    }

    /** 更新当前学期的开学日期并按新日期重新推算周次。 */
    fun updateSemesterStartDate(date: String) {
        val semesterId = _uiState.value.schedule.currentSemesterId
        if (semesterId.isBlank()) return
        settings.setSemesterStartDateFor(semesterId, date)
        val week = settings.currentWeekNumber(semesterId) ?: 1
        loadedQuery = null
        _uiState.update {
            it.copy(
                semesterStartDate = date,
                schedule = it.schedule.copy(currentWeek = week, data = null),
                session = it.session.copy(errorMessage = null)
            )
        }
        loadSchedule()
    }

    /** 登录成功后调用。 */
    fun onLoginSuccess(session: AuthSession) {
        // 只要成功登录过一次，后续启动不再强制登录
        settings.firstLoginCompleted = true
        repository.persistSession(session)
        _uiState.update { it.copy(session = it.session.copy(loggedIn = true, expired = false)) }
        viewModelScope.launch {
            // 首次登录 App 不知道学号：先向后端解析，账号级缓存才能落到正确分区；
            // 解析完成前不读写缓存，避免记到上一个账号名下
            ensureIdentity()
            val cached = repository.readSemestersCache()
            if (currentSemesterOrDefault(cached) != null) {
                applySemesters(cached)
                loadSchedule()
            }
            loadSemesters()
        }
    }

    /**
     * 加载学期列表，并在有默认学期时加载课表。
     *
     * 若当前学期没变化且下拉选项已存在，只静默刷新选项，不重复拉课表
     * （init 的缓存路径已经触发过查询）。
     */
    fun loadSemesters() {
        viewModelScope.launch {
            repository.loadSemesters()
                .onSuccess { semesters ->
                    val previous = _uiState.value.schedule.currentSemesterId
                    val current = currentSemesterOrDefault(semesters)
                    if (current != null && current.id == previous && _uiState.value.schedule.semesters.isNotEmpty()) {
                        _uiState.update { it.copy(schedule = it.schedule.copy(semesters = semesters)) }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            schedule = it.schedule.copy(
                                semesters = semesters,
                                currentSemesterId = current?.id.orEmpty()
                            )
                        )
                    }
                    if (current != null) {
                        applySemesters(semesters)
                        loadSchedule()
                    }
                }
                .onFailure { e ->
                    val expired = e is HutbError.NotLoggedIn
                    if (_uiState.value.schedule.semesters.isEmpty() || expired) {
                        _uiState.update {
                            it.copy(
                                session = it.session.copy(
                                    expired = it.session.expired || expired,
                                    loggedIn = if (expired) false else it.session.loggedIn,
                                    errorMessage = e.message ?: "学期列表加载失败"
                                )
                            )
                        }
                    }
                }
        }
    }

    /**
     * 加载当前学期/周次的课表。
     *
     * 策略（stale-while-revalidate）：先把同周次的本地缓存直接上屏，
     * 再请求服务端刷新替换——翻周次 / 重进页面时不再白屏转圈。
     *
     * @param fresh 跳过内存级页面缓存，强制从服务端重新拉取（刷新按钮用）
     */
    fun loadSchedule(fresh: Boolean = false) {
        val state = _uiState.value
        val scheduleState = state.schedule
        if (scheduleState.currentSemesterId.isBlank()) return

        val isRoomMode = scheduleState.queryMode == QueryMode.ROOM
        // 教室课表必须登录后查询；个人课表在未登录时仍可看离线缓存
        if (isRoomMode && !state.session.loggedIn) {
            _uiState.update {
                it.copy(
                    schedule = it.schedule.copy(loading = false),
                    session = it.session.copy(errorMessage = "查看教室课表需要登录")
                )
            }
            return
        }
        // 教室模式必须选中教学楼：只选校区会触发整校区大页面查询（实测 1MB+/12s+）
        if (isRoomMode && state.room.selectedBuildingId.isNullOrBlank()) {
            loadedQuery = null
            _uiState.update {
                it.copy(schedule = it.schedule.copy(loading = false, data = null, staleCacheWeek = null))
            }
            return
        }

        val query = ScheduleQuery(
            semesterId = scheduleState.currentSemesterId,
            week = scheduleState.currentWeek,
            // 个人课表模式不携带任何教室筛选条件
            roomCode = if (isRoomMode) state.room.selectedRoomCode else null,
            building = if (isRoomMode) state.room.selectedBuilding else null,
            campusId = if (isRoomMode) state.room.selectedCampusId else null,
            buildingId = if (isRoomMode) state.room.selectedBuildingId else null,
            roomMode = isRoomMode
        )

        scheduleJob?.cancel()
        scheduleJob = viewModelScope.launch {
            // 没有活动会话或会话已失效：只读缓存，不再白打一次网络请求
            if (!repository.hasStoredSession() || _uiState.value.session.expired) {
                loadOfflineWeek(query)
                return@launch
            }
            // 缓存先上屏（同周次才用，避免显示错周数据）。
            // 注意：空课表也是合法缓存（假期/停课周），不能用 isNotEmpty 把它过滤掉，
            // 否则用户切到空周必须干等一次完整的网络请求。
            if (!fresh) {
                repository.readExactCache(query)?.let { cached ->
                    _uiState.update {
                        it.copy(
                            schedule = it.schedule.copy(
                                data = cached,
                                fromCache = true,
                                loading = true,
                                staleCacheWeek = null
                            ),
                            session = it.session.copy(errorMessage = null, expired = false)
                        )
                    }
                }
            }
            _uiState.update {
                it.copy(
                    schedule = it.schedule.copy(loading = true, staleCacheWeek = null),
                    session = it.session.copy(errorMessage = null)
                )
            }
            val result = repository.loadSchedule(query, allowCache = true, fresh = fresh)
            result.onSuccess { schedule ->
                loadedQuery = query
                repository.rememberPosition(schedule.semesterId, schedule.week)
                _uiState.update {
                    it.copy(
                        schedule = it.schedule.copy(
                            loading = false,
                            data = schedule,
                            fromCache = schedule.isFromCache,
                            staleCacheWeek = null
                        ),
                        session = it.session.copy(errorMessage = null, expired = false)
                    )
                }
            }.onFailure { e ->
                // 查询被更新的查询取代时不要再写状态
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                val expired = e is HutbError.NotLoggedIn
                _uiState.update {
                    it.copy(
                        schedule = it.schedule.copy(loading = false, staleCacheWeek = null),
                        session = it.session.copy(
                            expired = expired,
                            loggedIn = !expired,
                            errorMessage = e.message ?: "课表加载失败"
                        )
                    )
                }
                if (!expired) offerStaleCache(query)
            }
        }
    }

    /**
     * 未登录/会话失效时切周次：只读该周缓存，不发起网络请求。
     */
    private suspend fun loadOfflineWeek(query: ScheduleQuery) {
        val cached = repository.readExactCache(query)
        // 读取期间可能已经重新登录成功，不要用离线结果覆盖在线状态
        if (repository.hasStoredSession() && !_uiState.value.session.expired) return
        _uiState.update {
            it.copy(
                schedule = it.schedule.copy(
                    loading = false,
                    data = cached,
                    fromCache = cached != null,
                    staleCacheWeek = null
                ),
                session = it.session.copy(
                    loggedIn = false,
                    errorMessage = if (cached == null && !it.session.expired) {
                        "该周没有离线缓存，登录后可查询"
                    } else {
                        null
                    }
                )
            )
        }
    }

    /**
     * 本周没有可用缓存且请求失败时，找出同一分区最近一次缓存，
     * 让 UI 提示"第 N 周有缓存，点此切换"——而不是把别的周次当当前周显示。
     */
    private suspend fun offerStaleCache(query: ScheduleQuery) {
        val current = _uiState.value
        // 屏幕上已有数据（例如刷新失败）时保持原样，只弹错误提示
        if (current.schedule.data != null) return
        val latest = repository.readLatestCache(query) ?: return
        if (latest.week == query.week) return
        _uiState.update {
            it.copy(schedule = it.schedule.copy(staleCacheWeek = latest.week))
        }
    }

    // ---------------- 教室查询 ----------------

    /**
     * 切换查询模式。
     *
     * 切到教室模式时按需加载场地选项；切回个人模式则恢复个人课表。
     */
    fun switchQueryMode(mode: QueryMode) {
        val state = _uiState.value
        if (state.schedule.queryMode == mode) return
        // 教室课表要求已登录；个人课表允许离线缓存
        if (mode == QueryMode.ROOM && !state.session.loggedIn) {
            _uiState.update {
                it.copy(session = it.session.copy(errorMessage = "查看教室课表需要登录"))
            }
            return
        }

        loadedQuery = null
        _uiState.update {
            it.copy(
                schedule = it.schedule.copy(
                    queryMode = mode,
                    // 切换模式时清空旧数据，避免显示上一种模式的课表
                    data = null,
                    staleCacheWeek = null
                ),
                session = it.session.copy(errorMessage = null)
            )
        }

        if (mode == QueryMode.ROOM) {
            loadRoomOptions()
        }
        loadSchedule()
    }

    /**
     * 加载场地选项：先取校区，再按校区取教学楼。
     *
     * 进入教室模式时调用，会顺带恢复上次记住的校区 / 教学楼 / 教室选择。
     */
    fun loadRoomOptions() {
        viewModelScope.launch {
            val semesterId = _uiState.value.schedule.currentSemesterId.takeIf { it.isNotBlank() }
            _uiState.update { it.copy(room = it.room.copy(errorMessage = null)) }

            repository.loadCampuses(semesterId)
                .onSuccess { campuses ->
                    val current = _uiState.value
                    // 恢复上次记住的校区；记忆失效（下拉里没有）时退回第一个
                    val campusId = current.room.selectedCampusId
                        ?: campuses.firstOrNull { it.code == repository.lastCampusId }?.code
                        ?: campuses.firstOrNull()?.code
                    _uiState.update {
                        it.copy(
                            room = it.room.copy(
                                campuses = campuses,
                                selectedCampusId = campusId,
                                errorMessage = null
                            )
                        )
                    }
                    if (campusId != null) {
                        loadBuildings(campusId, restoreBuilding = true)
                    }
                }
                .onFailure { handleRoomFailure(it) }
        }
    }

    /**
     * 加载指定校区的教学楼。
     *
     * @param restoreBuilding 是否在加载完成后恢复上次记住的教学楼（仅进入教室模式时）
     */
    fun loadBuildings(campusId: String, restoreBuilding: Boolean = false) {
        viewModelScope.launch {
            val semesterId = _uiState.value.schedule.currentSemesterId.takeIf { it.isNotBlank() }
            _uiState.update { it.copy(room = it.room.copy(errorMessage = null)) }
            repository.loadBuildings(campusId, semesterId)
                .onSuccess { options ->
                    _uiState.update {
                        it.copy(
                            room = it.room.copy(buildingOptions = options, errorMessage = null)
                        )
                    }
                    if (restoreBuilding) restoreRememberedBuilding(options)
                }
                .onFailure { handleRoomFailure(it) }
        }
    }

    /** 教室选项加载失败：登录失效走重新登录，其余错误在面板内提供重试。 */
    private fun handleRoomFailure(e: Throwable) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        val expired = e is HutbError.NotLoggedIn
        val message = e.message?.takeIf { it.isNotBlank() } ?: "教室选项加载失败"
        _uiState.update {
            it.copy(
                room = it.room.copy(
                    draftBuildingsLoading = false,
                    draftClassroomsLoading = false,
                    errorMessage = message
                ),
                session = it.session.copy(
                    expired = it.session.expired || expired,
                    loggedIn = if (expired) false else it.session.loggedIn,
                    errorMessage = message
                )
            )
        }
    }

    /**
     * 恢复上次记住的教学楼，进而联动加载教室并恢复教室选择。
     */
    private fun restoreRememberedBuilding(options: List<RoomOption>) {
        if (_uiState.value.room.selectedBuildingId != null) return
        val rememberedId = repository.lastBuildingId
        if (rememberedId.isBlank()) return
        val match = options.firstOrNull { it.code == rememberedId } ?: return
        selectBuildingById(match.code, match.name, restoreRoom = true)
    }

    /**
     * 打开教室选择器：以当前已加载的选项作为草稿起点。
     *
     * 之后浏览校区/教学楼只改草稿，点"确定"才提交；取消零请求、零状态变更。
     */
    fun beginRoomDraft() {
        val room = _uiState.value.room
        _uiState.update {
            it.copy(
                room = it.room.copy(
                    draftBuildingOptions = room.buildingOptions,
                    draftClassroomOptions = room.classroomOptions,
                    draftBuildingsLoading = false,
                    draftClassroomsLoading = false
                )
            )
        }
    }

    /** 草稿态：预览指定校区的教学楼（不改变当前查询条件）。 */
    fun previewCampus(campusId: String) {
        previewJob?.cancel()
        val semesterId = _uiState.value.schedule.currentSemesterId.takeIf { it.isNotBlank() }
        previewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    room = it.room.copy(
                        draftBuildingsLoading = true,
                        draftBuildingOptions = emptyList(),
                        draftClassroomOptions = emptyList(),
                        errorMessage = null
                    )
                )
            }
            repository.loadBuildings(campusId, semesterId)
                .onSuccess { options ->
                    _uiState.update {
                        it.copy(
                            room = it.room.copy(
                                draftBuildingOptions = options,
                                draftBuildingsLoading = false,
                                errorMessage = null
                            )
                        )
                    }
                }
                .onFailure { handleRoomFailure(it) }
        }
    }

    /** 草稿态：预览指定教学楼的教室（不改变当前查询条件）。 */
    fun previewBuilding(campusId: String, buildingId: String, buildingName: String?) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    room = it.room.copy(
                        draftClassroomsLoading = true,
                        draftClassroomOptions = emptyList(),
                        errorMessage = null
                    )
                )
            }
            val semesterId = _uiState.value.schedule.currentSemesterId.takeIf { it.isNotBlank() }
            val week = _uiState.value.schedule.currentWeek
            repository.loadClassrooms(
                campusId = campusId,
                buildingId = buildingId,
                semesterId = semesterId,
                week = week,
                buildingName = buildingName
            ).onSuccess { options ->
                val resolved = resolveBuildingName(buildingName, options)
                val scoped = scopeToBuilding(options, resolved)
                _uiState.update {
                    it.copy(
                        room = it.room.copy(
                            draftClassroomOptions = scoped,
                            draftClassroomsLoading = false,
                            errorMessage = null
                        )
                    )
                }
            }.onFailure { handleRoomFailure(it) }
        }
    }

    private fun selectBuildingById(
        buildingId: String?,
        buildingName: String?,
        restoreRoom: Boolean
    ) {
        val state = _uiState.value
        if (state.room.selectedBuildingId == buildingId) return
        _uiState.update {
            it.copy(
                room = it.room.copy(
                    selectedBuildingId = buildingId,
                    selectedBuilding = buildingName,
                    classroomOptions = emptyList(),
                    // 恢复路径：记住的教室名本身就是查询参数（服务端按名过滤客户端执行），
                    // 不必等教室清单回来就能先查课表
                    selectedRoomCode = if (restoreRoom) {
                        repository.lastRoomCode.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                )
            )
        }
        if (buildingId == null) return

        val campusId = state.room.selectedCampusId ?: return
        viewModelScope.launch {
            if (restoreRoom) {
                repository.rememberRoomCode(_uiState.value.room.selectedRoomCode)
                // 课表与教室清单并发：两者读的是同一个服务端页面，API 层共享在途请求
                loadSchedule()
            }
            val semesterId = _uiState.value.schedule.currentSemesterId.takeIf { it.isNotBlank() }
            val week = _uiState.value.schedule.currentWeek
            repository.loadClassrooms(
                campusId = campusId,
                buildingId = buildingId,
                semesterId = semesterId,
                week = week,
                buildingName = buildingName
            ).onSuccess { options ->
                val current = _uiState.value
                val resolvedName = resolveBuildingName(current.room.selectedBuilding, options)
                // 教学楼名确定后，把服务端混进来的其它教学楼教室剔除
                val scoped = scopeToBuilding(options, resolvedName)
                _uiState.update {
                    it.copy(
                        room = it.room.copy(
                            classroomOptions = scoped,
                            selectedBuilding = resolvedName,
                            errorMessage = null,
                            buildingOptions = if (resolvedName != null && resolvedName != current.room.selectedBuilding) {
                                current.room.buildingOptions.map {
                                    if (it.code == buildingId) it.copy(name = resolvedName) else it
                                }
                            } else {
                                current.room.buildingOptions
                            }
                        )
                    )
                }
                // 记住这次的教学楼选择；教室选择由 restoreRoom / applyRoomSelection / selectRoom 单独记
                repository.rememberRoomScope(current.room.selectedCampusId, buildingId, resolvedName)
                if (restoreRoom) validateRestoredRoom(scoped)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(room = it.room.copy(classroomOptions = emptyList()))
                }
                handleRoomFailure(e)
            }
        }
    }

    /**
     * 校验恢复路径里先行采用的记住教室。
     * 它不属于这栋楼时清掉并按"全部教室"重新查询
     * （此时页面已在内存缓存里，重查只是重新解析，很快）。
     */
    private fun validateRestoredRoom(classrooms: List<RoomOption>) {
        val remembered = _uiState.value.room.selectedRoomCode ?: return
        if (classrooms.any { it.code == remembered }) return
        _uiState.update { it.copy(room = it.room.copy(selectedRoomCode = null)) }
        repository.rememberRoomCode(null)
        loadSchedule()
    }

    /**
     * 选择具体教室。
     */
    fun selectRoom(roomCode: String?) {
        val state = _uiState.value
        if (state.room.selectedRoomCode == roomCode) return
        loadedQuery = null
        _uiState.update {
            it.copy(
                room = it.room.copy(selectedRoomCode = roomCode),
                schedule = it.schedule.copy(data = null, staleCacheWeek = null)
            )
        }
        repository.rememberRoomCode(roomCode)
        loadSchedule()
    }

    /**
     * 一次性应用校区 + 教学楼 + 教室选择（对话框"确定"）。
     *
     * 对话框浏览选项只改草稿，这里才提交；只有最终条件与屏幕上这份课表不同才重新请求。
     */
    fun applyRoomSelection(
        campusId: String?,
        buildingId: String?,
        buildingName: String?,
        roomCode: String?
    ) {
        val state = _uiState.value
        val isRoomMode = state.schedule.queryMode == QueryMode.ROOM

        val target = ScheduleQuery(
            semesterId = state.schedule.currentSemesterId,
            week = state.schedule.currentWeek,
            roomCode = if (isRoomMode) roomCode else null,
            building = if (isRoomMode) buildingName else null,
            campusId = if (isRoomMode) campusId else null,
            buildingId = if (isRoomMode) buildingId else null,
            roomMode = isRoomMode
        )
        // 用"上次成功加载的完整查询快照"判断，且屏幕无数据时一律重查
        val needsReload = state.schedule.data == null ||
            loadedQuery?.sameSnapshot(target) != true
        if (needsReload) loadedQuery = null

        _uiState.update {
            it.copy(
                room = it.room.copy(
                    selectedCampusId = campusId,
                    selectedBuildingId = buildingId,
                    selectedBuilding = buildingName,
                    selectedRoomCode = roomCode,
                    // 提交草稿里的选项，保证标题栏与矩阵立刻使用新教学楼的教室清单
                    buildingOptions = it.room.draftBuildingOptions.ifEmpty { it.room.buildingOptions },
                    classroomOptions = it.room.draftClassroomOptions
                ),
                schedule = it.schedule.copy(
                    data = if (needsReload) null else state.schedule.data,
                    staleCacheWeek = null
                )
            )
        }
        repository.rememberRoomScope(campusId, buildingId, buildingName)
        repository.rememberRoomCode(roomCode)
        if (needsReload) loadSchedule()
    }

    /**
     * 更新星期 / 节次筛选条件。
     *
     * 筛选是纯展示层的过滤，不触发网络请求。
     */
    fun updateFilter(filter: ScheduleFilter) {
        _uiState.update { it.copy(schedule = it.schedule.copy(filter = filter)) }
    }

    /** 切换教室名精简显示，并记住偏好。 */
    fun toggleShortRoomNames() {
        val next = !_uiState.value.shortRoomNames
        repository.shortRoomNames = next
        _uiState.update { it.copy(shortRoomNames = next) }
    }

    /** 切换教室行排序方式，并记住偏好。 */
    fun setRoomSortMode(mode: RoomSortMode) {
        if (mode == _uiState.value.roomSortMode) return
        repository.roomSortMode = mode
        _uiState.update { it.copy(roomSortMode = mode) }
    }

    /** 切换周次。 */
    fun switchWeek(week: Int) {
        val target = week.coerceIn(1, 30)
        if (target == _uiState.value.schedule.currentWeek) return
        // 立即清掉旧周数据：新周数据没到之前，绝不能把上一周当这一周显示
        loadedQuery = null
        _uiState.update {
            it.copy(schedule = it.schedule.copy(currentWeek = target, data = null, staleCacheWeek = null))
        }
        loadSchedule()
    }

    /** 切换学期。 */
    fun switchSemester(semesterId: String) {
        if (semesterId == _uiState.value.schedule.currentSemesterId) return
        loadedQuery = null
        settings.migrateLegacyStartDate(semesterId)
        _uiState.update {
            it.copy(
                semesterStartDate = settings.semesterStartDateFor(semesterId),
                schedule = it.schedule.copy(
                    currentSemesterId = semesterId,
                    currentWeek = 1,
                    // 立即清空旧学期的课表，避免加载期间显示上一学期的内容
                    data = null,
                    staleCacheWeek = null
                )
            )
        }
        loadSchedule()
    }

    /** 清除本地缓存数据（保留登录），随后重新拉取当前周。 */
    fun clearLocalData() {
        viewModelScope.launch {
            repository.clearLocalData()
            loadedQuery = null
            _uiState.update {
                it.copy(
                    schedule = it.schedule.copy(data = null, fromCache = false, staleCacheWeek = null),
                    room = it.room.copy(classroomOptions = emptyList(), buildingOptions = emptyList())
                )
            }
            loadSchedule(fresh = true)
        }
    }

    /** 清除错误提示。 */
    fun dismissError() {
        _uiState.update { it.copy(session = it.session.copy(errorMessage = null)) }
    }

    /** 退出登录（保留本地课表缓存，退出后仍可离线查看）。 */
    fun logout() {
        scheduleJob?.cancel()
        repository.cancelRoomQueries()
        repository.logout()
        _uiState.value = TimetableUiState(
            shortRoomNames = repository.shortRoomNames,
            roomSortMode = repository.roomSortMode,
            timetable = settings.activeTimetable
        )
        // 退出后立即切到上次账号的离线课表，用户关掉登录提示即可查看
        viewModelScope.launch { loadOfflinePersonal() }
    }

    override fun onCleared() {
        // 页面销毁时取消仍在进行的教室页面请求，避免 Deferred 与连接长期滞留
        repository.cancelRoomQueries()
        super.onCleared()
    }

    companion object {
        fun factory(repository: TimetableRepository, settings: AppSettings): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimetableViewModel(repository, settings) as T
                }
            }
    }
}
