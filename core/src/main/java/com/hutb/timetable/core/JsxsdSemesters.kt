package com.hutb.timetable.core

/**
 * 学年学期列表解析（多策略逐级降级）。
 *
 * 每一级策略命中时通过 [CoreLog] 输出诊断日志，
 * 线上"学期列表拿不到"时可直接定位降级到了哪一层。
 */

/** 学期 ID 格式：`2025-2026-1`。 */
private val SEMESTER_ID_REGEX = Regex("""(\d{4})-(\d{4})-(\d{1,2})""")

/**
 * 页面文案里的"学年学期"模式。
 *
 * 第 1 组是学期 ID，第 2 组是显示名。
 * 例如 "2025-2026学年第一学期" / "2025-2026-1 第一学期"。
 */
private val SEMESTER_TEXT_REGEX = Regex(
    """(\d{4}-\d{4}-\d{1,2})\s*([^\s<>，,]{0,24}学期)"""
)

/**
 * 框架页/登录页上的"当前学期"文案。
 *
 * 实测形态：`2026-2027学年第一学期  第  7周`
 */
private val CURRENT_SEMESTER_REGEX = Regex(
    """(\d{4}-\d{4}学年[第]?[一二三四五六七八九十\d]{1,3}学期)"""
)

/**
 * 解析学年学期列表。
 *
 * 教务系统改版后，学期下拉框的位置与写法多次变化，因此这里采用
 * **多策略逐级降级**，只要任意一种策略命中即返回：
 *
 * 1. **语义化下拉**：`<select>` 的 id/name 命中 xnxq/xq/xnxq01id 等关键字，
 *    取其 option 的 value 作为学期 ID、文本作为显示名。
 * 2. **任意下拉**：所有 `<select>` 中，option 文本含"学期"的都作为候选，
 *    按 ID 可用性排序（value 形似 `2025-2026-1` 的优先）。
 * 3. **纯 ID 值选项**：option 的 value 本身就是 `2025-2026-1` 这种标准 ID，
 *    即使显示文案不含"学期"也接受。
 * 4. **隐藏域**：`<input type="hidden" name="xnxq01id" value="2025-2026-1">`
 *    —— 强智表单常见写法，学期 ID 只藏在 hidden 里。
 * 5. **链接参数**：`<a href="...?xnxq01id=2025-2026-1">`，
 *    改版后按学期分页的链接列表。
 * 6. **内联 JSON**：`{"xnxq01id":"...","xqmc":"..."}` 内嵌在页面脚本里。
 * 7. **自定义下拉**：`<div data-value="...">` 之类的 JS 组件下拉。
 * 8. **纯文本正则**：页面里出现 "2025-2026学年第一学期" 之类的文案。
 * 9. **当前学期文案**：框架页上的 "2026-2027学年第一学期  第 7周"。
 *
 * 全部失败时返回空列表，由调用方决定报错策略。
 */
