package com.hutb.timetable.core

/**
 * 课表数据模型。
 *
 * 教务系统返回的是 HTML 表格（强智 jsxsd 服务端渲染），解析后统一收敛到这里的领域模型，
 * 界面层不直接依赖任何厂商字段名。
 */

/** 一节课。 */
data class Course(
    /** 课程名称 */
    val courseName: String,
    /** 任课教师 */
    val teacher: String = "",
    /** 上课班级 */
    val className: String = "",
    /** 教室位置，如「至诚楼 A201」 */
    val classroom: String = "",
    /** 星期，1=周一 … 7=周日 */
    val weekday: Int,
    /** 起始节次，1-based，如 1 表示第 1-2 节 */
    val startPeriod: Int,
    /** 结束节次（含），如 2 表示第 1-2 节 */
    val endPeriod: Int,
    /** 教学周描述，如「1-8周(单)」，可为空 */
    val weekText: String = "",
    /** 该课程适用的周次集合（1-based），空表示未解析出具体周次 */
    val weeks: List<Int> = emptyList(),
    /** 课程性质/属性，如「必修」 */
    val courseType: String = ""
) {
    /** 本节在周视图网格中占据的行跨度（节次数）。 */
    val periodSpan: Int get() = (endPeriod - startPeriod + 1).coerceAtLeast(1)
}

/** 某个教学周的整周课表。 */
data class WeekSchedule(
    /** 学年学期 ID，如 2025-2026-1 */
    val semesterId: String,
    /** 学年学期显示名，如「2025-2026学年第一学期」 */
    val semesterName: String,
    /** 教学周次，1-based */
    val week: Int,
    /** 该周全部课程 */
    val courses: List<Course>,
    /** true 表示这份数据来自本地缓存（离线状态） */
    val isFromCache: Boolean = false,
    /** 教室查询时的范围描述，如「至诚楼 A201」；个人课表为空 */
    val roomLabel: String = "",
    /** 解析过程中无法归位的行（教务系统改版时用于暴露问题） */
    val parseIssues: List<ParseIssue> = emptyList()
) {
    /** 按星期 + 起始节次排序，便于网格渲染。 */
    val sorted: List<Course>
        get() = courses.sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.endPeriod }))
}

/**
 * 解析问题：一条无法归位到任何已知字段（教师/班级/教室/周次/性质）的文本行。
 *
 * 不静默丢弃也不强行拼进课程名：保留原文与原因，debug 包展示、release 包写日志，
 * 教务系统改版导致字段变化时能第一时间发现。
 */
data class ParseIssue(
    /** 原始文本行 */
    val line: String,
    /** 无法归位的原因 */
    val reason: String
)

/** 可选学年学期。 */
data class Semester(
    val id: String,
    val name: String
)

/**
 * 场地（教学楼 / 教室）选项。
 *
 * [code] 对应强智教务系统的 `zcdm`（场地代码），提交查询时使用；
 * [name] 是页面上的显示文本，如「至诚楼A201」。
 */
data class RoomOption(
    val code: String,
    val name: String
)

/** 系统内部编码形态：16 位以上的纯十六进制串（常见于 MD5 化的场地 ID）。 */
private val OPAQUE_ROOM_NAME_REGEX = Regex("""^[0-9A-Fa-f]{16,}$""")

/**
 * 判断名称是否只是系统内部编码而不是可读名称。
 *
 * 教务系统的级联下拉框有时只给出哈希形式的场地 ID（如 `6F57A89B0A67435AB9E528529A6F2840`），
 * 这种串直接展示给用户就是"乱码"，需要另行推导可读名称。
 */
fun isOpaqueRoomName(name: String?): Boolean {
    val text = name?.trim().orEmpty()
    return text.isEmpty() || OPAQUE_ROOM_NAME_REGEX.matches(text)
}

/**
 * 从教室名反推教学楼名。
 *
 * 「风华楼302」→「风华楼」，「至诚楼A201」→「至诚楼A」。
 * 无法反推时返回空串，由调用方决定兜底文案。
 */
