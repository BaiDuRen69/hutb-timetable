package com.hutb.timetable.core

/**
 * 教室选项缓存的 key 与过期判断。
 *
 * 纯函数，不依赖 Android，便于单元测试：
 * 缓存是否仍新鲜、教学楼缓存 key 是否包含学期分区。
 */
object RoomOptionsTtl {

    /** 校区 / 教学楼列表的缓存有效期：一天。 */
    const val TTL_MS = 24L * 60 * 60 * 1000

    /**
     * 缓存是否仍在有效期内。
     *
     * @param cachedAt 写入时间（epoch millis），<=0 视为无效
     * @param now 当前时间（测试可注入）
     * @param ttlMs 有效期
     */
    fun isFresh(
        cachedAt: Long,
        now: Long = System.currentTimeMillis(),
        ttlMs: Long = TTL_MS
    ): Boolean {
        if (cachedAt <= 0L) return false
        // 设备时钟回拨导致 cachedAt 在未来时按"新鲜"处理，避免永远读不到缓存
        return now - cachedAt <= ttlMs
    }

    /** 校区列表的缓存 key。 */
    fun campusesKey(): String = "campuses"

    /** 教学楼列表按学期 + 校区分区，避免换学期后读到旧学期数据。 */
    fun buildingsKey(semesterId: String?, campusId: String): String =
        "buildings_${semesterId.orEmpty()}_$campusId"
}
