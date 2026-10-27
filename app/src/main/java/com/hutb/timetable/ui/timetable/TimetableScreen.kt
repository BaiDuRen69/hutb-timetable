package com.hutb.timetable.ui.timetable

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hutb.timetable.BuildConfig
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.CourseChange
import com.hutb.timetable.core.CourseChangeKind
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.WeekSchedule
import com.hutb.timetable.core.computeCurrentWeek
import com.hutb.timetable.core.formatSyncAge
import com.hutb.timetable.core.roomIdentity
import com.hutb.timetable.core.sortRoomLabels
import com.hutb.timetable.data.local.ClassTimetable
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 课表主界面。
 *
 * 头部只有两层：TopAppBar（标题/学期周次/教室条件）+ 一行工具条（模式/筛选/更多）。
 * 筛选改为 BottomSheet，其余状态页见 `TimetableViews.kt`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    state: TimetableUiState,
    onRefresh: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onSelectSemester: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSwitchQueryMode: (QueryMode) -> Unit,
    onPickRoom: () -> Unit,
    onShowSnapshots: () -> Unit,
    onSelectRoom: (String) -> Unit = {},
    onFilterChange: (ScheduleFilter) -> Unit,
    onToggleShortRoomNames: () -> Unit = {},
    onSelectRoomSortMode: (RoomSortMode) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onShowNowEmptyRooms: (ScheduleFilter) -> Unit = {},
    onDismissChanges: () -> Unit = {},
    onToggleShowWeekend: () -> Unit = {},
    onToggleHideEmptyPeriods: () -> Unit = {},
    /** 图标快捷方式一次性动作：now_empty_rooms / today_timetable */
    shortcutAction: String? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showSemesterMenu by rememberSaveable { mutableStateOf(false) }
    var showWeekDialog by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    // 教室模式下"只看空教室"的开关（纯展示层状态，不进 ViewModel）
    var emptyRoomsOnly by rememberSaveable { mutableStateOf(false) }
    // 从"现在/下一节空教室"进入后，跨节次自动更新筛选
    var nowEmptyMode by rememberSaveable { mutableStateOf(false) }
    // 教室模式：Canvas 矩阵 / 线性列表（读屏友好）
    var roomListMode by rememberSaveable { mutableStateOf(false) }
    // 个人课表：周视图 / 日视图
    var dayView by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    var pendingIcs by remember { mutableStateOf<String?>(null) }
    val icsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        val text = pendingIcs
        pendingIcs = null
        if (uri != null && text != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入所选文件")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "日历文件已导出" else "导出失败，请重试",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 筛选后的课程，用于统计命中数
    val visibleCourses = remember(state.schedule.data, state.schedule.filter) {
        state.schedule.filter.apply(state.schedule.data?.courses.orEmpty())
    }

    // 矩阵要展示的教室清单：没选具体教室时用整栋楼的教室列表（含没课的教室）。
    val allRoomsForMatrix = remember(state.room.classroomOptions, state.room.selectedRoomCode) {
        if (state.room.selectedRoomCode == null) {
            state.room.classroomOptions.map { it.name }
        } else {
            emptyList()
        }
    }

    // 空教室 = 教室清单里在当前筛选范围内没有任何课的教室。
    val emptyRooms = remember(
        visibleCourses,
        state.room.classroomOptions,
        state.schedule.data,
        state.schedule.queryMode,
        state.roomSortMode
    ) {
        val schedule = state.schedule.data
        if (state.schedule.queryMode != QueryMode.ROOM || schedule == null) {
            emptyList()
        } else {
            // 教室名写法可能不同（*(智慧) vs *（智慧）），按归一化身份比较
            val occupied = visibleCourses.map { roomIdentity(it.classroom) }.toSet()
            val allRooms = state.room.classroomOptions.map { it.name }
                .filter { it.isNotBlank() }
                .ifEmpty { schedule.courses.map { it.classroom } }
            sortRoomLabels(
                allRooms.map { it.trim() }
                    .filter { it.isNotEmpty() && roomIdentity(it) !in occupied }
                    .distinct(),
                state.roomSortMode
            )
        }
    }
    if (state.schedule.queryMode != QueryMode.ROOM) {
        LaunchedEffect(Unit) {
            emptyRoomsOnly = false
            nowEmptyMode = false
        }
    }
    // 选定具体教室后"只看空教室"没有意义：无论从选择器还是空教室列表进入，都关掉它，
    // 否则之后清除教室选择会突然跳回空教室列表
    LaunchedEffect(state.room.selectedRoomCode) {
        if (state.room.selectedRoomCode != null) {
            emptyRoomsOnly = false
            nowEmptyMode = false
        }
    }
    // 选了具体教室时，"空教室"清单没有意义（其它教室根本没查过），直接禁用
    val showEmptyRooms = state.schedule.queryMode == QueryMode.ROOM &&
        emptyRoomsOnly &&
        state.room.selectedRoomCode == null

    // 教室矩阵可见时：顶部工具栏固定，矩阵内部自己滚动（表头固定 + LazyColumn 行列表）；
    // 其余情况内容区自行处理滚动。
    // 注意：筛选后没有命中课程时仍显示矩阵（含全部教室行），只在顶部加一行提示，
    // 否则用户会以为加载失败或筛选条件失效。
    val showMatrix = state.schedule.queryMode == QueryMode.ROOM &&
        !showEmptyRooms &&
        state.schedule.data != null &&
        (allRoomsForMatrix.isNotEmpty() || state.schedule.data.courses.isNotEmpty())

    val filterSummary = remember(visibleCourses, state.schedule.queryMode, state.schedule.filter) {
        if (!state.schedule.filter.isActive) {
            null
        } else if (state.schedule.queryMode == QueryMode.ROOM) {
            val rooms = visibleCourses.map { it.classroom.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .size
            "命中 $rooms 间教室 · ${visibleCourses.size} 节课"
        } else {
            "命中 ${visibleCourses.size} 节课"
        }
    }

    // 依据开学日期推算"当前周"，供空态按钮"回到本周"使用
    val thisWeek = remember(state.semesterStartDate) {
        computeCurrentWeek(state.semesterStartDate, LocalDate.now())
    }
    val isCurrentWeek = thisWeek != null && thisWeek == state.schedule.currentWeek
    val nowTarget = rememberNowEmptyTarget(
        timetable = state.timetable,
        week = state.schedule.currentWeek,
        enabled = isCurrentWeek && state.session.loggedIn
    )
    val todayStatus = rememberTodayCourseStatus(
        courses = state.schedule.data?.courses.orEmpty(),
        timetable = state.timetable,
        enabled = isCurrentWeek && state.schedule.queryMode == QueryMode.PERSONAL
    )
    // 停留在"现在空教室"时，跨到下一节自动更新筛选（只影响客户端过滤，不重新请求）
    LaunchedEffect(nowTarget, nowEmptyMode) {
        if (nowEmptyMode && nowTarget != null &&
            state.schedule.filter != nowTarget.filter
        ) {
            onFilterChange(nowTarget.filter)
        }
    }
    // 一键退出"现在空教室"：清掉节次筛选与只看空教室，回到整楼矩阵
    val clearNowFilter: () -> Unit = {
        nowEmptyMode = false
        emptyRoomsOnly = false
        onFilterChange(ScheduleFilter())
    }
    val applyNowTarget: (NowEmptyTarget) -> Unit = { target ->
        nowEmptyMode = true
        emptyRoomsOnly = true
        val inRoomMode = state.schedule.queryMode == QueryMode.ROOM
        val buildingKnown = state.room.selectedBuildingId != null
        when {
            // 还没选教学楼：切到教室模式并保留筛选，选楼后直接显示空教室
            !buildingKnown -> {
                onFilterChange(target.filter)
                if (!inRoomMode) onSwitchQueryMode(QueryMode.ROOM)
            }
            // 已在该楼的整楼矩阵里，只需套用节次筛选
            inRoomMode && state.room.selectedRoomCode == null -> {
                onFilterChange(target.filter)
            }
            // 个人模式或正看着某间教室：一次性切模式/清教室并重查
            else -> onShowNowEmptyRooms(target.filter)
        }
    }
    LaunchedEffect(shortcutAction, nowTarget, state.session.loggedIn) {
        when (shortcutAction) {
            "now_empty_rooms" -> {
                if (nowTarget != null) {
                    applyNowTarget(nowTarget)
                } else if (!state.session.loggedIn) {
                    onLogin()
                }
                onShortcutHandled()
            }

            "today_timetable" -> {
                if (state.schedule.queryMode != QueryMode.PERSONAL) {
                    onSwitchQueryMode(QueryMode.PERSONAL)
                }
                onShortcutHandled()
            }

            null -> Unit
            else -> onShortcutHandled()
        }
    }

    // 教室条件：校区 · 教学楼 · 教室（README 承诺的三级信息）
    val roomScopeLabel = remember(
        state.room.campuses,
        state.room.selectedCampusId,
        state.room.selectedBuilding,
        state.room.classroomOptions,
        state.room.selectedRoomCode
    ) {
        val campus = state.room.campuses
            .firstOrNull { it.code == state.room.selectedCampusId }?.name
        val room = state.room.classroomOptions
            .firstOrNull { it.code == state.room.selectedRoomCode }?.name
        listOfNotNull(campus, state.room.selectedBuilding, room).joinToString(" · ")
    }

    // 错误提示
    LaunchedEffect(state.session.errorMessage) {
        state.session.errorMessage?.let { message ->
            if (!state.session.expired) {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    // 断网且本周无缓存时，提示可跳到有缓存的周次（不把别的周当本周渲染）
    LaunchedEffect(state.schedule.staleCacheWeek) {
        val staleWeek = state.schedule.staleCacheWeek ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "第 $staleWeek 周有本地缓存，可切换查看",
            actionLabel = "查看",
            withDismissAction = true
        )
        if (result == SnackbarResult.ActionPerformed) onSelectWeek(staleWeek)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // DropdownMenu 必须和触发文字在同一个 Box 里，
                            // 否则会锚定到内容区左上角而不是标题下方
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = state.schedule.semesters
                                        .firstOrNull { it.id == state.schedule.currentSemesterId }?.name
                                        ?: state.schedule.data?.semesterName?.takeIf { it.isNotBlank() }
                                        ?: "选择学期",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { showSemesterMenu = true }
                                )
                                SemesterMenu(
                                    expanded = showSemesterMenu,
                                    semesters = state.schedule.semesters,
                                    currentSemesterId = state.schedule.currentSemesterId,
                                    onDismiss = { showSemesterMenu = false },
                                    onSelect = onSelectSemester
                                )
                            }
                            Text(
                                text = " · 第 ${state.schedule.currentWeek} 周",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                modifier = Modifier.clickable { showWeekDialog = true }
                            )
                        }
                        val hasSubtitle = state.schedule.queryMode == QueryMode.ROOM ||
                            state.schedule.fromCache ||
                            (BuildConfig.DEBUG && (state.schedule.data?.parseIssues?.isNotEmpty() == true))
                        if (hasSubtitle) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                if (state.schedule.queryMode == QueryMode.ROOM) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable(onClick = onPickRoom)
                                            .padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MeetingRoom,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = roomScopeLabel.ifBlank { "选择教学楼 / 教室" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                        Icon(
                                            Icons.Default.ExpandMore,
                                            contentDescription = "更改教室",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (state.schedule.fromCache) {
                                    // 数据层只返回与请求周次一致的缓存；
                                    // 附带缓存年龄，让用户知道这份课表有多旧
                                    CacheBadge(text = cacheBadgeText(state.schedule.data))
                                }
                                if (BuildConfig.DEBUG) {
                                    val issueCount = state.schedule.data?.parseIssues?.size ?: 0
                                    if (issueCount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "解析提示 $issueCount 行",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onPrevWeek) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "上一周")
                    }
                    IconButton(onClick = onNextWeek) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "下一周")
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("设置") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = {
                                    showOverflow = false
                                    onOpenSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("搜索课程") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                enabled = state.schedule.data?.courses?.isNotEmpty() == true,
                                onClick = {
                                    showOverflow = false
                                    showSearch = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分享当前课表") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                enabled = visibleCourses.isNotEmpty(),
                                onClick = {
                                    showOverflow = false
                                    val semesterName = state.schedule.semesters
                                        .firstOrNull { it.id == state.schedule.currentSemesterId }?.name
                                        ?: state.schedule.data?.semesterName.orEmpty()
                                    val text = buildScheduleShareText(
                                        semesterName = semesterName,
                                        week = state.schedule.currentWeek,
                                        courses = visibleCourses,
                                        filter = state.schedule.filter,
                                        roomScope = if (state.schedule.queryMode == QueryMode.ROOM) {
                                            roomScopeLabel
                                        } else {
                                            ""
                                        }
                                    )
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "湖南工商大学课表")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "分享课表"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出当前课表 (.ics)") },
                                leadingIcon = { Icon(Icons.Default.DateRange, null) },
                                enabled = visibleCourses.isNotEmpty() &&
                                    state.semesterStartDate.isNotBlank() &&
                                    state.timetable != null,
                                onClick = {
                                    showOverflow = false
                                    val data = state.schedule.data
                                    if (data != null) {
                                        val ics = buildWeekIcs(
                                            semesterStartDate = state.semesterStartDate,
                                            week = state.schedule.currentWeek,
                                            semesterName = data.semesterName,
                                            courses = visibleCourses,
                                            timetable = state.timetable
                                        )
                                        if (ics == null) {
                                            Toast.makeText(
                                                context,
                                                "请先设置开学日期和作息时间表",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            pendingIcs = ics
                                            icsLauncher.launch(
                                                icsFileName(data.semesterName, state.schedule.currentWeek)
                                            )
                                        }
                                    }
                                }
                            )
                            if (BuildConfig.DEBUG) {
                                DropdownMenuItem(
                                    text = { Text("页面快照") },
                                    leadingIcon = { Icon(Icons.Default.BugReport, null) },
                                    onClick = {
                                        showOverflow = false
                                        onShowSnapshots()
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("账号 / 登录") },
                                leadingIcon = { Icon(Icons.Default.School, null) },
                                onClick = {
                                    showOverflow = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.schedule.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            HeaderBars(
                state = state,
                emptyRoomsOnly = emptyRoomsOnly,
                emptyRoomsEnabled = state.room.selectedRoomCode == null,
                filterSummary = filterSummary,
                onSwitchQueryMode = { mode ->
                    nowEmptyMode = false
                    onSwitchQueryMode(mode)
                },
                onOpenFilterSheet = { showFilterSheet = true },
                onToggleEmptyOnly = {
                    emptyRoomsOnly = !emptyRoomsOnly
                    nowEmptyMode = false
                },
                onToggleShortNames = onToggleShortRoomNames,
                onSelectSortMode = onSelectRoomSortMode,
                listMode = roomListMode,
                onToggleListMode = { roomListMode = !roomListMode },
                dayView = dayView,
                onToggleDayView = { dayView = !dayView },
                onToggleShowWeekend = onToggleShowWeekend,
                onToggleHideEmptyPeriods = onToggleHideEmptyPeriods
            )

            if (state.schedule.queryMode == QueryMode.PERSONAL &&
                state.schedule.changes.isNotEmpty()
            ) {
                ScheduleChangesBanner(
                    changes = state.schedule.changes,
                    onDismiss = onDismissChanges
                )
            }

            todayStatus?.takeIf { it.todayCount > 0 }?.let { status ->
                TodayCourseCard(
                    status = status,
                    onClick = {
                        (status.current ?: status.next)?.let { selectedCourse = it }
                    }
                )
            }

            nowTarget?.let { target ->
                NowEmptyRoomsBar(
                    label = target.label,
                    subtitle = target.subtitle,
                    active = nowEmptyMode,
                    onClear = clearNowFilter,
                    onClick = { applyNowTarget(target) }
                )
            }

            if (state.session.expired && state.schedule.data != null) {
                SessionExpiredBanner(onLogin = onLogin)
            } else if (!state.session.loggedIn && state.schedule.data != null) {
                OfflineCacheBanner(
                    week = state.schedule.data.week,
                    onLogin = onLogin
                )
            }

            if (showFilterSheet) {
                FilterSheet(
                    filter = state.schedule.filter,
                    onDismiss = { showFilterSheet = false },
                    onApply = {
                        nowEmptyMode = false
                        onFilterChange(it)
                    }
                )
            }

            if (showMatrix) {
                // 表头固定在顶部；矩阵内部用 LazyColumn 滚动（星期表头不随行滚走）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (roomListMode) {
                        RoomCourseList(
                            courses = visibleCourses,
                            onCourseClick = { selectedCourse = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        if (visibleCourses.isEmpty()) {
                            Text(
                                text = if (state.schedule.filter.isActive) {
                                    "当前筛选条件下没有课程，矩阵仍显示全部教室"
                                } else {
                                    "本周还没有教室课程安排"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        RoomMatrix(
                            courses = state.schedule.data.courses,
                            filter = state.schedule.filter,
                            onCourseClick = { selectedCourse = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shortNames = state.shortRoomNames,
                            buildingName = state.room.selectedBuilding,
                            sortMode = state.roomSortMode,
                            allRooms = allRoomsForMatrix,
                            week = state.schedule.currentWeek
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.schedule.loading && state.schedule.data == null -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        // 会话失效但仍有缓存时继续显示课表，由顶部横幅提示重新登录；
                        // 只有连缓存都没有才用整屏空态，避免把可看的离线课表盖住
                        state.session.expired && state.schedule.data == null -> {
                            SessionExpiredView(
                                onLogin = onLogin,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        state.schedule.data == null -> {
                            val loggedOut = !state.session.loggedIn
                            EmptyView(
                                text = when {
                                    loggedOut -> "登录后即可查询课表"
                                    state.schedule.queryMode == QueryMode.ROOM -> "请选择教学楼或教室后查询"
                                    else -> "暂无课表数据，点击刷新重试"
                                },
                                icon = if (state.schedule.queryMode == QueryMode.ROOM) {
                                    Icons.Default.MeetingRoom
                                } else {
                                    Icons.Outlined.EventBusy
                                },
                                actionLabel = when {
                                    loggedOut -> "登录"
                                    state.schedule.queryMode == QueryMode.ROOM -> "选择教室"
                                    else -> "刷新"
                                },
                                onAction = when {
                                    loggedOut -> onLogin
                                    state.schedule.queryMode == QueryMode.ROOM -> onPickRoom
                                    else -> onRefresh
                                },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // 教室模式下的空教室列表：当前筛选范围内没有课的教室。
                        // 必须放在"整周无课程"分支之前：整周课表为空时，
                        // 所有教室都是空教室，不能反过来报"没有数据"。
                        showEmptyRooms -> {
                            EmptyRoomsView(
                                rooms = emptyRooms,
                                shortNames = state.shortRoomNames,
                                buildingName = state.room.selectedBuilding,
                                // 点教室直接查看它的课表；空教室列表随之自动收起
                                onRoomClick = { room ->
                                    emptyRoomsOnly = false
                                    nowEmptyMode = false
                                    onSelectRoom(room)
                                },
                                onClearFilter = if (state.schedule.filter.isActive) {
                                    clearNowFilter
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        state.schedule.data.courses.isEmpty() && !showEmptyRooms -> {
                            EmptyView(
                                text = if (state.schedule.queryMode == QueryMode.ROOM) {
                                    "该范围本周没有教室课表数据"
                                } else {
                                    "第 ${state.schedule.currentWeek} 周没有课程安排"
                                },
                                icon = Icons.Outlined.EventBusy,
                                actionLabel = thisWeek
                                    ?.takeIf { it != state.schedule.currentWeek }
                                    ?.let { "回到本周" },
                                onAction = thisWeek
                                    ?.takeIf { it != state.schedule.currentWeek }
                                    ?.let { week -> { onSelectWeek(week) } },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        visibleCourses.isEmpty() &&
                            !(dayView && state.schedule.queryMode == QueryMode.PERSONAL) -> {
                            EmptyView(
                                text = "当前筛选条件下没有课程",
                                icon = Icons.Outlined.FilterAltOff,
                                actionLabel = "清除筛选",
                                onAction = {
                                    nowEmptyMode = false
                                    onFilterChange(ScheduleFilter())
                                },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        else -> {
                            val currentWeek = thisWeek != null &&
                                thisWeek == state.schedule.currentWeek
                            if (dayView) {
                                DayScheduleView(
                                    week = state.schedule.currentWeek,
                                    semesterStartDate = state.semesterStartDate,
                                    courses = state.schedule.data.courses,
                                    filter = state.schedule.filter,
                                    timetable = state.timetable,
                                    isCurrentWeek = currentWeek,
                                    onCourseClick = { selectedCourse = it },
                                    onClearFilter = clearNowFilter,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                // 个人课表：星期 × 节次网格
                                WeekGrid(
                                    courses = state.schedule.data.courses,
                                    filter = state.schedule.filter,
                                    onCourseClick = { selectedCourse = it },
                                    modifier = Modifier.fillMaxSize(),
                                    timetable = state.timetable,
                                    // 未设开学日期无法确认今天是第几周，不能画“今天/现在”误导用户
                                    isCurrentWeek = currentWeek,
                                    showWeekend = state.showWeekend,
                                    hideEmptyPeriods = state.hideEmptyPeriods
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 本周课程搜索（只查已加载的课表，不联网）
    if (showSearch) {
        CourseSearchDialog(
            courses = state.schedule.data?.courses.orEmpty(),
            timetable = state.timetable,
            week = state.schedule.currentWeek,
            onDismiss = { showSearch = false },
            onSelect = { course ->
                showSearch = false
                selectedCourse = course
            }
        )
    }

    // 课程详情
    selectedCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            onDismiss = { selectedCourse = null },
            timetable = state.timetable
        )
    }

    // 周次选择
    if (showWeekDialog) {
        WeekPickerDialog(
            currentWeek = state.schedule.currentWeek,
            onDismiss = { showWeekDialog = false },
            onConfirm = {
                showWeekDialog = false
                onSelectWeek(it)
            }
        )
    }
}

@Composable
private fun ScheduleChangesBanner(
    changes: List<CourseChange>,
    onDismiss: () -> Unit
) {
    var expanded by remember(changes) { mutableStateOf(false) }
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "本周有 ${changes.size} 处课表变化",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 2,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    text = if (expanded) "收起" else "查看",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "忽略课表变化",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (expanded) {
            changes.take(8).forEach { change ->
                Text(
                    text = "• ${describeCourseChange(change)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    modifier = Modifier.padding(start = 26.dp, end = 8.dp, bottom = 3.dp)
                )
            }
            if (changes.size > 8) {
                Text(
                    text = "…还有 ${changes.size - 8} 处",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    modifier = Modifier.padding(start = 26.dp, bottom = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun describeCourseChange(change: CourseChange): String {
    val day = WEEKDAY_NAMES.getOrNull(change.weekday - 1) ?: "周${change.weekday}"
    val course = change.newCourse ?: change.oldCourse
    val period = course?.let {
        if (it.startPeriod == it.endPeriod) {
            "第${it.startPeriod}节"
        } else {
            "第${it.startPeriod}-${it.endPeriod}节"
        }
    }.orEmpty()
    return when (change.kind) {
        CourseChangeKind.ADDED -> listOfNotNull(
            "新增",
            day,
            period.takeIf { it.isNotBlank() },
            change.courseName,
            course?.classroom?.takeIf { it.isNotBlank() },
            course?.teacher?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")

        CourseChangeKind.REMOVED -> listOfNotNull(
            "移出",
            day,
            period.takeIf { it.isNotBlank() },
            change.courseName
        ).joinToString(" · ")

        CourseChangeKind.UPDATED -> "$day ${change.courseName}：" +
            change.details.joinToString("；")
    }
}

/**
 * 今日课程状态每分钟刷新一次；只在个人课表的真实本周显示。
 */
@Composable
private fun rememberTodayCourseStatus(
    courses: List<Course>,
    timetable: ClassTimetable?,
    enabled: Boolean
): TodayCourseStatus? {
    if (!enabled) return null
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000)
        }
    }
    return remember(courses, timetable, now) {
        computeTodayCourseStatus(courses, timetable, now)
    }
}

@Composable
private fun TodayCourseCard(
    status: TodayCourseStatus,
    onClick: () -> Unit
) {
    val course = status.current ?: status.next
    val range = status.currentRange ?: status.nextRange
    val isNow = status.current != null
    val countdown = when {
        status.currentRange != null -> status.currentRange.last - status.nowMinutes
        status.nextRange != null -> status.nextRange.first - status.nowMinutes
        else -> null
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .then(
                if (course != null && range != null) {
                    Modifier.clickable(onClickLabel = "查看课程详情", onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = if (course != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    isNow -> "正在上课"
                    course != null -> "下一节"
                    else -> "今天的课已结束"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (course != null) accent else contentColor
            )
            if (course != null && range != null) {
                Text(
                    text = "${formatMinutes(range.first)}-${formatMinutes(range.last)} · " +
                        course.courseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOf(course.classroom, course.teacher)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "今天共有 ${status.todayCount} 节课程",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (course != null && countdown != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isNow) "还剩" else "还有",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$countdown 分钟",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = "剩余 ${status.remaining.size} 节",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class NowEmptyTarget(
    val label: String,
    val subtitle: String,
    val filter: ScheduleFilter
)

/**
 * 今天当前或下一节的空教室入口。
 *
 * 只在“显示的是真实本周且已登录”时出现；未设开学日期时无法确认周次，不猜。
 * 每分钟更新一次，跨节次后按钮文案和筛选自动切换。
 */
@Composable
private fun rememberNowEmptyTarget(
    timetable: ClassTimetable?,
    week: Int,
    enabled: Boolean
): NowEmptyTarget? {
    if (!enabled) return null
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000)
        }
    }
    val today = now.dayOfWeek.value
    val nowMinutes = now.hour * 60 + now.minute
    val slot = PERIOD_SLOTS.firstOrNull { candidate ->
        val range = slotRangeMinutes(timetable, candidate) ?: return@firstOrNull false
        nowMinutes <= range.last
    } ?: return null
    val range = slotRangeMinutes(timetable, slot) ?: return null
    val weekday = WEEKDAY_NAMES.getOrNull(today - 1) ?: return null
    val isNow = nowMinutes >= range.first
    return NowEmptyTarget(
        label = if (isNow) "现在空教室" else "下一节空教室",
        subtitle = "第${week}周 · ${weekday}第${slot.start}-${slot.end}节 · " +
            slotTimeLabel(timetable, slot),
        filter = ScheduleFilter(
            weekdays = setOf(today),
            startPeriod = slot.start,
            endPeriod = slot.end
        )
    )
}

@Composable
private fun NowEmptyRoomsBar(
    label: String,
    subtitle: String,
    active: Boolean,
    onClear: () -> Unit,
    onClick: () -> Unit
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MeetingRoom,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (active) {
            TextButton(onClick = onClear) {
                Text(
                    text = "清除筛选",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        } else {
            Text(
                text = "查看",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

/** 顶栏副标题里的缓存标签。 */
private fun cacheBadgeText(data: WeekSchedule?): String {
    val cachedAt = data?.cachedAt ?: 0L
    val age = formatSyncAge(cachedAt, System.currentTimeMillis())
    return if (age == null) "本地缓存" else "本地缓存 · $age"
}

@Composable
private fun CacheBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}
