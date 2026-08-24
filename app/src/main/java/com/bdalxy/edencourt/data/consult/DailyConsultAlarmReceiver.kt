package com.bdalxy.edencourt.data.consult

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bdalxy.edencourt.EdenCourtApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 每日打听闹钟接收器（v1.2 P2，T8.5）。
 * 窗口起点被 AlarmManager 触发后：读配置 → 启用则（且仅在）窗口内执行一次 runOnce() → 无论结果重排次日；已关则取消。
 */
class DailyConsultAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as EdenCourtApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val cfg = app.dailyConsultConfigStore.config.first()
                if (!cfg.enabled) {
                    // 已关闭：取消闹钟
                    app.dailyConsultScheduler.cancel(app)
                } else {
                    // force=true 为调试通道（adb 显示广播触发）：跳过窗口判断、强制覆盖概率 100%，用于真机验收链路
                    val force = intent.getBooleanExtra(EXTRA_FORCE, false)
                    if (force || app.dailyConsultScheduler.isWithinWindow(cfg)) {
                        val topic = intent.getStringExtra(EXTRA_TOPIC)
                        app.dailyConsultExecutor.runOnce(
                            forcedProbability = if (force) 1f else null,
                            topicOverride = if (force) topic else null,
                        )
                    }
                    // 无论结果，重排次日
                    app.dailyConsultScheduler.schedule(app)
                }
            } catch (e: Exception) {
                Log.w(TAG, "每日打听执行异常：${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DailyConsultAlarm"
        /** 调试强制触发 Extra（adb 显示广播，跳过窗口/概率） */
        const val EXTRA_FORCE = "extra_force"
        /** 调试强制定向主题 Extra（透传给 executor，验证定位→旁路→沉淀链路） */
        const val EXTRA_TOPIC = "extra_topic"
    }
}