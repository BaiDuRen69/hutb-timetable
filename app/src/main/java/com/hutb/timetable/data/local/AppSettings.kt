package com.hutb.timetable.data.local

import android.content.Context
import android.content.SharedPreferences
import com.hutb.timetable.core.computeCurrentWeek
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 一节课的上下课时间，"HH:mm" 24 小时制。 */
data class PeriodTime(
    val start: String,
    val end: String
)

/**
 * 一套作息时间表：12 节课各自的上下课时间。
 *
 * 学校按 夏令/冬令 × 南校区/北校区（月亮岛）分四套作息，
 * 这里允许用户自由修改并另存自定义时间表。
 */
data class ClassTimetable(
    val id: String,
    val name: String,
    val periods: List<PeriodTime>
) {
    init {
        require(periods.size == PERIOD_COUNT) { "作息时间表必须包含 $PERIOD_COUNT 节" }
    }

    companion object {
        const val PERIOD_COUNT = 12

        fun defaultTimetables(): List<ClassTimetable> = listOf(
            ClassTimetable(
                id = "summer_south",
                name = "夏时制·南校区",
                periods = listOf(
                    PeriodTime("08:00", "08:45"), PeriodTime("08:55", "09:40"),
                    PeriodTime("10:05", "10:50"), PeriodTime("11:00", "11:45"),
                    PeriodTime("14:30", "15:15"), PeriodTime("15:20", "16:05"),
                    PeriodTime("16:20", "17:05"), PeriodTime("17:10", "17:55"),
                    PeriodTime("19:00", "19:45"), PeriodTime("19:50", "20:35"),
                    PeriodTime("20:40", "21:25"), PeriodTime("21:30", "22:15")
                )
            ),
            ClassTimetable(
                id = "summer_north",
                name = "夏时制·北校区/月亮岛",
                periods = listOf(
                    PeriodTime("08:10", "08:55"), PeriodTime("09:05", "09:50"),
                    PeriodTime("10:05", "10:50"), PeriodTime("11:00", "11:45"),
                    PeriodTime("14:30", "15:15"), PeriodTime("15:20", "16:05"),
                    PeriodTime("16:15", "17:00"), PeriodTime("17:05", "17:50"),
                    PeriodTime("19:00", "19:45"), PeriodTime("19:50", "20:35"),
                    PeriodTime("20:40", "21:25"), PeriodTime("21:30", "22:15")
                )
            ),
            ClassTimetable(
                id = "winter_south",
                name = "冬时制·南校区",
                periods = listOf(
                    PeriodTime("08:00", "08:45"), PeriodTime("08:55", "09:40"),
                    PeriodTime("10:05", "10:50"), PeriodTime("11:00", "11:45"),
                    PeriodTime("14:00", "14:45"), PeriodTime("14:50", "15:35"),
                    PeriodTime("15:50", "16:35"), PeriodTime("16:40", "17:25"),
                    PeriodTime("19:00", "19:45"), PeriodTime("19:50", "20:35"),
                    PeriodTime("20:40", "21:25"), PeriodTime("21:30", "22:15")
                )
            ),
            ClassTimetable(
                id = "winter_north",
                name = "冬时制·北校区/月亮岛",
                periods = listOf(
                    PeriodTime("08:10", "08:55"), PeriodTime("09:05", "09:50"),
                    PeriodTime("10:05", "10:50"), PeriodTime("11:00", "11:45"),
                    PeriodTime("14:00", "14:45"), PeriodTime("14:50", "15:35"),
                    PeriodTime("15:45", "16:30"), PeriodTime("16:35", "17:20"),
                    PeriodTime("19:00", "19:45"), PeriodTime("19:50", "20:35"),
                    PeriodTime("20:40", "21:25"), PeriodTime("21:30", "22:15")
                )
            )
        )
    }
}

