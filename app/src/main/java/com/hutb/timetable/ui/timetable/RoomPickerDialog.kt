package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.RoomOption
import com.hutb.timetable.core.isOpaqueRoomName

/**
 * 教室选择面板（草稿态）。
 *
 * 三级结构：校区（`xqid`）→ 教学楼（`jzwid`）→ 教室。
 * 对话框内的选择只写本地草稿，浏览选项通过 [onPreviewCampus]/[onPreviewBuilding]
 * 加载对应的教学楼/教室列表；点"确定"才一次性提交，点"取消"零状态变更、零请求。
 *
 * 用 ModalBottomSheet 而不是 AlertDialog：360dp 宽的弹窗里塞三级级联会把
 * 教室列表压到 190dp，BottomSheet 可以占满宽度并把列表拉到 240dp。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomPickerDialog(
    campuses: List<RoomOption>,
    buildings: List<RoomOption>,
    classrooms: List<RoomOption>,
    initialCampusId: String?,
    initialBuildingId: String?,
    initialRoomCode: String?,
    buildingsLoading: Boolean,
    classroomsLoading: Boolean,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit,
    onPreviewCampus: (String) -> Unit,
    onPreviewBuilding: (campusId: String, buildingId: String, buildingName: String?) -> Unit,
    onConfirm: (campusId: String?, buildingId: String?, buildingName: String?, roomCode: String?) -> Unit
) {
    var campusId by remember { mutableStateOf(initialCampusId) }
    var buildingId by remember { mutableStateOf(initialBuildingId) }
    var roomCode by remember { mutableStateOf(initialRoomCode) }
    var keyword by remember { mutableStateOf("") }

    val filteredRooms = remember(classrooms, keyword) {
        if (keyword.isBlank()) {
            classrooms
        } else {
            classrooms.filter {
                it.name.contains(keyword, ignoreCase = true) ||
                    it.code.contains(keyword, ignoreCase = true)
            }
        }
    }
    val selectedBuildingName = buildings.firstOrNull { it.code == buildingId }?.name

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "选择校区 / 教学楼 / 教室",
                style = MaterialTheme.typography.titleMedium
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetry) {
                        Text("重试", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionLabel("校区")
            OptionChips(
                options = campuses,
                selectedCode = campusId,
                onSelect = { option ->
                    campusId = option.code
                    buildingId = null
                    roomCode = null
                    onPreviewCampus(option.code)
                }
            )

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            SectionLabel("教学楼")
            when {
                buildingsLoading -> LoadingBox(40.dp)
                buildings.isEmpty() -> HintText("未获取到教学楼，请检查网络后重试")
                else -> OptionChips(
                    options = buildings,
                    selectedCode = buildingId,
                    onSelect = { option ->
                        buildingId = option.code
                        roomCode = null
                        onPreviewBuilding(campusId.orEmpty(), option.code, option.name)
                    }
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))

            SectionLabel("教室")
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text("输入教室名或场地代码", style = MaterialTheme.typography.bodyMedium)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
            Spacer(Modifier.height(6.dp))

            when {
                classroomsLoading -> LoadingBox(120.dp)
                buildingId == null -> HintText("请先选择教学楼")
                classrooms.isEmpty() -> HintText("该教学楼下暂无可选教室")
                else -> {
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        item {
                            RoomRow(
                                text = "全部教室（不指定）",
                                selected = roomCode == null,
                                onClick = { roomCode = null }
                            )
                        }
                        items(filteredRooms, key = { it.code }) { option ->
                            RoomRow(
                                text = displayRoom(option),
                                selected = roomCode == option.code,
                                onClick = { roomCode = option.code }
                            )
                        }
                        if (filteredRooms.isEmpty()) {
                            item {
                                Text(
                                    text = "没有匹配的教室",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = { onConfirm(campusId, buildingId, selectedBuildingName, roomCode) },
                    enabled = campusId != null && buildingId != null,
                    modifier = Modifier.weight(1f)
                ) { Text("确定") }
            }
        }
    }
}

@Composable
private fun LoadingBox(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

/**
 * 教室条目文案。
 *
 * 名称与编码相同时（从课表反推的教室就是这种情况）只显示一次；
 * 编码是哈希形式的系统内部码时也不展示，避免"乱码"。
 */
private fun displayRoom(option: RoomOption): String = when {
    option.name == option.code -> option.name
    isOpaqueRoomName(option.code) -> option.name
    else -> "${option.name}（${option.code}）"
}

/** 分区标题。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))
}

/** 灰色提示文案。 */
@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 单选胶囊条（标准 FilterChip，自动换行）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionChips(
    options: List<RoomOption>,
    selectedCode: String?,
    onSelect: (RoomOption) -> Unit
) {
    if (options.isEmpty()) {
        HintText("暂无数据")
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selectedCode == option.code,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        text = option.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

/**
 * 教室单选行。
 */
@Composable
private fun RoomRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
