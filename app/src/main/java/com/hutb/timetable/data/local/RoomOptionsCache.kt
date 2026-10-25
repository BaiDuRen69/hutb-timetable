package com.hutb.timetable.data.local

import android.content.Context
import com.hutb.timetable.core.RoomOption
import com.hutb.timetable.core.RoomOptionsTtl
import org.json.JSONArray
import org.json.JSONObject

/**
 * 校区 / 教学楼下拉框选项的持久缓存。
 *
 * 这两类列表一学期内基本不变，没必要每次进教室模式都打一次服务端
 * （教室课表页服务端生成要 8~90 秒）。带 TTL，过期后自动重新拉取。
 * key 生成与过期判断在 core 的 [RoomOptionsTtl]，本类只做存储壳。
 */
class RoomOptionsCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("room_options_cache", Context.MODE_PRIVATE)

    fun getCampuses(): List<RoomOption>? = read(RoomOptionsTtl.campusesKey())

    fun putCampuses(items: List<RoomOption>) = write(RoomOptionsTtl.campusesKey(), items)

    /** 教学楼列表按学期分区缓存，避免换学期后读到旧学期数据。 */
    fun getBuildings(semesterId: String?, campusId: String): List<RoomOption>? =
        read(RoomOptionsTtl.buildingsKey(semesterId, campusId))

    fun putBuildings(semesterId: String?, campusId: String, items: List<RoomOption>) =
        write(RoomOptionsTtl.buildingsKey(semesterId, campusId), items)

    /** 清除校区/教学楼选项缓存。 */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun read(key: String): List<RoomOption>? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val obj = JSONObject(raw)
            if (!RoomOptionsTtl.isFresh(obj.optLong("t"))) return null
            val array = obj.optJSONArray("items") ?: return null
            val result = mutableListOf<RoomOption>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val code = item.optString("code")
                if (code.isEmpty()) continue
                result.add(RoomOption(code = code, name = item.optString("name")))
            }
            result.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun write(key: String, items: List<RoomOption>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("code", item.code).put("name", item.name))
        }
        prefs.edit()
            .putString(key, JSONObject().put("t", System.currentTimeMillis()).put("items", array).toString())
            .apply()
    }
}
