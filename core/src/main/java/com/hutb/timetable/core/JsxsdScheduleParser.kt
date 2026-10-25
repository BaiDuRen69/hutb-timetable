package com.hutb.timetable.core

import org.jsoup.nodes.Element

/**
 * 强智 jsxsd 课表页面解析器（公共门面）。
 *
 * 强智 jsxsd 的课表页面（`/jsxsd/kszx/cxks`、`/jsxsd/xskb/xskb_list.do` 等）
 * 服务端渲染为 HTML 表格，典型结构：
 *
 * ```html
 * <table id="kbtable">
 *   <tr><th>节次\星期</th><th>星期一</th> ... <th>星期日</th></tr>
 *   <tr>
 *     <td>第1-2节<br>08:00-09:35</td>
 *     <td>
 *       <div>高等数学(上)</div>
 *       <div>张三</div>
 *       <div>计科2201</div>
 *       <div>至诚楼A201</div>
 *       <div>1-16周(单)</div>
 *     </td>
 *   </tr>
 * </table>
 * ```
 *
 * 不同学校/版本的表头、单元格内部标签与换行规则不完全一致，
 * 因此这里采取"宽松提取"策略：
 * 1. 单元格内先按 `<br>` / 块级标签切成文本行；
 * 2. 同一单元格内可能有多门课（上下午不同课程），按"名字行"启发式切分；
 * 3. 每门课内部再按语义规则把各行归位到教师/班级/教室/周次。
 *
 * 该策略已在多组合成样本上验证（见 README「解析策略」一节）。
 *
 * 实现按职责拆分在三个文件里（本类只做编排与公共入口）：
 * - [JsxsdGridKt]：表格定位、网格展开、星期识别、单元格切行；
 * - [JsxsdSemanticKt]：节次/周次/课程名等语义判定与课程块组装；
 * - [JsxsdSemestersKt]：学期列表的多策略解析。
 */
object JsxsdScheduleParser {

    /** 解析个人课表 HTML。 */
    fun parseSchedule(
        html: String,
        semesterId: String,
        semesterName: String,
        week: Int
    ): WeekSchedule {
        val doc = parseJsxsdHtml(html)
        val table = findScheduleTable(doc)
            ?: throw HutbError.Parse("未找到课表表格")

        val headerRow = findHeaderRow(table)
        val dayColumns = detectDayColumns(headerRow)
        if (dayColumns.isEmpty()) {
            // 表头结构变了：宁可明确报错，也不按列序猜星期后交出一张错位的课表
            throw HutbError.Parse("无法识别星期表头")
        }
        val courses = mutableListOf<Course>()
        val issues = mutableListOf<ParseIssue>()

        // 注意：Jsoup 1.18 已移除 Element.rows()，改用 select("tr")
        val rows = table.select("tr").toList()
        val headerIndex = headerRow?.let { hr -> rows.indexOfFirst { it == hr } } ?: -1
        val dataRows = if (headerIndex >= 0) rows.drop(headerIndex + 1) else rows
        for (row in dataRows) {
            // 行首的节次标签放在 <th> 里，星期数据在 <td> 里，
            // 两者必须一起数，否则星期列会整体错位一列。
            val cells = row.select("th, td")
            if (cells.isEmpty()) continue

            // 节次以行首标签为准（如 "0102节"），不能从课程文本里猜——
            // 课程文本里的 "1-16(周)" 是周次，不是节次。
            val rowPeriod = parsePeriods(cells[0].text()) ?: continue

            for ((columnIndex, weekday) in dayColumns) {
                if (columnIndex >= cells.size) continue
                for (block in parsePersonalCell(cells[columnIndex])) {
                    block.unresolvedLines.forEach { line ->
                        issues.add(ParseIssue(line, "无法归位到教师/班级/教室/周次"))
                    }
                    val course = toCourse(block, weekday, rowPeriod.first, rowPeriod.second)
                    if (course == null) continue
                    // 课程自带的 "[01-02-03]节" 更准：连堂课会横跨多行
                    val period = block.periodRange ?: rowPeriod
                    courses.add(course.copy(startPeriod = period.first, endPeriod = period.second))
                }
            }
        }

        // 连堂课在服务端按行重复出现（同一门课同时写在 0102 行与 0304 行），
        // 这里按"星期+课程+节次"去重，只保留一份。
        // className 必须参与去重：平行班同名课（同教师/教室/时间）若被合并会静默丢课。
        val deduped = courses.distinctBy {
            listOf(
                it.weekday,
                it.courseName,
                it.teacher,
                it.className,
                it.classroom,
                it.weekText,
                it.startPeriod,
                it.endPeriod
            ).joinToString("|")
        }

        val dedupedIssues = issues.distinctBy { it.line }
        dedupedIssues.forEach { CoreLog.d("解析提示：未能识别「${it.line}」（${it.reason}）") }

        return WeekSchedule(
            semesterId = semesterId,
            semesterName = semesterName,
            week = week,
            courses = deduped,
            parseIssues = dedupedIssues
        )
    }

