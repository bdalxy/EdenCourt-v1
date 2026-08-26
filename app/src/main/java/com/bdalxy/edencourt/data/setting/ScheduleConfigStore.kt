package com.bdalxy.edencourt.data.setting

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 顶层 DataStore 委托：进程内单例，必须在文件顶层声明 */
private val Context.scheduleConfigDataStore by preferencesDataStore(name = "schedule_config")

/**
 * 角色生活作息配置（方向 B' T11.10 深化：全简化版）。
 * - 只保留「起床 + 入睡」两个时刻，其余时段按固定规则划分（见 CharacterSchedule.computeState）
 * - 入睡支持跨天（次日凌晨），用 0~MAX_SLEEP_MINUTE（次日 01:59）表示
 * - 单位：分钟。起床 0~1439，入睡 0~1559（>=1440 即次日凌晨）
 */
data class ScheduleConfig(
    /** 起床时刻（默认 07:00） */
    val wakeUpMinute: Int = DEFAULT_WAKE_UP,
    /** 入睡时刻（默认 23:00；>=1440 表示次日凌晨，如 1500=次日 01:00） */
    val sleepMinute: Int = DEFAULT_SLEEP,
) {
    companion object {
        /** 一天总分钟数 */
        const val MINUTE_DAY = 24 * 60

        const val DEFAULT_WAKE_UP = 7 * 60
        const val DEFAULT_SLEEP = 23 * 60

        /** 入睡时刻允许最大值：次日 01:59（再晚视为极端夜型，不鼓励） */
        const val MAX_SLEEP_MINUTE = 26 * 60 - 1
    }

    /**
     * 整体平移作息时间（T11.10 深化：个性作息派生）。
     * 起床钳制在当天 0~1439；入睡钳制在 0~次日 01:59。
     * @param minutes 偏移分钟数（正=延后，负=提前）
     */
    fun offsetBy(minutes: Int): ScheduleConfig = ScheduleConfig(
        wakeUpMinute = (wakeUpMinute + minutes).coerceIn(0, MINUTE_DAY - 1),
        sleepMinute = (sleepMinute + minutes).coerceIn(0, MAX_SLEEP_MINUTE),
    )
}

/**
 * 角色生活作息配置持久化：Preferences DataStore 读写，配置变化时 Flow 自动刷新（仿 InteractionConfigStore）。
 * 全局开关 enabled：T11.10 深化「两级开关」第一级——默认关闭，需在设置页手动开启后，
 * 再去对应会话角色开启（聊天页顶部入口）才会显示「此刻状态」。
 */
class ScheduleConfigStore(private val context: Context) {

    /** DataStore 键名 */
    private object Keys {
        val ENABLED = booleanPreferencesKey("schedule_enabled")
        val WAKE_UP = intPreferencesKey("wake_up_minute")
        val SLEEP = intPreferencesKey("sleep_minute")
    }

    /** 全局作息功能开关（默认关闭） */
    val enabled: Flow<Boolean> = context.scheduleConfigDataStore.data.map { it[Keys.ENABLED] ?: false }

    /** 配置流：DataStore 变化自动重发 */
    val config: Flow<ScheduleConfig> = context.scheduleConfigDataStore.data.map { prefs ->
        ScheduleConfig(
            wakeUpMinute = prefs[Keys.WAKE_UP] ?: ScheduleConfig.DEFAULT_WAKE_UP,
            sleepMinute = prefs[Keys.SLEEP] ?: ScheduleConfig.DEFAULT_SLEEP,
        )
    }

    /** 设置全局开关（true=开启角色作息功能） */
    suspend fun setEnabled(value: Boolean) {
        context.scheduleConfigDataStore.edit { prefs -> prefs[Keys.ENABLED] = value }
    }

    /** 更新配置（起床/入睡滑杆等连续控件即时保存） */
    suspend fun update(transform: (ScheduleConfig) -> ScheduleConfig) {
        val next = transform(config.first())
        context.scheduleConfigDataStore.edit { prefs ->
            prefs[Keys.WAKE_UP] = next.wakeUpMinute
            prefs[Keys.SLEEP] = next.sleepMinute
        }
    }

    /** 恢复默认作息 */
    suspend fun reset() {
        context.scheduleConfigDataStore.edit { prefs ->
            prefs[Keys.WAKE_UP] = ScheduleConfig.DEFAULT_WAKE_UP
            prefs[Keys.SLEEP] = ScheduleConfig.DEFAULT_SLEEP
        }
    }
}
