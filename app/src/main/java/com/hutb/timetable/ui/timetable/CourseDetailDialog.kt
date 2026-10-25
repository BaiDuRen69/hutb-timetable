package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.Course
import com.hutb.timetable.data.local.ClassTimetable

/**
 * 课程详情弹窗。
 *
 * 展示：课程名、任课教师、班级、教室位置、时间节次、授课周次、课程性质。
 */
@Composable
fun CourseDetailDialog(
    course: Course,
    onDismiss: () -> Unit,
    timetable: ClassTimetable? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val palette = coursePalette(course.courseName)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 延续课程卡的色条，弹窗与网格颜色对得上
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 22.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(palette.accent)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = course.courseName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow("任课教师", course.teacher.ifBlank { "—" })
                DetailRow("上课班级", course.className.ifBlank { "—" })
                DetailRow("教室位置", course.classroom.ifBlank { "—" })
                DetailRow(
                    "上课时间",
                    "${weekdayText(course.weekday)} ${periodText(course.startPeriod, course.endPeriod, timetable)}"
                )
                DetailRow(
                    "授课周次",
                    course.weekText.ifBlank {
                        if (course.weeks.isNotEmpty()) {
                            course.weeks.joinToString(",") + " 周"
                        } else "—"
                    }
                )
                if (course.courseType.isNotBlank()) {
                    DetailRow("课程性质", course.courseType)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun weekdayText(weekday: Int): String = when (weekday) {
    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
    5 -> "周五"; 6 -> "周六"; 7 -> "周日"
    else -> ""
}

/**
 * 节次文案。若命中标准作息表则显示时间区间，否则仅显示节次。
 */
private fun periodText(start: Int, end: Int, timetable: ClassTimetable?): String {
    val slot = PERIOD_SLOTS.firstOrNull { it.start == start && it.end == end }
    if (slot != null) {
        val time = if (timetable != null) {
            val first = timetable.periods.getOrNull(start - 1)?.start
            val last = timetable.periods.getOrNull(end - 1)?.end
            if (first != null && last != null) "$first-$last" else slot.timeLabel
        } else {
            slot.timeLabel
        }
        return "${slot.label}（$time）"
    }
    if (start == end) return "第 $start 节"
    return "第 $start-$end 节"
}
