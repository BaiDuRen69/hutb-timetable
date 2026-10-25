package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.roomIdentity
import com.hutb.timetable.core.shortRoomName
import com.hutb.timetable.core.sortRoomLabels

/**
 * 教室课表矩阵。
 *
 * 行是教室，列是 星期 × 节次对；没课的教室也保留一行（来自教学楼教室清单），
 * 便于一眼看出"这间教室当前时段空着"。
 *
 * 性能：一行不是 42 个格子节点，而是单个 Box——网格线用 drawBehind 画，
 * 只为有课的节次摆放卡片。整栋楼约 250 个节点，切换筛选时不至于卡顿。
 *
 * 本组件不含纵向滚动：纵向滚动由页面级容器持有（头部与矩阵一起滚动），
 * 横向滚动只在右侧节次网格内。
 */
@Composable
fun RoomMatrix(
    courses: List<Course>,
    filter: ScheduleFilter,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
    shortNames: Boolean = false,
    buildingName: String? = null,
    sortMode: RoomSortMode = RoomSortMode.BY_BUILDING,
    /** 教学楼的全部教室清单；没课的教室也会占一行 */
    allRooms: List<String> = emptyList()
) {
    // 节次没解析出来时按第 1 节起算，否则课程会整格漏掉
    val normalized = remember(courses) {
        courses.map { course ->
            val start = course.startPeriod.coerceAtLeast(1)
            course.copy(startPeriod = start, endPeriod = course.endPeriod.coerceAtLeast(start))
        }
    }
    // 只留下筛选范围内的课程
    val visible = remember(normalized, filter) { filter.apply(normalized) }
    val visibleDays = remember(filter) { filter.visibleWeekdays() }
    val visibleSlots = remember(filter) {
        PERIOD_SLOTS.filter { it.start <= filter.endPeriod && it.end >= filter.startPeriod }
    }
    // 行 = 有课的教室 ∪ 教学楼教室清单：没课的教室也占一行，
    // 两个来源的注记写法可能不同（*(智慧) vs *（智慧）），按 roomIdentity 合并。
    val rows = remember(visible, allRooms, sortMode) {
        val labels = LinkedHashMap<String, String>()
        val coursesByKey = LinkedHashMap<String, MutableList<Course>>()
        visible.forEach { course ->
            val raw = course.classroom.trim().ifEmpty { UNNAMED_ROOM }
            val key = roomIdentity(raw)
            labels.putIfAbsent(key, raw)
            coursesByKey.getOrPut(key) { mutableListOf() }.add(course)
        }
        allRooms.forEach { raw ->
            val label = raw.trim()
            if (label.isEmpty()) return@forEach
            val key = roomIdentity(label)
            labels.putIfAbsent(key, label)
            coursesByKey.getOrPut(key) { mutableListOf() }
        }
        sortRoomLabels(labels.keys.toList(), sortMode).map { key ->
            RoomRowData(labels.getValue(key), coursesByKey.getValue(key))
        }
    }

    // 教室名列宽按最长显示名自适应；测量与渲染必须共用同一个 TextStyle
    val roomTextStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val displayNames = remember(rows, shortNames, buildingName) {
        rows.mapTo(mutableSetOf()) {
            if (shortNames) shortRoomName(it.label, buildingName) else it.label
        }
    }
    val roomColumnWidth = remember(displayNames, density) {
        val maxPx = displayNames.maxOfOrNull { name ->
            textMeasurer.measure(
                text = name,
                style = roomTextStyle,
                maxLines = 1
            ).size.width
        } ?: 0
        ((maxPx / density.density).dp + 12.dp).coerceIn(44.dp, 132.dp)
    }

    val gridLine = MaterialTheme.colorScheme.outlineVariant
    val gridColumns = visibleDays.size * visibleSlots.size
    val gridWidth = SLOT_COLUMN_WIDTH * gridColumns
    val horizontalScroll = rememberScrollState()

    Row(modifier = modifier) {
        // 左边的教室名列固定，不随右侧横向滚动
        Column(modifier = Modifier.width(roomColumnWidth)) {
            MatrixHeaderCell(
                text = "教室",
                width = roomColumnWidth,
                height = DAY_HEADER_HEIGHT + SLOT_HEADER_HEIGHT,
                background = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (row in rows) {
                Box(
                    modifier = Modifier
                        .width(roomColumnWidth)
                        .height(ROW_HEIGHT)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .drawBehind {
                            val stroke = 0.5.dp.toPx()
                            drawLine(
                                gridLine,
                                Offset(0f, size.height),
                                Offset(size.width, size.height),
                                strokeWidth = stroke
                            )
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (shortNames) shortRoomName(row.label, buildingName) else row.label,
                        style = roomTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 右侧的节次网格
        Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
            Row {
                visibleDays.forEach { weekday ->
                    MatrixHeaderCell(
                        text = WEEKDAY_NAMES[weekday - 1],
                        width = SLOT_COLUMN_WIDTH * visibleSlots.size,
                        height = DAY_HEADER_HEIGHT,
                        background = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                visibleDays.forEach { _ ->
                    visibleSlots.forEach { slot ->
                        MatrixHeaderCell(
                            text = slot.shortLabel,
                            width = SLOT_COLUMN_WIDTH,
                            height = SLOT_HEADER_HEIGHT,
                            background = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            for (row in rows) {
                RoomRow(
                    roomCourses = row.courses,
                    visibleDays = visibleDays,
                    visibleSlots = visibleSlots,
                    gridWidth = gridWidth,
                    gridLine = gridLine,
                    onCourseClick = onCourseClick
                )
            }
        }
    }
}

/** 矩阵的一行：教室显示名 + 当前筛选范围内的课程（可能为空）。 */
private data class RoomRowData(
    val label: String,
    val courses: List<Course>
)

/** 一门课在矩阵行里的摆放位置。 */
private data class CoursePlacement(
    val course: Course,
    val colIndex: Int,
    val span: Int
)

/**
 * 矩阵的一行：单 Box 背景 + drawBehind 网格线 + 只摆有课的卡片。
 */
@Composable
private fun RoomRow(
    roomCourses: List<Course>,
    visibleDays: List<Int>,
    visibleSlots: List<PeriodSlot>,
    gridWidth: Dp,
    gridLine: androidx.compose.ui.graphics.Color,
    onCourseClick: (Course) -> Unit
) {
    val placements = remember(roomCourses, visibleDays, visibleSlots) {
        roomCourses.mapNotNull { course ->
            val dayIndex = visibleDays.indexOf(course.weekday)
            if (dayIndex < 0) return@mapNotNull null
            val firstIdx = visibleSlots.indexOfFirst {
                it.end >= course.startPeriod && it.start <= course.endPeriod
            }
            if (firstIdx < 0) return@mapNotNull null
            var span = 1
            for (i in firstIdx + 1 until visibleSlots.size) {
                val slot = visibleSlots[i]
                if (slot.end >= course.startPeriod && slot.start <= course.endPeriod) span++
            }
            CoursePlacement(course, dayIndex * visibleSlots.size + firstIdx, span)
        }
    }

    Box(
        modifier = Modifier
            .width(gridWidth)
            .height(ROW_HEIGHT)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val stroke = 0.5.dp.toPx()
                val columnPx = SLOT_COLUMN_WIDTH.toPx()
                var x = columnPx
                while (x < size.width - 0.5f) {
                    drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                    x += columnPx
                }
                drawLine(gridLine, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = stroke)
            }
    ) {
        placements.forEach { placement ->
            val palette = coursePalette(placement.course.courseName)
            Box(
                modifier = Modifier
                    .offset(x = SLOT_COLUMN_WIDTH * placement.colIndex)
                    .width(SLOT_COLUMN_WIDTH * placement.span.coerceAtLeast(1))
                    .fillMaxHeight()
                    .padding(1.dp)
                    .zIndex(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.small)
                        .background(palette.background)
                        .clickable { onCourseClick(placement.course) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(palette.accent)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = placement.course.courseName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (placement.course.teacher.isNotBlank()) {
                            Text(
                                text = placement.course.teacher,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.text.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixHeaderCell(
    text: String,
    width: Dp,
    height: Dp,
    background: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 教室名缺失时的占位。 */
private const val UNNAMED_ROOM = "未标注教室"

private val SLOT_COLUMN_WIDTH = 96.dp
private val DAY_HEADER_HEIGHT = 30.dp
private val SLOT_HEADER_HEIGHT = 22.dp
private val ROW_HEIGHT = 54.dp
