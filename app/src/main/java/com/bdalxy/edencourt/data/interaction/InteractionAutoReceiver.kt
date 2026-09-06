package com.bdalxy.edencourt.data.interaction

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
 * 互动自动驱动闹钟接收器（v1.2b 互动系统，T11.7）。
 * 自动窗口起点被 AlarmManager 触发后：读配置 → 全局总开关/自动开关都启用则（且仅在）窗口内执行一次 runOnce() → 无论结果重排次日；已关则取消。
 * 完全复用 T8.5 每日打听接收器的模式。
 */
class InteractionAutoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as EdenCourtApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val cfg = app.interactionConfigStore.config.first()
                if (!cfg.enabled || !cfg.autoEnabled) {
                    // 全局总开关或自动驱动已关：取消闹钟
                    app.interactionAutoScheduler.cancel(app)
                } else {
                    // 先重排次日闹钟（几 ms 级快速完成），再执行分钟级交互 LLM——
                    // 若 goAsync 超时（约 10s）进程被回收，次日排钟已落地，不会永久失效
                    app.interactionAutoScheduler.schedule(app)
                    // force=true 为调试通道（adb 显示广播触发）：跳过窗口/概率，用于真机验收链路
                    val force = intent.getBooleanExtra(EXTRA_FORCE, false)
                    if (force || app.interactionAutoScheduler.isWithinWindow(cfg)) {
                        val outcome = app.interactionAutoExecutor.runOnce(forcedProbability = if (force) 1f else null)
                        Log.d(TAG, "自动驱动结果：${if (outcome.success) "成功" else outcome.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "自动驱动执行异常：${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "InteractionAutoAlarm"
        /** 调试强制触发 Extra（adb 显示广播，跳过窗口/概率） */
        const val EXTRA_FORCE = "extra_force"
    }
}
