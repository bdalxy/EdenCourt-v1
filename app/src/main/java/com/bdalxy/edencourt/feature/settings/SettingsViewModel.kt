package com.bdalxy.edencourt.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.UserIdentity
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import com.bdalxy.edencourt.data.remote.ApiException
import com.bdalxy.edencourt.data.setting.ApiConfig
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页 ViewModel（T3.3 + T3.9，参考企划书 5.3.5 第 1 区 AI API）。
 * - 暴露 API 配置流供 UI 展示
 * - 所有更新方法「输入即保存」：每次变更直接写回 DataStore
 * - 测试连接：发 stream=false 最小请求，展示 加载/成功/失败 状态
 */

/** 测试连接状态（T3.9） */
sealed interface TestState {
    data object Idle : TestState
    data object Loading : TestState
    data object Success : TestState
    data class Error(val message: String) : TestState
}

class SettingsViewModel(
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
    private val userIdentityDao: UserIdentityDao,
) : ViewModel() {

    /** API 配置流（温度滑块等连续控件直接绑定此值） */
    val apiConfig: StateFlow<ApiConfig> = apiConfigStore.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ApiConfig(),
        )

    /** 用户自设身份流（T8.13：无则 null，UI 显示默认值） */
    val userIdentity: StateFlow<UserIdentity?> = userIdentityDao.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** 测试连接状态流 */
    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    /** Base URL：输入即保存 */
    fun updateBaseUrl(value: String) = viewModelScope.launch {
        apiConfigStore.update { it.copy(baseUrl = value) }
    }

    /** API Key：输入即保存（不打印日志） */
    fun updateApiKey(value: String) = viewModelScope.launch {
        apiConfigStore.update { it.copy(apiKey = value) }
    }

    /** 模型名：输入即保存 */
    fun updateModel(value: String) = viewModelScope.launch {
        apiConfigStore.update { it.copy(model = value) }
    }

    /** 温度：滑块拖动即保存（0~2） */
    fun updateTemperature(value: Double) = viewModelScope.launch {
        apiConfigStore.update { it.copy(temperature = value) }
    }

    /** max_tokens：输入即保存，非法/越界回退当前值 */
    fun updateMaxTokens(value: String) = viewModelScope.launch {
        apiConfigStore.update {
            it.copy(maxTokens = value.toIntOrNull()?.coerceIn(1, 32_768) ?: it.maxTokens)
        }
    }

    /** 保存用户自设身份（唯一一条，T8.13）：空白兜底默认值，避免空身份 */
    fun saveIdentity(name: String, calling: String, description: String, personality: String, background: String) = viewModelScope.launch {
        userIdentityDao.upsert(
            UserIdentity(
                name = name.trim().ifBlank { "我" },
                calling = calling.trim().ifBlank { "你" },
                description = description.trim(),
                personality = personality.trim(),
                background = background.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 测试连接：发最小 stream=false 请求，按结果更新状态（T3.9） */
    fun testConnection() {
        if (_testState.value == TestState.Loading) return
        _testState.value = TestState.Loading
        viewModelScope.launch {
            try {
                val config = apiConfigStore.config.first()
                aiChatService.testConnection(config)
                _testState.value = TestState.Success
            } catch (e: ApiException) {
                // 按错误类型给出明确文案（与聊天页一致的分类）
                val text = when (e.kind) {
                    ApiErrorKind.AUTH -> "API Key 无效，请检查后重试"
                    ApiErrorKind.RATE_LIMIT -> "请求过于频繁，请稍后重试"
                    ApiErrorKind.TIMEOUT -> "连接超时，请检查网络后重试"
                    ApiErrorKind.NETWORK -> "网络不可用，请检查网络后重试"
                    ApiErrorKind.SERVER -> "服务器错误（${e.message}）"
                }
                _testState.value = TestState.Error(text)
            } catch (e: Exception) {
                _testState.value = TestState.Error("连接失败：${e.message}")
            }
        }
    }

    companion object {
        /** 工厂：从 Application 取 ApiConfigStore 与 AI 服务单例 */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                SettingsViewModel(
                    apiConfigStore = app.apiConfigStore,
                    aiChatService = app.aiChatService,
                    userIdentityDao = app.userIdentityDao,
                )
            }
        }
    }
}
