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
 * 应用设置：开学日期（用于推算当前周次）+ 作息时间表集合。
 *
 * 时间表以 JSON 数组整体落盘；开学日期为 "yyyy-MM-dd"，
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

    /** 开学（第一周周一）日期，"yyyy-MM-dd"，空表示未设置。 */
    var semesterStartDate: String
        get() = prefs.getString(KEY_SEMESTER_START, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SEMESTER_START, value).apply()

    /** 由开学日期推算的当前周次；未设置或日期非法时返回 null。 */
    fun currentWeekNumber(): Int? = computeCurrentWeek(semesterStartDate, LocalDate.now())

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
        private const val KEY_SEMESTER_START = "semester_start_date"
    }
}
