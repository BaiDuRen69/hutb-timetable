package com.hutb.timetable.ui.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hutb.timetable.BuildConfig
import com.hutb.timetable.core.computeCurrentWeek
import com.hutb.timetable.core.isWithinQuietHours
import com.hutb.timetable.core.semesterStartForWeek
import com.hutb.timetable.data.local.AppSettings
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.data.local.PeriodTime
import com.hutb.timetable.notify.DailySummaryNotifier
import com.hutb.timetable.notify.DailySummaryScheduler
import com.hutb.timetable.ui.DISCLAIMER_BODY
import com.hutb.timetable.ui.theme.ThemeMode
import com.hutb.timetable.ui.theme.ThemeStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 设置页：当前周次（按学期）+ 主题 + 作息时间表。
 *
 * 用户只需知道"今天是开学第几周"，由 App 反推第一周周一；
 * 周次按 [semesterId] 分开保存，不同学期互不影响。
 *
 * 所有修改即时落盘并通过 [onChanged] 通知调用方刷新课表。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    semesterId: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeStyle: ThemeStyle = ThemeStyle.BLUE,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onThemeStyleChange: (ThemeStyle) -> Unit = {},
    /** 凭据是否加密落盘；false 时明确提示用户 */
    secureStorage: Boolean = true,
    /** 当前显示课表的获取时间（Unix 毫秒），0 表示暂无 */
    lastSyncAtMillis: Long = 0L,
    /** 清除本地缓存数据（保留登录） */
    onClearLocalData: () -> Unit = {}
) {
    // 覆盖层语义：返回键先关闭设置页，不能把整个 Activity 结束掉
    BackHandler { onBack() }

    // 可存入 Bundle 的简单状态用 rememberSaveable，旋转后不丢；
    // timetables 是复杂对象列表，初值来自持久化设置，保持 remember 即可
    var timetables by remember { mutableStateOf(settings.timetables) }
    var activeId by rememberSaveable { mutableStateOf(settings.activeTimetableId) }
    var startDate by rememberSaveable(semesterId) {
        mutableStateOf(settings.semesterStartDateFor(semesterId))
    }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showWeekPicker by rememberSaveable { mutableStateOf(false) }
    var showStartDateEditor by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showSponsor by rememberSaveable { mutableStateOf(false) }

    val active = timetables.firstOrNull { it.id == activeId }
    val currentWeek = if (startDate.isBlank()) null else computeCurrentWeek(startDate, LocalDate.now())
    // 没有选中学期时无处归属，先禁止编辑，避免把周次存到空键上
    val canEditStartDate = semesterId.isNotBlank()

    val context = LocalContext.current
    var dailySummaryEnabled by rememberSaveable { mutableStateOf(settings.dailySummaryEnabled) }
    var summaryMinutes by rememberSaveable { mutableStateOf(settings.dailySummaryMinutes) }
    var notifyWithoutCourses by rememberSaveable {
        mutableStateOf(settings.dailySummaryNotifyWithoutCourses)
    }
    var quietHoursEnabled by rememberSaveable { mutableStateOf(settings.quietHoursEnabled) }
    var quietStart by rememberSaveable { mutableStateOf(settings.quietHoursStartMinutes) }
    var quietEnd by rememberSaveable { mutableStateOf(settings.quietHoursEndMinutes) }
    var autoSyncEnabled by rememberSaveable { mutableStateOf(settings.autoSyncEnabled) }
    var autoSyncWifiOnly by rememberSaveable { mutableStateOf(settings.autoSyncWifiOnly) }
    var notificationsAllowed by remember { mutableStateOf(DailySummaryNotifier.canPost(context)) }
    var showNotificationDenied by rememberSaveable { mutableStateOf(false) }

    // 从系统设置返回后重新检查，用户可能刚关掉/打开通知权限
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = DailySummaryNotifier.canPost(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            dailySummaryEnabled = true
            settings.dailySummaryEnabled = true
            notificationsAllowed = DailySummaryNotifier.canPost(context)
            DailySummaryNotifier.ensureChannel(context)
            DailySummaryScheduler.reschedule(context)
        } else {
            showNotificationDenied = true
        }
    }

    fun pickTime(initialMinutes: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinutes / 60,
            initialMinutes % 60,
            true
        ).show()
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        if (!enabled) {
            dailySummaryEnabled = false
            settings.dailySummaryEnabled = false
            DailySummaryScheduler.reschedule(context)
            return
        }
        val needsPermission = !DailySummaryNotifier.hasPermission(context)
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            dailySummaryEnabled = true
            settings.dailySummaryEnabled = true
            notificationsAllowed = DailySummaryNotifier.canPost(context)
            DailySummaryNotifier.ensureChannel(context)
            DailySummaryScheduler.reschedule(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!secureStorage) {
            // 系统 KeyStore 不可用时凭据会落到普通 SharedPreferences，必须让用户知情
            Text(
                text = "⚠ 本设备不支持系统安全存储，登录凭据将以明文保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // ---- 当前周次（按学期） ----
        SectionCard(title = "当前学期周次") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canEditStartDate) { showWeekPicker = true }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !canEditStartDate -> "请先登录并选择学期"
                            startDate.isBlank() -> "未设置（点击选择）"
                            currentWeek != null -> "该学期第 $currentWeek 周（按今天反推）"
                            else -> "周次记录异常，请重新选择"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when {
                            !canEditStartDate -> "每个学期的周次独立保存"
                            startDate.isBlank() -> "设置后才能准确显示今天/现在和当前空教室"
                            else -> "第一周周一：$startDate"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEditStartDate) {
                    Text(
                        text = "修改",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (canEditStartDate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showStartDateEditor = true }) {
                        Text("直接填写第一周周一", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ---- 每日课程提醒 ----
        SectionCard(title = "每日课程提醒") {
            SettingSwitchRow(
                title = "每日课程摘要",
                subtitle = "按本地课表汇总当天课程；需先同步本周课表，不联网。",
                checked = dailySummaryEnabled,
                onCheckedChange = ::setDailySummaryEnabled
            )
            if (dailySummaryEnabled) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                SettingClickRow(
                    title = "提醒时间",
                    subtitle = "每天在此时刻发送当天课程",
                    value = formatMinuteOfDay(summaryMinutes),
                    onClick = {
                        pickTime(summaryMinutes) { minutes ->
                            summaryMinutes = minutes
                            settings.dailySummaryMinutes = minutes
                            DailySummaryScheduler.reschedule(context)
                        }
                    }
                )
                SettingSwitchRow(
                    title = "没课时仍然提醒",
                    subtitle = "关闭后，当天没有课程时不发送通知",
                    checked = notifyWithoutCourses,
                    onCheckedChange = { enabled ->
                        notifyWithoutCourses = enabled
                        settings.dailySummaryNotifyWithoutCourses = enabled
                    }
                )
                SettingSwitchRow(
                    title = "免打扰时段",
                    subtitle = "该时段内不发送课程摘要，可跨午夜",
                    checked = quietHoursEnabled,
                    onCheckedChange = { enabled ->
                        quietHoursEnabled = enabled
                        settings.quietHoursEnabled = enabled
                    }
                )
                if (quietHoursEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pickTime(quietStart) { minutes ->
                                    quietStart = minutes
                                    settings.quietHoursStartMinutes = minutes
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(formatMinuteOfDay(quietStart))
                        }
                        Text(
                            text = "至",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                pickTime(quietEnd) { minutes ->
                                    quietEnd = minutes
                                    settings.quietHoursEndMinutes = minutes
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Text(formatMinuteOfDay(quietEnd))
                        }
                    }
                }
                val reminderInQuietHours = quietHoursEnabled &&
                    isWithinQuietHours(summaryMinutes, quietStart, quietEnd)
                Text(
                    text = if (reminderInQuietHours) {
                        "提醒时间落在免打扰时段内，当前不会发送。"
                    } else {
                        "提醒依赖系统闹钟，省电策略下可能延迟几分钟。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reminderInQuietHours) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (!notificationsAllowed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "系统通知权限未开启，提醒无法送达。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = { openNotificationSettings(context) }) {
                        Text("去系统设置开启")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        val sent = DailySummaryNotifier.postTest(context)
                        Toast.makeText(
                            context,
                            if (sent) "已发送测试通知" else "通知权限未开启，无法发送测试通知",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Text("发送测试通知", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ---- 数据同步 ----
        SectionCard(title = "数据同步") {
            SettingSwitchRow(
                title = "回到 App 自动同步本周",
                subtitle = "停在当前周时静默刷新个人课表；查看其他周不会触发",
                checked = autoSyncEnabled,
                onCheckedChange = { enabled ->
                    autoSyncEnabled = enabled
                    settings.autoSyncEnabled = enabled
                }
            )
            if (autoSyncEnabled) {
                SettingSwitchRow(
                    title = "仅在 Wi-Fi 下自动同步",
                    subtitle = "移动网络下不自动请求；手动刷新不受限制",
                    checked = autoSyncWifiOnly,
                    onCheckedChange = { enabled ->
                        autoSyncWifiOnly = enabled
                        settings.autoSyncWifiOnly = enabled
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "当前数据同步：${formatLastSyncTime(lastSyncAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "数据超过 15 分钟才会在回到 App 时自动同步；启动时的正常刷新不受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- 主题 ----
        SectionCard(title = "主题") {
            Text(
                text = "深浅模式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = {
                            Text(mode.label, style = MaterialTheme.typography.labelMedium)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "主题配色",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThemeStyle.entries.forEach { style ->
                    FilterChip(
                        selected = themeStyle == style,
                        onClick = { onThemeStyleChange(style) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(style.preview)
                            )
                        },
                        label = {
                            Text(style.label, style = MaterialTheme.typography.labelMedium)
                        }
                    )
                }
            }
        }

        // ---- 关于 ----
        SectionCard(title = "关于") {
            Text(
                text = "课表查询 v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "课表数据来自学校教务系统，本应用仅作只读查询，与学校无隶属关系。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- 免责声明 ----
        SectionCard(title = "免责声明") {
            Text(
                text = "课表仅供参考",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = DISCLAIMER_BODY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- 隐私与许可 ----
        SectionCard(title = "隐私与许可") {
            Text(
                text = "隐私：账号密码只在教务系统页面内输入，App 不保存密码；" +
                    "登录会话加密保存在本机，课表缓存只留本机，不上传第三方。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "开源组件：AndroidX / Kotlin（Apache 2.0）、OkHttp（Apache 2.0）、" +
                    "Jsoup（MIT）。完整许可见各组件仓库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            val clipboard = LocalClipboardManager.current
            var copied by remember { mutableStateOf(false) }
            OutlinedButton(onClick = {
                clipboard.setText(
                    AnnotatedString(
                        buildString {
                            append("课表查询 v").append(BuildConfig.VERSION_NAME)
                            append("\nAndroid ").append(Build.VERSION.RELEASE)
                            append(" (API ").append(Build.VERSION.SDK_INT).append(')')
                            append("\n机型：").append(Build.MANUFACTURER).append(' ')
                            append(Build.MODEL)
                        }
                    )
                )
                copied = true
            }) {
                Text(if (copied) "已复制诊断信息" else "复制诊断信息（反馈时附上）")
            }
        }

        // ---- 支持作者 ----
        SectionCard(title = "支持作者") {
            SponsorEntry(onClick = { showSponsor = true })
        }

        // ---- 作息时间表 ----
        SectionCard(title = "作息时间表") {
            Text(
                text = "当前生效：${active?.name ?: "无"}。同一门课在不同时间表下的上下课时间不同，可切换或自定义。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            timetables.forEach { timetable ->
                TimetableRow(
                    timetable = timetable,
                    active = timetable.id == activeId,
                    expanded = expandedId == timetable.id,
                    canDelete = timetables.size > 1,
                    onActivate = {
                        activeId = timetable.id
                        settings.activeTimetableId = timetable.id
                        onChanged()
                    },
                    onToggleExpand = {
                        expandedId = if (expandedId == timetable.id) null else timetable.id
                    },
                    onRename = { newName ->
                        val updated = timetable.copy(name = newName)
                        timetables = timetables.map { if (it.id == updated.id) updated else it }
                        settings.timetables = timetables
                        onChanged()
                    },
                    onPeriodChange = { index, period ->
                        val updatedPeriods = timetable.periods.toMutableList()
                        updatedPeriods[index] = period
                        val updated = timetable.copy(periods = updatedPeriods)
                        timetables = timetables.map { if (it.id == updated.id) updated else it }
                        settings.timetables = timetables
                        onChanged()
                    },
                    onDelete = {
                        val remaining = timetables.filter { it.id != timetable.id }
                        timetables = remaining
                        settings.timetables = remaining
                        if (activeId == timetable.id) {
                            activeId = remaining.first().id
                            settings.activeTimetableId = activeId
                        }
                        if (expandedId == timetable.id) expandedId = null
                        onChanged()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    val newId = settings.newTimetableId()
                    var index = 1
                    while (timetables.any { it.name == "自定义时间表 $index" }) index++
                    val cloned = ClassTimetable(
                        id = newId,
                        name = "自定义时间表 $index",
                        periods = (timetables.firstOrNull { it.id == activeId }
                            ?: timetables.first()).periods
                    )
                    timetables = timetables + cloned
                    settings.timetables = timetables
                    activeId = newId
                    settings.activeTimetableId = newId
                    expandedId = newId
                    onChanged()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加时间表", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        SectionCard(title = "本地数据") {
            Text(
                text = "清除缓存的课表、学期与教室列表；不会退出登录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showClearConfirm = true }) {
                Text("清除本地缓存")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除本地缓存？") },
            text = { Text("将删除本机保存的课表、学期与教室列表缓存，下次查询需重新联网。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearLocalData()
                }) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showSponsor) {
        SponsorDialog(onDismiss = { showSponsor = false })
    }

    if (showNotificationDenied) {
        AlertDialog(
            onDismissRequest = { showNotificationDenied = false },
            title = { Text("需要通知权限") },
            text = {
                Text("每日课程摘要需要系统通知权限才能送达。可在系统设置中为本应用开启通知。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationDenied = false
                    openNotificationSettings(context)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDenied = false }) { Text("取消") }
            }
        )
    }

    if (showWeekPicker) {
        // 用户只选"今天是第几周"，由 App 反推第一周周一
        var selectedWeek by remember { mutableStateOf(currentWeek ?: 1) }
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = { Text("该学期第几周？（按今天反推）") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "第 $selectedWeek 周",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 5
                    ) {
                        for (week in 1..30) {
                            FilterChip(
                                selected = week == selectedWeek,
                                onClick = { selectedWeek = week },
                                label = {
                                    Text("$week", style = MaterialTheme.typography.labelMedium)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val date = semesterStartForWeek(selectedWeek, LocalDate.now())
                    startDate = date
                    settings.setSemesterStartDateFor(semesterId, date)
                    showWeekPicker = false
                    onChanged()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showWeekPicker = false }) { Text("取消") }
            }
        )
    }

    if (showStartDateEditor) {
        // 知道准确开学日期的用户可以直接输入，不必按“今天第几周”反推
        var dateText by rememberSaveable(startDate) { mutableStateOf(startDate) }
        var dateError by rememberSaveable { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showStartDateEditor = false },
            title = { Text("第一周周一") },
            text = {
                Column {
                    Text(
                        text = "输入第一周周一的日期，App 会据此推算今天属于第几周。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {
                            dateText = it
                            dateError = null
                        },
                        singleLine = true,
                        isError = dateError != null,
                        label = { Text("日期") },
                        placeholder = { Text("例如 2026-09-07") },
                        supportingText = dateError?.let { message ->
                            { Text(message) }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    if (parsed == null) {
                        dateError = "日期格式应为 yyyy-MM-dd，例如 2026-09-07"
                    } else {
                        val value = parsed.toString()
                        startDate = value
                        settings.setSemesterStartDateFor(semesterId, value)
                        showStartDateEditor = false
                        onChanged()
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDateEditor = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingClickRow(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatMinuteOfDay(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

private fun formatLastSyncTime(cachedAtMillis: Long): String {
    if (cachedAtMillis <= 0L) return "暂无记录"
    val time = Instant.ofEpochMilli(cachedAtMillis).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val day = when (time.toLocalDate()) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> time.format(DateTimeFormatter.ofPattern("MM-dd"))
    }
    return "$day ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun TimetableRow(
    timetable: ClassTimetable,
    active: Boolean,
    expanded: Boolean,
    canDelete: Boolean,
    onActivate: () -> Unit,
    onToggleExpand: () -> Unit,
    onRename: (String) -> Unit,
    onPeriodChange: (Int, PeriodTime) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = active, onClick = onActivate)
        Text(
            text = timetable.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .clickable { onActivate() }
        )
        IconButton(onClick = onToggleExpand) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起" else "编辑"
            )
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (expanded) {
        TimetableEditor(
            timetable = timetable,
            onRename = onRename,
            onPeriodChange = onPeriodChange
        )
    }
}

/** 时间表编辑器：名称 + 12 节的上下课时间。 */
@Composable
private fun TimetableEditor(
    timetable: ClassTimetable,
    onRename: (String) -> Unit,
    onPeriodChange: (Int, PeriodTime) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 名称先写本地草稿，失焦/回车才提交，避免逐字符落盘
        var nameText by remember(timetable.id) { mutableStateOf(timetable.name) }
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = { Text("时间表名称") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    if (nameText != timetable.name) onRename(nameText)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (!focus.isFocused && nameText != timetable.name) {
                        onRename(nameText)
                    }
                }
        )
        timetable.periods.forEachIndexed { index, period ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第${index + 1}节",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(46.dp)
                )
                TimeField(
                    value = period.start,
                    label = "上课",
                    onCommit = { onPeriodChange(index, period.copy(start = it)) }
                )
                Text(text = "—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
                TimeField(
                    value = period.end,
                    label = "下课",
                    onCommit = { onPeriodChange(index, period.copy(end = it)) }
                )
            }
        }
    }
}

/** "HH:mm" 时间输入框：本地草稿，失焦/回车时才校验并提交。 */
@Composable
private fun TimeField(
    value: String,
    label: String,
    onCommit: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun commit() {
        if (TIME_PATTERN.matches(text)) {
            if (text != value) onCommit(text)
        } else {
            // 非法输入不落盘：失焦/完成时退回上一次有效时间
            text = value
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            // 逐字符只更新草稿，不触发设置落盘
            text = input.filter { it.isDigit() || it == ':' }.take(5)
        },
        label = { Text(label) },
        singleLine = true,
        isError = text.isNotEmpty() && !TIME_PATTERN.matches(text),
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Done
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = {
                keyboard?.hide()
                commit()
            }
        ),
        modifier = Modifier
            .width(96.dp)
            .onFocusChanged { focus ->
                if (!focus.isFocused) commit()
            }
    )
}

private val TIME_PATTERN = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
