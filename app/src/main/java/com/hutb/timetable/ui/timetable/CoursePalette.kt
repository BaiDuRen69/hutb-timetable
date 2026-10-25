package com.hutb.timetable.ui.timetable

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hutb.timetable.ui.theme.LocalIsDarkTheme

/**
 * 一套课程配色：卡片底色、左侧色条、文字色。
 */
data class CoursePalette(
    val background: Color,
    val accent: Color,
    val text: Color
)

private val LightPalettes = listOf(
    CoursePalette(Color(0xFFDCEBFB), Color(0xFF2F6FA8), Color(0xFF14456F)),
    CoursePalette(Color(0xFFE1F3E8), Color(0xFF2F7D52), Color(0xFF1D5233)),
    CoursePalette(Color(0xFFFBE8DA), Color(0xFFB4682A), Color(0xFF7A4415)),
    CoursePalette(Color(0xFFEDE3F8), Color(0xFF7551A8), Color(0xFF4B2C73)),
    CoursePalette(Color(0xFFFBE1E6), Color(0xFFB04A60), Color(0xFF7B2438)),
    CoursePalette(Color(0xFFFBF3D6), Color(0xFF9A7B1E), Color(0xFF6B5312)),
    CoursePalette(Color(0xFFDCF0F0), Color(0xFF2A7A77), Color(0xFF14514F)),
    CoursePalette(Color(0xFFE9E9F5), Color(0xFF5A5A96), Color(0xFF35355C))
)

private val DarkPalettes = listOf(
    CoursePalette(Color(0xFF1E344A), Color(0xFF8AB8E8), Color(0xFFD6E6F7)),
    CoursePalette(Color(0xFF1E3A2A), Color(0xFF8FD3AE), Color(0xFFD4EFDF)),
    CoursePalette(Color(0xFF3A2A1A), Color(0xFFEFC39B), Color(0xFFF7E3CC)),
    CoursePalette(Color(0xFF2E2440), Color(0xFFC7AEE9), Color(0xFFE9DDF9)),
    CoursePalette(Color(0xFF3A2027), Color(0xFFEDA0B2), Color(0xFFF8DCE3)),
    CoursePalette(Color(0xFF332D18), Color(0xFFE4CE8B), Color(0xFFF5EBC8)),
    CoursePalette(Color(0xFF173333), Color(0xFF9FD5D5), Color(0xFFD8F0F0)),
    CoursePalette(Color(0xFF262636), Color(0xFFBDBDDD), Color(0xFFE3E3F2))
)

/**
 * 课程名 → 配色。
 *
 * 用**稳定的名称散列**（FNV-1a）而不是"当前周课程名排序下标"：
 * 排序下标依赖当周有哪些课，切到另一周同一门课会换色，用户建立的
 * "颜色 = 课程" 心智会崩掉。稳定散列只有极低概率的两门课撞色，
 * 危害远小于每换一周就重新配色。
 */
fun coursePaletteIndex(courseName: String): Int {
    var hash = -2128831035 // FNV offset basis (2166136261)
    for (ch in courseName) {
        hash = hash xor ch.code
        hash *= 16777619
    }
    return (hash and Int.MAX_VALUE) % 8
}

/** 取课程配色（跟随应用主题的深浅色，而不是系统设置）。 */
@Composable
fun coursePalette(courseName: String): CoursePalette {
    val palettes = if (LocalIsDarkTheme.current) DarkPalettes else LightPalettes
    return palettes[coursePaletteIndex(courseName)]
}
