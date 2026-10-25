package com.hutb.timetable.core

/**
 * core 模块的可注入日志出口。
 *
 * core 是纯 JVM 模块，不依赖 android.util.Log；
 * 宿主（App / CLI）在启动时设置 [sink] 即可收到解析过程的诊断日志，
 * 例如学期解析命中了哪一级策略——线上"学期列表拿不到"时能直接定位降级到哪一层。
 * 未设置 sink 时所有日志调用是无副作用的空操作。
 */
object CoreLog {
    @Volatile
    var sink: ((String) -> Unit)? = null

    fun d(message: String) {
        val s = sink ?: return
        // 日志系统故障绝不能反过来打断解析
        runCatching { s(message) }
    }
}
