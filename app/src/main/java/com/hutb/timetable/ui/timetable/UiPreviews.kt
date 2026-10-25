package com.hutb.timetable.ui.timetable

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.hutb.timetable.core.Course
import com.hutb.timetable.core.RoomSortMode
import com.hutb.timetable.core.ScheduleFilter
import com.hutb.timetable.ui.theme.HutbTimetableTheme

/**
 * UI 预览：改主题/配色时不必反复装机。
 */
private val PreviewCourses = listOf(
    Course("高等数学(一)", "张伟", "计科2501", "至诚楼A201", 1, 1, 2, "1-16周"),
    Course("数据结构与算法", "李娜", "计科2501", "乐知楼E102", 2, 3, 4, "1-16周"),
    Course("大学英语", "王强", "计科2501", "至诚楼B102", 3, 5, 6, "1-16周"),
    Course("AI大模型辅助数据分析", "赵敏", "计科2501", "乐知楼E101", 1, 5, 6, "1-16周"),
    Course("人工智能导论", "孙思源", "计科2501", "乐知楼E101", 1, 7, 8, "1-16周")
)

@Preview(name = "周课表 · 浅色", showBackground = true, widthDp = 400, heightDp = 700)
@Preview(
    name = "周课表 · 深色",
    showBackground = true,
    widthDp = 400,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun WeekGridPreview() {
    HutbTimetableTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            WeekGrid(
                courses = PreviewCourses,
                filter = ScheduleFilter(),
                onCourseClick = {},
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(name = "教室矩阵 · 浅色", showBackground = true, widthDp = 400, heightDp = 700)
@Preview(
    name = "教室矩阵 · 深色",
    showBackground = true,
    widthDp = 400,
    heightDp = 700,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun RoomMatrixPreview() {
    HutbTimetableTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            RoomMatrix(
                courses = PreviewCourses,
                filter = ScheduleFilter(),
                onCourseClick = {},
                modifier = Modifier.fillMaxSize(),
                shortNames = true,
                buildingName = "乐知楼",
                sortMode = RoomSortMode.BY_BUILDING,
                allRooms = listOf("乐知楼E101", "乐知楼E102", "乐知楼E103", "乐知楼E201")
            )
        }
    }
}
