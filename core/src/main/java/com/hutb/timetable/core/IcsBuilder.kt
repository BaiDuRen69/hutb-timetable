package com.hutb.timetable.core

import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 一条待导出的日历事件，时间使用设备本地时区的“浮动时间”。 */
data class IcsEvent(
    val uid: String,
    val title: String,
    val location: String = "",
    val description: String = "",
    val start: LocalDateTime,
    val end: LocalDateTime
)

private val ICS_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
private val ICS_STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
private const val MAX_LINE_BYTES = 75

/**
 * 生成 RFC 5545 风格的 ICS 文本。
 *
 * 只导出当前周的单次事件，不做 RRULE：课程是否每周都上由教务系统的周次决定，
 * 导出重复规则反而会制造错误课表。
 */
fun buildIcs(
    calendarName: String,
    events: List<IcsEvent>,
    nowUtc: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
): String {
    val stamp = nowUtc.format(ICS_STAMP_FORMAT)
    val lines = mutableListOf(
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//HUTB Timetable//CN",
        "CALSCALE:GREGORIAN",
        "METHOD:PUBLISH",
        "X-WR-CALNAME:${escapeIcs(calendarName)}"
    )
    events.forEach { event ->
        lines += "BEGIN:VEVENT"
        lines += "UID:${escapeIcs(event.uid)}"
        lines += "DTSTAMP:$stamp"
        lines += "DTSTART:${event.start.format(ICS_TIME_FORMAT)}"
        lines += "DTEND:${event.end.format(ICS_TIME_FORMAT)}"
        lines += "SUMMARY:${escapeIcs(event.title)}"
        if (event.location.isNotBlank()) lines += "LOCATION:${escapeIcs(event.location)}"
        if (event.description.isNotBlank()) {
            lines += "DESCRIPTION:${escapeIcs(event.description)}"
        }
        lines += "END:VEVENT"
    }
    lines += "END:VCALENDAR"
    return lines.joinToString(separator = "\r\n") { foldIcsLine(it) } + "\r\n"
}

/** 转义 ICS 文本值中的反斜杠、分号、逗号和换行。 */
internal fun escapeIcs(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")
    .replace("\r", "\\n")
    .replace(";", "\\;")
    .replace(",", "\\,")

/**
 * 按 UTF-8 字节数折行；续行以空格开头，避免中文长标题超出 75 字节。
 */
internal fun foldIcsLine(line: String): String {
    if (line.toByteArray(StandardCharsets.UTF_8).size <= MAX_LINE_BYTES) return line
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    var currentBytes = 0
    var limit = MAX_LINE_BYTES
    var index = 0
    while (index < line.length) {
        val codePoint = line.codePointAt(index)
        val text = String(Character.toChars(codePoint))
        val size = text.toByteArray(StandardCharsets.UTF_8).size
        if (current.isNotEmpty() && currentBytes + size > limit) {
            chunks += current.toString()
            current.setLength(0)
            currentBytes = 0
            limit = MAX_LINE_BYTES - 1
        }
        current.append(text)
        currentBytes += size
        index += Character.charCount(codePoint)
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks.joinToString(separator = "\r\n ")
}
