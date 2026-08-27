package com.bdalxy.edencourt.data.interaction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bdalxy.edencourt.EdenCourtApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机启动接收器（T12.8：互动自动驱动补开机重排）。
 * 背景：互动自动驱动此前只有 AlarmManager 窗口闹钟，设备重启后原闹钟被系统清除，
 *     必须开机重建才能继续触发（否则重启后自动驱动静默失效），与 T8.5 每日打听的 Boot 重排保持一致。
 * BOOT_COMPLETED 后调用 InteractionAutoScheduler.schedule（其内部已处理「全局总开关+自动开关」都开→安排、任一关→取消）。
 */
class InteractionAutoBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as EdenCourtApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                // schedule(): 开关全开则重排下次窗口闹钟，任一关闭则取消（无需在此重复读配置判断）
                app.interactionAutoScheduler.schedule(app)
            } catch (e: Exception) {
                Log.w(TAG, "开机重排互动失败：${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "InteractionAutoBoot"
    }
}