package com.hutb.timetable.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 当前是否深色主题。
 *
 * 供非 Composable 的配色函数（课程 8 色板）读取，
 * 保证与 [HutbTimetableTheme] 的 darkTheme 入参一致；
 * 不能直接调用 isSystemInDarkTheme()，否则将来加"强制浅色/深色"开关时课程卡会脱节。
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * 配色体系。
 *
 * 课表场景的高频需求是"晚上在宿舍看"，因此深色不是装饰而是刚需。
 * 品牌蓝（primary）保持稳定，不启用 Material You 动态取色——
 * 动态色会与课程卡的八套配色打架。
 *
 * M3 1.3 新增的 surfaceContainer* / inverse* 必须显式给值：
 * 否则会落回基线紫调（ModalBottomSheet 变淡紫、Snackbar 按钮变紫）。
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E9E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F7),
    onPrimaryContainer = Color(0xFF0A2A47),
    inversePrimary = Color(0xFF8AB8E8),
    secondary = Color(0xFF4A6C8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F1),
    onSecondaryContainer = Color(0xFF102A41),
    tertiary = Color(0xFF7A5B10),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF8E6),
    onTertiaryContainer = Color(0xFF7A5B10),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE8EEF5),
    onSurfaceVariant = Color(0xFF41474D),
    surfaceTint = Color(0xFF1B5E9E),
    surfaceDim = Color(0xFFDDE4EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8FC),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFEAEFF5),
    surfaceContainerHighest = Color(0xFFE3EAF2),
    inverseSurface = Color(0xFF2E3A46),
    inverseOnSurface = Color(0xFFEAF1F8),
    outline = Color(0xFF8A99A8),
    outlineVariant = Color(0xFFCBD6E2),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB8E8),
    onPrimary = Color(0xFF0A2A47),
    primaryContainer = Color(0xFF1B5E9E),
    onPrimaryContainer = Color(0xFFD3E4F7),
    inversePrimary = Color(0xFF1B5E9E),
    secondary = Color(0xFFA8C0D8),
    onSecondary = Color(0xFF0F2942),
    secondaryContainer = Color(0xFF2A3F55),
    onSecondaryContainer = Color(0xFFDCE6F1),
    tertiary = Color(0xFFE0C07A),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5C4400),
    onTertiaryContainer = Color(0xFFFFDF9E),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF2A2E33),
    onSurfaceVariant = Color(0xFFC4C7CB),
    surfaceTint = Color(0xFF8AB8E8),
    surfaceDim = Color(0xFF101316),
    surfaceBright = Color(0xFF373E46),
    surfaceContainerLowest = Color(0xFF0C0F12),
    surfaceContainerLow = Color(0xFF171B1F),
    surfaceContainer = Color(0xFF1C2126),
    surfaceContainerHigh = Color(0xFF232930),
    surfaceContainerHighest = Color(0xFF2A3138),
    inverseSurface = Color(0xFFE2E8EF),
    inverseOnSurface = Color(0xFF1A1D21),
    outline = Color(0xFF8A9199),
    outlineVariant = Color(0xFF3A4046),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * 字号层级（含行高）。
 *
 * 中文字形没有西文那种天然行距，lineHeight 不显式给会让两行课程名偏挤。
 */
val HutbTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
)

val HutbShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 应用主题：跟随系统深浅色。
 */
@Composable
fun HutbTimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = HutbTypography,
            shapes = HutbShapes,
            content = content
        )
    }
}
