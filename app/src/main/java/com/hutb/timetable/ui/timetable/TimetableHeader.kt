package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.RoomSortMode

/**
 * 头部工具行：模式切换 + 筛选入口 + 低频工具。
 *
 * 头部总共两层：TopAppBar（标题/学期周次/教室条件）+ 本行。
 * 原先的模式条、周次条、教室条件条、空教室工具条、筛选条（共 6 层）
 * 全部收进这里与 AppBar，课表本体可多露出 3~4 行。
 */
@Composable
internal fun HeaderBars(
    state: TimetableUiState,
    emptyRoomsOnly: Boolean,
    emptyRoomsEnabled: Boolean = true,
    filterSummary: String?,
    onSwitchQueryMode: (QueryMode) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onToggleEmptyOnly: () -> Unit,
    onToggleShortNames: () -> Unit,
    onSelectSortMode: (RoomSortMode) -> Unit,
    listMode: Boolean = false,
    onToggleListMode: () -> Unit = {},
    dayView: Boolean = false,
    onToggleDayView: () -> Unit = {},
    onToggleShowWeekend: () -> Unit = {},
    onToggleHideEmptyPeriods: () -> Unit = {}
) {
    var showTools by remember { mutableStateOf(false) }
    val filterActive = state.schedule.filter.isActive

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .height(34.dp)
                .weight(1f)
        ) {
            QueryMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.schedule.queryMode == mode,
                    onClick = { onSwitchQueryMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = QueryMode.entries.size
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .weight(1f),
                    icon = {}
                ) {
                    Text(
                        text = if (mode == QueryMode.PERSONAL) "我的课表" else "教室课表",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }
        }

        if (state.schedule.queryMode == QueryMode.PERSONAL) {
            IconButton(onClick = onToggleDayView) {
                Icon(
                    imageVector = if (dayView) Icons.Default.ViewWeek else Icons.Default.ViewDay,
                    contentDescription = if (dayView) "切换到周视图" else "切换到日视图",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onOpenFilterSheet) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = if (filterActive) "筛选（已启用）" else "筛选",
                modifier = Modifier.size(20.dp),
                tint = if (filterActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Box {
            IconButton(onClick = { showTools = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多工具")
            }
            DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }) {
                if (state.schedule.queryMode == QueryMode.ROOM && emptyRoomsEnabled) {
                    DropdownMenuItem(
                        text = { Text(if (emptyRoomsOnly) "✓ 只看空教室" else "只看空教室") },
                        onClick = {
                            showTools = false
                            onToggleEmptyOnly()
                        }
                    )
                }
                if (state.schedule.queryMode == QueryMode.ROOM) {
                    DropdownMenuItem(
                        text = { Text(if (listMode) "✓ 列表视图" else "列表视图（读屏友好）") },
                        onClick = {
                            showTools = false
                            onToggleListMode()
                        }
                    )
                }
                if (state.schedule.queryMode == QueryMode.PERSONAL && !dayView) {
                    DropdownMenuItem(
                        text = {
                            Text(if (state.showWeekend) "隐藏周末" else "✓ 隐藏周末")
                        },
                        onClick = {
                            showTools = false
                            onToggleShowWeekend()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (state.hideEmptyPeriods) {
                                    "✓ 隐藏无课时段"
                                } else {
                                    "隐藏无课时段"
                                }
                            )
                        },
                        onClick = {
                            showTools = false
                            onToggleHideEmptyPeriods()
                        }
                    )
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text(if (state.shortRoomNames) "✓ 教室名简短" else "教室名完整") },
                    onClick = {
                        showTools = false
                        onToggleShortNames()
                    }
                )
                HorizontalDivider()
                RoomSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (mode == state.roomSortMode) "✓ ${mode.label()}" else mode.label()
                            )
                        },
                        onClick = {
                            showTools = false
                            onSelectSortMode(mode)
                        }
                    )
                }
            }
        }
    }

    // 筛选摘要只作为无障碍/辅助信息，真正内容在 BottomSheet 里
    if (filterActive && !filterSummary.isNullOrBlank()) {
        Text(
            text = filterSummary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
    }
}

/** 排序方式的显示名。 */
private fun RoomSortMode.label(): String = when (this) {
    RoomSortMode.BY_BUILDING -> "排序：楼栋优先"
    RoomSortMode.BY_NUMBER -> "排序：编号优先"
    RoomSortMode.BY_FLOOR -> "排序：楼层优先"
}

/**
 * 学期下拉菜单。
 */
@Composable
internal fun SemesterMenu(
    expanded: Boolean,
    semesters: List<com.hutb.timetable.core.Semester>,
    currentSemesterId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (semesters.isEmpty()) {
            DropdownMenuItem(
                text = { Text("暂无学期数据") },
                onClick = onDismiss
            )
        }
        semesters.forEach { semester ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = semester.name,
                        style = if (semester.id == currentSemesterId) {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium
                        }
                    )
                },
                onClick = {
                    onDismiss()
                    onSelect(semester.id)
                }
            )
        }
    }
}
