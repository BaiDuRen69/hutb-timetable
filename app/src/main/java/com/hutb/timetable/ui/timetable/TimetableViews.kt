package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.shortRoomName

/**
 * 空态：图标 + 说明 + 可选操作。
 *
 * 比一行灰字更容易让用户判断"是没数据、没选条件，还是筛选太窄"。
 */
@Composable
internal fun EmptyView(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.EventBusy,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * 空教室列表：当前筛选范围内没有课的教室，按行铺开。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyRoomsView(
    rooms: List<String>,
    shortNames: Boolean,
    buildingName: String?,
    modifier: Modifier = Modifier,
    onRoomClick: (String) -> Unit = {},
    /** 有筛选时提供一键清除；清除后通常回到整楼矩阵 */
    onClearFilter: (() -> Unit)? = null
) {
    if (rooms.isEmpty()) {
        EmptyView(
            text = "当前范围内没有空教室",
            modifier = modifier
        )
        return
    }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "以下教室在当前筛选范围内没有课",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (onClearFilter != null) {
                TextButton(onClick = onClearFilter) {
                    Text("清除筛选", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rooms.forEach { room ->
                SuggestionChip(
                    onClick = { onRoomClick(room) },
                    label = {
                        Text(
                            text = if (shortNames) shortRoomName(room, buildingName) else room,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

/** 未登录但本机有离线课表时的提示条：只提示，不阻断浏览。 */
@Composable
internal fun OfflineCacheBanner(
    week: Int?,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (week != null) {
                "未登录，当前显示第 $week 周的离线课表"
            } else {
                "未登录，当前显示上次登录的离线课表"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onLogin) {
            Text("登录", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 数据仍在屏幕上时的会话失效提示条（不打断浏览）。 */
@Composable
internal fun SessionExpiredBanner(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "登录已失效，当前显示的是本地缓存",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onLogin) {
            Text("重新登录", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun SessionExpiredView(onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "登录已失效",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "请重新登录教务系统后继续查询",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogin) { Text("重新登录") }
    }
}

/**
 * 周次选择对话框（5×5 chip 网格，不再用 25 行竖排按钮）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekPickerDialog(
    currentWeek: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableStateOf(currentWeek) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择周次", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "第 $selected 周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 5
                ) {
                    for (week in 1..30) {
                        FilterChip(
                            selected = week == selected,
                            onClick = { selected = week },
                            label = {
                                Text(
                                    text = "$week",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
