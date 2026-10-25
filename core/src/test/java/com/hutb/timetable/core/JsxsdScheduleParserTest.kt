package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表解析器单元测试。
 *
 * 样本 HTML 按强智 jsxsd 服务端渲染的课表表格结构构造。
 */
class JsxsdScheduleParserTest {

    private val sampleHtml = """
        <html><body>
        <select name="xnxq01id">
          <option value="2025-2026-1" selected>2025-2026学年第一学期</option>
          <option value="2024-2025-2">2024-2025学年第二学期</option>
        </select>
        <table id="kbtable" border="1">
        <tr><th>节次\星期</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th>
            <th>星期五</th><th>星期六</th><th>星期日</th></tr>
        <tr><td>第1-2节<br>08:00-09:35</td>
          <td><div>高等数学(上)</div><div>张三</div><div>计科2201</div>
              <div>至诚楼A201</div><div>1-16周(单)</div></td>
          <td></td>
          <td><div>大学英语(一)</div><div>李四</div><div>计科2201</div>
              <div>至诚楼B102</div><div>1-16周</div></td>
          <td></td><td></td><td></td><td></td>
        </tr>
        <tr><td>第3-4节<br>10:00-11:35</td>
          <td></td><td></td><td></td>
          <td><div>数据结构</div><div>王五</div><div>软件2202</div>
              <div>乐知楼C305</div><div>3-14周</div></td>
          <td></td><td></td><td></td>
        </tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析学期列表`() {
        val semesters = JsxsdScheduleParser.parseSemesters(sampleHtml)
        assertEquals(2, semesters.size)
        assertEquals("2025-2026-1", semesters[0].id)
        assertEquals("2025-2026学年第一学期", semesters[0].name)
    }

    @Test
    fun `解析课表得到三门课程`() {
        val schedule = JsxsdScheduleParser.parseSchedule(
            html = sampleHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1
        )
        assertEquals(3, schedule.courses.size)
    }

    @Test
    fun `课程字段归位正确`() {
        val schedule = JsxsdScheduleParser.parseSchedule(
            html = sampleHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1
        )
        val math = schedule.courses.first { it.courseName.contains("高等数学") }
        assertEquals("张三", math.teacher)
        assertEquals("计科2201", math.className)
        assertEquals("至诚楼A201", math.classroom)
        assertEquals(1, math.weekday)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals("1-16周(单)", math.weekText)
        assertEquals((1..16).toList(), math.weeks)
    }

    @Test
    fun `课程名不会被误判为教师`() {
        val schedule = JsxsdScheduleParser.parseSchedule(
            html = sampleHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1
        )
        // "数据结构"是 4 字课程名，不是教师名
        val ds = schedule.courses.first { it.courseName.contains("数据结构") }
        assertEquals("数据结构", ds.courseName)
        assertEquals("王五", ds.teacher)
        assertEquals("乐知楼C305", ds.classroom)
    }

    @Test
    fun `星期与节次解析正确`() {
        val schedule = JsxsdScheduleParser.parseSchedule(
            html = sampleHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1
        )
        val english = schedule.courses.first { it.courseName.contains("英语") }
        assertEquals(3, english.weekday)
        assertEquals(1, english.startPeriod)

        val ds = schedule.courses.first { it.courseName.contains("数据结构") }
        assertEquals(4, ds.weekday)
        assertEquals(3, ds.startPeriod)
        assertEquals(4, ds.endPeriod)
    }

    @Test
    fun `周次解析支持多种格式`() {
        assertEquals(listOf(1, 2, 3), JsxsdScheduleParser.parseWeeks("1-3周"))
        assertEquals(listOf(1, 3, 5), JsxsdScheduleParser.parseWeeks("1,3,5周"))
        assertEquals((1..8).toList(), JsxsdScheduleParser.parseWeeks("1-8周(单)"))
        assertEquals(listOf(5), JsxsdScheduleParser.parseWeeks("第5周"))
        assertTrue(JsxsdScheduleParser.parseWeeks("").isEmpty())
    }

    @Test
    fun `空课表解析为空列表`() {
        val emptyHtml = """
            <table id="kbtable">
            <tr><th>节次\星期</th><th>星期一</th><th>星期二</th><th>星期三</th>
                <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td>第1-2节<br>08:00-09:35</td><td></td><td></td><td></td>
                <td></td><td></td><td></td><td></td></tr>
            </table>
        """.trimIndent()
        val schedule = JsxsdScheduleParser.parseSchedule(emptyHtml, "2025-2026-1", "测试", 1)
        assertEquals(0, schedule.courses.size)
    }

    // ---------------- 教室（教学安排）课表 ----------------

    /** 教学安排表格：行 = 场地，列 = 星期。 */
    private val roomHtml = """
        <html><body>
        <select id="zcdm" name="zcdm">
          <option value="">请选择</option>
          <option value="0301">至诚楼A201</option>
          <option value="0302">至诚楼A202</option>
          <option value="0401">乐知楼C305</option>
        </select>
        <table id="kbtable" border="1">
        <tr><th>场地\星期</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th>
            <th>星期五</th><th>星期六</th><th>星期日</th></tr>
        <tr><td>至诚楼A201</td>
          <td><div>高等数学(上)</div><div>张三</div><div>计科2201</div><div>1-16周(单)</div></td>
          <td></td>
          <td><div>大学英语(一)</div><div>李四</div><div>计科2201</div><div>1-16周</div></td>
          <td></td><td></td><td></td><td></td>
        </tr>
        <tr><td>乐知楼C305</td>
          <td></td><td></td><td></td>
          <td><div>数据结构</div><div>王五</div><div>软件2202</div><div>3-14周</div></td>
          <td></td><td></td><td></td>
        </tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析教学安排得到课程且教室来自行首`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = roomHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1
        )
        assertEquals(3, schedule.courses.size)

        val math = schedule.courses.first { it.courseName.contains("高等数学") }
        assertEquals("至诚楼A201", math.classroom)
        assertEquals("张三", math.teacher)
        assertEquals("计科2201", math.className)
        assertEquals(1, math.weekday)

        val ds = schedule.courses.first { it.courseName.contains("数据结构") }
        assertEquals("乐知楼C305", ds.classroom)
        assertEquals(4, ds.weekday)
    }

    @Test
    fun `教学安排可按场地代码过滤`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = roomHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1,
            roomCode = "0301",
            roomName = "至诚楼A201"
        )
        // A201 行有两门课，C305 被过滤掉
        assertEquals(2, schedule.courses.size)
        assertTrue(schedule.courses.all { it.classroom.contains("A201") })
    }

    @Test
    fun `教学安排可按教学楼过滤`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = roomHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1,
            building = "乐知楼"
        )
        assertEquals(1, schedule.courses.size)
        assertEquals("数据结构", schedule.courses[0].courseName)
    }

    @Test
    fun `解析场地选项`() {
        val options = JsxsdScheduleParser.parseRoomOptions(roomHtml)
        assertEquals(3, options.size)
        assertEquals("0301", options[0].code)
        assertEquals("至诚楼A201", options[0].name)
    }

    @Test
    fun `课表查询参数区分个人与教室缓存分区`() {
        val personal = ScheduleQuery(semesterId = "2025-2026-1", week = 1)
        val room = ScheduleQuery(semesterId = "2025-2026-1", week = 1, roomCode = "0301")

        assertTrue(!personal.isRoomQuery)
        assertTrue(room.isRoomQuery)
        assertTrue(personal.cacheScope != room.cacheScope)
    }

    @Test
    fun `教室模式下即使未选教室也走教室接口`() {
        val roomMode = ScheduleQuery(semesterId = "2025-2026-1", week = 1, roomMode = true)
        assertTrue(roomMode.isRoomQuery)
        // 未选教室时缓存分区与个人课表仍然隔离
        assertTrue(roomMode.cacheScope != "personal")
    }

    @Test
    fun `节次解析不会把教室名误判为节次`() {
        // "至诚楼A201" 含数字 20，但不是节次标签
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = roomHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1
        )
        // 3 门课都来自"场地为行"的形态，节次无从推断时保持 0
        assertEquals(3, schedule.courses.size)
        assertTrue(schedule.courses.all { it.classroom.isNotBlank() })
    }

    // ---------------- 真实教室课表：行 = 教室，列 = 星期 × 6 个节次对 ----------------

    /** 强智真实教室课表的列：每天 6 个节次对，共 1 + 7 × 6 = 43 列。 */
    private fun buildRealRoomHtml(): String {
        val days = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        val pairs = listOf("0102", "0304", "0506", "0708", "0910", "1112")

        fun cell(content: String?): String = if (content == null) {
            "<td  height=\"28\" align=\"center\" valign=\"top\"><nobr> &nbsp;</nobr></td>"
        } else {
            "<td  height=\"28\" align=\"center\" valign=\"top\"><nobr> <div id='' class=\"kbcontent1\">" +
                "$content</div></nobr></td>"
        }

        val sb = StringBuilder()
        sb.append("<html><body><table id=\"kbtable\" border=\"1\" width=\"100%\">")
        sb.append("<tr><th height=\"28\" align=\"center\">&nbsp;</th>")
        days.forEach { sb.append("<th height=\"28\" align=\"center\" colspan=\"6\">$it</th>") }
        sb.append("</tr>")

        sb.append("<tr><td height=\"28\" align=\"center\">教室\\节次</td>")
        days.forEach { pairs.forEach { pair -> sb.append("<td height=\"28\" align=\"center\">$pair</td>") } }
        sb.append("</tr>")

        // 至诚楼A101：周一 0102、周三 0506 有课
        sb.append("<tr><td height=\"28\" align=\"center\"><nobr>至诚楼A101*(智慧)</nobr></td>")
        val a101 = arrayOfNulls<String>(42)
        a101[0] = "旅游法规<br>陈小春<br>(1-8周)<br>旅管2401"
        a101[14] = "风险评估理论与方法<br>郭梓焱<br>(1-16周)<br>应急2401"
        a101.forEach { sb.append(cell(it)) }
        sb.append("</tr>")

        // 至诚楼A102：周二 0304 有课
        sb.append("<tr><td height=\"28\" align=\"center\"><nobr>至诚楼A102*(智慧)</nobr></td>")
        val a102 = arrayOfNulls<String>(42)
        a102[7] = "审计学A(线上一学分)<br>水会莉<br>(1-12周)<br>审计2401"
        a102.forEach { sb.append(cell(it)) }
        sb.append("</tr>")

        sb.append("</table></body></html>")
        return sb.toString()
    }

    @Test
    fun `真实教室课表能解析出课程与所在节次`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = buildRealRoomHtml(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1
        )

        assertEquals(3, schedule.courses.size)

        val tour = schedule.courses.first { it.courseName.contains("旅游法规") }
        assertEquals("至诚楼A101*(智慧)", tour.classroom)
        assertEquals("陈小春", tour.teacher)
        assertEquals("旅管2401", tour.className)
        assertEquals(1, tour.weekday)
        assertEquals(1, tour.startPeriod)
        assertEquals(2, tour.endPeriod)

        val risk = schedule.courses.first { it.courseName.contains("风险评估") }
        assertEquals("至诚楼A101*(智慧)", risk.classroom)
        assertEquals(3, risk.weekday)
        assertEquals(5, risk.startPeriod)
        assertEquals(6, risk.endPeriod)

        val audit = schedule.courses.first { it.courseName.contains("审计学") }
        assertEquals("至诚楼A102*(智慧)", audit.classroom)
        assertEquals(2, audit.weekday)
        assertEquals(3, audit.startPeriod)
        assertEquals(4, audit.endPeriod)
    }

    @Test
    fun `真实教室课表不会把空单元格当成课程`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = buildRealRoomHtml(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1
        )
        assertTrue(schedule.courses.none { it.courseName.contains("nbsp") })
        assertTrue(schedule.courses.none { it.courseName == "0102" })
        assertTrue(schedule.courses.all { it.startPeriod > 0 && it.endPeriod >= it.startPeriod })
    }

    @Test
    fun `真实教室课表可按教室名过滤`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = buildRealRoomHtml(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1,
            roomName = "至诚楼A102*(智慧)",
            building = "至诚楼"
        )
        assertEquals(1, schedule.courses.size)
        assertTrue(schedule.courses[0].courseName.contains("审计学"))
    }

    @Test
    fun `选定教室时不依赖教学楼名也能过滤`() {
        // 教学楼名可能与教室名前缀不一致（如"月亮岛立德楼" vs "立德楼A401"），
        // 只按教室名过滤即可，不能因为教学楼名对不上就退回整栋楼的课
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = buildRealRoomHtml(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1,
            roomName = "至诚楼A102*(智慧)",
            building = "月亮岛立德楼"
        )
        assertEquals(1, schedule.courses.size)
        assertTrue(schedule.courses[0].courseName.contains("审计学"))
    }

    @Test
    fun `教室清单可从课表首列反推`() {
        val labels = JsxsdScheduleParser.parseRoomLabels(buildRealRoomHtml())
        assertEquals(listOf("至诚楼A101*(智慧)", "至诚楼A102*(智慧)"), labels)
    }

    // ------------------------------------------------------------------
    // 连堂课：服务端把跨到的每个格子都写一遍同样的内容
    // ------------------------------------------------------------------

    /** 至诚楼A204：周四 1-4 节连堂，服务端在 0102 / 0304 两格写同样的内容。 */
    private fun buildLianTangHtml(): String {
        val days = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        val pairs = listOf("0102", "0304", "0506", "0708", "0910", "1112")

        fun cell(content: String?): String = if (content == null) {
            "<td height=\"28\" align=\"center\" valign=\"top\"><nobr> &nbsp;</nobr></td>"
        } else {
            "<td height=\"28\" align=\"center\" valign=\"top\"><nobr> <div id='' class=\"kbcontent1\">" +
                "$content</div></nobr></td>"
        }

        val sb = StringBuilder()
        sb.append("<html><body><table id=\"kbtable\" border=\"1\">")
        sb.append("<tr><th height=\"28\" align=\"center\">&nbsp;</th>")
        days.forEach { sb.append("<th height=\"28\" align=\"center\" colspan=\"6\">$it</th>") }
        sb.append("</tr>")
        sb.append("<tr><td height=\"28\" align=\"center\">教室\\节次</td>")
        days.forEach { pairs.forEach { pair -> sb.append("<td height=\"28\" align=\"center\">$pair</td>") } }
        sb.append("</tr>")

        val body = "房地产估价理论与实务<br>谭小宁<br>(1-4周)<br>财管2301-2"
        sb.append("<tr><td height=\"28\" align=\"center\"><nobr>至诚楼A204*(智慧)</nobr></td>")
        val cells = arrayOfNulls<String>(42)
        // 周四 = 第 4 天，占 18..23 列；1-4 节跨 0102 与 0304 两格
        cells[18] = body
        cells[19] = body
        // 周六 1-2 节是另一门课，不能被误并
        cells[30] = "体育鉴赏<br>周小舟<br>(1-8周)<br>工商2301"
        cells.forEach { sb.append(cell(it)) }
        sb.append("</tr>")
        sb.append("</table></body></html>")
        return sb.toString()
    }

    @Test
    fun `连堂课合并成一门课并覆盖完整节次`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = buildLianTangHtml(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1
        )

        val lianTang = schedule.courses.filter { it.courseName.contains("房地产") }
        assertEquals("连堂课应只出现一次，实际 ${lianTang.size}", 1, lianTang.size)
        assertEquals(4, lianTang[0].weekday)
        assertEquals(1, lianTang[0].startPeriod)
        assertEquals(4, lianTang[0].endPeriod)
        assertEquals("至诚楼A204*(智慧)", lianTang[0].classroom)
    }

    @Test
    fun `连堂课合并不影响其它格子`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = buildLianTangHtml(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1
        )

        assertEquals(2, schedule.courses.size)
        val sport = schedule.courses.first { it.courseName.contains("体育鉴赏") }
        assertEquals(6, sport.weekday)
        assertEquals(1, sport.startPeriod)
        assertEquals(2, sport.endPeriod)
    }

    // ------------------------------------------------------------------
    // 教学楼名与教室名的归属判定
    // ------------------------------------------------------------------

    @Test
    fun `教室归属教学楼可忽略校区前缀`() {
        assertTrue(roomBelongsToBuilding("立德楼A401", "月亮岛立德楼"))
        assertTrue(roomBelongsToBuilding("至诚楼A101*(智慧)", "至诚楼"))
        assertTrue(roomBelongsToBuilding("风华楼302", "风华楼"))
        assertTrue(!roomBelongsToBuilding("日新楼201", "至诚楼"))
        // 教学楼名是哈希编码时无从比对，交由调用方兜底
        assertTrue(roomBelongsToBuilding("任意教室", "6F57A89B0A67435AB9E528529A6F2840"))
        assertTrue(roomBelongsToBuilding("任意教室", null))
    }

    @Test
    fun `去掉校区前缀后的教学楼核心名`() {
        assertEquals("立德楼", buildingCoreName("月亮岛立德楼"))
        assertEquals("立德楼", buildingCoreName("南校区立德楼"))
        assertEquals("风华楼", buildingCoreName("风华楼"))
        assertEquals("", buildingCoreName(null))
    }

    @Test
    fun `按教学楼名过滤能穿透校区前缀`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = buildRealRoomHtml(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1,
            building = "月亮岛至诚楼"
        )
        assertEquals(3, schedule.courses.size)
        assertTrue(schedule.courses.all { it.classroom.contains("至诚楼") })
    }

    // ------------------------------------------------------------------
    // 结构指纹必须反映真实列数
    // ------------------------------------------------------------------

    @Test
    fun `结构指纹给出真实列数与表头跨度`() {
        val text = HtmlStructureProbe.probe(buildRealRoomHtml()).render()
        assertTrue("应标出真实列数，实际:\n$text", text.contains("列数43"))
        assertTrue("表头应标出 colspan，实际:\n$text", text.contains("星期一×6"))
        assertTrue("截断必须显式标出，实际:\n$text", text.contains("共43格"))
    }

    @Test
    fun `紧凑节次不会被场地代码误判`() {
        // "0301" 是场地代码形态（3 > 1），不是第 3-1 节，不能因此丢掉整行
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = """
                <html><body><table id="kbtable">
                <tr><th>&nbsp;</th><th colspan="2">星期一</th><th colspan="2">星期二</th></tr>
                <tr><td>教室\节次</td><td>0102</td><td>0304</td><td>0506</td><td>0708</td></tr>
                <tr><td>0301</td><td></td><td></td>
                  <td><div>高等数学</div><div>张三</div><div>计科2201</div><div>1-16周</div></td>
                  <td></td></tr>
                </table></body></html>
            """.trimIndent(),
            semesterId = "2026-2027-1",
            semesterName = "2026-2027学年第一学期",
            week = 1
        )
        assertEquals(1, schedule.courses.size)
        assertEquals("0301", schedule.courses[0].classroom)
        assertEquals(2, schedule.courses[0].weekday)
        assertEquals(5, schedule.courses[0].startPeriod)
        assertEquals(6, schedule.courses[0].endPeriod)
    }

    // ------------------------------------------------------------------
    // 教务系统改版兼容：学期列表的多种页面形态
    // ------------------------------------------------------------------

    @Test
    fun `学期下拉 id 为 xnxq01id 时可解析`() {
        val html = """
            <html><body>
            <select id="xnxq01id" name="xnxq01id">
              <option value="2026-2027-1" selected="selected">2026-2027学年第一学期</option>
              <option value="2025-2026-2">2025-2026学年第二学期</option>
              <option value="2025-2026-1">2025-2026学年第一学期</option>
            </select>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(3, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
    }

    @Test
    fun `学期下拉 id 为 xnxq 时可解析`() {
        val html = """
            <html><body>
            <select id="xnxq">
              <option value="2026-2027-1">2026-2027学年第一学期</option>
              <option value="2025-2026-2">2025-2026学年第二学期</option>
            </select>
            </body></html>
        """.trimIndent()
        assertEquals(2, JsxsdScheduleParser.parseSemesters(html).size)
    }

    @Test
    fun `学期只存在于隐藏域时也能解析`() {
        // 强智表单常见写法：下拉框由 JS 渲染，真实值藏在 hidden 里
        val html = """
            <html><body>
            <input type="hidden" id="xnxq01id" name="xnxq01id" value="2026-2027-1" />
            <div class="sem-tip">当前学年学期</div>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(1, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
        assertEquals("2026-2027学年第一学期", semesters[0].name)
    }

    @Test
    fun `学期下拉无 id 时按 option 文本识别`() {
        val html = """
            <html><body>
            <select class="dropdown">
              <option value="">请选择学期</option>
              <option value="2026-2027-1">2026-2027学年第一学期</option>
              <option value="2025-2026-2">2025-2026学年第二学期</option>
            </select>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(2, semesters.size)
        assertTrue(semesters.all { it.name.contains("学期") })
    }

    @Test
    fun `新版自定义下拉用 data-value 也能识别`() {
        // 部分改版把 <select> 换成了 div + data-* 的自定义下拉
        val html = """
            <html><body>
            <div class="semester-select">
              <div class="semester-item" data-value="2026-2027-1">2026-2027学年第一学期</div>
              <div class="semester-item" data-value="2025-2026-2">2025-2026学年第二学期</div>
            </div>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertTrue("自定义下拉应能解析出学期，实际: $semesters", semesters.isNotEmpty())
    }

    @Test
    fun `页面只有当前学期文案时至少能给出当前学期`() {
        // 实测：登录页/框架页右下角显示 "2026-2027学年第一学期  第  7周"
        val html = """
            <html><body>
            <div>湖南工商大学教务一体化服务平台</div>
            <span>2026-2027学年第一学期  第  7周</span>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(1, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
        assertEquals("2026-2027学年第一学期", semesters[0].name)
    }

    @Test
    fun `文本中出现学年学期时按正则兜底`() {
        val html = """
            <html><body>
            <p>2026-2027-1 2026-2027学年第一学期</p>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertTrue(semesters.isNotEmpty())
        assertEquals("2026-2027-1", semesters[0].id)
    }

    @Test
    fun `完全没有学期信息时返回空列表`() {
        val html = "<html><body><p>页面改版了</p></body></html>"
        assertTrue(JsxsdScheduleParser.parseSemesters(html).isEmpty())
    }

    @Test
    fun `空白输入不抛异常`() {
        assertTrue(JsxsdScheduleParser.parseSemesters("").isEmpty())
        assertTrue(JsxsdScheduleParser.parseSemesters("   ").isEmpty())
    }

    @Test
    fun `真实登录页片段能反推当前学期`() {
        // 真实抓取：http://jwgl.hutb.edu.cn/jsxsd/framework/xsMain.jsp
        // 未登录时该路径返回登录页，右下角有 "2026-2027学年第一学期  第  7周"。
        // 这个夹具锁定了策略 6 的行为：即使拿不到学期下拉框，也不至于完全不可用。
        val html = """
            <html><body>
            <form action="/jsxsd/xk/LoginToXk" method="post" id="Form1" name="Form1" onsubmit="return submitForm1();">
            <div class="dl"><div class="dlti">用户登录</div>
            <div class="dlmi">
            	<p>用户名:</p>
            	<input type="text" id="userAccount" value="" style="position:absolute; top:7px;"/>
            	<p>密码:</p>
            	<input type="password" id="userPassword" value="" style="position:absolute; top:54px;"/>
            	<p>验证码:</p>
            	<input type="text" id="randomCode" name="randomCode" maxlength="4" autocomplete="off" style="left:77px;width:90px;position:absolute;top:101px;"/>
            	<input type="hidden" id="encoded" name="encoded" />
            	<br/>
            	<font color="red">请先登录系统</font>
             </div>
            </div>
            <div class="end">
            	<table border="0" width="100%">
            		<tr>
            		<td align="right"><span  class="copyright">2026-2027学年第一学期  第  7周</span></td>
            		</tr>
            	</table>
            </div>
            </form>
            </body></html>
        """.trimIndent()

        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(1, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
        assertEquals("2026-2027学年第一学期", semesters[0].name)
    }

    // ------------------------------------------------------------------
    // 课表表格定位的改版兼容
    // ------------------------------------------------------------------

    @Test
    fun `表格 id 改变时按内容特征仍能定位`() {
        // 改版后表格不再叫 kbtable，只能靠"含星期一 + 行列数够多"来认
        val html = """
            <html><body>
            <table class="new-grid-2026">
            <tr><th>节次\星期</th><th>星期一</th><th>星期二</th><th>星期三</th>
                <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td>第1-2节<br>08:00-09:35</td>
              <td><div>高等数学(上)</div><div>张三</div><div>计科2201</div>
                  <div>至诚楼A201</div><div>1-16周(单)</div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr>
            </table>
            </body></html>
        """.trimIndent()
        val schedule = JsxsdScheduleParser.parseSchedule(html, "2026-2027-1", "测试", 1)
        assertEquals(1, schedule.courses.size)
        assertEquals("高等数学(上)", schedule.courses[0].courseName)
        assertEquals(1, schedule.courses[0].weekday)
    }

    @Test
    fun `表头用 td 而非 th 也能识别星期`() {
        val html = """
            <html><body>
            <table id="kbtable">
            <tr><td>节次\星期</td><td>星期一</td><td>星期二</td><td>星期三</td>
                <td>星期四</td><td>星期五</td><td>星期六</td><td>星期日</td></tr>
            <tr><td>第3-4节<br>10:00-11:35</td>
              <td></td><td></td><td></td>
              <td><div>数据结构</div><div>王五</div><div>软件2202</div>
                  <div>乐知楼C305</div><div>3-14周</div></td>
              <td></td><td></td><td></td>
            </tr>
            </table>
            </body></html>
        """.trimIndent()
        val schedule = JsxsdScheduleParser.parseSchedule(html, "2026-2027-1", "测试", 1)
        assertEquals(1, schedule.courses.size)
        assertEquals("数据结构", schedule.courses[0].courseName)
        assertEquals(4, schedule.courses[0].weekday)
    }

    @Test
    fun `表头用周一时也能识别星期`() {
        val html = """
            <html><body>
            <table id="kbtable">
            <tr><th>节次\星期</th><th>周一</th><th>周二</th><th>周三</th>
                <th>周四</th><th>周五</th><th>周六</th><th>周日</th></tr>
            <tr><td>第1-2节<br>08:00-09:35</td>
              <td><div>大学英语(一)</div><div>李四</div><div>计科2201</div>
                  <div>至诚楼B102</div><div>1-16周</div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr>
            </table>
            </body></html>
        """.trimIndent()
        val schedule = JsxsdScheduleParser.parseSchedule(html, "2026-2027-1", "测试", 1)
        assertEquals(1, schedule.courses.size)
        assertEquals(1, schedule.courses[0].weekday)
    }

    // ------------------------------------------------------------------
    // 改版兼容：学期列表的新形态
    // ------------------------------------------------------------------

    @Test
    fun `option值本身是学期ID时也能解析`() {
        // 改版后下拉框文案不再含"学期"二字
        val html = """
            <html><body>
            <select id="termSel">
              <option value="2026-2027-1">2026-2027</option>
              <option value="2025-2026-2">2025-2026</option>
            </select>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(2, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
    }

    @Test
    fun `链接参数里的学期ID能被解析`() {
        val html = """
            <html><body>
            <a href="/jsxsd/kszx/cxks?xnxq01id=2026-2027-1&zc=1">2026-2027学年第一学期</a>
            <a href="/jsxsd/kszx/cxks?xnxq01id=2025-2026-2&zc=1">2025-2026学年第二学期</a>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(2, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
        assertEquals("2026-2027学年第一学期", semesters[0].name)
    }

    @Test
    fun `内联JSON里的学期列表能被解析`() {
        val html = """
            <html><body>
            <script>
            var list = [{"xnxq01id":"2026-2027-1","xqmc":"2026-2027学年第一学期"},
                        {"xnxq01id":"2025-2026-2","xqmc":"2025-2026学年第二学期"}];
            </script>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(2, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
        assertEquals("2025-2026学年第二学期", semesters[1].name)
    }

    @Test
    fun `隐藏域里的学期ID能被解析`() {
        val html = """
            <html><body>
            <form action="/jsxsd/kszx/cxks" method="post">
              <input type="hidden" name="xnxq01id" value="2026-2027-1" />
              <input type="hidden" name="zc" value="1" />
            </form>
            </body></html>
        """.trimIndent()
        val semesters = JsxsdScheduleParser.parseSemesters(html)
        assertEquals(1, semesters.size)
        assertEquals("2026-2027-1", semesters[0].id)
    }

    // ------------------------------------------------------------------
    // 页面结构探针
    // ------------------------------------------------------------------

    @Test
    fun `结构探针能提取表单下拉与表格`() {
        val report = HtmlStructureProbe.probe(sampleHtml)
        val text = report.render()

        assertTrue(text.contains("结构指纹"))
        assertTrue(text.contains("xnxq01id"))
        assertTrue(text.contains("kbtable"))
        assertTrue(text.contains("星期一"))
    }

    @Test
    fun `结构探针能暴露候选接口路径`() {
        val html = """
            <html><head>
            <script>var cfg = {url: "/jsxsd/new/api/termList.do"};</script>
            </head><body>
            <a href="/jsxsd/kszx/cxks?xnxq01id=2026-2027-1">课表</a>
            </body></html>
        """.trimIndent()
        val text = HtmlStructureProbe.probe(html).render()
        assertTrue(text.contains("/jsxsd/new/api/termList.do"))
        assertTrue(text.contains("xnxq01id=2026-2027-1"))
    }

    @Test
    fun `结构探针对空页面不崩溃`() {
        val report = HtmlStructureProbe.probe("")
        assertEquals(0, report.length)
        assertTrue(report.render().contains("结构指纹"))
    }

    @Test
    fun `结构探针对JSON响应给出原始内容`() {
        val json = """[{"id":"6F57A89B0A67435AB9E528529A6F2840","jzwh":"风华楼"}]"""
        val text = HtmlStructureProbe.probe(json).render()
        assertTrue(text.contains("原始响应"))
        assertTrue(text.contains("风华楼"))
    }

    @Test
    fun `哈希形式的名称判定为系统编码`() {
        assertTrue(isOpaqueRoomName("6F57A89B0A67435AB9E528529A6F2840"))
        assertTrue(isOpaqueRoomName(""))
        assertTrue(isOpaqueRoomName(null))
        assertTrue(!isOpaqueRoomName("风华楼"))
    }

    @Test
    fun `可从教室名反推教学楼名`() {
        assertEquals("风华楼", deriveBuildingName("风华楼302"))
        assertEquals("至诚楼A", deriveBuildingName("至诚楼A201"))
        assertEquals("", deriveBuildingName("302"))
        assertEquals("", deriveBuildingName(null))
    }

    @Test
    fun `指纹带出快照的来源地址`() {
        val file = """
            <!-- PageSnapshot
                 tag: rooms_ajax
                 url: /jsxsd/kbcx/getJxlByAjax?xqid=00001
                 time: 20261022_221500
                 size: 1052
                 ---- 结构指纹 ----
            -->
            [{"dm":"6F57A89B0A67435AB9E528529A6F2840","dmmc":"风华楼"}]
        """.trimIndent()
        val text = HtmlStructureProbe.probe(file).render()
        assertTrue(text.contains("tag=rooms_ajax"))
        assertTrue(text.contains("getJxlByAjax?xqid=00001"))
        assertTrue(text.contains("风华楼"))
    }

    /**
     * 真实个人课表的结构：节次标签在 `<th>` 里，每门课拆成可见的 `div.kbcontent1`
     * 与隐藏的 `div.kbcontent`，隐藏那份带教师与 `[01-02-03]节` 节次标记。
     */
    private val realPersonalHtml = """
        <table id="kbtable" border="1">
        <tr>
          <th width="70">&nbsp;</th>
          <th width="123">星期一</th><th width="123">星期二</th><th width="123">星期三</th>
          <th width="123">星期四</th><th width="123">星期五</th><th width="123">星期六</th>
          <th width="123">星期日</th>
        </tr>
        <tr>
          <th width="70">0102节&nbsp;</th>
          <td width="123">
            <div class="kbcontent1">AI大模型辅助数据分析<br/>
              <font title='周次(单双)'>1-16(周)</font><br/>
              <font title='教室'>日新楼D304*</font><br/></div>
            <div style="display: none;" class="kbcontent">AI大模型辅助数据分析<br/>
              <font title='教师'>王俊杰</font><br/>
              <font title='周次(单双)'>1-16(周)</font><br/>
              <font title='教室'>日新楼D304*</font><br/>[01-02-03]节</font><br/>
              <font title='班级'>临班62</font><br/></div>
          </td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
        </tr>
        <tr>
          <th width="70">0304节&nbsp;</th>
          <td width="123">&nbsp;</td>
          <td width="123">
            <div class="kbcontent1">数据结构与算法分析(线上一学分)<br/></div>
            <div style="display: none;" class="kbcontent">数据结构与算法分析(线上一学分)<br/>
              <font title='教师'>刘丽艳</font><br/>
              <font title='周次(单双)'>1-16(周)</font><br/>
              <font title='教室'>乐知楼E602(计信)</font><br/>[03-04]节</font><br/>
              <font title='班级'>大数据2501-2</font><br/></div>
          </td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
          <td width="123">&nbsp;</td>
        </tr>
        <tr><th width="70">备注:</th><td colspan="7">&nbsp;</td></tr>
        </table>
    """.trimIndent()

    private fun parseRealPersonal(): WeekSchedule = JsxsdScheduleParser.parseSchedule(
        html = realPersonalHtml,
        semesterId = "2026-2027-1",
        semesterName = "2026-2027学年第一学期",
        week = 7
    )

    @Test
    fun `个人课表行首节次标签用 th 承载时星期不错位`() {
        val schedule = parseRealPersonal()
        assertEquals(2, schedule.courses.size)

        val ai = schedule.courses.first { it.courseName.contains("AI") }
        assertEquals(1, ai.weekday)
        val ds = schedule.courses.first { it.courseName.contains("数据结构") }
        assertEquals(2, ds.weekday)
    }

    @Test
    fun `个人课表的节次取自隐藏块里的标记`() {
        val schedule = parseRealPersonal()
        val ai = schedule.courses.first { it.courseName.contains("AI") }
        assertEquals(1, ai.startPeriod)
        assertEquals(3, ai.endPeriod)

        val ds = schedule.courses.first { it.courseName.contains("数据结构") }
        assertEquals(3, ds.startPeriod)
        assertEquals(4, ds.endPeriod)
    }

    @Test
    fun `隐藏块里的教师与班级被正确取出`() {
        val schedule = parseRealPersonal()
        val ai = schedule.courses.first { it.courseName.contains("AI") }
        assertEquals("王俊杰", ai.teacher)
        assertEquals("临班62", ai.className)
        assertEquals("日新楼D304*", ai.classroom)
        assertEquals("1-16(周)", ai.weekText)
    }

    @Test
    fun `节次标记不会被当成课程名`() {
        val schedule = parseRealPersonal()
        assertTrue(schedule.courses.none { it.courseName.contains("节") })
    }

    // ---------------- 负向用例：静默错数据比解析失败危害更大 ----------------

    @Test
    fun `识别不了的行不会拼进课程名`() {
        val html = """
            <html><body>
            <table id="kbtable">
            <tr><th>节次\星期</th><th>星期一</th><th>星期二</th><th>星期三</th>
                <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td>第1-2节<br>08:00-09:35</td>
              <td><div>高等数学(上)</div><div>张三</div><div>计科2201</div>
                  <div>至诚楼A201</div><div>1-16周(单)</div><div>备注：本周调课</div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr>
            </table>
            </body></html>
        """.trimIndent()
        val schedule = JsxsdScheduleParser.parseSchedule(html, "2025-2026-1", "测试", 1)
        assertEquals(1, schedule.courses.size)
        // 未知行直接忽略，而不是追加成 "高等数学(上) 备注：本周调课"
        assertEquals("高等数学(上)", schedule.courses[0].courseName)
        // 但也不能静默丢弃：必须作为解析 issue 暴露出来
        assertTrue(schedule.parseIssues.any { it.line.contains("备注") })
    }

    @Test
    fun `同名课不同班级不会被去重合并`() {
        val html = """
            <html><body>
            <table id="kbtable">
            <tr><th>节次\星期</th><th>星期一</th><th>星期二</th><th>星期三</th>
                <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td>第1-2节<br>08:00-09:35</td>
              <td><div>大学英语</div><div>李四</div><div>计科2201</div>
                  <div>至诚楼B102</div><div>1-16周</div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr>
            <tr><td>第3-4节<br>10:00-11:35</td>
              <td><div>大学英语</div><div>李四</div><div>软件2202</div>
                  <div>至诚楼B102</div><div>1-16周</div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr>
            </table>
            </body></html>
        """.trimIndent()
        val schedule = JsxsdScheduleParser.parseSchedule(html, "2025-2026-1", "测试", 1)
        // 两门课同名同教师同教室同时间，仅班级不同：去重键含 className，必须保留两条
        assertEquals(2, schedule.courses.size)
    }

    @Test
    fun `缺少课表表格时抛出解析错误而不是猜`() {
        val html = "<html><body><p>页面被改版了，没有课表表格</p></body></html>"
        val error = runCatching {
            JsxsdScheduleParser.parseSchedule(html, "2025-2026-1", "测试", 1)
        }.exceptionOrNull()
        assertTrue("应抛出 HutbError.Parse", error is HutbError.Parse)
    }

    @Test
    fun `教室名对不上时返回空课表而不是全量回退`() {
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html = roomHtml,
            semesterId = "2025-2026-1",
            semesterName = "2025-2026学年第一学期",
            week = 1,
            roomCode = "9999",
            roomName = "不存在的教室"
        )
        // 过滤为空就如实为空，绝不能把整栋楼的课伪装成这间教室的
        assertTrue(schedule.courses.isEmpty())
    }

    @Test
    fun `星期表头识别不了时抛出解析错误而不是按列序猜`() {
        val html = """
            <html><body>
            <table id="kbtable">
            <tr><th>节次</th><th>课程</th><th>教师</th></tr>
            <tr><td>第1-2节</td><td>高等数学</td><td>张三</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val error = runCatching {
            JsxsdScheduleParser.parseSchedule(html, "2025-2026-1", "测试", 1)
        }.exceptionOrNull()
        assertTrue("应抛出 HutbError.Parse", error is HutbError.Parse)
    }

    @Test
    fun `教室课表里无法归位的行同样产出解析 issue`() {
        val html = """
            <html><body>
            <table id="kbtable">
            <tr><th>场地\星期</th><th>星期一</th><th>星期二</th><th>星期三</th>
                <th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
            <tr><td>至诚楼A101*(智慧)</td>
              <td><div>高等数学(上)</div><div>张三</div><div>计科2201</div>
                  <div>1-16周</div><div>课程备注：临时调课</div></td>
              <td></td><td></td><td></td><td></td><td></td><td></td>
            </tr>
            </table>
            </body></html>
        """.trimIndent()
        val schedule = JsxsdScheduleParser.parseRoomSchedule(
            html, "2025-2026-1", "测试", 1,
            roomName = "至诚楼A101*(智慧)", building = "至诚楼"
        )
        assertEquals(1, schedule.courses.size)
        assertEquals("高等数学(上)", schedule.courses[0].courseName)
        assertTrue(schedule.parseIssues.any { it.line.contains("课程备注") })
    }

    @Test
    fun `星期文本采用精确匹配，课程名里的字不算星期`() {
        assertEquals(1, "星期一".toWeekday())
        assertEquals(1, "周一".toWeekday())
        assertEquals(1, " Mon ".toWeekday())
        assertEquals(7, "Sun".toWeekday())
        assertEquals(7, "星期日".toWeekday())

        // 旧实现用 contains("一")/contains("日") 会把下面这些都当成星期
        assertNull("高等数学(一)".toWeekday())
        assertNull("日语".toWeekday())
        assertNull("每天".toWeekday())
        assertNull("天气".toWeekday())
        assertNull("周天".toWeekday())
        assertNull("第二节实验".toWeekday())
    }

    @Test
    fun `教室课表表头识别失败时抛错而不是把课程行当表头`() {
        // 数据行里含「高等数学(一)」「日语」，旧实现会把它们当星期并抢走表头位置
        val html = """
            <html><body>
            <table id="kbtable">
            <tr><th>场地\星期</th><th>第1-2节</th><th>第3-4节</th></tr>
            <tr><td>至诚楼A101</td><td>高等数学(一)</td><td>日语</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val error = runCatching {
            JsxsdScheduleParser.parseRoomSchedule(
                html, "2025-2026-1", "测试", 1,
                roomName = null, building = null
            )
        }.exceptionOrNull()
        assertTrue("应抛出 HutbError.Parse", error is HutbError.Parse)
    }
}
