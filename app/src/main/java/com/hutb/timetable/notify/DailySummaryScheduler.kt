package com.hutb.timetable.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.hutb.timetable.HutbApp
import com.hutb.timetable.core.nextDailyTrigger
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 每日摘要的系统闹钟。
 *
 * 用 setAndAllowWhileIdle 而非精确闹钟：不需要 SCHEDULE_EXACT_ALARM，
 * 系统可能延迟几分钟送达，对课程提醒可以接受。
 */
object DailySummaryScheduler {

    const val ACTION_FIRE = "com.hutb.timetable.action.DAILY_SUMMARY"
    private const val REQUEST_CODE = 2001

    fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val settings = (appContext as? HutbApp)?.container?.settings ?: return
        val pending = pendingIntent(appContext)
        manager.cancel(pending)

        if (!settings.dailySummaryEnabled) {
            pending.cancel()
            return
        }

        val next = nextDailyTrigger(LocalDateTime.now(), settings.dailySummaryMinutes)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, DailySummaryReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
