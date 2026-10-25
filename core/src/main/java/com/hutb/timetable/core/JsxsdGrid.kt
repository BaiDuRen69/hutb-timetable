package com.hutb.timetable.core

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 强智课表页的"结构层"工具：表格定位、rowspan/colspan 网格展开、
 * 星期表头识别、单元格文本切行。
 *
 * 与 JsxsdSemantic.kt 的"语义层"（课程/教师/教室判定）分开：
 * 结构层只关心 HTML 长什么样，语义层只关心文本是什么意思。
 */

/** 教务系统页面中用于承载课表数据的表格候选选择器。 */
private val TABLE_SELECTORS = listOf(
    "table#kbtable",
    "table#kbtable_show",
    "table[id*=kb]",
    "table[class*=kb]",
    "table.kbtable",
    "table"
)

/**
 * 定位课表表格。
 *
 * 只按已知的 id/class 选择器命中；全部落空时返回 null，
 * 由调用方抛 [HutbError.Parse]。
 * 不做"扫描所有表格取行数最多"之类的猜测：教务系统加一个统计表格
 * 就可能选错主表，错表产出的错数据比明确的解析失败危害大得多。
 */
internal fun findScheduleTable(doc: Document): Element? {
    for (selector in TABLE_SELECTORS) {
        val found = doc.select(selector).firstOrNull()
        if (found != null && found.select("tr").size >= 2) return found
    }
    return null
}

/**
 * 找到含星期的表头行。
 *
 * 判据是"该行至少有两个**互不相同**的星期单元格"，
 * 而不能用 `row.text().contains("一")` 这类子串匹配——
 * 课程名里的「高等数学(一)」「日语」「每天」都会误命中。
 */
internal fun findHeaderRow(table: Element): Element? =
    table.select("tr").firstOrNull { row ->
        row.select("th, td")
            .mapNotNull { it.text().toWeekday() }
            .distinct()
            .size >= 2
    }

/**
 * 推断列索引对应的星期几。
 *
 * 表头单元格可能是 `<th>` 也可能是 `<td>`（新版强智两种都出现过）。
 * 识别不出任何星期时返回**空映射**，由调用方抛解析错误：
 * 按列序猜星期在表头结构变化时会让整张表错位，错数据比明确的失败危害大得多。
 */
internal fun detectDayColumns(headerRow: Element?): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    headerRow?.select("th, td")?.forEachIndexed { index, cell ->
        val weekday = cell.text().trim().toWeekday()
        if (weekday != null && index !in map) map[index] = weekday
    }
    return map
}

/**
 * 星期文本的**精确**匹配表。
 *
 * 只接受教务系统实际使用的写法：星期一/周一/礼拜一 + 英文缩写。
 * 不做 `contains("一")` 之类的宽松匹配：
 * 「高等数学(一)」「日语」「天气」都会误命中，把数据行当成表头。
 * 教务系统不会用"天"表示周日，因此不收录「周天/星期天」。
 */
private val WEEKDAY_LABELS: Map<String, Int> = buildMap {
    val cn = listOf("一", "二", "三", "四", "五", "六", "日")
    cn.forEachIndexed { index, c ->
        put("星期$c", index + 1)
        put("周$c", index + 1)
        put("礼拜$c", index + 1)
    }
    listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        .forEachIndexed { index, key -> put(key, index + 1) }
    listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        .forEachIndexed { index, key -> put(key, index + 1) }
}

/**
 * 从文本推断星期几（1=周一 … 7=周日）。
 *
 * 仅接受 [WEEKDAY_LABELS] 中的精确写法（忽略空白与大小写），
 * 其余一律返回 null，由调用方决定报错还是跳过。
 */
internal fun String.toWeekday(): Int? {
    val t = this.trim().replace(Regex("""\s+"""), "").lowercase()
    if (t.isEmpty()) return null
    return WEEKDAY_LABELS[t]
}

/** 星期解析的公共入口。 */
internal fun String.toWeekdayOrNull(): Int? = toWeekday()

/**
 * 把 HTML 表格展开成二维网格，正确处理 rowspan / colspan。
 *
 * 强智的课表表头常用 colspan 把"星期一"横跨多个节次列，
 * 数据行用 rowspan 合并空单元格。直接用 `select("td")` 会错位，
 * 因此这里先做一次网格展开。
 *
 * 注意：网格里保存的是**元素**而不是文本，
 * 因为单元格的 `.text()` 会把 `<div>` 内的多行内容压成一行，
 * 导致课程名/教师/教室无法按行切分。
 *
 * @return 每行一个 List<Element>，长度按该行实际占用的列数
 */
internal fun expandGrid(table: Element): List<List<Element>> {
    val grid = mutableListOf<List<Element>>()
    // 记录跨行单元格：列索引 -> (元素, 剩余行数)
    val pending = mutableMapOf<Int, Pair<Element, Int>>()

    for (tr in table.select("tr")) {
        val cells = tr.select("th, td")
        if (cells.isEmpty()) continue

        val row = mutableListOf<Element>()
        var column = 0

        fun fillPending() {
            while (pending.containsKey(column)) {
                val (element, remaining) = pending.getValue(column)
                row.add(element)
                if (remaining <= 1) pending.remove(column) else pending[column] = element to (remaining - 1)
                column++
            }
        }

        for (cell in cells) {
            fillPending()
            val colspan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
            val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1

            repeat(colspan) { i ->
                row.add(cell)
                if (rowspan > 1) {
                    pending[column + i] = cell to (rowspan - 1)
                }
            }
            column += colspan
        }
        fillPending()
        grid.add(row)
    }
    return grid
}

/**
 * 判断某一行是否是"节次表头行"。
 *
 * 特征：整行的非空单元格都是节次编号，如
 * "第1-2节 08:00-09:35"、"1-2节"、"08:00-09:35"，
 * 教室课表里还有紧凑写法 "0102"（表示第 1-2 节）。
 *
 * 角标单元格（"教室\节次"）不是节次，允许存在至多一个。
 */
internal fun isPeriodHeaderRow(row: List<String>): Boolean {
    val cells = row.map { it.trim() }.filter { it.isNotEmpty() }
    if (cells.size < 2) return false
    val periodCells = cells.count { parsePeriods(it) != null }
    return periodCells >= 2 && periodCells >= cells.size - 1
}

/**
 * 把单元格 HTML 拆成文本行。
 *
 * 空单元格在强智里渲染成 `<nobr>&nbsp;</nobr>`，必须当成空，
 * 否则会被解析成一门名叫 "&nbsp;" 的课。
 */
internal fun cellLines(cell: Element): List<String> {
    val html = cell.html()
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</(div|p|td|tr|li)>""", RegexOption.IGNORE_CASE), "\n")
    return html.split('\n')
        .map { it.replace(Regex("""<[^>]+>"""), "") }
        .map { it.replace("&nbsp;", "").trim() }
        .filter { it.isNotEmpty() }
}

/**
 * 是否像场地（教室）名。
 *
 * 教室课表首列是场地名（如"至诚楼A101*(智慧)"），
 * 不能因为它含数字就被当成节次行。
 */
internal fun looksLikeVenueName(text: String): Boolean {
    if (text.contains("楼") || text.contains("教室") || text.contains("场地")) return true
    return text.contains("*") || text.contains("（") || text.contains("(")
}
