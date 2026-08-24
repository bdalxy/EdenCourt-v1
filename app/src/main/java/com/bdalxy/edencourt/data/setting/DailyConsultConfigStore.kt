package com.bdalxy.edencourt.data.setting

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 顶层 DataStore 委托：进程内单例，必须在文件顶层声明 */
private val Context.dailyConsultDataStore by preferencesDataStore(name = "daily_consult")

/**
 * 每日打听配置数据模型（v1.2 P2，T8.5）。
 * 控制「每日定时 + 概率 + 角色自驱动打听」的开关、窗口时段与触发概率。
 */
data class DailyConsultConfig(
    /** 是否启用每日打听（默认开启） */
    val enabled: Boolean = true,
    /** 窗口起始分钟（一天内的分钟数，0~1439；默认 20:00 = 1200） */
    val windowStartMinute: Int = DEFAULT_START_MINUTE,
    /** 窗口结束分钟（0~1439；默认 22:00 = 1320） */
    val windowEndMinute: Int = DEFAULT_END_MINUTE,
    /** 单个活跃会话是否发起打听的概率（默认 0.3） */
    val probability: Float = DEFAULT_PROBABILITY,
) {
    companion object {
        /** 默认窗口起点 20:00 */
        const val DEFAULT_START_MINUTE = 20 * 60
        /** 默认窗口终点 22:00 */
        const val DEFAULT_END_MINUTE = 22 * 60
        /** 默认触发概率 30% */
        const val DEFAULT_PROBABILITY = 0.3f
    }
}

/** 每日打听配置持久化：Preferences DataStore 读写，配置变化时 Flow 自动刷新 */
class DailyConsultConfigStore(private val context: Context) {

    /** DataStore 键名 */
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val WINDOW_START_MINUTE = intPreferencesKey("window_start_minute")
        val WINDOW_END_MINUTE = intPreferencesKey("window_end_minute")
        val PROBABILITY = floatPreferencesKey("probability")
    }

    /** 配置流：DataStore 变化自动重发 */
    val config: Flow<DailyConsultConfig> = context.dailyConsultDataStore.data.map { prefs ->
        DailyConsultConfig(
            enabled = prefs[Keys.ENABLED] ?: true,
            windowStartMinute = prefs[Keys.WINDOW_START_MINUTE] ?: DailyConsultConfig.DEFAULT_START_MINUTE,
            windowEndMinute = prefs[Keys.WINDOW_END_MINUTE] ?: DailyConsultConfig.DEFAULT_END_MINUTE,
            probability = prefs[Keys.PROBABILITY] ?: DailyConsultConfig.DEFAULT_PROBABILITY,
        )
    }

    /** 更新配置：基于当前值生成新值后写回 */
    suspend fun update(transform: (DailyConsultConfig) -> DailyConsultConfig) {
        val next = transform(config.first())
        context.dailyConsultDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = next.enabled
            prefs[Keys.WINDOW_START_MINUTE] = next.windowStartMinute
            prefs[Keys.WINDOW_END_MINUTE] = next.windowEndMinute
            prefs[Keys.PROBABILITY] = next.probability
        }
    }
}