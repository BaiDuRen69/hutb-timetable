package com.hutb.timetable.ui.timetable

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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hutb.timetable.BuildConfig
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.computeCurrentWeek
import com.hutb.timetable.core.sortRoomLabels
import java.time.LocalDate

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
    onOpenSettings: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var showSemesterMenu by rememberSaveable { mutableStateOf(false) }
    var showWeekDialog by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    // 教室模式下"只看空教室"的开关（纯展示层状态，不进 ViewModel）
    var emptyRoomsOnly by rememberSaveable { mutableStateOf(false) }


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
            val occupied = visibleCourses.map { it.classroom.trim() }.toSet()
            val allRooms = state.room.classroomOptions.map { it.name }
                .filter { it.isNotBlank() }
                .ifEmpty { schedule.courses.map { it.classroom } }
            sortRoomLabels(
                allRooms.map { it.trim() }
                    .filter { it.isNotEmpty() && it !in occupied }
                    .distinct(),
                state.roomSortMode
            )
        }
    }
    if (state.schedule.queryMode != QueryMode.ROOM) {
        LaunchedEffect(Unit) { emptyRoomsOnly = false }
    }
    // 选了具体教室时，"空教室"清单没有意义（其它教室根本没查过），直接禁用
    val showEmptyRooms = state.schedule.queryMode == QueryMode.ROOM &&
        emptyRoomsOnly &&
        state.room.selectedRoomCode == null

    // 教室矩阵可见时，头部工具栏与矩阵放进同一个滚动容器（头部随下滑自然隐藏）；
    // 其余情况头部固定，内容区自行处理滚动。
    val showMatrix = state.schedule.queryMode == QueryMode.ROOM &&
        !showEmptyRooms &&
        state.schedule.data != null &&
        visibleCourses.isNotEmpty()

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
                                    CacheBadge(
                                        text = state.schedule.data
                                            ?.takeIf { it.week != state.schedule.currentWeek }
                                            ?.let { "第 ${it.week} 周缓存" }
                                            ?: "本地缓存"
                                    )
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
                filterSummary = filterSummary,
                onSwitchQueryMode = onSwitchQueryMode,
                onOpenFilterSheet = { showFilterSheet = true },
                onToggleEmptyOnly = { emptyRoomsOnly = !emptyRoomsOnly },
                onToggleShortNames = onToggleShortRoomNames,
                onSelectSortMode = onSelectRoomSortMode
            )

            if (state.session.expired && state.schedule.data != null) {
                SessionExpiredBanner(onLogin = onLogin)
            }

            if (showFilterSheet) {
                FilterSheet(
                    filter = state.schedule.filter,
                    onDismiss = { showFilterSheet = false },
                    onApply = onFilterChange
                )
            }

            if (showMatrix) {
                // 表头固定在顶部；矩阵内部用 LazyColumn 滚动（星期表头不随行滚走）
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
                    allRooms = allRoomsForMatrix
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.schedule.loading && state.schedule.data == null -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }

                        state.session.expired -> {
                            SessionExpiredView(
                                onLogin = onLogin,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        state.schedule.data == null -> {
                            EmptyView(
                                text = if (state.schedule.queryMode == QueryMode.ROOM) {
                                    "请选择教学楼或教室后查询"
                                } else {
                                    "暂无课表数据，点击刷新重试"
                                },
                                icon = if (state.schedule.queryMode == QueryMode.ROOM) {
                                    Icons.Default.MeetingRoom
                                } else {
                                    Icons.Outlined.EventBusy
                                },
                                actionLabel = if (state.schedule.queryMode == QueryMode.ROOM) {
                                    "选择教室"
                                } else {
                                    "刷新"
                                },
                                onAction = if (state.schedule.queryMode == QueryMode.ROOM) onPickRoom else onRefresh,
                                modifier = Modifier.align(Alignment.Center)
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

                        // 教室模式下的空教室列表：当前筛选范围内没有课的教室
                        showEmptyRooms -> {
                            EmptyRoomsView(
                                rooms = emptyRooms,
                                shortNames = state.shortRoomNames,
                                buildingName = state.room.selectedBuilding,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        visibleCourses.isEmpty() -> {
                            EmptyView(
                                text = "当前筛选条件下没有课程",
                                icon = Icons.Outlined.FilterAltOff,
                                actionLabel = "清除筛选",
                                onAction = { onFilterChange(ScheduleFilter()) },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        else -> {
                            // 个人课表：星期 × 节次网格
                            WeekGrid(
                                courses = state.schedule.data.courses,
                                filter = state.schedule.filter,
                                onCourseClick = { selectedCourse = it },
                                modifier = Modifier.fillMaxSize(),
                                timetable = state.timetable
                            )
                        }
                    }
                }
            }
        }
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

/** 顶栏副标题里的缓存标签。 */
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
