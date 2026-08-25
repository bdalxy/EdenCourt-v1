package com.bdalxy.edencourt.data.setting

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 顶层 DataStore 委托：进程内单例，必须在文件顶层声明 */
private val Context.interactionConfigDataStore by preferencesDataStore(name = "interaction_config")

/**
 * 角色间互动全局配置（v1.2b，T8.7）。
 * enabled=全局总开关：关闭后所有会话都不参与角色间互动。
 * 默认开启，但每个会话仍需各自 conversation.interactionEnabled=true 才可成为互动成员（新建默认 false）。
 */
data class InteractionConfig(
    /** 全局是否允许角色间互动（默认开启） */
    val enabled: Boolean = true,
)

/** 角色间互动全局配置持久化：Preferences DataStore 读写，配置变化时 Flow 自动刷新 */
class InteractionConfigStore(private val context: Context) {

    /** DataStore 键名 */
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
    }

    /** 配置流：DataStore 变化自动重发 */
    val config: Flow<InteractionConfig> = context.interactionConfigDataStore.data.map { prefs ->
        InteractionConfig(enabled = prefs[Keys.ENABLED] ?: true)
    }

    /** 更新全局总开关 */
    suspend fun setEnabled(enabled: Boolean) {
        context.interactionConfigDataStore.edit { prefs -> prefs[Keys.ENABLED] = enabled }
    }

    /** 读取当前配置（一次性） */
    suspend fun current(): InteractionConfig = config.first()
}