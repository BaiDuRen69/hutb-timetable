package com.hutb.timetable.core

/**
 * 强智课表页的"语义层"：把单元格文本行归位到
 * 节次 / 课程名 / 教师 / 班级 / 教室 / 周次 / 课程性质。
 *
 * 与 JsxsdGrid.kt 的"结构层"（表格定位、网格展开、切行）分开：
 * 结构层只关心 HTML 长什么样，语义层只关心文本是什么意思。
 */

/** 紧凑节次写法："0102" 表示第 1-2 节，个人课表里写成 "0102节"。 */
private val COMPACT_PERIOD_REGEX = Regex("""^(\d{2})(\d{2})\s*节?$""")

/** 课程块内的节次标记，如 "[01-02-03]节"。 */
private val PERIOD_MARKER_REGEX = Regex("""^\[(\d{1,2}(?:\s*[-－~]\s*\d{1,2})*)\]\s*节?$""")

/** 紧凑周次写法："1-16(单)"，标签里的"周次"字样在渲染时被去掉了。 */
private val COMPACT_WEEK_REGEX =
    Regex("""^\d{1,2}\s*[-~－]\s*\d{1,2}\s*[（(]?\s*[单双]?\s*[)）]?$""")

/** 一门课的原始文本行集合。 */
internal data class CourseBlock(
    val name: String = "",
    val teacher: String = "",
    val className: String = "",
    val classroom: String = "",
    val weekText: String = "",
    val courseType: String = "",
    /** 课程自带的节次标记，如 "[01-02-03]节"，比表格行的节次更准。 */
    val periodRange: Pair<Int, Int>? = null,
    /** 该课程块包含的全部原始文本行，用于二次信息提取 */
    val lines: List<String> = emptyList(),
    /** 归位不到任何已知字段的行（用于解析 issue 上报，不静默丢弃） */
    val unresolvedLines: List<String> = emptyList()
)

/**
 * 从一组文本行里找出节次信息。
 *
 * 只接受"纯节次行"（如 "第1-2节 08:00-09:35"），
 * 避免把课程名里的数字（如 "C语言程序设计(第3版)"）误判成节次。
 */
internal fun findPeriodInLines(lines: List<String>): Pair<Int, Int>? {
    for (line in lines) {
        val clean = line.trim()
        if (clean.isEmpty()) continue
        val looksLikePeriod = clean.contains("节") ||
            Regex("""\d{1,2}\s*[:：]\s*\d{2}""").containsMatchIn(clean)
        if (!looksLikePeriod) continue
        val parsed = parsePeriods(clean) ?: continue
        // 只有当整行几乎只有节次/时间时才采信
        if (clean.length > 24) continue
        return parsed
    }
    return null
}

/**
 * 从"节次"单元格解析出起始与结束节次。
 * 典型文本："第1-2节 08:00-09:35"、"1-2"、"第一节"
 *
 * 教室课表的节次表头用紧凑写法："0102" 表示第 1-2 节、"1112" 表示第 11-12 节。
 * 紧凑写法与场地代码（如 "0301"）同形，因此要求前半段不大于后半段，
 * 且两段都落在合理节次区间内，"0301" 这类才会被排除。
 *
 * 这里先做一次**形态校验**再抽数字，避免把"至诚楼A201"、
 * "软件2202"这类含数字的教室/班级名误判成节次。
 */
