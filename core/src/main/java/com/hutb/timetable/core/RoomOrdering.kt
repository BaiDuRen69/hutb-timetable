package com.hutb.timetable.core

/**
 * 教室课表矩阵里各教室行的排序方式。
 */
enum class RoomSortMode {
    /**
     * 楼栋优先：同一栋楼（字母相同）排在一起，楼内按房间编号。
     *
     * E101、E102、E201…、F101、F102…
     */
    BY_BUILDING,

    /**
     * 编号优先：先按房间编号，编号相同再按楼栋字母。
     *
     * E101、F101、E102、F102…
     */
    BY_NUMBER,

    /**
     * 楼层优先：先按楼层，同层再按楼栋字母、房间编号。
     *
     * 1F：E101、E102…、F101、F102…；2F：E201、E202…
     */
    BY_FLOOR
}

/**
 * 按 [mode] 排序教室名列表。
 *
 * 教室名形如「至诚楼A101*(智慧)」「乐知楼E602(计信)」「E101」。
 * 排序键从名称中提取：
 * - **楼栋字母**：名称里紧挨房间编号的字母（A101 → A）；提取不到时排最后；
 * - **房间编号**：名称里的数字（A101-2 取 101）；
 * - **楼层**：编号 ÷ 100（101 → 1F，1201 → 12F），提取不到编号时排最后。
 *
 * 名称相同时按原字符串比较，保证顺序稳定。
 */
fun sortRoomLabels(labels: List<String>, mode: RoomSortMode): List<String> {
    if (labels.size <= 1) return labels
    val keys = labels.map { RoomSortKey.of(it) }
    val comparator: Comparator<RoomSortKey> = when (mode) {
        RoomSortMode.BY_BUILDING -> compareBy<RoomSortKey> { it.buildingRank }
            .thenBy { it.numberRank }
            .thenBy { it.label }

        RoomSortMode.BY_NUMBER -> compareBy<RoomSortKey> { it.numberRank }
            .thenBy { it.buildingRank }
            .thenBy { it.label }

        RoomSortMode.BY_FLOOR -> compareBy<RoomSortKey> { it.floorRank }
            .thenBy { it.buildingRank }
            .thenBy { it.numberRank }
            .thenBy { it.label }
    }
    return keys.sortedWith(comparator).map { it.label }
}

/** 教室名的排序键。 */
private data class RoomSortKey(
    val label: String,
    val building: String,
    val number: Int
) {
    /** 无楼栋字母的排最后（'~' 大于任何字母）。 */
    val buildingRank: String get() = building.ifEmpty { "~" }

    /** 无房间编号的排最后。 */
    val numberRank: Int get() = if (number > 0) number else Int.MAX_VALUE

    /** 无编号时无法判断楼层，排最后。 */
    val floorRank: Int get() = if (number > 0) number / 100 else Int.MAX_VALUE

    companion object {
        /** 括号注记，如「至诚楼A101*(智慧)」的「*(智慧)」。 */
        private val ANNOTATION_REGEX = Regex("""\*?[（(][^（()）]*[)）]""")

        /** 楼栋字母 + 房间编号，兼容「A101」「B座304」「A-201」「C栋502」。 */
        private val LETTER_NUMBER_REGEX = Regex("""([A-Za-z]{1,3})\s*(?:座|栋|号楼|[-–—])?\s*(\d{2,4})""")

        private val LETTER_REGEX = Regex("""[A-Za-z]{1,3}""")
        private val NUMBER_REGEX = Regex("""\d{1,4}""")

        fun of(label: String): RoomSortKey {
            val clean = label
                .replace(ANNOTATION_REGEX, "")
                .replace("*", "")
                .trim()

            val combined = LETTER_NUMBER_REGEX.findAll(clean).lastOrNull()
            if (combined != null) {
                return RoomSortKey(
                    label = label,
                    building = combined.groupValues[1].uppercase(),
                    number = combined.groupValues[2].toIntOrNull() ?: 0
                )
            }

            // 组合模式识别不了时（如「J1-101」），退化为"第一个字母 + 最后一段数字"
            val number = NUMBER_REGEX.findAll(clean).lastOrNull()?.value?.toIntOrNull() ?: 0
            val building = LETTER_REGEX.find(clean)?.value?.uppercase().orEmpty()
            return RoomSortKey(label = label, building = building, number = number)
        }
    }
}
