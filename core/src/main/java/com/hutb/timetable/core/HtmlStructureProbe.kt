package com.hutb.timetable.core

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 页面结构探针。
 *
 * 教务系统改版时，解析失败几乎都源于"我们假设的 HTML 与真实 HTML 不一致"。
 * 传统做法是把整页 HTML 抓下来人工阅读，但教务页面动辄上百 KB，
 * 传输和阅读成本都很高。
 *
 * 这个探针把页面压缩成一份**结构指纹**：只保留改版排查真正需要的信息：
 * - 表单（action / method / 字段名）——决定查询参数怎么传；
 * - 下拉框（name / id / 选项）——决定学期、场地怎么选；
 * - 表格（id / 行数 / 表头）——决定课表怎么解析；
 * - 链接与脚本 ——暴露改版后新出现的接口路径；
 * - 页面文本里的"学年学期"线索。
 *
 * 产出的 [Report.render] 是纯文本，可以直接复制粘贴到聊天窗口反馈给开发者，
 * 不需要传输原始 HTML。
 */
object HtmlStructureProbe {

    /** 单个下拉框最多展示的选项数。 */
    private const val MAX_OPTIONS = 40

    /** 单个表格最多展示的表头单元格数。 */
    private const val MAX_HEADER_CELLS = 16

    /** 单个表格最多展示的样例行数。 */
    private const val MAX_ROWS = 4

    /** 样例行最多展示的单元格数（超出部分用「…(共N格)…」标出）。 */
    private const val MAX_SAMPLE_CELLS = 8

    /** 最多收集的候选接口路径数。 */
    private const val MAX_LINKS = 60

    /** 内联脚本最多保留的字符数。 */
    private const val MAX_SCRIPT_CHARS = 4000

    /** 非 HTML 响应（JSON 等）最多保留的字符数。 */
    private const val MAX_RAW_CHARS = 3000

    /** HTML 注释（快照文件头就是一段注释）。 */
    private val COMMENT_REGEX = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

    /** 快照头部里的来源信息（tag / url / time / size）。 */
    private val SOURCE_LINE_REGEX = Regex("""^\s*(tag|url|time|size)\s*:\s*(.+)$""", RegexOption.MULTILINE)

