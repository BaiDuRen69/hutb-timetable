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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.SlotCoverage
import com.hutb.timetable.core.slotCoverage
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * 节次定义。
 *
 * 湖南工商大学作息（据研究生院公告）：
 * 上午 1-4 节 8:00-11:45，下午 5-8 节 14:30-17:55，晚上 9-12 节 19:00-22:15。
 * App 内展示到第 12 节，实际以教务系统返回的节次为准。
 */
data class PeriodSlot(
    val start: Int,
    val end: Int,
    val label: String,
    val timeLabel: String
) {
    /** 列头用的短标签，如「0102」，与教务系统课表表头一致。 */
    val shortLabel: String get() = "%02d%02d".format(start, end)
}

val PERIOD_SLOTS = listOf(
    PeriodSlot(1, 2, "第1-2节", "08:00-09:35"),
    PeriodSlot(3, 4, "第3-4节", "10:00-11:35"),
    PeriodSlot(5, 6, "第5-6节", "14:30-16:05"),
    PeriodSlot(7, 8, "第7-8节", "16:15-17:55"),
    PeriodSlot(9, 10, "第9-10节", "19:00-20:35"),
    PeriodSlot(11, 12, "第11-12节", "20:45-22:15")
)

/** 星期表头，下标 +1 即星期几。 */
val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 周视图课表网格。
 *
 * 布局：左侧"节次"列固定，右侧 7 天横向滚动；整个表格可随字体放大/横屏纵向滚动。
 * 今天所在列与当前节次用主色高亮，并在网格上画当前时刻线。
 */