    /**
     * 解析个人课表的一个格子。
     *
     * 强智把每门课渲染成两个 div：
     * - `div.kbcontent1`：可见部分（课程名 / 周次 / 教室 / 班级）
     * - `div.kbcontent`：隐藏部分，多了老师与 `[01-02]节` 节次标记
     *
     * 两个 div 指的是同一门课，只取隐藏的那个，
     * 否则同一门课会被算成两门，还会多出一门叫 "[01-02]节" 的假课。
     */
    private fun parsePersonalCell(cell: Element): List<CourseBlock> {
        val divs = cell.select("div.kbcontent").ifEmpty { cell.select("div.kbcontent1") }
        if (divs.isEmpty()) {
            // 老版页面没有 div 包裹，按整格文本处理
            return groupCourseBlocks(cellLines(cell))
        }
        return divs.flatMap { div -> groupCourseBlocks(cellLines(div)) }
    }

    /** 解析学年学期列表（多策略逐级降级，见 JsxsdSemesters.kt）。 */
    fun parseSemesters(html: String): List<Semester> = parseSemestersWithStrategies(html)

    /**
     * 解析"教室课表"（教学安排视图）。
     *
     * 与个人课表不同：教学安排表格的**行是场地**，列是星期，
     * 形如：
     *
     * ```html
     * <table id="kbtable">
     *   <tr><th>场地\星期</th><th>星期一</th> ... <th>星期日</th></tr>
     *   <tr>
     *     <td>至诚楼A201</td>
     *     <td><div>高等数学(上)</div><div>张三</div><div>计科2201</div><div>1-16周</div></td>
     *     ...
     *   </tr>
     * </table>
     * ```
     *
     * 教室信息来自行首单元格，因此这里在解析完单元格内容后，
     * 用行首文本补全 [Course.classroom]。
     *
     * 实际部署中该表可能有多种形态，本实现通过"表头列推断 + rowspan/colspan 展开"
     * 兼容以下几种：
     * 1. 单行表头（星期） + 每行一个场地；
     * 2. 双行表头（星期分组 + 节次）+ 每行一个场地；
     * 3. 单行表头（星期）+ 每行一个节次（与个人课表同构，此时教室取查询参数）。
     */
    fun parseRoomSchedule(
        html: String,
        semesterId: String,
        semesterName: String,
        week: Int,
        roomCode: String? = null,
        roomName: String? = null,
        building: String? = null
    ): WeekSchedule {
        val doc = parseJsxsdHtml(html)
        val table = findScheduleTable(doc)
            ?: throw HutbError.Parse("未找到教学安排表格")

        val grid = expandGrid(table)

        // 找到含星期的表头行：要求至少两个互不相同的星期单元格，
        // 避免课程名里的「高等数学(一)」「日语」把数据行误判成表头
        val headerIdx = grid.indexOfFirst { row ->
            row.mapNotNull { it.text().toWeekdayOrNull() }.distinct().size >= 2
        }
        if (headerIdx < 0) throw HutbError.Parse("未找到星期表头")

        // 节次表头行（紧跟在星期表头下方，且整行都只有节次/时间）
        val periodRowIdx =
            if (headerIdx + 1 < grid.size && isPeriodHeaderRow(grid[headerIdx + 1].map { it.text() })) {
                headerIdx + 1
            } else {
                -1
            }

        val dataStart = if (periodRowIdx >= 0) periodRowIdx + 1 else headerIdx + 1

        // 星期 → 列索引 的映射
        val weekdayByColumn = mutableMapOf<Int, Int>()
        for (c in grid[headerIdx].indices) {
            val wd = grid[headerIdx][c].text().toWeekdayOrNull()
                ?: (if (headerIdx > 0) grid[headerIdx - 1].getOrNull(c)?.text()?.toWeekdayOrNull() else null)
            if (wd != null) weekdayByColumn[c] = wd
        }
        if (weekdayByColumn.isEmpty()) {
            // 与个人课表保持同一套失败哲学：认不出表头就报错，绝不按列序猜
            throw HutbError.Parse("无法识别星期表头")
        }

        val courses = mutableListOf<Course>()
        val issues = mutableListOf<ParseIssue>()

        // 同一天的列按列序排好，连堂课靠"相邻且内容相同"来识别
        val columnsByWeekday = LinkedHashMap<Int, MutableList<Int>>()
        for ((columnIndex, weekday) in weekdayByColumn) {
            columnsByWeekday.getOrPut(weekday) { mutableListOf() }.add(columnIndex)
        }

        fun periodOf(columnIndex: Int): Pair<Int, Int>? =
            if (periodRowIdx >= 0) {
                parsePeriods(grid[periodRowIdx].getOrNull(columnIndex)?.text().orEmpty())
            } else {
                null
            }

        for (r in dataStart until grid.size) {
            val row = grid[r]
            if (row.isEmpty()) continue
            val rowLabel = cellLines(row.first()).firstOrNull().orEmpty()
            if (rowLabel.isEmpty()) continue

            // 行首是"节次"还是"场地"？
            // 场地名（如"至诚楼A101*(智慧)"）不能因为含数字就当成节次行。
            val labelPeriod = parsePeriods(rowLabel)
            val isPeriodRow = labelPeriod != null && !looksLikeVenueName(rowLabel)
            val roomFromRow = if (isPeriodRow) "" else rowLabel

            for ((weekday, columns) in columnsByWeekday) {
                var position = 0
                while (position < columns.size) {
                    val columnIndex = columns[position]
                    if (columnIndex >= row.size) {
                        position++
                        continue
                    }
                    val cellElement = row[columnIndex]
                    val lines = cellLines(cellElement)
                    if (lines.isEmpty()) {
                        position++
                        continue
                    }

                    // 连堂课在服务端是"跨到的每个格子都写一遍同样的内容"，
                    // 这里把同行、同一天、相邻且内容相同的格子并成一次课。
                    var lastPosition = position
                    while (lastPosition + 1 < columns.size) {
                        val nextColumn = columns[lastPosition + 1]
                        if (nextColumn >= row.size) break
                        if (cellLines(row[nextColumn]) != lines) break
                        lastPosition++
                    }

                    val firstColumnPeriod = periodOf(columns[position])
                    val lastColumnPeriod = periodOf(columns[lastPosition])

                    for (group in groupCourseBlocks(lines)) {
                        group.unresolvedLines.forEach { line ->
                            issues.add(ParseIssue(line, "无法归位到教师/班级/教室/周次"))
                        }
                        val course = toCourse(group, weekday, 0, 0)
                        if (course == null) continue

                        val cellPeriod = findPeriodInLines(group.lines)
                        val start = firstColumnPeriod?.first ?: cellPeriod?.first ?: 0
                        val end = lastColumnPeriod?.second ?: cellPeriod?.second ?: 0

                        val classroom = course.classroom.ifBlank {
                            roomFromRow.ifBlank {
                                // 同构表（第 3 种形态）：教室只能来自查询参数
                                listOfNotNull(
                                    roomName?.takeIf { it.isNotBlank() },
                                    building?.takeIf { it.isNotBlank() }
                                ).joinToString(" ")
                            }
                        }

                        courses.add(
                            course.copy(
                                classroom = classroom,
                                startPeriod = start,
                                endPeriod = end
                            )
                        )
                    }
                    position = lastPosition + 1
                }
            }
        }

        // 过滤：服务端可能返回整个教学楼的合表。
        //
        // 选定了具体教室时只按教室名过滤——教室名来自表格首列，
        // 是唯一可靠的标识（服务端不接受教室名做查询参数）。
        // 没选教室时才退到按教学楼名过滤。
        val filterName = roomName?.takeIf { it.isNotBlank() }
        val filtered = when {
            filterName != null -> courses.filter { course ->
                course.classroom.contains(filterName, ignoreCase = true)
            }

            building != null -> courses.filter { course ->
                roomBelongsToBuilding(course.classroom, building)
            }

            else -> courses
        }

        // 过滤结果为空就如实返回空：回退全量会让"选 A 楼 101"
        // 显示成整栋楼的课，且用户毫无察觉。宁可显示空课表让用户发现
        // 名称对不上，也不能给出看似正常的错误数据。

        val dedupedIssues = issues.distinctBy { it.line }
        dedupedIssues.forEach { CoreLog.d("解析提示：未能识别「${it.line}」（${it.reason}）") }

        return WeekSchedule(
            semesterId = semesterId,
            semesterName = semesterName,
            week = week,
            courses = filtered,
            roomLabel = listOfNotNull(
                roomName?.takeIf { it.isNotBlank() },
                building?.takeIf { it.isNotBlank() }
            ).joinToString(" "),
            parseIssues = dedupedIssues
        )
    }