    /** 判断文本是否是 HTML：只要有标签就按 HTML 处理。 */
    private val HTML_MARKUP_REGEX = Regex(
        """<\s*(html|body|table|form|select|div|span|script|p|br|a)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * 页面结构指纹。
     *
     * @param title 页面标题
     * @param length 页面字符数
     * @param forms 表单摘要
     * @param selects 下拉框摘要
     * @param tables 表格摘要
     * @param candidates 候选接口 / 跳转地址
     * @param semesterHints 页面文本中与"学年学期"相关的片段
     * @param scripts 内联脚本里的 AJAX 调用片段
     * @param raw 非 HTML 响应（如 JSON）的原始内容
     * @param source 快照来源（tag / url / time），只有快照文件才有
     */
    data class Report(
        val title: String,
        val length: Int,
        val forms: List<String>,
        val selects: List<String>,
        val tables: List<String>,
        val candidates: List<String>,
        val semesterHints: List<String>,
        val scripts: List<String> = emptyList(),
        val raw: String = "",
        val source: String = ""
    ) {

        /**
         * 渲染成可读文本。
         *
         * 刻意保持短小：一页结构指纹通常在 2~4 KB，
         * 可以完整粘贴进聊天窗口而不截断。
         */
        fun render(): String = buildString {
            appendLine("【页面结构指纹】")
            appendLine("标题：${title.ifBlank { "(无)" }}")
            appendLine("长度：$length 字符")
            if (source.isNotBlank()) appendLine("来源：$source")
            appendLine()

            appendLine("— 表单 (${forms.size}) —")
            if (forms.isEmpty()) appendLine("(无)")
            forms.forEach { appendLine("  $it") }
            appendLine()

            appendLine("— 下拉框 (${selects.size}) —")
            if (selects.isEmpty()) appendLine("(无)")
            selects.forEach { appendLine("  $it") }
            appendLine()

            appendLine("— 表格 (${tables.size}) —")
            if (tables.isEmpty()) appendLine("(无)")
            tables.forEach { appendLine("  $it") }
            appendLine()

            appendLine("— 候选接口/跳转 (${candidates.size}) —")
            if (candidates.isEmpty()) appendLine("(无)")
            candidates.forEach { appendLine("  $it") }
            appendLine()

            appendLine("— 页面脚本 (${scripts.size}) —")
            if (scripts.isEmpty()) appendLine("(无)")
            scripts.forEach { appendLine(it) }
            appendLine()

            if (raw.isNotBlank()) {
                appendLine("— 原始响应 (前 ${raw.length} 字符) —")
                appendLine(raw)
                appendLine()
            }

            appendLine("— 学年学期线索 (${semesterHints.size}) —")
            if (semesterHints.isEmpty()) appendLine("(无)")
            semesterHints.forEach { appendLine("  $it") }
        }
    }

    /**
     * 分析页面，产出结构指纹。
     */
    fun probe(html: String): Report {
        if (html.isBlank()) {
            return Report("(空页面)", 0, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }

        // 快照文件的头部是一段 HTML 注释，先去掉再判断内容类型
        val body = html.replace(COMMENT_REGEX, "").trim()
        val source = extractSource(html)

        // AJAX 接口返回的是 JSON，结构指纹对它没有意义，直接给出原始内容
        if (!HTML_MARKUP_REGEX.containsMatchIn(body)) {
            return Report(
                title = "(非 HTML 响应)",
                length = html.length,
                forms = emptyList(),
                selects = emptyList(),
                tables = emptyList(),
                candidates = emptyList(),
                semesterHints = emptyList(),
                scripts = emptyList(),
                raw = body.take(MAX_RAW_CHARS),
                source = source
            )
        }

        val doc = parseJsxsdHtml(html)
        return Report(
            title = doc.title().trim(),
            length = html.length,
            forms = probeForms(doc),
            selects = probeSelects(doc),
            tables = probeTables(doc),
            candidates = probeCandidates(doc),
            semesterHints = probeSemesterHints(doc),
            scripts = probeScripts(doc),
            source = source
        )
    }

    /**
     * 提取快照文件的来源信息。
     *
     * 快照头部是一段注释，里面记着这个响应来自哪个 URL。
     * 排查时"哪个请求返回了这份内容"和内容本身同样重要。
     */
    private fun extractSource(html: String): String {
        val comment = COMMENT_REGEX.find(html)?.value ?: return ""
        // 指纹正文也写在注释里，只取"结构指纹"标记之前的部分
        val header = comment.substringBefore("---- 结构指纹 ----")
        return header.lines().mapNotNull { line ->
            SOURCE_LINE_REGEX.find(line)?.let { "${it.groupValues[1]}=${it.groupValues[2].trim()}" }
        }.joinToString("  ")
    }

    // ------------------------------------------------------------------
    // 表单
    // ------------------------------------------------------------------

    private fun probeForms(doc: Document): List<String> {
        return doc.select("form").map { form ->
            val id = form.attr("id").ifBlank { form.attr("name") }
            val action = form.attr("action").ifBlank { "(当前页)" }
            val method = form.attr("method").ifBlank { "GET" }
            val fields = form.select("input, select, textarea")
                .map { field ->
                    val name = field.attr("name").ifBlank { field.attr("id") }
                    val type = field.attr("type").ifBlank { field.tagName() }
                    if (name.isBlank()) "" else "$name($type)"
                }
                .filter { it.isNotBlank() }
                .distinct()
            buildString {
                if (id.isNotBlank()) append("#$id ")
                append("$method $action")
                if (fields.isNotEmpty()) append(" | 字段: ${fields.joinToString(",")}")
            }
        }
    }

    // ------------------------------------------------------------------
    // 下拉框
    // ------------------------------------------------------------------

    private fun probeSelects(doc: Document): List<String> {
        return doc.select("select").map { select ->
            val id = select.attr("id").ifBlank { select.attr("name") }
            val options = select.select("option").take(MAX_OPTIONS).map { opt ->
                val value = opt.attr("value").trim()
                val text = opt.text().trim().replace(Regex("\\s+"), " ")
                if (value.isEmpty()) text else "$value=$text"
            }
            buildString {
                if (id.isNotBlank()) append("#$id ") else append("(无 id) ")
                append("选项${options.size}: ")
                append(options.joinToString(" | "))
            }
        }
    }

    // ------------------------------------------------------------------
    // 表格
    // ------------------------------------------------------------------

    private fun probeTables(doc: Document): List<String> {
        return doc.select("table").map { table ->
            val id = table.attr("id").ifBlank { table.attr("class") }
            val rows = table.select("tr")
            // 真实列数：把 colspan 展开后，一行占多少列
            val columnCount = rows.maxOfOrNull { row ->
                row.select("th, td").sumOf { it.spanCount() }
            } ?: 0

            val headerCells = rows.firstOrNull()?.select("th, td").orEmpty()
            val header = headerCells
                .take(MAX_HEADER_CELLS)
                .joinToString(" | ") { cell ->
                    val span = cell.spanCount()
                    val text = cell.text().trim().replace(Regex("\\s+"), " ")
                    // colspan 必须标出来，否则「星期一×6」会被误读成六列都是星期一
                    if (span > 1) "$text×$span" else text
                }
            val headerNote = if (headerCells.size > MAX_HEADER_CELLS) " …(共${headerCells.size}格)" else ""

            val sample = rows.drop(1).take(MAX_ROWS).joinToString(" ‖ ") { row ->
                val cells = row.select("td, th")
                val parts = cells.take(MAX_SAMPLE_CELLS)
                    .map { it.text().trim().replace(Regex("\\s+"), " ").take(40) }
                    .toMutableList()
                if (cells.size > MAX_SAMPLE_CELLS) {
                    // 截断必须显式标出，否则 43 列的表会被看成 8 列
                    parts += "…(共${cells.size}格)…"
                    parts += cells.takeLast(2)
                        .map { it.text().trim().replace(Regex("\\s+"), " ").take(40) }
                }
                parts.joinToString(",")
            }
            buildString {
                if (id.isNotBlank()) append("#$id ") else append("(无 id) ")
                append("行数${rows.size} 列数$columnCount ")
                if (header.isNotBlank()) append("表头: $header$headerNote")
                if (sample.isNotBlank()) append(" | 样例行: $sample")
            }
        }
    }

    /** 单元格的 colspan（缺省为 1）。 */
    private fun Element.spanCount(): Int =
        attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1

    // ------------------------------------------------------------------
    // 候选接口 / 跳转
    // ------------------------------------------------------------------

    private fun probeCandidates(doc: Document): List<String> {
        val result = LinkedHashSet<String>()

        // 链接
        doc.select("a[href]").forEach { a ->
            val href = a.attr("href").trim()
            if (href.isEmpty()) return@forEach
            if (href.startsWith("javascript:") || href.startsWith("#")) return@forEach
            val text = a.text().trim().replace(Regex("\\s+"), " ").take(24)
            result.add("$href  「$text」")
        }

        // 表单 action
        doc.select("form[action]").forEach { form ->
            val action = form.attr("action").trim()
            if (action.isNotEmpty()) result.add("FORM $action")
        }

        // 脚本里的接口路径
        doc.select("script").forEach { script ->
            val text = script.html()
            Regex("""(?:url|action|href|src)\s*[:=]\s*["']([^"']{3,120})["']""").findAll(text)
                .forEach { m ->
                    val value = m.groupValues[1].trim()
                    if (value.contains("/") && !value.startsWith("data:")) {
                        result.add("JS $value")
                    }
                }
        }

        return result.take(MAX_LINKS)
    }

    // ------------------------------------------------------------------
    // 学年学期线索
    // ------------------------------------------------------------------

    private fun probeSemesterHints(doc: Document): List<String> {
        val hints = LinkedHashSet<String>()

        // 文本中含"学期"/"学年"的片段
        val bodyText = doc.body()?.text()?.replace(Regex("\\s+"), " ") ?: doc.text()
        Regex("""[^ ]{0,24}(?:学年|学期)[^ ]{0,24}""").findAll(bodyText).forEach { m ->
            hints.add("文本: ${m.value.trim()}")
        }

        // 值为学期 ID 的元素（option / input / a）
        val semesterIdRegex = Regex("""\d{4}-\d{4}-\d{1,2}""")
        doc.select("option, input, a, li, span, td").forEach { el ->
            val value = el.attr("value").ifBlank { el.attr("data-value").ifBlank { el.attr("href") } }
            if (value.isNotBlank() && semesterIdRegex.containsMatchIn(value)) {
                val name = el.attr("name").ifBlank { el.attr("id") }
                hints.add("属性: $name=$value 「${el.text().trim().take(30)}」")
            }
        }

        // 内联脚本里的学期 JSON
        doc.select("script").forEach { script ->
            Regex("""xnxq[^,}]{0,80}""", RegexOption.IGNORE_CASE).findAll(script.html())
                .take(6)
                .forEach { m ->
                    hints.add("脚本: ${m.value.trim()}")
                }
        }

        return hints.take(30)
    }

    /**
     * 提取内联脚本里的 AJAX 调用片段。
     *
     * 改版后很多下拉框（教学楼、教室等）不再服务端渲染，
     * 而是由页面脚本按上级选择动态填充。此时**接口路径与请求参数只存在于脚本里**，
     * 指纹里没有它就无从适配，因此这里把相关片段原样带出来。
     */
    private fun probeScripts(doc: Document): List<String> {
        val result = mutableListOf<String>()
        for (script in doc.select("script")) {
            if (script.attr("src").isNotBlank()) continue
            val text = script.html()
            if (text.isBlank()) continue
            if (!text.contains("ajax", ignoreCase = true) &&
                !text.contains("$.post", ignoreCase = true) &&
                !text.contains("$.get", ignoreCase = true) &&
                !text.contains("XMLHttpRequest", ignoreCase = true)
            ) {
                continue
            }
            val snippet = text.trim().take(MAX_SCRIPT_CHARS)
            result.add("  " + snippet.replace(Regex("\n{3,}"), "\n\n"))
        }
        return result
    }
}