@Composable
fun WeekGrid(
    courses: List<Course>,
    filter: ScheduleFilter,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier,
    timetable: ClassTimetable? = null,
    /** 当前显示的是否为真实本周；非本周时不显示"今天/现在" */
    isCurrentWeek: Boolean = true,
    /** 周视图显示周末；若筛选只选了周末则以筛选为准 */
    showWeekend: Boolean = true,
    /** 紧凑周视图：只保留有课的节次 */
    hideEmptyPeriods: Boolean = false
) {
    val visibleDays = remember(filter, showWeekend) {
        val base = filter.visibleWeekdays()
        val weekdaysOnly = base.filter { it <= 5 }
        if (showWeekend) base else weekdaysOnly.ifEmpty { base }
    }
    val visibleSlots = remember(filter, hideEmptyPeriods, courses, visibleDays) {
        val base = PERIOD_SLOTS.filter {
            it.start <= filter.endPeriod && it.end >= filter.startPeriod
        }
        if (!hideEmptyPeriods) {
            base
        } else {
            val scoped = filter.apply(courses).filter { it.weekday in visibleDays }
            base.filter { slot ->
                scoped.any { it.startPeriod <= slot.end && it.endPeriod >= slot.start }
            }.ifEmpty { base.take(1) }
        }
    }

    val courseMap = remember(courses, filter, visibleSlots) {
        val map = mutableMapOf<Pair<Int, Int>, MutableList<SlotCourse>>()
        for (course in filter.apply(courses)) {
            // 一门课可能横跨多个节次对（如 1-4 节）：每个覆盖到的格子都要摆，
            // 后续格子标记为"续"，避免第二格空白让人以为课只上一半
            val matched = visibleSlots.filter {
                it.end >= course.startPeriod && it.start <= course.endPeriod
            }
            matched.forEachIndexed { index, slot ->
                map.getOrPut(course.weekday to slot.start) { mutableListOf() }
                    .add(
                        SlotCourse(
                            course = course,
                            continued = index > 0,
                            coverage = slotCoverage(
                                courseStart = course.startPeriod,
                                courseEnd = course.endPeriod,
                                slotStart = slot.start,
                                slotEnd = slot.end
                            )
                        )
                    )
            }
        }
        map
    }

    // 每分钟刷新：跨过上课时间点、跨午夜后"现在/今天"能自己更新
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000)
        }
    }
    val today = now.dayOfWeek.value
    val nowMinutes = now.hour * 60 + now.minute
    val currentSlotIndex = if (isCurrentWeek) {
        visibleSlots.indexOfFirst { slotRangeMinutes(timetable, it)?.contains(nowMinutes) == true }
    } else {
        -1
    }

    val isDark = LocalIsDarkTheme.current
    val todayTint = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.16f else 0.10f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary

    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceIn(1f, 1.6f)
    val headerHeight = (44 * minOf(fontScale, 1.4f)).dp
    val periodHeight = (64 * fontScale).dp
    val dayColumnWidth = (116 * minOf(fontScale, 1.4f)).dp
    val periodColumnWidth = (78 * minOf(fontScale, 1.4f)).dp
    val gridWidth = dayColumnWidth * visibleDays.size
    val gridWidthPx = with(density) { gridWidth.toPx() }
    val verticalScroll = rememberScrollState()

    // 横向滚动只挂一个手势节点，所有列用 graphicsLayer 平移：
    // 原先每个横向滚动容器都会在拖动时重新测量整张网格，大字体下明显掉帧。
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

    // 首次进入（或切回本周）自动横向滚动到今天
    LaunchedEffect(isCurrentWeek, today, visibleDays, maxOffsetPx) {
        if (isCurrentWeek) {
            val index = visibleDays.indexOf(today)
            if (index > 0) {
                horizontalOffset = with(density) { (dayColumnWidth * index).toPx() }
                    .coerceAtMost(maxOffsetPx)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(verticalScroll)
    ) {
        // 左侧节次列固定，不随右侧横向滚动
        Column(modifier = Modifier.width(periodColumnWidth)) {
            PeriodHeaderCell(
                width = periodColumnWidth,
                height = headerHeight,
                outline = outline
            )
            visibleSlots.forEachIndexed { index, slot ->
                PeriodCell(
                    slot = slot,
                    timeLabel = slotTimeLabel(timetable, slot),
                    width = periodColumnWidth,
                    height = periodHeight,
                    isNow = index == currentSlotIndex,
                    outline = outline
                )
            }
        }

        // 右侧 7 天网格：手势在视口上，内容整体位移
        Column(
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { viewportWidthPx = it.width }
                .scrollable(
                    state = horizontalState,
                    orientation = Orientation.Horizontal,
                    flingBehavior = ScrollableDefaults.flingBehavior()
                )
                .clipToBounds()
        ) {
            Box {
                StartAlignedContent(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .requiredWidth(gridWidth)
                            .graphicsLayer { translationX = -horizontalOffset }
                    ) {
                        Row {
                            visibleDays.forEach { weekday ->
                                DayHeaderCell(
                                    text = WEEKDAY_NAMES[weekday - 1],
                                    width = dayColumnWidth,
                                    height = headerHeight,
                                    isToday = isCurrentWeek && weekday == today,
                                    outline = outline
                                )
                            }
                        }
                        visibleSlots.forEach { slot ->
                            Row {
                                visibleDays.forEach { weekday ->
                                    DayCell(
                                        courses = courseMap[weekday to slot.start].orEmpty(),
                                        width = dayColumnWidth,
                                        height = periodHeight,
                                        isToday = isCurrentWeek && weekday == today,
                                        surfaceColor = surfaceColor,
                                        todayTint = todayTint,
                                        outline = outline,
                                        onCourseClick = onCourseClick
                                    )
                                }
                            }
                        }
                    }
                }

                // 当前时刻线：画在视口层，不随横向位移重复裁剪
                val currentRange = visibleSlots.getOrNull(currentSlotIndex)
                    ?.let { slotRangeMinutes(timetable, it) }
                if (currentSlotIndex >= 0 && currentRange != null) {
                    val fraction = ((nowMinutes - currentRange.first).toFloat() /
                        (currentRange.last - currentRange.first).coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                    val y = headerHeight + periodHeight * currentSlotIndex + periodHeight * fraction
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBehind {
                                drawLine(
                                    color = primary,
                                    start = Offset(0f, y.toPx()),
                                    end = Offset(size.width, y.toPx()),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawCircle(
                                    color = primary,
                                    radius = 3.dp.toPx(),
                                    center = Offset(4.dp.toPx(), y.toPx())
                                )
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodHeaderCell(width: Dp, height: Dp, outline: Color) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .gridCellLines(outline),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "节次",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayHeaderCell(
    text: String,
    width: Dp,
    height: Dp,
    isToday: Boolean,
    outline: Color
) {
    val background = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isToday) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(background)
            .gridCellLines(outline),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (isToday) {
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun PeriodCell(
    slot: PeriodSlot,
    timeLabel: String,
    width: Dp,
    height: Dp,
    isNow: Boolean,
    outline: Color
) {
    val background = if (isNow) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isNow) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(background)
            .gridCellLines(outline),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = slot.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (isNow) {
                Text(
                    text = "现在",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    courses: List<SlotCourse>,
    width: Dp,
    height: Dp,
    isToday: Boolean,
    surfaceColor: Color,
    todayTint: Color,
    outline: Color,
    onCourseClick: (Course) -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(surfaceColor)
            .then(if (isToday) Modifier.background(todayTint) else Modifier)
            .gridCellLines(outline)
            .padding(1.5.dp)
    ) {
        if (courses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize())
        } else if (courses.all { it.coverage == SlotCoverage.FULL }) {
            Column(modifier = Modifier.fillMaxSize()) {
                courses.forEach { item ->
                    CourseCard(
                        course = item.course,
                        onClick = { onCourseClick(item.course) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        continued = item.continued
                    )
                }
            }
        } else {
            // 课程只占半格时按上/下半格对齐，避免把 1-3 节画成 1-4 节
            Box(modifier = Modifier.fillMaxSize()) {
                courses.forEach { item ->
                    val fraction = if (item.coverage == SlotCoverage.FULL) 1f else 0.5f
                    val alignment = when (item.coverage) {
                        SlotCoverage.BOTTOM_HALF -> Alignment.BottomCenter
                        SlotCoverage.TOP_HALF -> Alignment.TopCenter
                        else -> Alignment.Center
                    }
                    CourseCard(
                        course = item.course,
                        onClick = { onCourseClick(item.course) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .align(alignment),
                        continued = item.continued,
                        compact = item.coverage != SlotCoverage.FULL
                    )
                }
            }
        }
    }
}

/** 网格里的课程 + 它是该课程的第几段（第二段起显示"续"） + 在该格子里的实际占用。 */
private data class SlotCourse(
    val course: Course,
    val continued: Boolean,
    val coverage: SlotCoverage
)

/**
 * 只画右边和下边的网格线：相邻格的共享边不会叠成双线。
 */
private fun Modifier.gridCellLines(outline: Color): Modifier = drawBehind {
    val stroke = 0.5.dp.toPx()
    drawLine(outline, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = stroke)
    drawLine(outline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = stroke)
}

/**
 * 课程卡片：左侧色条 + 两行文字。
 */
@Composable
fun CourseCard(
    course: Course,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 连堂课的第二段起：显示"续"而不是重复一遍完整信息 */
    continued: Boolean = false,
    /** 半格卡片：只显示一行，给准确占位让路 */
    compact: Boolean = false
) {
    val palette = coursePalette(course.courseName)
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 24.dp else 40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(palette.background)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(palette.accent)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp, vertical = if (compact) 1.dp else 3.dp)
        ) {
            Text(
                text = if (continued) "续 · ${course.courseName}" else course.courseName,
                style = if (compact) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                val meta = listOf(course.classroom, course.teacher)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.text.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 节次时间文案（优先用当前作息表）。 */
internal fun slotTimeLabel(timetable: ClassTimetable?, slot: PeriodSlot): String {
    if (timetable == null) return slot.timeLabel
    val first = timetable.periods.getOrNull(slot.start - 1) ?: return slot.timeLabel
    val last = timetable.periods.getOrNull(slot.end - 1) ?: first
    return "${first.start}-${last.end}"
}

/** 节次对应的分钟区间，用于"当前节次"判定。 */
internal fun slotRangeMinutes(timetable: ClassTimetable?, slot: PeriodSlot): IntRange? {
    val startText: String
    val endText: String
    if (timetable != null) {
        startText = timetable.periods.getOrNull(slot.start - 1)?.start ?: return null
        endText = timetable.periods.getOrNull(slot.end - 1)?.end ?: return null
    } else {
        val parts = slot.timeLabel.split("-")
        if (parts.size != 2) return null
        startText = parts[0]
        endText = parts[1]
    }
    val start = parseMinutes(startText) ?: return null
    val end = parseMinutes(endText) ?: return null
    return start..end
}

private fun parseMinutes(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return hour * 60 + minute
}