    /**
     * 解析指定 id 的下拉框选项。
     *
     * 教室课表页的三个关键下拉框（实测）：
     * - `select#xqid`        校区，如 `0t`=湖南工商大学月亮岛校区
     * - `select#jzwid`       教学楼（内容由 AJAX 填充，静态页里通常只有"请选择"）
     * - `select#classroomID` 教室（同上）
     *
     * 「请选择」这类占位项会被过滤掉。
     */
    fun parseSelectOptions(html: String, selectId: String): List<RoomOption> {
        val doc = parseJsxsdHtml(html)
        val select = doc.selectFirst("select#$selectId") ?: return emptyList()
        val result = mutableListOf<RoomOption>()
        val seen = mutableSetOf<String>()
        for (opt in select.select("option")) {
            val code = opt.attr("value").trim()
            val text = opt.text().trim().replace(Regex("\\s+"), " ")
            if (code.isEmpty() || text.isEmpty()) continue
            if (text.contains("请选择")) continue
            if (!seen.add(code)) continue
            result.add(RoomOption(code = code, name = text))
        }
        return result
    }

    /**
     * 解析校区列表（教室课表页的 `select#xqid`）。
     */
    fun parseCampuses(html: String): List<RoomOption> = parseSelectOptions(html, "xqid")