fun deriveBuildingName(roomName: String?): String {
    val text = roomName?.trim().orEmpty()
    if (text.isEmpty()) return ""
    val stripped = text.replace(Regex("""\d+$"""), "").trim()
    val candidate = stripped.ifEmpty { text }
    // 反推结果里还得有可读内容，否则不如不显示
    return if (candidate.any { it.code in 0x4E00..0x9FFF }) candidate else ""
}

/** 教务系统里教学楼名可能带的校区前缀，如「月亮岛立德楼」。 */
private val CAMPUS_PREFIXES = listOf(
    "湖南工商大学", "月亮岛校区", "月亮岛", "南校区", "北校区", "东校区", "西校区", "校区"
)

/**
 * 去掉教学楼名里的校区前缀，得到能和教室名比较的核心名。
 *
 * 「月亮岛立德楼」→「立德楼」。
 */
fun buildingCoreName(buildingName: String?): String {
    val text = buildingName?.trim().orEmpty()
    if (text.isEmpty()) return ""
    for (prefix in CAMPUS_PREFIXES) {
        if (text.length > prefix.length && text.startsWith(prefix)) {
            val rest = text.substring(prefix.length)
            if (rest.any { it.code in 0x4E00..0x9FFF }) return rest
        }
    }
    return text
}

/**
 * 教室名是否属于指定教学楼。
 *
 * 服务端返回的教室首列形如「至诚楼A101*(智慧)」，
 * 教学楼名是它的前缀（「至诚楼」）。教学楼名为空或不可读时不做判定。
 */
fun roomBelongsToBuilding(roomLabel: String, buildingName: String?): Boolean {
    val core = buildingCoreName(buildingName)
    if (core.isEmpty()) return true
    // 教学楼名是哈希编码时无从比对，交由调用方兜底
    if (isOpaqueRoomName(core)) return true
    val label = roomLabel.trim()
    if (label.isEmpty()) return false
    return label.contains(core, ignoreCase = true)
}

/**
 * 教室名里的括号注记，如「至诚楼A101*(智慧)」的「*(智慧)」。
 * 全角/半角括号都可能出现。
 */
private val ROOM_ANNOTATION_REGEX = Regex("""\*?[（(][^（()）]*[)）]""")

/**
 * 精简教室名：剥掉教学楼前缀与括号注记，只留房间编号。
 *
 * 「乐知楼E101*(智慧)」（教学楼「乐知楼」）→「E101」，
 * 「立德楼A401」（教学楼「月亮岛立德楼」）→「A401」。
 * 教室名本身就是教学楼名（如「体育馆」）或剥完为空时回退原名。
 */
fun shortRoomName(roomLabel: String, buildingName: String? = null): String {
    val original = roomLabel.trim()
    if (original.isEmpty()) return original
    val stripped = original
        .replace(ROOM_ANNOTATION_REGEX, "")
        // 没有括号注记的孤立星号也要去掉，如「至诚楼B405*」「日新楼D304*」
        .replace("*", "")
        .trim()
    val core = buildingCoreName(buildingName)
    val withoutBuilding = if (core.isNotEmpty() && stripped.contains(core)) {
        stripped.replace(core, "").trim()
    } else {
        stripped
    }
    return withoutBuilding.ifEmpty { stripped.ifEmpty { original } }
}

/**
 * 教室名的身份键：剥掉括号注记（含全角/半角）、星号与空白，只用于比较。
 *
 * 教室清单与课表里的同一间教室注记写法可能不同
 * （「乐知楼E101*(智慧)」vs「乐知楼E101*（智慧）」），
 * 直接按字符串合并会出现两行同名教室。
 */
fun roomIdentity(roomLabel: String): String = roomLabel
    .replace(ROOM_ANNOTATION_REGEX, "")
    .replace("*", "")
    .replace(Regex("""\s+"""), "")
    .trim()

/**
 * 课表筛选条件：星期几 + 第几到第几节。
 *
 * 教务系统的教室课表页面只有"节次"下拉（`jc1`/`jc2`），没有星期筛选，
 * 且服务端不认任何星期参数，因此星期筛选只能在客户端做。
 * 节次筛选同样放在客户端：改一次筛选就重新拉一次结果页要等 1~50 秒，
 * 而筛选本身是纯展示层的过滤，没必要走网络。
 */
