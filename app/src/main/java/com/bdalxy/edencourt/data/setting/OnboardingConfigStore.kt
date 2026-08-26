package com.bdalxy.edencourt.data.setting

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 顶层 DataStore 委托：进程内单例，必须在文件顶层声明 */
private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_config")

/**
 * 新手上手引导配置（T11.14）。
 * seen=true 表示用户已看过首启引导（无论跳过还是看完），之后不再弹。
 */
data class OnboardingConfig(
    /** 是否已看过首启引导（默认 false：首次启动展示引导） */
    val seen: Boolean = false,
)

/** 首启引导状态持久化：Preferences DataStore 读写 */
class OnboardingConfigStore(private val context: Context) {

    /** DataStore 键名 */
    private object Keys {
        val SEEN = booleanPreferencesKey("seen")
    }

    /** 配置流：DataStore 变化自动重发 */
    val config: Flow<OnboardingConfig> = context.onboardingDataStore.data.map { prefs ->
        OnboardingConfig(seen = prefs[Keys.SEEN] ?: false)
    }

    /** 标记已看过引导（跳过或看完都调用，保证只弹一次） */
    suspend fun markSeen() {
        context.onboardingDataStore.edit { prefs -> prefs[Keys.SEEN] = true }
    }
}
