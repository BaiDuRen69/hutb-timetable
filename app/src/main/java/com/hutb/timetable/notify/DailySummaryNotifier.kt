package com.hutb.timetable.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hutb.timetable.HutbApp
import com.hutb.timetable.R
import com.hutb.timetable.core.isWithinQuietHours
import com.hutb.timetable.data.repo.loadLocalTodaySchedule
import com.hutb.timetable.ui.MainActivity
import com.hutb.timetable.ui.timetable.courseEntriesForDay
import java.time.LocalDateTime

/**
 * 每日课程摘要通知。
 *
 * 数据只来自本机课表缓存；不联网、不读取密码。
 * 当前周没有缓存时不发送，避免把旧周的课程当成今天。
 */
object DailySummaryNotifier {

    private const val CHANNEL_ID = "daily_summary"
    private const val DAILY_NOTIFICATION_ID = 2001
    private const val TEST_NOTIFICATION_ID = 2002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "每日课程摘要",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每天在设定时间汇总当天课程，内容只来自本地缓存"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    suspend fun postDailySummary(context: Context, now: LocalDateTime): Boolean {
        val appContext = context.applicationContext
        val container = (appContext as? HutbApp)?.container ?: return false
        val settings = container.settings
        if (!settings.dailySummaryEnabled) return false

        val minutes = now.hour * 60 + now.minute
        if (settings.quietHoursEnabled &&
            isWithinQuietHours(
                minutes,
                settings.quietHoursStartMinutes,
                settings.quietHoursEndMinutes
            )
        ) {
            return false
        }
        if (!canPost(appContext)) return false

        val local = loadLocalTodaySchedule(appContext) ?: return false
        // 没有本周缓存时宁可不说，也不把上周课程当成今天
        if (!local.currentWeekOnly) return false

        val entries = courseEntriesForDay(local.courses, local.timetable, now.dayOfWeek.value)
        if (entries.isEmpty() && !settings.dailySummaryNotifyWithoutCourses) return false

        ensureChannel(appContext)
        val content = buildDailySummaryContent(entries, minutes)
        show(
            context = appContext,
            id = DAILY_NOTIFICATION_ID,
            title = content.title,
            body = content.body,
            week = local.week
        )
        return true
    }

    /** 设置页的“发送测试通知”：绕过开关与免打扰，只验证系统通道是否畅通。 */
    @SuppressLint("MissingPermission")
    fun postTest(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!canPost(appContext)) return false
        ensureChannel(appContext)
        show(
            context = appContext,
            id = TEST_NOTIFICATION_ID,
            title = "每日课程提醒（测试）",
            body = "正式提醒会在设定时间发送，内容只来自本地课表缓存，不会联网。",
            week = 0
        )
        return true
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, id: Int, title: String, body: String, week: Int) {
        val contentIntent = PendingIntent.getActivity(
            context,
            2001,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_SHORTCUT_ACTION, "today_timetable")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timetable)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(summarySubtitle(week))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
