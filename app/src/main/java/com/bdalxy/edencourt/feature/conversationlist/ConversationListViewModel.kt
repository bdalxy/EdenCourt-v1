package com.bdalxy.edencourt.feature.conversationlist

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationTraitsDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import com.bdalxy.edencourt.data.traits.ConversationTraitsGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话列表页 ViewModel（T2.6 + T4.7）。
 * 数据源：repository.observeConversations()（Room 响应式 Flow → StateFlow），
 * 数据库变化时列表自动刷新，无需手动通知。
 * T4.7 起：FAB 新建会话前先选角色卡（或默认助手），会话绑定 characterCardId。
 */
class ConversationListViewModel(
    private val repository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val scheduleConfigStore: ScheduleConfigStore,
    private val conversationTraitsDao: ConversationTraitsDao,
    private val conversationTraitsGenerator: ConversationTraitsGenerator,
) : ViewModel() {

    /** 会话列表 UI 状态（按更新时间降序，数据库自动维护） */
    val conversations: StateFlow<List<Conversation>> =
        repository.observeConversations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 角色生活作息配置（T11.10：会话列表角色卡片「此刻状态」按此计算） */
    val scheduleConfig: StateFlow<ScheduleConfig> = scheduleConfigStore.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleConfig(),
        )

    /** 角色作息全局开关（T11.10 深化两级开关第一级：关闭时会话列表/聊天页均不显示状态） */
    val scheduleEnabled: StateFlow<Boolean> = scheduleConfigStore.enabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /** 首次加载是否完成（T6.1：首帧前显示进度圈，避免空状态闪烁，企划书 5.5.1） */
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            // 等待首次数据下发后结束加载态
            repository.observeConversations().first()
            _isLoading.value = false
        }
    }

    /** 角色卡列表（新建会话选卡弹窗数据源；内置优先） */
    val characterCards: StateFlow<List<CharacterCardEntity>> =
        characterCardDao.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * 新建会话（T4.7：绑定角色卡）。
     * 卡片会话建会话后后台生成「独有人格」（6 维数值 + 反差对 + 印象摘要，T13.4）：生成不阻塞跳转，
     * LLM 调用失败自动走启发式/模板降级，任何异常都不影响会话建立。
     * @param cardId 绑定的角色卡 id；null = 默认助手（不生成人格）
     * @param onCreated 回调新会话 id 供跳转聊天页
     */
    fun createConversation(cardId: Long?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createConversation(characterCardId = cardId)
            if (cardId != null) {
                generateTraits(id, cardId)
            }
            onCreated(id)
        }
    }

    /** 生成并落库该会话的独有人格（卡片会话专用；任何失败静默降级，不阻断建会话） */
    private suspend fun generateTraits(conversationId: Long, cardId: Long) {
        runCatching {
            val card = characterCardDao.getById(cardId) ?: return
            val traits = conversationTraitsGenerator.generateForConversation(
                conversationId = conversationId,
                characterCardId = cardId,
                cardJson = card.jsonContent,
            )
            conversationTraitsDao.upsert(traits)
        }.onFailure { e ->
            // 生成失败不阻塞建会话；LLM 未配置/网络异常属预期降级路径
            android.util.Log.w("TraitsGen", "会话($conversationId)人格生成失败，将无差异标识：${e.message}")
        }
    }

    /** 删除会话（长按二次确认后调用；消息由外键级联删除） */
    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            repository.deleteConversation(conversation)
        }
    }

    /**
     * 置顶/取消置顶会话（T6.10）。
     * 翻转 isPinned 字段；Room 响应式查询自动刷新列表（置顶排最前）。
     */
    fun togglePin(conversation: Conversation) {
        viewModelScope.launch {
            repository.updateConversation(conversation.copy(isPinned = !conversation.isPinned))
        }
    }

    /**
     * 更新会话备注别名（T6.14，类微信备注）。
     * remark 为空则回退为会话原 title 展示。
     */
    fun updateRemark(conversation: Conversation, remark: String) {
        viewModelScope.launch {
            repository.updateConversation(conversation.copy(remark = remark.trim()))
        }
    }

    companion object {
        /** 工厂：从 Application 中取仓库/角色卡 DAO 单例 */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                ConversationListViewModel(
                    repository = app.chatRepository,
                    characterCardDao = app.characterCardDao,
                    scheduleConfigStore = app.scheduleConfigStore,
                    conversationTraitsDao = app.conversationTraitsDao,
                    conversationTraitsGenerator = app.conversationTraitsGenerator,
                )
            }
        }
    }
}
