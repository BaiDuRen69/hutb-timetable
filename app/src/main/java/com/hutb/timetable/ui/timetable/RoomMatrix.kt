package com.hutb.timetable.ui.timetable

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.core.roomIdentity
import com.hutb.timetable.core.shortRoomName
import com.hutb.timetable.core.sortRoomLabels
import com.hutb.timetable.ui.theme.LocalIsDarkTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

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
    allRooms: List<String> = emptyList(),
    /** 当前周次，仅用于无障碍播报 */
    week: Int = 0
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
                    // 用表头右侧网格 Box 的宽度（不含教室名列）计算横向最大偏移
                    .onSizeChanged { viewportWidthPx = it.width }
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
            modifier = Modifier.weight(1f)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.label }) { row ->
                    val canvasData = rememberRoomCanvasData(
                        courses = row.courses,
                        visibleDays = visibleDays,
                        visibleSlots = visibleSlots,
                        slotWidth = slotWidth,
                        rowHeight = rowHeight,
                        week = week,
                        rowLabel = row.label
                    )
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
                        RoomGridCanvas(
                            data = canvasData,
                            horizontalOffset = { horizontalOffset },
                            onCourseClick = onCourseClick,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .scrollable(
                                    state = horizontalState,
                                    orientation = Orientation.Horizontal,
                                    flingBehavior = ScrollableDefaults.flingBehavior()
                                )
                                .clipToBounds()
                        )
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

/** 已排好版、等待 Canvas 绘制的课程卡。文字布局只在本行数据变化时重建。 */
private data class CanvasCourse(
    val course: Course,
    val leftPx: Float,
    val widthPx: Float,
    val background: Color,
    val accent: Color,
    val nameLayout: TextLayoutResult,
    val teacherLayout: TextLayoutResult?,
    val accessibilityLabel: String
)

/** 一行矩阵的绘制参数（全部为像素，避免绘制时反复换算 dp）。 */
private data class RoomCanvasData(
    val courses: List<CanvasCourse>,
    val slotWidthPx: Float,
    val rowHeightPx: Float,
    val columnCount: Int,
    val surface: Color,
    val gridLine: Color,
    val insetPx: Float,
    val accentWidthPx: Float,
    val textPadPx: Float,
    val verticalPadPx: Float,
    val cornerPx: Float
)

@Composable
private fun rememberRoomCanvasData(
    courses: List<Course>,
    visibleDays: List<Int>,
    visibleSlots: List<PeriodSlot>,
    slotWidth: Dp,
    rowHeight: Dp,
    week: Int,
    rowLabel: String
): RoomCanvasData {
    val density = LocalDensity.current
    val dark = LocalIsDarkTheme.current
    val measurer = rememberTextMeasurer()
    val nameStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val teacherStyle = MaterialTheme.typography.labelMedium
    val surface = MaterialTheme.colorScheme.surface
    val gridLine = MaterialTheme.colorScheme.outlineVariant

    val slotWidthPx = with(density) { slotWidth.toPx() }
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val insetPx = with(density) { 1.dp.toPx() }
    val accentWidthPx = with(density) { 3.dp.toPx() }
    val textPadPx = with(density) { 4.dp.toPx() }
    val verticalPadPx = with(density) { 2.dp.toPx() }
    val cornerPx = with(density) { 8.dp.toPx() }

    return remember(
        courses, visibleDays, visibleSlots,
        slotWidthPx, rowHeightPx, dark, nameStyle, teacherStyle,
        surface, gridLine, measurer, density.density, density.fontScale,
        week, rowLabel
    ) {
        val cards = courses.mapNotNull { course ->
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
            val widthPx = span * slotWidthPx - insetPx * 2
            val textWidth = (widthPx - accentWidthPx - textPadPx * 2).roundToInt()
            if (textWidth <= 0) return@mapNotNull null
            val palette = coursePalette(course.courseName, dark)
            val nameLayout = measurer.measure(
                text = AnnotatedString(course.courseName),
                style = nameStyle.copy(color = palette.text),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = textWidth)
            )
            val teacherLayout = course.teacher.trim().takeIf { it.isNotEmpty() }?.let { teacher ->
                measurer.measure(
                    text = AnnotatedString(teacher),
                    style = teacherStyle.copy(color = palette.text.copy(alpha = 0.8f)),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = textWidth)
                )
            }
            val periodText = if (course.startPeriod == course.endPeriod) {
                "第${course.startPeriod}节"
            } else {
                "第${course.startPeriod}-${course.endPeriod}节"
            }
            val accessibilityLabel = listOfNotNull(
                week.takeIf { it > 0 }?.let { "第${it}周" },
                WEEKDAY_NAMES.getOrNull(course.weekday - 1).orEmpty() + periodText,
                course.courseName.takeIf { it.isNotBlank() },
                course.classroom.ifBlank { rowLabel }.takeIf { it.isNotBlank() },
                course.teacher.takeIf { it.isNotBlank() }
            ).joinToString("，")
            CanvasCourse(
                course = course,
                leftPx = (dayIndex * visibleSlots.size + firstIdx) * slotWidthPx + insetPx,
                widthPx = widthPx,
                background = palette.background,
                accent = palette.accent,
                nameLayout = nameLayout,
                teacherLayout = teacherLayout,
                accessibilityLabel = accessibilityLabel
            )
        }
        RoomCanvasData(
            courses = cards,
            slotWidthPx = slotWidthPx,
            rowHeightPx = rowHeightPx,
            columnCount = visibleDays.size * visibleSlots.size,
            surface = surface,
            gridLine = gridLine,
            insetPx = insetPx,
            accentWidthPx = accentWidthPx,
            textPadPx = textPadPx,
            verticalPadPx = verticalPadPx,
            cornerPx = cornerPx
        )
    }
}

