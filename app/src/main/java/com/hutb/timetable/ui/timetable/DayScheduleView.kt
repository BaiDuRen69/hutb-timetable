package com.hutb.timetable.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.data.local.ClassTimetable
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 个人课表的单日视图：按天横向滑动，整屏宽度显示课程。
 *
 * 与周视图共享同一份课程数据和作息表；筛选仍然生效，
 * 如果筛选把当前日期排除在外，会明确提示并可一键清除。
 */
@Composable
internal fun DayScheduleView(
    week: Int,
    semesterStartDate: String,
    courses: List<Course>,
    filter: ScheduleFilter,
    timetable: ClassTimetable?,
    isCurrentWeek: Boolean,
    onCourseClick: (Course) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = if (isCurrentWeek) LocalDate.now().dayOfWeek.value else 0
    var selectedDay by rememberSaveable { mutableIntStateOf(today.takeIf { it > 0 } ?: 1) }
    val pagerState = rememberPagerState(initialPage = selectedDay - 1) { 7 }
    // 用 settledPage 同步手势：动画过程中 currentPage 会经过中间页，
    // 用它回写 selectedDay 会把点击切日的动画打断
    LaunchedEffect(pagerState.settledPage) {
        selectedDay = pagerState.settledPage + 1
    }
    LaunchedEffect(selectedDay) {
        if (pagerState.settledPage != selectedDay - 1) {
            pagerState.animateScrollToPage(selectedDay - 1)
        }
    }
    val monday = remember(semesterStartDate, week) {
        runCatching { LocalDate.parse(semesterStartDate) }.getOrNull()
            ?.plusWeeks((week - 1).toLong())
    }
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000)
        }
    }

    Column(modifier = modifier) {
        DayChips(
            selectedDay = selectedDay,
            today = today,
            onSelect = { selectedDay = it }
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val day = page + 1
            DayAgenda(
                week = week,
                day = day,
                date = monday?.plusDays(page.toLong()),
                courses = courses,
                filter = filter,
                timetable = timetable,
                isToday = isCurrentWeek && day == today,
                now = now,
                onCourseClick = onCourseClick,
                onClearFilter = onClearFilter
            )
        }
    }
}

@Composable
private fun DayChips(
    selectedDay: Int,
    today: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (day in 1..7) {
            val isToday = day == today
            FilterChip(
                selected = selectedDay == day,
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f),
                label = {
                    Text(
                        // 等宽芯片放不下“今天”两字，用“今”并在当天标题里写全
                        text = if (isToday) "今" else WEEKDAY_NAMES[day - 1].removePrefix("周"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun DayAgenda(
    week: Int,
    day: Int,
    date: LocalDate?,
    courses: List<Course>,
    filter: ScheduleFilter,
    timetable: ClassTimetable?,
    isToday: Boolean,
    now: LocalDateTime,
    onCourseClick: (Course) -> Unit,
    onClearFilter: () -> Unit
) {
    val allDayCourses = remember(courses, day) {
        courses.filter { it.weekday == day }
            .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
    }
    val visibleCourses = remember(courses, day, filter) {
        filter.apply(courses)
            .filter { it.weekday == day }
            .sortedWith(compareBy({ it.startPeriod }, { it.endPeriod }))
    }
    val dayItems = remember(visibleCourses, timetable) {
        visibleCourses.map { course -> course to courseRangeMinutes(timetable, course) }
    }
    val nowMinutes = now.hour * 60 + now.minute
    val nextCourse = if (isToday) {
        dayItems.firstOrNull { (_, range) -> range != null && range.first > nowMinutes }?.first
    } else {
        null
    }
    val weekdayAllowed = !filter.limitsWeekday || day in filter.weekdays

    if (!weekdayAllowed || (allDayCourses.isNotEmpty() && visibleCourses.isEmpty())) {
        EmptyView(
            text = "当前筛选下${WEEKDAY_NAMES[day - 1]}没有课程",
            icon = Icons.Outlined.FilterAltOff,
            actionLabel = "清除筛选",
            onAction = onClearFilter,
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    if (allDayCourses.isEmpty()) {
        EmptyView(
            text = "${WEEKDAY_NAMES[day - 1]}没有课程",
            icon = Icons.Outlined.EventBusy,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            DayHeader(week = week, day = day, date = date, isToday = isToday)
        }

        var dividerInserted = false
        dayItems.forEachIndexed { index, (course, range) ->
            val isNow = isToday && range?.contains(nowMinutes) == true
            val isPast = isToday && range != null && range.last < nowMinutes
            if (isToday && !dividerInserted && range != null && range.last >= nowMinutes) {
                item(key = "now-$day") {
                    NowDivider(nowMinutes)
                }
                dividerInserted = true
            }
            item(key = "course-$day-$index-${course.courseName}") {
                DayCourseCard(
                    course = course,
                    range = range,
                    isNow = isNow,
                    isNext = course == nextCourse,
                    isPast = isPast,
                    onClick = { onCourseClick(course) }
                )
            }
        }
        if (isToday && !dividerInserted) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${formatMinutes(nowMinutes)} · 今天的课已结束",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    week: Int,
    day: Int,
    date: LocalDate?,
    isToday: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date?.let {
                    "${it.monthValue}月${it.dayOfMonth}日 · ${WEEKDAY_NAMES[day - 1]}"
                } ?: WEEKDAY_NAMES[day - 1],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "第${week}周" + if (isToday) " · 今天" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NowDivider(nowMinutes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = " 现在 ${formatMinutes(nowMinutes)} ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun DayCourseCard(
    course: Course,
    range: IntRange?,
    isNow: Boolean,
    isNext: Boolean,
    isPast: Boolean,
    onClick: () -> Unit
) {
    val palette = coursePalette(course.courseName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast && !isNow) 0.58f else 1f)
            .clip(MaterialTheme.shapes.medium)
            .background(palette.background)
            .clickable(onClickLabel = "查看课程详情", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(68.dp)) {
            Text(
                text = range?.let { formatMinutes(it.first) }.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.text
            )
            Text(
                text = if (course.startPeriod == course.endPeriod) {
                    "第${course.startPeriod}节"
                } else {
                    "第${course.startPeriod}-${course.endPeriod}节"
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.text.copy(alpha = 0.85f)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOf(course.classroom, course.teacher)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isNow || isNext) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isNow) "现在" else "下一节",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.text
            )
        }
    }
}