data class ScheduleFilter(
    /** 选中的星期（1=周一 … 7=周日），空表示不限 */
    val weekdays: Set<Int> = emptySet(),
    /** 起始节次（含） */
    val startPeriod: Int = 1,
    /** 结束节次（含） */
    val endPeriod: Int = MAX_PERIOD
) {
    /** 是否限制了星期。 */
    val limitsWeekday: Boolean get() = weekdays.isNotEmpty()

    /** 是否限制了节次。 */
    val limitsPeriod: Boolean get() = startPeriod > 1 || endPeriod < MAX_PERIOD

    /** 是否有筛选生效。 */
    val isActive: Boolean get() = limitsWeekday || limitsPeriod

    /** 筛选后要显示的星期（升序）。 */
    fun visibleWeekdays(): List<Int> =
        if (weekdays.isEmpty()) (1..7).toList() else weekdays.sorted()

    /** 该课程是否落在筛选范围内。 */
    fun matches(course: Course): Boolean {
        if (weekdays.isNotEmpty() && course.weekday !in weekdays) return false
        // 节次没解析出来的课程无从判断归属，不因节次筛选被误伤
        if (course.endPeriod <= 0) return true
        val start = course.startPeriod.coerceAtLeast(1)
        return start <= endPeriod && course.endPeriod >= startPeriod
    }

    /** 按筛选条件过滤课程。 */
    fun apply(courses: List<Course>): List<Course> =
        if (!isActive) courses else courses.filter { matches(it) }

    /** 切换某一天的选中状态；至少保留一天。 */
    fun toggleWeekday(weekday: Int): ScheduleFilter {
        val next = if (weekday in weekdays) {
            if (weekdays.size == 1) this else copy(weekdays = weekdays - weekday)
        } else {
            copy(weekdays = weekdays + weekday)
        }
        return next
    }

    /** 设置节次区间，起始节次不大于结束节次。 */
    fun withPeriodRange(start: Int, end: Int): ScheduleFilter {
        val s = start.coerceIn(1, MAX_PERIOD)
        return copy(startPeriod = s, endPeriod = end.coerceIn(s, MAX_PERIOD))
    }

    companion object {
        /**
         * 一天最多 12 节。
         *
         * 教务系统 `/jsxsd/kbxx/initJc` 返回 `mtdjs=6`，即每天 6 个节次对
         * （0102/0304/0506/0708/0910/1112），共 12 节。
         */
        const val MAX_PERIOD = 12
    }
}

/** 课表查询请求参数。 */
data class ScheduleQuery(
    val semesterId: String,
    val week: Int,
    /** 场地代码（zcdm），为空表示查询个人课表 */
    val roomCode: String? = null,
    /** 教学楼/教室名称，仅用于前端过滤与兜底匹配 */
    val building: String? = null,
    /** 校区 ID（xqid），教室查询时必填 */
    val campusId: String? = null,
    /** 教学楼 ID（jzwid），教室查询时可选 */
    val buildingId: String? = null,
    /**
     * 是否为教室（教学安排）模式。
     *
     * 即使没选具体教室，只要处于教室模式，也应走教室查询接口
     * （返回整栋楼/全校的安排），而不是个人课表接口。
     */
    val roomMode: Boolean = false
) {
    /** 是否为教室（教学楼）课表查询。 */
    val isRoomQuery: Boolean
        get() = roomMode || !roomCode.isNullOrBlank() || !building.isNullOrBlank()

    /**
     * 缓存分区标识。
     *
     * 个人课表与教室课表分开缓存，避免互相覆盖。
     */
    val cacheScope: String
        get() = if (isRoomQuery) {
            "room|${campusId.orEmpty()}|${buildingId.orEmpty()}|${roomCode.orEmpty()}"
        } else {
            "personal"
        }

    /**
     * 两次查询是否对应**同一份可展示数据**：学期 + 周次 + 场地选择全部一致。
     *
     * 用于判断"确定教室选择后是否真的需要重新请求"；
     * 与 [cacheScope] 的区别是这里包含学期与周次，避免陈旧的查询条件误判为相同。
     */
    fun sameSnapshot(other: ScheduleQuery): Boolean =
        semesterId == other.semesterId && week == other.week && cacheScope == other.cacheScope
}
