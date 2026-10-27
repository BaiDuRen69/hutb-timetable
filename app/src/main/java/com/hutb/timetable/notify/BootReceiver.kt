package com.hutb.timetable.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 重启、改时间/时区或应用更新后，系统闹钟会丢失，需要重新安排。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> DailySummaryScheduler.reschedule(context)
        }
    }
}
