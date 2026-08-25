package com.bdalxy.edencourt.data.interaction

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bdalxy.edencourt.data.setting.InteractionConfig
import com.bdalxy.edencourt.data.setting.InteractionConfigStore
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * 互动自动驱动定时调度器（v1.2b 互动系统，T11.7）。
 * 用系统 AlarmManager 在「下次自动窗口起点」触发一次广播；
 * 接收器触发后是否执行由全局总开关 + 自动开关 + 窗口内判断决定。
 * 完全复用 T8.5 每日打听的定时模式。
 */
class InteractionAutoScheduler(
    private val configStore: InteractionConfigStore,
) {

    /**
     * 安排下次自动窗口起点的精确闹钟；仅在「全局总开关 + 自动驱动开关」都启用时生效（否则取消）。
     * 精确闹钟在 Android 12+ 需 SCHEDULE_EXACT_ALARM，未授权时退回非精确（不崩溃）。
     */
    suspend fun schedule(context: Context) {
        val cfg = configStore.config.first()
        if (!cfg.enabled || !cfg.autoEnabled) {
            cancel(context)
            return
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextWindowStartMillis(cfg.autoWindowStartMinute)
        runCatching {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
        }.onFailure { e ->
            Log.w(TAG, "精确闹钟不可用，退回非精确：${e.message}")
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context))
        }
    }

    /** 取消自动驱动闹钟 */
    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }

    /** 当前时刻是否落在自动窗口内（含端点） */
    fun isWithinWindow(config: InteractionConfig): Boolean {
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val start = config.autoWindowStartMinute.coerceIn(0, 23 * 60 + 59)
        val end = config.autoWindowEndMinute.coerceIn(0, 23 * 60 + 59)
        return minute in start..end
    }

    /** 计算「下次自动窗口起点」毫秒时间戳：严格晚于当前时刻，当天已过则顺延到次日 */
    private fun nextWindowStartMillis(startMinute: Int): Long {
        val minute = startMinute.coerceIn(0, 23 * 60 + 59)
        val now = ZonedDateTime.now()
        var candidate = now
            .withHour(minute / 60)
            .withMinute(minute % 60)
            .withSecond(0)
            .withNano(0)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, InteractionAutoReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val REQUEST_CODE = 0xDCB1
        const val TAG = "InteractionAutoScheduler"
    }
}
