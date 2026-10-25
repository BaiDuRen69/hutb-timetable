package com.hutb.timetable.core

/**
 * 教室选择相关的纯逻辑（从 ViewModel 下沉，便于单测）。
 */

/**
 * 教学楼名称只给出哈希编码时，用教室名反推可读名称。
 *
 * 「6F57A89B0A67435AB9E528529A6F2840」→「风华楼」（取自「风华楼302」）。
 * 列表里可能混着别的教学楼，取出现次数最多的那个。
 */
fun resolveBuildingName(current: String?, classrooms: List<RoomOption>): String? {
    if (!isOpaqueRoomName(current)) return current
    return classrooms.asSequence()
        .map { deriveBuildingName(it.name) }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: current
}

/**
 * 把教室列表收窄到指定教学楼。
 *
 * 服务端返回的表里会混入其它教学楼的教室；
 * 收窄后为空时保留原列表，避免教学楼名对不上导致列表变空。
 */
fun scopeToBuilding(classrooms: List<RoomOption>, buildingName: String?): List<RoomOption> {
    if (buildingName.isNullOrBlank()) return classrooms
    val scoped = classrooms.filter { roomBelongsToBuilding(it.name, buildingName) }
    return if (scoped.isEmpty()) classrooms else scoped
}