/**
 * 应用设置：按学期保存的开学日期（用于推算周次）+ 作息时间表集合 + 主题。
 *
 * 时间表以 JSON 数组整体落盘；开学日期按学期存为 "yyyy-MM-dd"，
 * 未设置时周次沿用上次手动选择的值。
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    /** 全部作息时间表（含内置四套 + 用户自定义），顺序即展示顺序。 */
    var timetables: List<ClassTimetable>
        get() = decode(prefs.getString(KEY_TIMETABLES, null))
        set(value) = prefs.edit().putString(KEY_TIMETABLES, encode(value)).apply()

    /** 当前生效的作息时间表 ID。 */
    var activeTimetableId: String
        get() = prefs.getString(KEY_ACTIVE_TIMETABLE, "winter_north") ?: "winter_north"
        set(value) = prefs.edit().putString(KEY_ACTIVE_TIMETABLE, value).apply()

    /** 生效中的时间表；ID 失效时退回第一套。 */
    val activeTimetable: ClassTimetable
        get() = timetables.firstOrNull { it.id == activeTimetableId }
            ?: timetables.firstOrNull()
            ?: ClassTimetable.defaultTimetables().first()

    /**
     * 指定学期的开学（第一周周一）日期，"yyyy-MM-dd"，空表示未设置。
     *
     * 按学期隔离：不同学期开学日期不同，用一个全局值会把上学期日期套到下学期。
     */
    fun semesterStartDateFor(semesterId: String): String =
        if (semesterId.isBlank()) "" else prefs.getString(keyStart(semesterId), "") ?: ""

    fun setSemesterStartDateFor(semesterId: String, value: String) {
        if (semesterId.isBlank()) return
        prefs.edit().putString(keyStart(semesterId), value).apply()
    }

    /**
     * 旧版本只存了一个全局开学日期；升级后归到当前学期并删除旧键，
     * 避免它继续被当作"任何学期都适用"的日期。
     */
    fun migrateLegacyStartDate(semesterId: String) {
        if (semesterId.isBlank()) return
        val legacy = prefs.getString(KEY_SEMESTER_START, "").orEmpty()
        if (legacy.isBlank()) return
        if (semesterStartDateFor(semesterId).isBlank()) {
            setSemesterStartDateFor(semesterId, legacy)
        }
        prefs.edit().remove(KEY_SEMESTER_START).apply()
    }

    /** 由指定学期的开学日期推算当前周次；未设置或日期非法时返回 null。 */
    fun currentWeekNumber(semesterId: String): Int? =
        computeCurrentWeek(semesterStartDateFor(semesterId), LocalDate.now())

    /** 深浅模式（system / light / dark），UI 层负责映射成枚举。 */
    var themeModeId: String
        get() = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /** 主题配色 id（blue / green / purple / orange / rose）。 */
    var themeStyleId: String
        get() = prefs.getString(KEY_THEME_STYLE, "blue") ?: "blue"
        set(value) = prefs.edit().putString(KEY_THEME_STYLE, value).apply()

    /**
     * 是否完成过首次登录。
     *
     * 首次安装必须登录；之后未登录只提示、不阻拦离线查看，
     * 只有切到教室课表时才要求重新登录。
     */
    var firstLoginCompleted: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LOGIN_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LOGIN_COMPLETED, value).apply()

    /** 是否已确认首次启动的“课表仅供参考”使用须知。 */
    var disclaimerAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER_ACKNOWLEDGED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER_ACKNOWLEDGED, value).apply()

    /** 是否启用每日课程摘要通知。 */
    var dailySummaryEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_SUMMARY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DAILY_SUMMARY_ENABLED, value).apply()

    /** 每日摘要的发送时刻，距当天 00:00 的分钟数。 */
    var dailySummaryMinutes: Int
        get() = prefs.getInt(KEY_DAILY_SUMMARY_MINUTES, DEFAULT_SUMMARY_MINUTES)
            .coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = prefs.edit()
            .putInt(KEY_DAILY_SUMMARY_MINUTES, value.coerceIn(0, MINUTES_PER_DAY - 1))
            .apply()

    /** 当天没有课程时是否仍发送摘要。 */
    var dailySummaryNotifyWithoutCourses: Boolean
        get() = prefs.getBoolean(KEY_SUMMARY_WITHOUT_COURSES, false)
        set(value) = prefs.edit().putBoolean(KEY_SUMMARY_WITHOUT_COURSES, value).apply()

    /** 是否启用免打扰时段（该时段内不发送摘要）。 */
    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_HOURS_ENABLED, value).apply()

    var quietHoursStartMinutes: Int
        get() = prefs.getInt(KEY_QUIET_START, DEFAULT_QUIET_START)
            .coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = prefs.edit()
            .putInt(KEY_QUIET_START, value.coerceIn(0, MINUTES_PER_DAY - 1))
            .apply()

    var quietHoursEndMinutes: Int
        get() = prefs.getInt(KEY_QUIET_END, DEFAULT_QUIET_END)
            .coerceIn(0, MINUTES_PER_DAY - 1)
        set(value) = prefs.edit()
            .putInt(KEY_QUIET_END, value.coerceIn(0, MINUTES_PER_DAY - 1))
            .apply()

    private fun keyStart(semesterId: String): String = "$KEY_SEMESTER_START_PREFIX$semesterId"

    fun newTimetableId(): String =
        "custom_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))

    private fun encode(list: List<ClassTimetable>): String {
        val arr = JSONArray()
        list.forEach { t ->
            val periods = JSONArray()
            t.periods.forEach { p ->
                periods.put(JSONObject().put("start", p.start).put("end", p.end))
            }
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("periods", periods)
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String?): List<ClassTimetable> {
        if (raw.isNullOrBlank()) return ClassTimetable.defaultTimetables()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val periodsArr = obj.getJSONArray("periods")
                if (periodsArr.length() != ClassTimetable.PERIOD_COUNT) return@mapNotNull null
                val periods = (0 until periodsArr.length()).map { j ->
                    val p = periodsArr.getJSONObject(j)
                    PeriodTime(p.getString("start"), p.getString("end"))
                }
                ClassTimetable(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    periods = periods
                )
            }
        }.getOrElse { ClassTimetable.defaultTimetables() }
    }

    companion object {
        private const val KEY_TIMETABLES = "class_timetables"
        private const val KEY_ACTIVE_TIMETABLE = "active_timetable_id"

        /** 旧版本遗留的全局开学日期，仅用于迁移。 */
        private const val KEY_SEMESTER_START = "semester_start_date"
        private const val KEY_SEMESTER_START_PREFIX = "semester_start_"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_STYLE = "theme_style"
        private const val KEY_FIRST_LOGIN_COMPLETED = "first_login_completed"
        private const val KEY_DISCLAIMER_ACKNOWLEDGED = "disclaimer_acknowledged"

        private const val KEY_DAILY_SUMMARY_ENABLED = "daily_summary_enabled"
        private const val KEY_DAILY_SUMMARY_MINUTES = "daily_summary_minutes"
        private const val KEY_SUMMARY_WITHOUT_COURSES = "daily_summary_without_courses"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_START = "quiet_hours_start"
        private const val KEY_QUIET_END = "quiet_hours_end"

        private const val MINUTES_PER_DAY = 24 * 60

        /** 默认 07:20：早课前汇总当天课程。 */
        private const val DEFAULT_SUMMARY_MINUTES = 7 * 60 + 20
        private const val DEFAULT_QUIET_START = 22 * 60
        private const val DEFAULT_QUIET_END = 7 * 60
    }
}
