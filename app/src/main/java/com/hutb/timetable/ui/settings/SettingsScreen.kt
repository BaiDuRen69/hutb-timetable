package com.hutb.timetable.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hutb.timetable.data.local.AppSettings
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.data.local.PeriodTime
import com.hutb.timetable.core.computeCurrentWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 设置页：开学时间（按学期推算当前周次）+ 作息时间表（四套内置 + 自定义）。
 *
 * 开学日期按 [semesterId] 分开存储：不同学期开学日期不同，
 * 全局一个值会把上学期日期套到下学期。
 *
 * 所有修改即时落盘并通过 [onChanged] 通知调用方刷新课表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    semesterId: String,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    /** 凭据是否加密落盘；false 时明确提示用户 */
    secureStorage: Boolean = true,
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
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    val active = timetables.firstOrNull { it.id == activeId }
    val currentWeek = if (startDate.isBlank()) null else computeCurrentWeek(startDate, LocalDate.now())
    // 没有选中学期时无处归属，先禁止编辑，避免把日期存到空键上
    val canEditStartDate = semesterId.isNotBlank()

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

        // ---- 开学时间（按学期） ----
        SectionCard(title = "开学时间（当前学期）") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canEditStartDate) { showDatePicker = true }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !canEditStartDate -> "请先登录并选择学期"
                            startDate.isBlank() -> "未设置（点击选择）"
                            else -> "第一周周一：$startDate"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when {
                            !canEditStartDate -> "每个学期的开学日期独立保存"
                            startDate.isBlank() -> "设置后自动推算当前是第几周"
                            currentWeek != null -> "当前是第 $currentWeek 周"
                            else -> "日期格式有误，请重新选择"
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

    if (showDatePicker) {
        val initialState = runCatching {
            LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialState
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        startDate = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        settings.setSemesterStartDateFor(semesterId, startDate)
                        onChanged()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
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
