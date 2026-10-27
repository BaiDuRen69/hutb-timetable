package com.hutb.timetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.hutb.timetable.R
import com.hutb.timetable.data.local.ClassTimetable
import com.hutb.timetable.data.repo.loadLocalTodaySchedule
import com.hutb.timetable.ui.MainActivity
import com.hutb.timetable.ui.timetable.TodayCourseStatus
import com.hutb.timetable.ui.timetable.computeTodayCourseStatus
import com.hutb.timetable.ui.timetable.courseRangeMinutes
import com.hutb.timetable.ui.timetable.formatMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * “今日课表”桌面小组件。
 *
 * 只读本地课表缓存：不发起网络请求、不读取密码。
 * 4×2 显示下一节/正在上的课，拉高到 4×4 时多列几门剩余课程。
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = runCatching { loadSnapshot(appContext) }
                    .getOrElse { WidgetSnapshot() }
                withContext(Dispatchers.Main) {
                    appWidgetIds.forEach { id ->
                        updateWidget(
                            context = appContext,
                            manager = appWidgetManager,
                            id = id,
                            options = appWidgetManager.getAppWidgetOptions(id),
                            snapshot = snapshot
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private data class WidgetSnapshot(
        val loaded: Boolean = false,
        val week: Int = 0,
        val status: TodayCourseStatus? = null,
        val timetable: ClassTimetable? = null,
        val currentWeekOnly: Boolean = true
    )

    companion object {

        /** App 内刷新课表/作息后调用；没有添加小组件时直接返回。 */
        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, TodayWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch {
                val snapshot = runCatching { loadSnapshot(appContext) }
                    .getOrElse { WidgetSnapshot() }
                withContext(Dispatchers.Main) {
                    ids.forEach { id ->
                        updateWidget(
                            context = appContext,
                            manager = manager,
                            id = id,
                            options = manager.getAppWidgetOptions(id),
                            snapshot = snapshot
                        )
                    }
                }
            }
        }

        private suspend fun loadSnapshot(context: Context): WidgetSnapshot {
            val local = loadLocalTodaySchedule(context) ?: return WidgetSnapshot()
            val status = computeTodayCourseStatus(
                courses = local.courses,
                timetable = local.timetable,
                now = LocalDateTime.now()
            )
            return WidgetSnapshot(
                loaded = true,
                week = local.week,
                status = status,
                timetable = local.timetable,
                currentWeekOnly = local.currentWeekOnly
            )
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            options: Bundle,
            snapshot: WidgetSnapshot
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)

            if (!snapshot.loaded || snapshot.status == null) {
                views.setTextViewText(R.id.widget_title, "今日课表")
                views.setTextViewText(R.id.widget_headline, "打开 App 同步课表")
                views.setTextViewText(
                    R.id.widget_detail,
                    "小组件只读本地缓存，不会发起网络请求"
                )
                views.setTextViewText(R.id.widget_list, "")
                views.setViewVisibility(R.id.widget_list, View.GONE)
                views.setTextViewText(R.id.widget_footer, "")
                manager.updateAppWidget(id, views)
                return
            }

            val status = snapshot.status
            val timetable = snapshot.timetable
            views.setTextViewText(R.id.widget_title, "今日课表 · 第${snapshot.week}周")
            val activeCourse = status.current ?: status.next
            val activeRange = status.currentRange ?: status.nextRange

            when {
                activeCourse != null && activeRange != null -> {
                    val isNow = status.current != null
                    views.setTextViewText(
                        R.id.widget_headline,
                        if (isNow) {
                            "正在上课 · ${activeCourse.courseName}"
                        } else {
                            "下一节 · ${activeCourse.courseName}"
                        }
                    )
                    val minutes = if (isNow) {
                        activeRange.last - status.nowMinutes
                    } else {
                        activeRange.first - status.nowMinutes
                    }
                    val room = activeCourse.classroom.ifBlank { "教室待定" }
                    views.setTextViewText(
                        R.id.widget_detail,
                        "${formatMinutes(activeRange.first)}-${formatMinutes(activeRange.last)} · $room\n" +
                            (if (isNow) "还剩 $minutes 分钟" else "还有 $minutes 分钟")
                    )
                }

                status.todayCount > 0 -> {
                    views.setTextViewText(R.id.widget_headline, "今天的课已结束")
                    views.setTextViewText(
                        R.id.widget_detail,
                        "今天共有 ${status.todayCount} 节课程"
                    )
                }

                else -> {
                    views.setTextViewText(R.id.widget_headline, "今天没有课")
                    views.setTextViewText(R.id.widget_detail, "")
                }
            }

            val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val expanded = maxHeight >= 160
            val upcoming = status.remaining
                .filter { it != activeCourse }
                .mapNotNull { course ->
                    val range = courseRangeMinutes(timetable, course)
                        ?: return@mapNotNull null
                    "${formatMinutes(range.first)} ${course.courseName} · ${course.classroom.ifBlank { "教室待定" }}"
                }
                .take(if (expanded) 4 else 1)
            if (upcoming.isEmpty()) {
                views.setViewVisibility(R.id.widget_list, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_list, View.VISIBLE)
                views.setTextViewText(R.id.widget_list, upcoming.joinToString("\n"))
            }
            views.setTextViewText(
                R.id.widget_footer,
                if (snapshot.currentWeekOnly) "本地缓存 · 仅供参考" else "第${snapshot.week}周缓存 · 仅供参考"
            )
            manager.updateAppWidget(id, views)
        }
    }
}