internal fun parsePeriods(text: String): Pair<Int, Int>? {
    // 表格里的 &nbsp; 会被 JSoup 还原成不换行空格，它既不被 trim 也不匹配 \s
    val trimmed = text.replace(' ', ' ').trim()
    if (trimmed.isEmpty()) return null

    // 紧凑写法：两位一段，如 "0102" -> (1, 2)
    COMPACT_PERIOD_REGEX.find(trimmed)?.let { match ->
        val start = match.groupValues[1].toIntOrNull()
        val end = match.groupValues[2].toIntOrNull()
        if (start != null && end != null &&
            start in 1..ScheduleFilter.MAX_PERIOD && end in 1..ScheduleFilter.MAX_PERIOD && start <= end
        ) {
            return start to end
        }
        return null
    }

    // 只有下列形态才认为是节次标签
    val looksLikePeriodLabel =
        trimmed.contains("节") ||
            trimmed.matches(Regex("""\d{1,2}\s*[-~－]\s*\d{1,2}""")) ||
            trimmed.matches(Regex("""\d{1,2}""")) ||
            trimmed.matches(Regex("""\d{1,2}\s*[:：]\s*\d{2}\s*[-~－]\s*\d{1,2}\s*[:：]\s*\d{2}"""))
    if (!looksLikePeriodLabel) return null

    val clean = trimmed.replace("第", " ").replace("节", " ")
    val range = Regex("""(\d{1,2})\s*[-~－]\s*(\d{1,2})""").find(clean)
    if (range != null) {
        val start = range.groupValues[1].toIntOrNull()
        val end = range.groupValues[2].toIntOrNull()
        if (start != null && end != null && start in 1..ScheduleFilter.MAX_PERIOD) {
            return start to maxOf(start, end)
        }
    }
    val single = Regex("""(\d{1,2})""").find(clean)
    if (single != null) {
        val p = single.groupValues[1].toIntOrNull()
        if (p != null && p in 1..ScheduleFilter.MAX_PERIOD) return p to p
    }
    return null
}

/**
 * 解析课程块里的节次标记。
 *
 * 强智在隐藏的 `div.kbcontent` 里用 "[01-02-03]节" 标出这门课占用的节次，
 * 连堂课会写成三段数字。第一段是起始节，最后一段是结束节。
 */
private fun parsePeriodMarker(line: String): Pair<Int, Int>? {
    val match = PERIOD_MARKER_REGEX.find(line.trim()) ?: return null
    val numbers = Regex("""\d{1,2}""")
        .findAll(match.groupValues[1])
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
    if (numbers.isEmpty()) return null
    val start = numbers.first()
    val end = numbers.last()
    if (start !in 1..ScheduleFilter.MAX_PERIOD || end !in 1..ScheduleFilter.MAX_PERIOD) return null
    return start to maxOf(start, end)
}

/**
 * 把一个单元格内的文本行切分成一门或多门课。
 *
 * 启发式：课程名通常是该门课的第一个文本行。
 * 当已出现"看起来像课程名"的行，且当前课已收集到教师/班级/教室/周次之一时，
 * 认为开始了新的一门课。
 */
internal fun groupCourseBlocks(lines: List<String>): List<CourseBlock> {
    if (lines.isEmpty()) return emptyList()
    val blocks = mutableListOf<CourseBlock>()
    var current = CourseBlock()
    val currentLines = mutableListOf<String>()

    fun flush() {
        if (current.name.isNotEmpty()) blocks.add(current.copy(lines = currentLines.toList()))
        current = CourseBlock()
        currentLines.clear()
    }

    for (line in lines) {
        // 节次标记 "[01-02-03]节" 长得像课程名，必须先摘出来
        val marker = parsePeriodMarker(line)
        if (marker != null) {
            current = current.copy(periodRange = marker)
            currentLines.add(line)
            continue
        }
        if (current.name.isEmpty()) {
            current = current.copy(name = line)
            currentLines.add(line)
            continue
        }
        val looksLikeNewCourse = isNameLike(line) &&
            (current.teacher.isNotEmpty() || current.className.isNotEmpty() ||
                current.classroom.isNotEmpty() || current.weekText.isNotEmpty())
        if (looksLikeNewCourse) {
            flush()
            current = CourseBlock(name = line)
            currentLines.add(line)
            continue
        }
        currentLines.add(line)
        current = when {
            current.weekText.isEmpty() && isWeekText(line) ->
                current.copy(weekText = line)
            current.teacher.isEmpty() && isTeacher(line) ->
                current.copy(teacher = line)
            current.className.isEmpty() && isClassName(line) ->
                current.copy(className = line)
            current.classroom.isEmpty() && isClassroom(line) ->
                current.copy(classroom = line)
            current.courseType.isEmpty() && isCourseType(line) ->
                current.copy(courseType = line)
            // 识别不出的行不强行拼进课程名：错归位比漏一行危害大得多；
            // 也不静默丢弃——记进 unresolvedLines，由上层汇总成解析 issue
            else -> current.copy(unresolvedLines = current.unresolvedLines + line)
        }
    }
    flush()
    return blocks
}

