package com.hutb.timetable.ui

import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hutb.timetable.HutbApp
import com.hutb.timetable.data.model.AuthSession
import com.hutb.timetable.data.remote.PageSnapshot
import com.hutb.timetable.ui.login.LoginScreen
import com.hutb.timetable.ui.login.WebLoginActivity
import com.hutb.timetable.ui.settings.SettingsScreen
import com.hutb.timetable.ui.timetable.QueryMode
import com.hutb.timetable.ui.timetable.RoomPickerDialog
import com.hutb.timetable.ui.timetable.SnapshotDialog
import com.hutb.timetable.ui.timetable.TimetableScreen
import com.hutb.timetable.ui.timetable.TimetableViewModel
import com.hutb.timetable.ui.theme.HutbTimetableTheme
import com.hutb.timetable.ui.theme.ThemeMode
import com.hutb.timetable.ui.theme.ThemeStyle
import kotlinx.coroutines.delay

/**
 * 主界面宿主。
 *
 * 负责：
 * - 未登录时显示登录页；
 * - 登录后展示课表；
 * - 承接 WebView 登录结果并写入会话。
 */
class MainActivity : ComponentActivity() {

    private var viewModel: TimetableViewModel? = null

    private val loginLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != RESULT_OK || data == null) return@registerForActivityResult

        val jsessionId = data.getStringExtra(WebLoginActivity.RESULT_JSESSION_ID).orEmpty()
        if (jsessionId.isBlank()) {
            Toast.makeText(this, "未获取到登录会话，请重试", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val serverId = data.getStringExtra(WebLoginActivity.RESULT_SERVER_ID).orEmpty()

        // 学业身份不由登录页回传（它可能只是上一个账号的旧值），
        // 由 ViewModel 在新会话上重新向后端解析，缓存分区才可靠
        viewModel?.onLoginSuccess(
            AuthSession(
                jsessionId = jsessionId,
                serverId = serverId
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36：Android 15+ 强制 edge-to-edge，旧的状态栏着色属性已失效
        enableEdgeToEdge()

        val container = (application as HutbApp).container

        setContent {
            var themeMode by remember {
                mutableStateOf(ThemeMode.fromId(container.settings.themeModeId))
            }
            var themeStyle by remember {
                mutableStateOf(ThemeStyle.fromId(container.settings.themeStyleId))
            }
            HutbTimetableTheme(themeMode = themeMode, themeStyle = themeStyle) {
                val vm: TimetableViewModel = viewModel(
                    factory = TimetableViewModel.factory(container.repository, container.settings)
                )
                viewModel = vm

                val state by vm.uiState.collectAsStateWithLifecycle()

                // 显式的"是否展示登录页"状态：初值取决于本地是否已有会话，
                // 之后只由用户操作（登录成功 / 主动退出）改变，避免重组时来回跳。
                // rememberSaveable：屏幕旋转后覆盖层不凭空消失。
                var showLogin by rememberSaveable { mutableStateOf(!container.repository.hasStoredSession()) }

                // 首次启动明确告知课表仅供参考；确认过就不再打扰
                var showDisclaimer by rememberSaveable {
                    mutableStateOf(!container.settings.disclaimerAcknowledged)
                }

                // 设置页开关
                var showSettings by rememberSaveable { mutableStateOf(false) }

                // 教室选择对话框的开关
                var showRoomPicker by rememberSaveable { mutableStateOf(false) }

                // 页面快照对话框（排查教务系统改版用）
                var showSnapshots by rememberSaveable { mutableStateOf(false) }
                var snapshots by remember { mutableStateOf(emptyList<java.io.File>()) }

                // 主界面"再按一次退出"：避免在根界面误触直接退出
                var exitArmed by remember { mutableStateOf(false) }
                LaunchedEffect(exitArmed) {
                    if (exitArmed) {
                        delay(2000)
                        exitArmed = false
                    }
                }

                // 首次安装必须登录；登录过一次后，未登录只提示，离线课表仍可查看
                val loginMandatory = !state.session.loggedIn &&
                    !container.settings.firstLoginCompleted

                // 未登录时点"教室课表"：登录成功后自动切到教室模式
                var pendingRoomMode by rememberSaveable { mutableStateOf(false) }

                val hasOverlay = showLogin || showSettings || showRoomPicker || showSnapshots

                // 返回键分层：先关闭最上层覆盖层，主界面才是"再按一次退出"。
                // 首次强制登录时返回键直接退出，不能绕过登录进入主界面。
                BackHandler(enabled = hasOverlay) {
                    when {
                        showLogin -> {
                            if (loginMandatory) {
                                finish()
                            } else {
                                pendingRoomMode = false
                                showLogin = false
                            }
                        }
                        showSettings -> showSettings = false
                        showRoomPicker -> showRoomPicker = false
                        showSnapshots -> showSnapshots = false
                    }
                }
                BackHandler(enabled = !hasOverlay) {
                    if (exitArmed) {
                        finish()
                    } else {
                        exitArmed = true
                        Toast.makeText(this@MainActivity, "再按一次退出应用", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(state.session.loggedIn) {
                    if (state.session.loggedIn) showLogin = false
                }

                LaunchedEffect(
                    pendingRoomMode,
                    state.session.loggedIn,
                    state.schedule.currentSemesterId
                ) {
                    if (pendingRoomMode &&
                        state.session.loggedIn &&
                        state.schedule.currentSemesterId.isNotBlank()
                    ) {
                        pendingRoomMode = false
                        vm.switchQueryMode(QueryMode.ROOM)
                    }
                }

                // 主界面常驻组合；登录页 / 设置页以覆盖层叠在其上，
                // 返回时主界面不重组（整栋楼矩阵重组一次要几百毫秒，替换式显示每次返回都重建）
                Box(modifier = Modifier.fillMaxSize()) {
                    TimetableScreen(
                        state = state,
                        onRefresh = { vm.loadSchedule(fresh = true) },
                        onPrevWeek = { vm.switchWeek(state.schedule.currentWeek - 1) },
                        onNextWeek = { vm.switchWeek(state.schedule.currentWeek + 1) },
                        onSelectWeek = { vm.switchWeek(it) },
                        onSelectSemester = { vm.switchSemester(it) },
                        onLogin = {
                            loginLauncher.launch(WebLoginActivity.intent(this@MainActivity))
                        },
                        // 已登录时点右上角账号按钮只是打开登录页（可返回），不直接登出
                        onLogout = { showLogin = true },
                        onSwitchQueryMode = { mode ->
                            if (mode == QueryMode.ROOM && !state.session.loggedIn) {
                                // 教室课表要求登录；登录成功后自动切过去
                                pendingRoomMode = true
                                showLogin = true
                            } else {
                                vm.switchQueryMode(mode)
                            }
                        },
                        onPickRoom = {
                            if (!state.session.loggedIn) {
                                pendingRoomMode = true
                                showLogin = true
                            } else {
                                // 只准备草稿；对话框内的浏览不影响当前查询条件
                                vm.beginRoomDraft()
                                showRoomPicker = true
                            }
                        },
                        onFilterChange = { vm.updateFilter(it) },
                        onSelectRoom = { vm.selectRoom(it) },
                        onToggleShortRoomNames = { vm.toggleShortRoomNames() },
                        onSelectRoomSortMode = { vm.setRoomSortMode(it) },
                        onOpenSettings = { showSettings = true },
                        onShowSnapshots = {
                            snapshots = PageSnapshot.list(this@MainActivity)
                            showSnapshots = true
                        }
                    )

                    // 教室选择对话框（草稿态：确定才提交，取消零请求）
                    if (showRoomPicker) {
                        RoomPickerDialog(
                            campuses = state.room.campuses,
                            buildings = state.room.draftBuildingOptions,
                            classrooms = state.room.draftClassroomOptions,
                            initialCampusId = state.room.selectedCampusId,
                            initialBuildingId = state.room.selectedBuildingId,
                            initialRoomCode = state.room.selectedRoomCode,
                            buildingsLoading = state.room.draftBuildingsLoading,
                            classroomsLoading = state.room.draftClassroomsLoading,
                            errorMessage = state.room.errorMessage,
                            onRetry = { vm.loadRoomOptions() },
                            onDismiss = { showRoomPicker = false },
                            onPreviewCampus = { vm.previewCampus(it) },
                            onPreviewBuilding = { campusId, buildingId, buildingName ->
                                vm.previewBuilding(campusId, buildingId, buildingName)
                            },
                            onConfirm = { campusId, buildingId, buildingName, roomCode ->
                                showRoomPicker = false
                                vm.applyRoomSelection(campusId, buildingId, buildingName, roomCode)
                            }
                        )
                    }

                    // 页面快照对话框（排查教务系统改版用）
                    if (showSnapshots) {
                        SnapshotDialog(
                            snapshots = snapshots,
                            onDismiss = { showSnapshots = false },
                            onRefreshSnapshots = { snapshots = PageSnapshot.list(this@MainActivity) }
                        )
                    }

                    AnimatedVisibility(
                        visible = showSettings,
                        enter = fadeIn(tween(180)) +
                            slideInHorizontally(tween(220)) { it / 8 },
                        exit = fadeOut(tween(140)) +
                            slideOutHorizontally(tween(180)) { it / 8 }
                    ) {
                        // 设置页自身不带背景色，覆盖层模式下包一层不透明背景
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            SettingsScreen(
                                settings = container.settings,
                                semesterId = state.schedule.currentSemesterId,
                                themeMode = themeMode,
                                themeStyle = themeStyle,
                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    container.settings.themeModeId = mode.id
                                },
                                onThemeStyleChange = { style ->
                                    themeStyle = style
                                    container.settings.themeStyleId = style.id
                                },
                                onBack = { showSettings = false },
                                onChanged = { vm.reloadSettings() },
                                secureStorage = container.credentials.secureStorage,
                                onClearLocalData = {
                                    vm.clearLocalData()
                                    Toast.makeText(
                                        this@MainActivity,
                                        "已清除本地缓存",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showLogin,
                        enter = fadeIn(tween(180)) +
                            slideInVertically(tween(220)) { it / 10 },
                        exit = fadeOut(tween(140)) +
                            slideOutVertically(tween(180)) { it / 10 }
                    ) {
                        LoginScreen(
                            loading = false,
                            errorMessage = state.session.errorMessage,
                            // 首次安装必须登录；之后仅提示，关掉后仍可看离线课表
                            canBack = !loginMandatory,
                            loggedIn = state.session.loggedIn,
                            onLogin = {
                                loginLauncher.launch(WebLoginActivity.intent(this@MainActivity))
                            },
                            onBack = {
                                pendingRoomMode = false
                                showLogin = false
                            },
                            onLogout = { clearOffline ->
                                // 登出同时清掉 WebView 的 CAS 会话：
                                // 否则下次"切换账号"会被 SSO 直接送回原账号
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                pendingRoomMode = false
                                vm.logout(clearOffline)
                                showLogin = true
                            },
                            secureStorage = container.credentials.secureStorage
                        )
                    }

                    if (showDisclaimer) {
                        DisclaimerDialog(
                            onConfirm = {
                                container.settings.disclaimerAcknowledged = true
                                showDisclaimer = false
                            }
                        )
                    }
                }
            }
        }
    }
}
