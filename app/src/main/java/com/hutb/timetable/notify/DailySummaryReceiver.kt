package com.hutb.timetable.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** 闹钟触发：读本地缓存发摘要，然后安排下一天。 */
class DailySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailySummaryScheduler.ACTION_FIRE) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    DailySummaryNotifier.postDailySummary(appContext, LocalDateTime.now())
                }.onFailure {
                    Log.w("DailySummary", "发送每日摘要失败", it)
                }
            } finally {
                runCatching { DailySummaryScheduler.reschedule(appContext) }
                pendingResult.finish()
            }
        }
    }
}