internal fun parseSemestersWithStrategies(html: String): List<Semester> {
    if (html.isBlank()) return emptyList()
    val doc = parseJsxsdHtml(html)
    val collected = LinkedHashMap<String, Semester>()

    fun put(id: String, name: String) {
        val cleanId = id.trim()
        val cleanName = name.trim()
        if (cleanId.isEmpty()) return
        // 名字必须像学期文案；或者 ID 本身就是标准学期 ID（此时名字可由 ID 反推）
        val nameLooksSemester = cleanName.contains("学期") || cleanName.contains("学年")
        if (!nameLooksSemester && !SEMESTER_ID_REGEX.matches(cleanId)) return
        // 同一个 ID 只保留第一次出现的名字
        collected.putIfAbsent(cleanId, Semester(id = cleanId, name = cleanName))
    }

    fun hit(strategy: String): List<Semester> {
        CoreLog.d("学期解析：策略[$strategy] 命中 ${collected.size} 个学期")
        return collected.values.toList()
    }

    // ---- 策略 1 & 2：语义化下拉 + 任意下拉 ----
    val selects = doc.select("select")
    // 优先级：id/name 含 xnxq/xq 关键字的排前面
    val prioritized = selects.sortedBy { select ->
        val marker = (select.attr("id") + " " + select.attr("name")).lowercase()
        when {
            marker.contains("xnxq") -> 0
            marker.contains("xq") && !marker.contains("xqjh") -> 1
            marker.contains("semester") || marker.contains("term") -> 1
            else -> 2
        }
    }

    for (select in prioritized) {
        val options = select.select("option")
        if (options.isEmpty()) continue
        var hits = 0
        for (opt in options) {
            val value = opt.attr("value").trim()
            val text = opt.text().trim()
            if (text.isEmpty()) continue
            if (text.contains("请选择") || text.contains("全部")) continue
            if (!text.contains("学期") && !text.contains("学年")) continue
            // value 为空时可能是纯展示型 option，跳过
            if (value.isEmpty()) continue
            put(value, text)
            hits++
        }
        // 命中足够多就直接返回，避免后续无关 select 污染
        if (hits >= 2) return hit("语义化下拉")
    }

    // ---- 策略 2c：option 的 value 本身就是学期 ID ----
    // 改版后下拉框文案可能不再含"学期"二字，只剩形如 "2025-2026-1" 的 value。
    // 这类 value 用正则判定，避免误收其它下拉框。
    doc.select("select option").forEach { opt ->
        val value = opt.attr("value").trim()
        if (!SEMESTER_ID_REGEX.matches(value)) return@forEach
        val text = opt.text().trim()
        put(value, text.ifBlank { guessSemesterName(value) })
    }
    if (collected.isNotEmpty()) return hit("纯ID选项")

    // ---- 策略 3：隐藏域 ----
    doc.select("input[type=hidden], input").forEach { input ->
        val name = input.attr("name").lowercase()
        val id = input.attr("id").lowercase()
        if (!name.contains("xnxq") && !id.contains("xnxq")) return@forEach
        val value = input.attr("value").trim()
        if (value.isEmpty()) return@forEach
        put(value, guessSemesterName(value))
    }
    if (collected.isNotEmpty()) return hit("隐藏域")

    // ---- 策略 3b：链接 / 元素属性上的学期 ID 参数 ----
    // 改版后可能是"按学期分页的链接列表"：<a href="...?xnxq01id=2025-2026-1">
    doc.select("a[href], [data-xnxq01id], [data-xnxqid]").forEach { el ->
        val raw = el.attr("href").ifBlank {
            el.attr("data-xnxq01id").ifBlank { el.attr("data-xnxqid") }
        }
        if (raw.isEmpty()) return@forEach
        // 形如 xnxq01id=2025-2026-1 或 xnxq01id%3D... 都覆盖
        val m = Regex("""xnxq0?1?id\s*[=:\s]\s*["']?([0-9]{4}-[0-9]{4}-[0-9]{1,2})""", RegexOption.IGNORE_CASE)
            .find(raw)
        val id = m?.groupValues?.get(1) ?: return@forEach
        val text = el.text().trim()
        put(id, text.ifBlank { guessSemesterName(id) })
    }
    if (collected.isNotEmpty()) return hit("链接参数")

    // ---- 策略 4：脱离 select 的 option / 自定义下拉 ----
    doc.select("option").forEach { opt ->
        val value = opt.attr("value").trim()
        val text = opt.text().trim()
        if (value.isEmpty() || text.isEmpty()) return@forEach
        if (!text.contains("学期") && !text.contains("学年")) return@forEach
        put(value, text)
    }
    if (collected.isNotEmpty()) return hit("游离option")

    // ---- 策略 4b：自定义下拉（div/li + data-value / data-id） ----
    // 部分改版把原生 <select> 换成 JS 组件，用 data-* 存真实值
    doc.select("[data-value], [data-id], [data-code], [data-key]").forEach { el ->
        val value = (el.attr("data-value").ifBlank {
            el.attr("data-id").ifBlank {
                el.attr("data-code").ifBlank { el.attr("data-key") }
            }
        }).trim()
        val text = el.text().trim()
        if (value.isEmpty() || text.isEmpty()) return@forEach
        if (!text.contains("学期") && !text.contains("学年")) return@forEach
        put(value, text)
    }
    if (collected.isNotEmpty()) return hit("自定义下拉")

    // ---- 策略 4c：内联 JSON ----
    // 改版后学期数据常以 JSON 内嵌在页面脚本中：
    //   {"xnxq01id":"2025-2026-1","xqmc":"2025-2026学年第一学期"}
    // 这里不引入 JSON 库（core 是纯 JVM 模块），用正则成对提取。
    // 注意：必须按 **JSON 对象边界** 配对，不能用固定窗口，
    // 否则相邻对象的 xqmc 会串位（对象之间通常只隔几十字符）。
    val idPattern = Regex(""""xnxq0?1?id"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    val namePattern = Regex(""""xqmc"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    for (idMatch in idPattern.findAll(html).toList()) {
        val id = idMatch.groupValues[1].trim()
        if (id.isEmpty()) continue

        // 定位该 ID 所属的 JSON 对象（就近的花括号）
        val objStart = html.lastIndexOf('{', idMatch.range.first).coerceAtLeast(0)
        val objEnd = html.indexOf('}', idMatch.range.last).let { if (it < 0) html.length else it }
        val objText = if (objEnd > objStart) html.substring(objStart, objEnd + 1) else ""

        // 优先取同对象内的 xqmc；取不到再退回就近的前向匹配
        val name = namePattern.find(objText)?.groupValues?.get(1)?.trim()
            ?: run {
                val forward = html.substring(
                    idMatch.range.last,
                    minOf(html.length, idMatch.range.last + 200)
                )
                namePattern.find(forward)?.groupValues?.get(1)?.trim()
            }
            .orEmpty()

        put(id, name.ifBlank { guessSemesterName(id) })
    }
    if (collected.isNotEmpty()) return hit("内联JSON")

    // ---- 策略 5：纯文本正则兜底 ----
    // 形如 "2025-2026学年第一学期" / "2025-2026-1" / "2025-2026学年第1学期"
    val text = doc.text()
    SEMESTER_TEXT_REGEX.findAll(text).forEach { m ->
        val id = m.groupValues[1]
        val name = m.groupValues[2]
        put(id, name)
    }
    if (collected.isNotEmpty()) return hit("纯文本正则")

    // ---- 策略 6：页面上的"当前学期"文案 ----
    // 登录页/框架页右下角常显示 "2026-2027学年第一学期  第  7周"，
    // 至少能让 App 用当前学期工作，不至于完全不可用。
    CURRENT_SEMESTER_REGEX.find(text)?.let { m ->
        val name = m.groupValues[1].trim()
        val id = guessSemesterId(name) ?: guessSemesterId(name.replace("学年", "-").replace("学期", ""))
        if (id != null) {
            put(id, name)
            CoreLog.d("学期解析：策略[当前学期文案] 命中 $id")
        }
    }
    if (collected.isNotEmpty()) return hit("当前学期文案")

    CoreLog.d("学期解析：全部策略均未命中")
    return emptyList()
}

/**
 * 由学期 ID 反推显示名。
 *
 * `2025-2026-1` → "2025-2026学年第一学期"
 */
private fun guessSemesterName(id: String): String {
    val m = SEMESTER_ID_REGEX.matchEntire(id) ?: return id
    val (startYear, endYear, term) = m.destructured
    val termName = when (term) {
        "1" -> "第一学期"
        "2" -> "第二学期"
        else -> "第${term}学期"
    }
    return "${startYear}-${endYear}学年$termName"
}

/**
 * 从"学年学期"文案中反推学期 ID。
 *
 * "2025-2026学年第一学期" → "2025-2026-1"
 * "2025-2026学年第1学期"  → "2025-2026-1"
 * "2025-2026-1"           → "2025-2026-1"
 */
private fun guessSemesterId(name: String): String? {
    val clean = name.trim()

    // 已是标准 ID 格式
    SEMESTER_ID_REGEX.matchEntire(clean)?.let { return it.value }

    // "2025-2026学年第1学期" / "2025-2026学年第一学期"
    val m = Regex("""(\d{4})-(\d{4})学年第?([一二三四五六七八九十]|\d{1,2})学期""").matchEntire(clean)
        ?: return null
    val (startYear, endYear, termRaw) = m.destructured
    val term = when (termRaw) {
        "一" -> "1"
        "二" -> "2"
        "三" -> "3"
        "四" -> "4"
        "五" -> "5"
        "六" -> "6"
        "七" -> "7"
        "八" -> "8"
        "九" -> "9"
        "十" -> "10"
        else -> termRaw
    }
    return "$startYear-$endYear-$term"
}
