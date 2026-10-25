package com.hutb.timetable.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 解析教务系统页面。
 *
 * 必须关闭美化输出：JSoup 默认开启 `prettyPrint`，序列化单元格时会把
 * 文本节点里的换行折叠成空格。强智的教室课表正好把"教师"与"周次"之间的
 * 分隔写成了换行（`陈小春\r\n(1-8周)`），一旦被折叠成空格，
 * 教师名就会和周次粘在一起，教师字段解析不出来。
 */
internal fun parseJsxsdHtml(html: String): Document {
    val doc = Jsoup.parse(html)
    doc.outputSettings().prettyPrint(false)
    return doc
}
