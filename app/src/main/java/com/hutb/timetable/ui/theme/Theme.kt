package com.hutb.timetable.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

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
 * 深浅模式。默认跟随系统，用户可在设置中强制浅色/深色。
 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        fun fromId(id: String): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * 主题配色。
 *
 * 只替换主色系（primary / primaryContainer）并用它调和 secondary，
 * 中性表面保持同一套灰阶，避免 5 套主题各写一份完整 ColorScheme 后互相漂移。
 * tertiary 仍是暖金色提示色，不与主色争抢注意力。
 */
enum class ThemeStyle(
    val id: String,
    val label: String,
    val preview: Color,
    private val lightPrimary: Color,
    private val lightOnPrimary: Color,
    private val lightPrimaryContainer: Color,
    private val lightOnPrimaryContainer: Color,
    private val darkPrimary: Color,
    private val darkOnPrimary: Color,
    private val darkPrimaryContainer: Color,
    private val darkOnPrimaryContainer: Color
) {
    BLUE(
        id = "blue",
        label = "青蓝",
        preview = Color(0xFF1B5E9E),
        lightPrimary = Color(0xFF1B5E9E),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFD3E4F7),
        lightOnPrimaryContainer = Color(0xFF0A2A47),
        darkPrimary = Color(0xFF8AB8E8),
        darkOnPrimary = Color(0xFF0A2A47),
        darkPrimaryContainer = Color(0xFF1B5E9E),
        darkOnPrimaryContainer = Color(0xFFD3E4F7)
    ),
    GREEN(
        id = "green",
        label = "竹绿",
        preview = Color(0xFF2F6B4F),
        lightPrimary = Color(0xFF2F6B4F),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFD3EBDD),
        lightOnPrimaryContainer = Color(0xFF0B2B1B),
        darkPrimary = Color(0xFF9CD3B4),
        darkOnPrimary = Color(0xFF06301C),
        darkPrimaryContainer = Color(0xFF2F6B4F),
        darkOnPrimaryContainer = Color(0xFFD3EBDD)
    ),
    PURPLE(
        id = "purple",
        label = "紫藤",
        preview = Color(0xFF6A4C93),
        lightPrimary = Color(0xFF6A4C93),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEADDF6),
        lightOnPrimaryContainer = Color(0xFF2A1245),
        darkPrimary = Color(0xFFD3B8F0),
        darkOnPrimary = Color(0xFF321A50),
        darkPrimaryContainer = Color(0xFF6A4C93),
        darkOnPrimaryContainer = Color(0xFFEADDF6)
    ),
    ORANGE(
        id = "orange",
        label = "暖橙",
        preview = Color(0xFF8A4A12),
        lightPrimary = Color(0xFF8A4A12),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFDCC7),
        lightOnPrimaryContainer = Color(0xFF331A05),
        darkPrimary = Color(0xFFFFB98A),
        darkOnPrimary = Color(0xFF4A2500),
        darkPrimaryContainer = Color(0xFF8A4A12),
        darkOnPrimaryContainer = Color(0xFFFFDCC7)
    ),
    ROSE(
        id = "rose",
        label = "绛桃",
        preview = Color(0xFF8F3F5A),
        lightPrimary = Color(0xFF8F3F5A),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFD9E2),
        lightOnPrimaryContainer = Color(0xFF3B0A1C),
        darkPrimary = Color(0xFFFFB1C6),
        darkOnPrimary = Color(0xFF561F33),
        darkPrimaryContainer = Color(0xFF8F3F5A),
        darkOnPrimaryContainer = Color(0xFFFFD9E2)
    );

    fun lightScheme(): ColorScheme = LightColors.copy(
        primary = lightPrimary,
        onPrimary = lightOnPrimary,
        primaryContainer = lightPrimaryContainer,
        onPrimaryContainer = lightOnPrimaryContainer,
        inversePrimary = darkPrimary,
        secondary = lerp(LightColors.secondary, lightPrimary, 0.6f),
        secondaryContainer = lerp(LightColors.secondaryContainer, lightPrimaryContainer, 0.7f),
        onSecondaryContainer = lerp(
            LightColors.onSecondaryContainer,
            lightOnPrimaryContainer,
            0.5f
        ),
        surfaceTint = lightPrimary
    )

    fun darkScheme(): ColorScheme = DarkColors.copy(
        primary = darkPrimary,
        onPrimary = darkOnPrimary,
        primaryContainer = darkPrimaryContainer,
        onPrimaryContainer = darkOnPrimaryContainer,
        inversePrimary = lightPrimary,
        secondary = lerp(DarkColors.secondary, darkPrimary, 0.6f),
        secondaryContainer = lerp(DarkColors.secondaryContainer, darkPrimaryContainer, 0.7f),
        onSecondaryContainer = lerp(
            DarkColors.onSecondaryContainer,
            darkOnPrimaryContainer,
            0.5f
        ),
        surfaceTint = darkPrimary
    )

    companion object {
        fun fromId(id: String): ThemeStyle = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

/**
 * 应用主题：深浅模式 + 配色均由用户选择。
 */@Composable
fun HutbTimetableTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themeStyle: ThemeStyle = ThemeStyle.BLUE,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = remember(themeStyle, darkTheme) {
        if (darkTheme) themeStyle.darkScheme() else themeStyle.lightScheme()
    }
    // 强制深浅色变化时系统栏图标要同步，否则浅色系统栏图标会落在深色 App 背景上看不见
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HutbTypography,
            shapes = HutbShapes,
            content = content
        )
    }
}

/** Compose 的 Context 可能被主题/布局包装过，向上找到 Activity 的 Window。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
