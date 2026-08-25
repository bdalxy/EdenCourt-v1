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
private val Context.interactionConfigDataStore by preferencesDataStore(name = "interaction_config")

/**
 * 角色间互动全局配置（v1.2b，T8.7 / T11.7）。
 * enabled=全局总开关：关闭后所有会话都不参与角色间互动。
 * auto*=互动自动驱动（T11.7）：已开放互动的角色在每天窗口内按概率自发互相串门，取代纯手动触发。
 * 默认开启，但每个会话仍需各自 conversation.interactionEnabled=true 才可成为互动成员（新建默认 false）。
 */
data class InteractionConfig(
    /** 全局是否允许角色间互动（默认开启） */
    val enabled: Boolean = true,
    /** 是否启用互动自动驱动（默认开启；跟随全局总开关：总开关关则自动也停） */
    val autoEnabled: Boolean = true,
    /** 自动驱动窗口起始分钟（一天内分钟数 0~1439；默认 20:00 = 1200） */
    val autoWindowStartMinute: Int = DEFAULT_START_MINUTE,
    /** 自动驱动窗口结束分钟（0~1439；默认 22:00 = 1320） */
    val autoWindowEndMinute: Int = DEFAULT_END_MINUTE,
    /** 窗口内角色自发发起互动的概率（默认 0.3） */
    val autoProbability: Float = DEFAULT_PROBABILITY,
) {
    companion object {
        /** 默认自动窗口起点 20:00 */
        const val DEFAULT_START_MINUTE = 20 * 60
        /** 默认自动窗口终点 22:00 */
        const val DEFAULT_END_MINUTE = 22 * 60
        /** 默认自动触发概率 30% */
        const val DEFAULT_PROBABILITY = 0.3f
    }
}

/** 角色间互动全局配置持久化：Preferences DataStore 读写，配置变化时 Flow 自动刷新 */
class InteractionConfigStore(private val context: Context) {

    /** DataStore 键名 */
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val AUTO_ENABLED = booleanPreferencesKey("auto_enabled")
        val AUTO_WINDOW_START_MINUTE = intPreferencesKey("auto_window_start_minute")
        val AUTO_WINDOW_END_MINUTE = intPreferencesKey("auto_window_end_minute")
        val AUTO_PROBABILITY = floatPreferencesKey("auto_probability")
    }

    /** 配置流：DataStore 变化自动重发 */
    val config: Flow<InteractionConfig> = context.interactionConfigDataStore.data.map { prefs ->
        InteractionConfig(
            enabled = prefs[Keys.ENABLED] ?: true,
            autoEnabled = prefs[Keys.AUTO_ENABLED] ?: true,
            autoWindowStartMinute = prefs[Keys.AUTO_WINDOW_START_MINUTE] ?: InteractionConfig.DEFAULT_START_MINUTE,
            autoWindowEndMinute = prefs[Keys.AUTO_WINDOW_END_MINUTE] ?: InteractionConfig.DEFAULT_END_MINUTE,
            autoProbability = prefs[Keys.AUTO_PROBABILITY] ?: InteractionConfig.DEFAULT_PROBABILITY,
        )
    }

    /** 更新全局总开关 */
    suspend fun setEnabled(enabled: Boolean) {
        context.interactionConfigDataStore.edit { prefs -> prefs[Keys.ENABLED] = enabled }
    }

    /** 更新配置（T11.7：自动驱动开关/窗口/概率） */
    suspend fun update(transform: (InteractionConfig) -> InteractionConfig) {
        val next = transform(config.first())
        context.interactionConfigDataStore.edit { prefs ->
            prefs[Keys.ENABLED] = next.enabled
            prefs[Keys.AUTO_ENABLED] = next.autoEnabled
            prefs[Keys.AUTO_WINDOW_START_MINUTE] = next.autoWindowStartMinute
            prefs[Keys.AUTO_WINDOW_END_MINUTE] = next.autoWindowEndMinute
            prefs[Keys.AUTO_PROBABILITY] = next.autoProbability
        }
    }

    /** 读取当前配置（一次性） */
    suspend fun current(): InteractionConfig = config.first()
}