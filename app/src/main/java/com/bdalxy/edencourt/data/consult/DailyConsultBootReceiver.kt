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
 * 开机启动接收器（v1.2 P2，T8.5）。
 * BOOT_COMPLETED 后重排每日打听闹钟（设备重启后 AlarmManager 清除原闹钟，需重建）。
 */
class DailyConsultBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as EdenCourtApp
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val enabled = app.dailyConsultConfigStore.config.first().enabled
                if (enabled) app.dailyConsultScheduler.schedule(app) else app.dailyConsultScheduler.cancel(app)
            } catch (e: Exception) {
                Log.w(TAG, "开机重排失败：${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "DailyConsultBoot"
    }
}