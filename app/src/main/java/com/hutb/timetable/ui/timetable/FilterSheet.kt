package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.ScheduleFilter
import kotlin.math.roundToInt

private const val SLOT_COUNT = 6

/** 节次对对应的时间段提示。 */
private fun slotRangeLabel(startPeriod: Int, endPeriod: Int): String = when {
    startPeriod == 1 && endPeriod == 12 -> "全天"
    endPeriod <= 4 -> "上午"
    endPeriod <= 8 -> "下午"
    else -> "晚上"
}

/**
 * 筛选面板（BottomSheet）。
 *
 * 星期用 FilterChip、节次用 RangeSlider，替代原来常驻的两行筛选条：
 * 默认只占一个 chip 的位置，需要时再展开。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: ScheduleFilter,
    onDismiss: () -> Unit,
    onApply: (ScheduleFilter) -> Unit
) {
    var draft by remember { mutableStateOf(filter) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("筛选", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            Text("星期", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = !draft.limitsWeekday,
                    onClick = { draft = draft.copy(weekdays = emptySet()) },
                    label = { Text("全部") }
                )
                WEEKDAY_NAMES.forEachIndexed { index, name ->
                    val weekday = index + 1
                    FilterChip(
                        selected = weekday in draft.weekdays,
                        onClick = { draft = draft.toggleWeekday(weekday) },
                        label = { Text(name) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("节次", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            // 课表按"节次对"渲染（1-2 / 3-4 / …），滑块也按对取值，
            // 否则用户选"第 2-3 节"会看到 1-4 节两整行，粒度对不上。
            val startSlot = ((draft.startPeriod - 1) / 2).coerceIn(0, SLOT_COUNT - 1)
            val endSlot = ((draft.endPeriod - 1) / 2).coerceIn(startSlot, SLOT_COUNT - 1)
            RangeSlider(
                value = startSlot.toFloat()..endSlot.toFloat(),
                onValueChange = { range ->
                    val s = range.start.roundToInt().coerceIn(0, SLOT_COUNT - 1)
                    val e = range.endInclusive.roundToInt().coerceIn(s, SLOT_COUNT - 1)
                    draft = draft.copy(startPeriod = s * 2 + 1, endPeriod = e * 2 + 2)
                },
                valueRange = 0f..(SLOT_COUNT - 1).toFloat(),
                steps = SLOT_COUNT - 2
            )
            Text(
                text = "第 ${draft.startPeriod}–${draft.endPeriod} 节（${slotRangeLabel(draft.startPeriod, draft.endPeriod)}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { draft = ScheduleFilter() },
                    modifier = Modifier.weight(1f)
                ) { Text("重置") }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("应用") }
            }
        }
    }
}
