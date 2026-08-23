package com.bdalxy.edencourt.data.setting

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 顶层 DataStore 委托：进程内单例，必须在文件顶层声明 */
private val Context.apiConfigDataStore by preferencesDataStore(name = "api_config")

/**
 * API 配置数据模型（T3.1，参考企划书 3.6.1）。
 * 默认值指向 DeepSeek OpenAI 兼容端点；apiKey 存 DataStore，不打印明文日志。
 */
data class ApiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val topP: Double = 1.0,
) {
    companion object {
        /** DeepSeek OpenAI 兼容端点 */
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        /** 默认模型（用户指定 deepseek-v4-flash） */
        const val DEFAULT_MODEL = "deepseek-v4-flash"
    }
}

/** API 配置持久化：Preferences DataStore 读写，配置变化时 Flow 自动刷新 */
class ApiConfigStore(private val context: Context) {

    /** DataStore 键名 */
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TOP_P = doublePreferencesKey("top_p")
    }

    /** 配置流：DataStore 变化自动重发 */
    val config: Flow<ApiConfig> = context.apiConfigDataStore.data.map { prefs ->
        ApiConfig(
            baseUrl = prefs[Keys.BASE_URL] ?: ApiConfig.DEFAULT_BASE_URL,
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: ApiConfig.DEFAULT_MODEL,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 2048,
            topP = prefs[Keys.TOP_P] ?: 1.0,
        )
    }

    /** 更新配置：基于当前值生成新值后写回 */
    suspend fun update(transform: (ApiConfig) -> ApiConfig) {
        val next = transform(config.first())
        context.apiConfigDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = next.baseUrl
            prefs[Keys.API_KEY] = next.apiKey
            prefs[Keys.MODEL] = next.model
            prefs[Keys.TEMPERATURE] = next.temperature
            prefs[Keys.MAX_TOKENS] = next.maxTokens
            prefs[Keys.TOP_P] = next.topP
        }
    }
}
