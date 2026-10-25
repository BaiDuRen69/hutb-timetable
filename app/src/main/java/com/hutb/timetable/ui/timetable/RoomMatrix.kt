package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.roomIdentity
import com.hutb.timetable.core.shortRoomName
import com.hutb.timetable.core.sortRoomLabels
import kotlin.math.min

/**
 * 教室课表矩阵。
 *
 * 行是教室，列是 星期 × 节次对；没课的教室也保留一行（来自教学楼教室清单）。
 *
 * 结构：
 * - 表头固定在顶部（教室列 + 星期/节次表头），行列表用 LazyColumn 纵向滚动；
 * - 左侧教室列固定，右侧网格横向滚动，表头与所有行共享同一个 ScrollState；
 * - 行高/列宽/表头高度随系统字体缩放放大，避免大字体裁切。
 *
 * 性能：一行是单个 Box + drawBehind 网格线，只为有课的节次摆卡片。
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
    val normalized = remember(courses) {
        courses.map { course ->
            val start = course.startPeriod.coerceAtLeast(1)
            course.copy(startPeriod = start, endPeriod = course.endPeriod.coerceAtLeast(start))
        }
    }
    val visible = remember(normalized, filter) { filter.apply(normalized) }
    val visibleDays = remember(filter) { filter.visibleWeekdays() }
    val visibleSlots = remember(filter) {
        PERIOD_SLOTS.filter { it.start <= filter.endPeriod && it.end >= filter.startPeriod }
    }
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

    // 大字体下放大行高/表头/列宽，保证 1.5x~2x 字号不裁切
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceIn(1f, 1.6f)
    val rowHeight = (54 * fontScale).dp
    val dayHeaderHeight = (30 * min(fontScale, 1.4f)).dp
    val slotHeaderHeight = (22 * min(fontScale, 1.4f)).dp
    val slotWidth = (96 * min(fontScale, 1.4f)).dp

    // 教室名列宽按最长显示名自适应；测量与渲染共用同一 TextStyle
    val roomTextStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val displayNames = remember(rows, shortNames, buildingName) {
        rows.mapTo(mutableSetOf()) {
            if (shortNames) shortRoomName(it.label, buildingName) else it.label
        }
    }
    val roomColumnWidth = remember(displayNames, density) {
        val maxPx = displayNames.maxOfOrNull { name ->
            textMeasurer.measure(text = name, style = roomTextStyle, maxLines = 1).size.width
        } ?: 0
        val maxWidth = (132 * min(density.fontScale, 1.5f)).dp
        ((maxPx / density.density).dp + 12.dp).coerceIn(44.dp, maxWidth)
    }

    val gridLine = MaterialTheme.colorScheme.outlineVariant
    val gridWidth = slotWidth * (visibleDays.size * visibleSlots.size)
    val gridWidthPx = with(density) { gridWidth.toPx() }

    // 表头与所有行共用同一个横向偏移量，内容用 graphicsLayer 平移。
    // 不再用 horizontalScroll：拖动时它会让每个可见行重新测量 42 个格子，大字体下明显掉帧。
    // 手势节点放在每行网格上（而不是 LazyColumn 外层），否则会抢走纵向滚动。
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    var maxOffsetPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(gridWidthPx, viewportWidthPx) {
        maxOffsetPx = (gridWidthPx - viewportWidthPx).coerceAtLeast(0f)
        horizontalOffset = horizontalOffset.coerceIn(0f, maxOffsetPx)
    }
    val horizontalState = rememberScrollableState { delta ->
        // scrollable 的 delta 与内容位移方向相反：左滑为负，偏移量应增加
        val next = (horizontalOffset - delta).coerceIn(0f, maxOffsetPx)
        val consumed = horizontalOffset - next
        horizontalOffset = next
        consumed
    }

    Column(modifier = modifier) {
        // ---- 固定表头 ----
        Row {
            MatrixHeaderCell(
                text = "教室",
                width = roomColumnWidth,
                height = dayHeaderHeight + slotHeaderHeight,
                background = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .scrollable(
                        state = horizontalState,
                        orientation = Orientation.Horizontal,
                        flingBehavior = ScrollableDefaults.flingBehavior()
                    )
                    .clipToBounds()
            ) {
                StartAlignedContent(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .requiredWidth(gridWidth)
                            .graphicsLayer { translationX = -horizontalOffset }
                    ) {
                        Row {
                            visibleDays.forEach { weekday ->
                                MatrixHeaderCell(
                                    text = WEEKDAY_NAMES[weekday - 1],
                                    width = slotWidth * visibleSlots.size,
                                    height = dayHeaderHeight,
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
                                        width = slotWidth,
                                        height = slotHeaderHeight,
                                        background = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 行（LazyColumn：表头固定，行滚动；横向手势挂在每行网格上） ----
        Box(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { viewportWidthPx = it.width }
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.label }) { row ->
                    Row(modifier = Modifier.height(rowHeight)) {
                        Box(
                            modifier = Modifier
                                .width(roomColumnWidth)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .drawWithCache {
                                    val stroke = 0.5.dp.toPx()
                                    onDrawBehind {
                                        drawLine(
                                            gridLine,
                                            Offset(0f, size.height),
                                            Offset(size.width, size.height),
                                            strokeWidth = stroke
                                        )
                                    }
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .scrollable(
                                    state = horizontalState,
                                    orientation = Orientation.Horizontal,
                                    flingBehavior = ScrollableDefaults.flingBehavior()
                                )
                                .clipToBounds()
                        ) {
                            StartAlignedContent(modifier = Modifier.fillMaxWidth()) {
                                RoomRow(
                                    roomCourses = row.courses,
                                    visibleDays = visibleDays,
                                    visibleSlots = visibleSlots,
                                    rowHeight = rowHeight,
                                    slotWidth = slotWidth,
                                    gridLine = gridLine,
                                    onCourseClick = onCourseClick,
                                    modifier = Modifier
                                        .requiredWidth(gridWidth)
                                        .graphicsLayer { translationX = -horizontalOffset }
                                )
                            }
                        }
                    }
                }
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

@Composable
private fun RoomRow(
    roomCourses: List<Course>,
    visibleDays: List<Int>,
    visibleSlots: List<PeriodSlot>,
    rowHeight: Dp,
    slotWidth: Dp,
    gridLine: androidx.compose.ui.graphics.Color,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
            .height(rowHeight)
            .background(MaterialTheme.colorScheme.surface)
            .drawWithCache {
                val stroke = 0.5.dp.toPx()
                val columnPx = slotWidth.toPx()
                val totalWidth = size.width
                // 网格线位置只随尺寸/列宽变化，缓存起来避免拖动时每帧重算
                val columnXs = buildList {
                    var x = columnPx
                    while (x < totalWidth - 0.5f) {
                        add(x)
                        x += columnPx
                    }
                }
                onDrawBehind {
                    columnXs.forEach { x ->
                        drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
                    }
                    drawLine(
                        gridLine,
                        Offset(0f, size.height),
                        Offset(size.width, size.height),
                        strokeWidth = stroke
                    )
                }
            }
    ) {
        placements.forEach { placement ->
            val palette = coursePalette(placement.course.courseName)
            Box(
                modifier = Modifier
                    .offset(x = slotWidth * placement.colIndex)
                    .width(slotWidth * placement.span.coerceAtLeast(1))
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
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** 教室名缺失时的占位。 */
private const val UNNAMED_ROOM = "未标注教室"
