package com.bdalxy.edencourt.feature.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.debug.SeedDataTool
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.UserIdentity
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import com.bdalxy.edencourt.data.remote.ApiException
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.ScheduleTrait
import com.bdalxy.edencourt.data.schedule.scheduleTrait
import com.bdalxy.edencourt.data.setting.ApiConfig
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

/**
 * 种子数据生成状态（M3 性能压测工具，仅 debug 构建设置页「开发者工具」区使用）。
 * - Idle=未开始 · Running=生成中 · Done=完成（携带新建测试会话 id）· Error=失败
 */
sealed interface SeedState {
    data object Idle : SeedState
    data object Running : SeedState
    data class Done(val conversationId: Long) : SeedState
    data class Error(val message: String) : SeedState
}

/**
 * 单个角色个性作息概览（设置页「角色作息」区只读展示，T11.10 深化）。
 * 由基础作息 + 该角色的作息特征（trait）/自定义作息派生；让用户直观看到「每个角色作息不同」。
 */
data class CharacterSchedulePreview(
    /** 角色展示名（备注优先） */
    val name: String,
    /** 角色头像路径（可空，无图用首字色块） */
    val avatar: String?,
    /** 作息特征（标准/晨型/夜型） */
    val trait: ScheduleTrait,
    /** 是否手动设置过自定义作息（true=覆盖了默认派生，设置页打「自定义」标记） */
    val isCustom: Boolean,
    /** 起床时刻（HH:mm） */
    val wakeUp: String,
    /** 入睡时刻（HH:mm） */
    val sleep: String,
)

class SettingsViewModel(
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
    private val userIdentityDao: UserIdentityDao,
    private val scheduleConfigStore: ScheduleConfigStore,
    private val characterCardDao: CharacterCardDao,
    private val seedDataTool: SeedDataTool,
) : ViewModel() {

    /** API 配置流（温度滑块等连续控件直接绑定此值） */
    val apiConfig: StateFlow<ApiConfig> = apiConfigStore.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ApiConfig(),
        )

    /** 角色生活作息配置流（T11.10：起床/入睡滑杆绑定，会话列表/聊天页状态实时联动） */
    val scheduleConfig: StateFlow<ScheduleConfig> = scheduleConfigStore.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleConfig(),
        )

    /** 角色作息功能总开关（T11.10 深化两级开关第一级；默认关闭，需手动开启后才会在各会话显示「此刻状态」） */
    val scheduleEnabled: StateFlow<Boolean> = scheduleConfigStore.enabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * 各角色个性作息概览（T11.10 深化：基础作息 + 角色个性派生）。
     * 角色卡列表与基础作息任一变化即重算；空列表时 UI 显示空态文案。
     */
    val characterSchedulePreviews: StateFlow<List<CharacterSchedulePreview>> =
        characterCardDao.observeAll()
            .combine(scheduleConfig) { cards, config ->
                cards.map { card ->
                    // 解析该角色生效作息（自定义 > 个性派生 > 基础），trait 标签单独展示
                    val cfg = CharacterSchedule.resolveConfig(config, card)
                    CharacterSchedulePreview(
                        name = card.remark.ifBlank { card.name },
                        avatar = card.avatar,
                        trait = card.scheduleTrait(),
                        isCustom = card.customSchedule != null,
                        wakeUp = formatMinute(cfg.wakeUpMinute),
                        sleep = formatMinute(cfg.sleepMinute),
                    )
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
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

    /** 种子数据生成状态流（仅 debug 构建「开发者工具」区使用） */
    private val _seedState = MutableStateFlow<SeedState>(SeedState.Idle)
    val seedState: StateFlow<SeedState> = _seedState.asStateFlow()

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

    /** 设置角色作息功能总开关（T11.10 深化两级开关第一级） */
    fun setScheduleEnabled(value: Boolean) = viewModelScope.launch {
        scheduleConfigStore.setEnabled(value)
    }

    /** 更新角色生活作息（T11.10：滑杆即时保存，会话列表/聊天页状态实时联动） */
    fun updateScheduleConfig(transform: (ScheduleConfig) -> ScheduleConfig) = viewModelScope.launch {
        scheduleConfigStore.update(transform)
    }

    /** 恢复默认作息（T11.10） */
    fun resetScheduleConfig() = viewModelScope.launch {
        scheduleConfigStore.reset()
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

    /** 生成千条压测种子数据（M3 性能压测工具，仅 debug 构建设置页「开发者工具」区调用） */
    fun generateSeedData(tag: Int) {
        if (_seedState.value == SeedState.Running) return
        _seedState.value = SeedState.Running
        viewModelScope.launch {
            try {
                val convId = seedDataTool.generate(tag = tag)
                _seedState.value = SeedState.Done(convId)
            } catch (e: Exception) {
                _seedState.value = SeedState.Error("生成失败：${e.message}")
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
                    scheduleConfigStore = app.scheduleConfigStore,
                    characterCardDao = app.characterCardDao,
                    seedDataTool = app.seedDataTool,
                )
            }
        }
    }
}

/** 分钟数 → HH:mm（设置页各角色个性作息概览展示） */
private fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return "%02d:%02d".format(h, m)
}
