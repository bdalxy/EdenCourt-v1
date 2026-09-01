package com.bdalxy.edencourt.data.consult

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bdalxy.edencourt.data.setting.DailyConsultConfig
import com.bdalxy.edencourt.data.setting.DailyConsultConfigStore
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * 每日打听定时调度器（v1.2 P2，T8.5）。
 * 用系统 AlarmManager 在「下次窗口起点」触发一次广播；
 * 接收器触发后是否执行由 enable + 窗口内判断决定。
 */
class DailyConsultScheduler(
    private val configStore: DailyConsultConfigStore,
) {

    /**
     * 安排下次窗口起点的精确闹钟；仅在启用时生效（未启用则取消）。
     * 精确闹钟在 Android 12+ 需 SCHEDULE_EXACT_ALARM，未授权时退回非精确（不崩溃）。
     */
    suspend fun schedule(context: Context) {
        val cfg = configStore.config.first()
        if (!cfg.enabled) {
            cancel(context)
            return
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextWindowStartMillis(cfg.windowStartMinute)
        val pi = pendingIntent(context)
        // lint 修复：显式判断精确闹钟是否可用（Android 12+ 需 SCHEDULE_EXACT_ALARM），
        // 未授权退回非精确；行为与原 runCatching 兜底一致，但静态可识别、不依赖抛异常。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** 取消每日打听闹钟 */
    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(context))
    }

    /** 当前时刻是否落在每日打听窗口内（含端点） */
    fun isWithinWindow(config: DailyConsultConfig): Boolean {
        val now = ZonedDateTime.now()
        val minute = now.hour * 60 + now.minute
        val start = config.windowStartMinute.coerceIn(0, 23 * 60 + 59)
        val end = config.windowEndMinute.coerceIn(0, 23 * 60 + 59)
        return minute in start..end
    }

    /** 计算「下次窗口起点」毫秒时间戳：严格晚于当前时刻，当天已过则顺延到次日 */
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
            Intent(context, DailyConsultAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val REQUEST_CODE = 0xDCA1
    }
}