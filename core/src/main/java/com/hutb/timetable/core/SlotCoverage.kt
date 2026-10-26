package com.hutb.timetable.core

/** 课程在一个"两节一组"的格子里的实际占用范围。 */
enum class SlotCoverage {
    /** 完全不落在该格子。 */
    NONE,

    /** 只占上半格（如 1-3 节的课落在 3-4 节格子里）。 */
    TOP_HALF,

    /** 只占下半格（如 2-3 节的课落在 1-2 节格子里）。 */
    BOTTOM_HALF,

    /** 上下半格都被课程覆盖。 */
    FULL
}

/**
 * 计算课程与节次对格子的交集。
 *
 * 课表按 1-2、3-4 两节一格展示，但课程可能从奇数节开始、
 * 到奇数节结束；按整格绘制会夸大占用，需要按半格裁剪。
 */
fun slotCoverage(
    courseStart: Int,
    courseEnd: Int,
    slotStart: Int,
    slotEnd: Int
): SlotCoverage {
    if (courseStart <= 0 || courseEnd < courseStart) return SlotCoverage.NONE
    if (courseEnd < slotStart || courseStart > slotEnd) return SlotCoverage.NONE
    val coversTop = courseStart <= slotStart
    val coversBottom = courseEnd >= slotEnd
    return when {
        coversTop && coversBottom -> SlotCoverage.FULL
        coversTop -> SlotCoverage.TOP_HALF
        coversBottom -> SlotCoverage.BOTTOM_HALF
        else -> SlotCoverage.NONE
    }
}