/**
 * 用单个 Canvas 绘制一行网格与课程卡。
 *
 * 旧实现每门课都是一组 Compose 节点，纵向滚动时不断组合/布局 Text；
 * 这里每行只保留一个绘制节点，滚动时只做画布绘制，明显更轻。
 * 点击命中通过偏移量反算 Grid 坐标，仍然可以打开课程详情。
 * 开启 TalkBack 时，在可见课程卡上叠加透明语义/点击节点，保证逐张卡可聚焦。
 */
@Composable
private fun RoomGridCanvas(
    data: RoomCanvasData,
    horizontalOffset: () -> Float,
    onCourseClick: (Course) -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibilityEnabled = rememberAccessibilityOverlayEnabled()
    val density = LocalDensity.current
    var viewportWidthPx by remember { mutableIntStateOf(0) }

    Box(modifier = modifier.onSizeChanged { viewportWidthPx = it.width }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (accessibilityEnabled) {
                        Modifier
                    } else {
                        Modifier.pointerInput(data) {
                            detectTapGestures { position ->
                                val x = position.x + horizontalOffset()
                                data.courses
                                    .lastOrNull { x >= it.leftPx && x <= it.leftPx + it.widthPx }
                                    ?.let { onCourseClick(it.course) }
                            }
                        }
                    }
                )
        ) {
            val offsetPx = horizontalOffset()
            drawRect(data.surface)

            val stroke = 0.5.dp.toPx()
            val firstCol = floor(offsetPx / data.slotWidthPx).toInt().coerceAtLeast(0)
            val lastCol = ceil((offsetPx + size.width) / data.slotWidthPx).toInt()
                .coerceAtMost(data.columnCount)
            for (col in firstCol..lastCol) {
                if (col <= 0) continue
                val x = col * data.slotWidthPx - offsetPx
                if (x in 0f..size.width) {
                    drawLine(data.gridLine, Offset(x, 0f), Offset(x, size.height), stroke)
                }
            }
            drawLine(
                data.gridLine,
                Offset(0f, size.height),
                Offset(size.width, size.height),
                stroke
            )

            val cardTop = data.insetPx
            val cardHeight = data.rowHeightPx - data.insetPx * 2
            val corner = CornerRadius(data.cornerPx)
            data.courses.forEach { card ->
                val left = card.leftPx - offsetPx
                if (left + card.widthPx < 0f || left > size.width) return@forEach
                drawRoundRect(
                    color = card.background,
                    topLeft = Offset(left, cardTop),
                    size = Size(card.widthPx, cardHeight),
                    cornerRadius = corner
                )
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = cardTop,
                            right = left + card.widthPx,
                            bottom = cardTop + cardHeight,
                            cornerRadius = corner
                        )
                    )
                }
                clipPath(path) {
                    drawRect(
                        color = card.accent,
                        topLeft = Offset(left, cardTop),
                        size = Size(data.accentWidthPx, cardHeight)
                    )
                    val textX = left + data.accentWidthPx + data.textPadPx
                    val nameTop = cardTop + data.verticalPadPx
                    drawText(card.nameLayout, topLeft = Offset(textX, nameTop))
                    card.teacherLayout?.let { teacher ->
                        drawText(
                            teacher,
                            topLeft = Offset(textX, nameTop + card.nameLayout.size.height)
                        )
                    }
                }
            }
        }

        if (accessibilityEnabled) {
            val offsetPx = horizontalOffset()
            data.courses
                .filter {
                    it.leftPx + it.widthPx > offsetPx &&
                        it.leftPx < offsetPx + viewportWidthPx
                }
                .forEach { card ->
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset((card.leftPx - horizontalOffset()).roundToInt(), 0)
                            }
                            .width(with(density) { card.widthPx.toDp() })
                            .fillMaxHeight()
                            .semantics {
                                contentDescription = card.accessibilityLabel
                                role = Role.Button
                            }
                            .clickable(
                                onClickLabel = "查看课程详情"
                            ) { onCourseClick(card.course) }
                    )
                }
        }
    }
}

/**
 * 读屏/辅助服务开启时为 Canvas 叠加语义节点。
 *
 * 监听开关变化而不是只读一次：用户在 App 运行中打开 TalkBack 后，
 * 当前页面不用重建也能获得可聚焦的课程卡；关闭后恢复普通 Canvas 点击。
 * Switch Access 等不依赖“触摸探索”的服务也需要节点，因此同时看服务列表。
 */
@Composable
private fun rememberAccessibilityOverlayEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var enabled by remember(manager) { mutableStateOf(manager.needsAccessibilityOverlay()) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose {}
        val touchListener = AccessibilityManager.TouchExplorationStateChangeListener {
            enabled = manager.needsAccessibilityOverlay()
        }
        val serviceListener = AccessibilityManager.AccessibilityStateChangeListener {
            enabled = manager.needsAccessibilityOverlay()
        }
        manager.addTouchExplorationStateChangeListener(touchListener)
        manager.addAccessibilityStateChangeListener(serviceListener)
        onDispose {
            manager.removeTouchExplorationStateChangeListener(touchListener)
            manager.removeAccessibilityStateChangeListener(serviceListener)
        }
    }
    return enabled
}

private fun AccessibilityManager?.needsAccessibilityOverlay(): Boolean {
    if (this == null) return false
    return isTouchExplorationEnabled ||
        getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).isNotEmpty()
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
