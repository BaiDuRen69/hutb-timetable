package com.hutb.timetable.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * 非敏感的偏好设置。
 *
 * 与 [CredentialStore] 分开：这里只存"记住上次位置 / 显示偏好"，
 * 清理偏好不会误伤登录会话，反之亦然。
 */
class AppPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    /** 最近一次使用的学期 ID，便于下次打开直接定位。 */
    var lastSemesterId: String
        get() = prefs.getString(KEY_LAST_SEMESTER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SEMESTER, value).apply()

    /** 最近一次使用的周次。 */
    var lastWeek: Int
        get() = prefs.getInt(KEY_LAST_WEEK, 1).coerceIn(1, 30)
        set(value) = prefs.edit().putInt(KEY_LAST_WEEK, value).apply()

    /** 最近一次教室查询的校区 ID（xqid）。 */
    var lastCampusId: String
        get() = prefs.getString(KEY_LAST_CAMPUS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CAMPUS, value).apply()

    /** 最近一次教室查询的教学楼 ID（jzwid）。 */
    var lastBuildingId: String
        get() = prefs.getString(KEY_LAST_BUILDING, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_BUILDING, value).apply()

    /** 最近一次教室查询的教学楼名称。 */
    var lastBuildingName: String
        get() = prefs.getString(KEY_LAST_BUILDING_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_BUILDING_NAME, value).apply()

    /** 最近一次选中的教室代码（classroomID），空表示不指定教室。 */
    var lastRoomCode: String
        get() = prefs.getString(KEY_LAST_ROOM_CODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_ROOM_CODE, value).apply()

    /** 教室课表是否精简教室名（去掉教学楼前缀只留房间号）。 */
    var shortRoomNames: Boolean
        get() = prefs.getBoolean(KEY_SHORT_ROOM_NAMES, true)
        set(value) = prefs.edit().putBoolean(KEY_SHORT_ROOM_NAMES, value).apply()

    /** 教室行的排序方式（存储 RoomSortMode 枚举名）。 */
    var roomSortMode: String
        get() = prefs.getString(KEY_ROOM_SORT_MODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROOM_SORT_MODE, value).apply()

    /** 最近一次成功解析出学期列表的 HTML 页面路径。 */
    var lastSemesterSourcePath: String
        get() = prefs.getString(KEY_LAST_SEMESTER_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SEMESTER_PATH, value).apply()

    /**
     * 最近一次成功登录的账号分区键（哈希，不含学号明文）。
     *
     * 退出登录后仍保留：未登录时只读这个分区的离线课表；
     * 下次登录会在解析出学号后覆盖，避免不同账号互相看到缓存。
     */
    var lastAccountKey: String
        get() = prefs.getString(KEY_LAST_ACCOUNT_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_ACCOUNT_KEY, value).apply()

    /** 上次学期（按账号隔离）。 */
    fun lastSemesterIdFor(accountKey: String): String =
        prefs.getString("last_semester_$accountKey", "") ?: ""

    fun setLastSemesterIdFor(accountKey: String, value: String) {
        prefs.edit().putString("last_semester_$accountKey", value).apply()
    }

    /** 上次周次（按账号隔离）。 */
    fun lastWeekFor(accountKey: String): Int =
        prefs.getInt("last_week_$accountKey", 1).coerceIn(1, 30)

    fun setLastWeekFor(accountKey: String, value: Int) {
        prefs.edit().putInt("last_week_$accountKey", value).apply()
    }

    /** 清空全部偏好（不影响登录会话）。 */
    fun clearPreferences() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LAST_SEMESTER = "last_semester_id"
        private const val KEY_LAST_WEEK = "last_week"
        private const val KEY_LAST_CAMPUS = "last_campus_id"
        private const val KEY_LAST_BUILDING = "last_building_id"
        private const val KEY_LAST_BUILDING_NAME = "last_building_name"
        private const val KEY_LAST_ROOM_CODE = "last_room_code"
        private const val KEY_SHORT_ROOM_NAMES = "short_room_names"
        private const val KEY_ROOM_SORT_MODE = "room_sort_mode"
        private const val KEY_LAST_SEMESTER_PATH = "last_semester_source_path"
        private const val KEY_LAST_ACCOUNT_KEY = "last_account_key"
    }
}