    /**
     * 解析教学楼列表（教室课表页的 `select#jzwid`）。
     */
    fun parseBuildingOptions(html: String): List<RoomOption> = parseSelectOptions(html, "jzwid")

    /**
     * 从教室课表表格的首列取出场地（教室）名。
     *
     * 教室课表页每行首列是该行的场地，如「至诚楼A101*(智慧)」。
     * 当 `#classroomID` 下拉框没有内容（依赖 AJAX）时，
     * 可以用查询整栋楼返回的表格反推出这栋楼下的教室清单。
     */
    fun parseRoomLabels(html: String): List<String> {
        val doc = parseJsxsdHtml(html)
        val table = findScheduleTable(doc) ?: return emptyList()
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (row in table.select("tr")) {
            val first = row.selectFirst("td, th") ?: continue
            // 表头占位格是 `&nbsp;`，JSoup 会还原成不换行空格，必须先去掉
            val label = first.text()
                .replace('\u00A0', ' ')
                .trim()
                .replace(Regex("""\s+"""), " ")
            if (label.isEmpty()) continue
            // 首列是"节次"表头或节次行（数字）时跳过
            if (label.contains("节次") || label.contains("教室")) continue
            if (label.all { it.isDigit() || it == '-' || it == ':' }) continue
            if (seen.add(label)) result.add(label)
        }
        return result
    }

    /**
     * 解析场地（教室）下拉选项。
     *
     * 强智 jsxsd 的教学安排页通常有：
     * - `select#zcdm` / `select[name=zcdm]`：场地代码（单选某个教室）
     * - `select#jxddbh` / `select[name=jxddbh]`：教学楼
     *
     * 这里做宽松匹配：优先找 id/name 含 zcdm 的 select；
     * 找不到时，退而求其次找 option 文本里带"楼"且 value 形似场地代码的 select。
     */
    fun parseRoomOptions(html: String): List<RoomOption> {
        val doc = parseJsxsdHtml(html)
        val result = mutableListOf<RoomOption>()
        val seen = mutableSetOf<String>()

        val selects = doc.select("select")
        // 优先级：显式 zcdm > 显式 jxddbh > 任何含"楼"的 select
        val prioritized = selects.sortedBy { select ->
            val marker = (select.attr("id") + " " + select.attr("name")).lowercase()
            when {
                marker.contains("zcdm") -> 0
                marker.contains("jxdd") || marker.contains("ddmc") -> 1
                else -> 2
            }
        }

        for (select in prioritized) {
            for (opt in select.select("option")) {
                val code = opt.attr("value").trim()
                val text = opt.text().trim()
                if (code.isEmpty() || text.isEmpty()) continue
                if (text.contains("请选择") || text.contains("全部")) continue
                if (!seen.add(code)) continue
                result.add(RoomOption(code = code, name = text))
            }
            // 找到明确的场地 select 后就不再往下找了
            val marker = (select.attr("id") + " " + select.attr("name")).lowercase()
            if (marker.contains("zcdm") && result.isNotEmpty()) break
        }

        return result
    }

    /**
     * 解析周次文本为周次集合。
     * 支持："1-8周"、"1,3,5-7周"、"第1-16周(单)"、"1-8周(单)"。
     */
    fun parseWeeks(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val result = sortedSetOf<Int>()
        // 剥离括号内容（单/双），周次范围本身不受影响
        val withoutParen = text.replace(Regex("""[（(][^)）]*[)）]"""), "")
        Regex("""(\d{1,2})\s*[-~－]\s*(\d{1,2})|(\d{1,2})""").findAll(withoutParen).forEach { m ->
            if (m.groupValues[1].isNotEmpty()) {
                val s = m.groupValues[1].toIntOrNull() ?: return@forEach
                val e = m.groupValues[2].toIntOrNull() ?: return@forEach
                if (s in 1..30 && e in 1..30) {
                    for (i in minOf(s, e)..maxOf(s, e)) result.add(i)
                }
            } else {
                m.groupValues[3].toIntOrNull()?.let { if (it in 1..30) result.add(it) }
            }
        }
        return result.toList()
    }
}
