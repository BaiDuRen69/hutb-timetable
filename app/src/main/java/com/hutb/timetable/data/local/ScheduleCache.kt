package com.hutb.timetable.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 课表本地缓存。
 *
 * 用原生 SQLiteOpenHelper 实现，不引入注解处理器（KSP/KAPT），
 * 既减少构建复杂度，也让 AGP 9 的内置 Kotlin 支持可以直接使用。
 *
 * 表结构：
 * - `cached_schedule`：主键 (semester_id, week, scope)，payload 存课表 JSON
 * - `cached_semester`：学期列表
 *
 * `scope` 用来区分个人课表与教室课表（见 ScheduleQuery.cacheScope），
 * 避免两种查询互相覆盖缓存。
 */
class ScheduleCache(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {

    companion object {
        private const val DB_NAME = "hutb_timetable.db"
        private const val DB_VERSION = 3

        /** 全局最多保留的缓存条数（约覆盖当前学期 + 历史若干周）。 */
        private const val MAX_CACHED_ROWS = 80

        /** 缓存 payload 的总字节上限；整栋楼的教室课表 JSON 远比个人课表大。 */
        private const val MAX_CACHED_BYTES = 4L * 1024 * 1024

        private const val TABLE_SCHEDULE = "cached_schedule"
        private const val TABLE_SEMESTER = "cached_semester"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SCHEDULE (
                semester_id TEXT NOT NULL,
                week INTEGER NOT NULL,
                scope TEXT NOT NULL DEFAULT 'personal',
                semester_name TEXT NOT NULL,
                payload TEXT NOT NULL,
                cached_at INTEGER NOT NULL,
                PRIMARY KEY (semester_id, week, scope)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_SEMESTER (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_schedule_cached_at ON $TABLE_SCHEDULE(cached_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) migrateToV3(db)
    }

    /**
     * v3 迁移：只做增量结构修复，**绝不 DROP 用户数据**。
     *
     * - v1 的 `cached_schedule` 没有 `scope` 列：建新表 → INSERT SELECT 搬运 →
     *   删除旧表 → 改名，主键升级为 (semester_id, week, scope)；
     * - v2 已经是目标结构，本迁移是空操作（v2→v3 数据原样保留）；
     * - `cached_semester` 不参与重建，升级后学期下拉立即可用。
     */
    private fun migrateToV3(db: SQLiteDatabase) {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($TABLE_SCHEDULE)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
        }

        if ("scope" !in columns) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ${TABLE_SCHEDULE}_v3 (
                    semester_id TEXT NOT NULL,
                    week INTEGER NOT NULL,
                    scope TEXT NOT NULL DEFAULT 'personal',
                    semester_name TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    cached_at INTEGER NOT NULL,
                    PRIMARY KEY (semester_id, week, scope)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO ${TABLE_SCHEDULE}_v3
                    (semester_id, week, scope, semester_name, payload, cached_at)
                SELECT semester_id, week, 'personal', semester_name, payload, cached_at
                FROM $TABLE_SCHEDULE
                """.trimIndent()
            )
            db.execSQL("DROP TABLE $TABLE_SCHEDULE")
            db.execSQL("ALTER TABLE ${TABLE_SCHEDULE}_v3 RENAME TO $TABLE_SCHEDULE")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_schedule_cached_at ON $TABLE_SCHEDULE(cached_at)")
        }

        // 学期缓存表不随课表迁移重建，保证升级后学期下拉立即可用
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SEMESTER (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    /** 覆盖写入一周课表缓存。 */
    fun putSchedule(
        semesterId: String,
        week: Int,
        scope: String,
        semesterName: String,
        payload: String,
        cachedAt: Long = System.currentTimeMillis()
    ) {
        val values = ContentValues().apply {
            put("semester_id", semesterId)
            put("week", week)
            put("scope", scope)
            put("semester_name", semesterName)
            put("payload", payload)
            put("cached_at", cachedAt)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_SCHEDULE, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
        pruneOldRows()
    }

    /** 只保留最近的若干条缓存，避免按学期/周永久累积。 */
    private fun pruneOldRows(maxRows: Int = MAX_CACHED_ROWS) {
        writableDatabase.execSQL(
            "DELETE FROM $TABLE_SCHEDULE WHERE rowid NOT IN " +
                "(SELECT rowid FROM $TABLE_SCHEDULE ORDER BY cached_at DESC LIMIT ?)",
            arrayOf(maxRows)
        )
        pruneByBytes()
    }

    /**
     * 再进行一次字节级裁剪：条数相同不代表体积相同，
     * 教室全楼课表一周可能就有几百 KB。从最旧的开始删，至少保留最近一条。
     */
    private fun pruneByBytes(maxBytes: Long = MAX_CACHED_BYTES) {
        val deleteIds = mutableListOf<Long>()
        var kept = 0
        var total = 0L
        writableDatabase.query(
            TABLE_SCHEDULE,
            arrayOf("rowid", "LENGTH(payload)"),
            null, null, null, null,
            "cached_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val bytes = cursor.getLong(1)
                if (kept > 0 && total + bytes > maxBytes) {
                    deleteIds.add(rowId)
                } else {
                    kept++
                    total += bytes
                }
            }
        }
        // rowid 是 SQLite 内部整数，直接拼进 IN 列表是安全的
        deleteIds.chunked(200).forEach { chunk ->
            writableDatabase.execSQL(
                "DELETE FROM $TABLE_SCHEDULE WHERE rowid IN (${chunk.joinToString(",")})"
            )
        }
    }

    /** 读取指定学期/周次/分区的缓存。 */
    fun getSchedule(semesterId: String, week: Int, scope: String): CachedSchedule? {
        readableDatabase.query(
            TABLE_SCHEDULE,
            arrayOf("semester_id", "week", "semester_name", "payload", "cached_at"),
            "semester_id = ? AND week = ? AND scope = ?",
            arrayOf(semesterId, week.toString(), scope),
            null, null, null, "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return CachedSchedule(
                semesterId = cursor.getString(0),
                week = cursor.getInt(1),
                semesterName = cursor.getString(2),
                payload = cursor.getString(3),
                cachedAt = cursor.getLong(4)
            )
        }
    }

    /** 读取某学期某分区最近一次缓存。 */
    fun getLatestOfSemester(semesterId: String, scope: String): CachedSchedule? {
        readableDatabase.query(
            TABLE_SCHEDULE,
            arrayOf("semester_id", "week", "semester_name", "payload", "cached_at"),
            "semester_id = ? AND scope = ?",
            arrayOf(semesterId, scope),
            null, null, "cached_at DESC", "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return CachedSchedule(
                semesterId = cursor.getString(0),
                week = cursor.getInt(1),
                semesterName = cursor.getString(2),
                payload = cursor.getString(3),
                cachedAt = cursor.getLong(4)
            )
        }
    }

    /** 覆盖写入学期列表。 */
    fun putSemesters(items: List<Pair<String, String>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_SEMESTER, null, null)
            items.forEach { (id, name) ->
                val values = ContentValues().apply {
                    put("id", id)
                    put("name", name)
                }
                db.insertWithOnConflict(TABLE_SEMESTER, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 读取缓存的学期列表。 */
    fun getSemesters(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        readableDatabase.query(
            TABLE_SEMESTER,
            arrayOf("id", "name"),
            null, null, null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getString(0) to cursor.getString(1))
            }
        }
        return result
    }

    /** 清空全部本地数据（课表 + 学期列表）。 */
    fun clearAll() {
        writableDatabase.delete(TABLE_SCHEDULE, null, null)
        writableDatabase.delete(TABLE_SEMESTER, null, null)
    }

    /** 清空课表缓存（保留学期列表）。 */
    fun clearSchedules() {
        writableDatabase.delete(TABLE_SCHEDULE, null, null)
    }
}

/**
 * 缓存的课表记录。
 */
data class CachedSchedule(
    val semesterId: String,
    val week: Int,
    val semesterName: String,
    val payload: String,
    val cachedAt: Long
)