internal fun toCourse(
    block: CourseBlock,
    weekday: Int,
    startPeriod: Int,
    endPeriod: Int
): Course? {
    val name = block.name.trim()
    if (name.isEmpty()) return null
    // 过滤占位符
    if (name.contains("暂无") || name.contains("无安排") ||
        name.contains("空教室") || name.contains("暂无安排")
    ) return null
    if (name.all { it == '-' || it == '—' || it == '/' || it.isWhitespace() }) return null

    return Course(
        courseName = name,
        teacher = block.teacher.trim(),
        className = block.className.trim(),
        classroom = block.classroom.trim(),
        weekday = weekday,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weekText = block.weekText.trim(),
        weeks = JsxsdScheduleParser.parseWeeks(block.weekText),
        courseType = block.courseType.trim()
    )
}

// ---------------- 语义判定规则 ----------------

/** "1-8周(单)"、"第1-16周" 之类的周次文本。 */
internal fun isWeekText(line: String): Boolean {
    if (line.contains("周") && Regex("""\d+""").containsMatchIn(line)) return true
    // 课表单元格里渲染出来的是 "1-16(单)"，"周次"二字只存在于 title 属性里
    return COMPACT_WEEK_REGEX.matches(line.trim())
}

/**
 * 是否像"课程名"：不属于教师/班级/教室/周次/性质的短文本。
 * 注意：不能仅靠"2-4 个汉字"判断，否则"数据结构"这类课程名会被误判为教师。
 * 含冒号的行（"备注：调课"等注释行）不算课程名，避免整行变成幻影课。
 */
internal fun isNameLike(line: String): Boolean =
    !line.contains("：") && !line.contains(":") &&
        !isTeacher(line) && !isClassName(line) && !isClassroom(line) &&
        !isWeekText(line) && !isCourseType(line)

/** 教师名：2-4 个汉字，或含"教授/老师/讲师"等职称，也支持多人（"郝怡然,刘东"）。 */
internal fun isTeacher(line: String): Boolean {
    if (line.contains("(") || line.contains("（")) return false
    if (line.contains("楼") || line.contains("教室")) return false
    if (Regex("""\d""").containsMatchIn(line)) return false
    if (line.length !in 2..9) return false
    val teacherKeywords = listOf("教授", "老师", "讲师", "副教授", "助教")
    if (teacherKeywords.any { line.contains(it) }) return true
    return line.split(",", "，").all { it.matches(Regex("""[\u4e00-\u9fa5]{2,4}""")) }
}

/**
 * 班级名：含"班"，或"专业代码+班号"形式（如 计科2201 / 软件2202 / 会计学2103），
 * 也允许带"-2"这种合班后缀（如 体育教育2401-2）。
 */
internal fun isClassName(line: String): Boolean {
    if (line.contains("楼") || line.contains("教室")) return false
    if (line.contains("班")) return true
    return line.matches(Regex("""[\u4e00-\u9fa5]{2,6}\d{2,4}(-\d{1,2})?"""))
}

/** 教室：含"楼"/"教室"，或形如 "A201"、"J1-101"。 */
internal fun isClassroom(line: String): Boolean {
    if (line.contains("楼") || line.contains("教室")) return true
    return line.matches(Regex("""[A-Za-z]?\d{1,2}[-]?\d{2,4}"""))
}

/** 课程性质：必修/选修/限选等。 */
internal fun isCourseType(line: String): Boolean {
    val keywords = listOf("必修", "选修", "限选", "任选", "辅修", "实践")
    return keywords.any { line.contains(it) } && line.length <= 8
}
