package com.bdalxy.edencourt.data.setting

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform

/** 顶层 DataStore 委托：进程内单例，必须在文件顶层声明 */
private val Context.apiConfigDataStore by preferencesDataStore(name = "api_config")

/**
 * API 配置数据模型（T3.1，参考企划书 3.6.1）。
 * 默认值指向 DeepSeek OpenAI 兼容端点；apiKey 存 DataStore 前由 [ApiKeyCipher] 加密，不打印明文日志。
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

/**
 * API 配置持久化：Preferences DataStore 读写，配置变化时 Flow 自动刷新（T3.1）。
 *
 * T12.1（S1 安全红线）改造：apiKey 不再明文落盘，改为 [ApiKeyCipher] 加密后的密文存入
 * `api_key_enc` 键；读取时解密后暴露给调用方（对外 [ApiConfig.apiKey] 语义不变）。
 * 兼容旧版本已落地的明文 `api_key`：首次读取时检测到「明文存在 + 密文缺失」则自动迁移
 * （加密写密文键、删除明文键），用户无需重新输入。
 */
class ApiConfigStore(
    private val context: Context,
    private val cipher: ApiKeyCipher = ApiKeyCipher(),
) {

    /** DataStore 键名 */
    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        /** 密文 apiKey（T12.1 起）：非空即加密后的「IV+密文」Base64 */
        val API_KEY_ENC = stringPreferencesKey("api_key_enc")
        /** 旧明文 apiKey 键：仅作为迁移源保留，迁移后删除；新代码不再写入 */
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val TOP_P = doublePreferencesKey("top_p")
    }

    /** 配置流：DataStore 变化自动重发；读取时自动完成「旧明文→密文」迁移并解密返回 */
    val config: Flow<ApiConfig> = context.apiConfigDataStore.data.transform { prefs ->
        // 读取密文；缺失则回退为明文（用于一键迁移）
        val enc = prefs[Keys.API_KEY_ENC]
        var apiKey = if (enc == null) "" else cipher.decrypt(enc)
        val legacy = prefs[Keys.API_KEY]
        // 自动迁移：旧的明文 apiKey 存在且尚无密文 → 加密写入密文键并清空明文键（幂等，仅一次）
        if (!legacy.isNullOrEmpty() && enc == null) {
            apiKey = legacy
            context.apiConfigDataStore.edit { it ->
                it[Keys.API_KEY_ENC] = cipher.encrypt(legacy)
                it.remove(Keys.API_KEY)
            }
        }
        emit(buildConfig(prefs, apiKey))
    }

    /** 把 DataStore Preference 映射为对外配置对象 */
    private fun buildConfig(prefs: androidx.datastore.preferences.core.Preferences, apiKey: String): ApiConfig =
        ApiConfig(
            baseUrl = prefs[Keys.BASE_URL] ?: ApiConfig.DEFAULT_BASE_URL,
            apiKey = apiKey,
            model = prefs[Keys.MODEL] ?: ApiConfig.DEFAULT_MODEL,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.7,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 2048,
            topP = prefs[Keys.TOP_P] ?: 1.0,
        )

    /** 更新配置：基于当前值生成新值后写回；apiKey 加密后写入密文键。
     * baseUrl 强制要求 HTTPS（S4 安全：避免用户误填 http:// 明文传输 API Key）。
     */
    suspend fun update(transform: (ApiConfig) -> ApiConfig) {
        val next = transform(config.first())
        // HTTPS 校验：非 https 地址直接拒绝保存（本地调试走 https://localhost 或代理 https 转发）
        require(next.baseUrl.isBlank() || next.baseUrl.trim().startsWith("https://", ignoreCase = true)) {
            "API 地址必须使用 HTTPS 安全连接，防止 API Key 明文传输"
        }
        context.apiConfigDataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = next.baseUrl
            prefs[Keys.API_KEY_ENC] = cipher.encrypt(next.apiKey)
            prefs.remove(Keys.API_KEY)
            prefs[Keys.MODEL] = next.model
            prefs[Keys.TEMPERATURE] = next.temperature
            prefs[Keys.MAX_TOKENS] = next.maxTokens
            prefs[Keys.TOP_P] = next.topP
        }
    }
}
