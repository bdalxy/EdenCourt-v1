package com.bdalxy.edencourt.feature.conversationlist

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.repository.ChatRepository
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
) : ViewModel() {

    /** 会话列表 UI 状态（按更新时间降序，数据库自动维护） */
    val conversations: StateFlow<List<Conversation>> =
        repository.observeConversations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
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
     * @param cardId 绑定的角色卡 id；null = 默认助手
     * @param onCreated 回调新会话 id 供跳转聊天页
     */
    fun createConversation(cardId: Long?, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createConversation(characterCardId = cardId)
            onCreated(id)
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

    companion object {
        /** 工厂：从 Application 中取仓库/角色卡 DAO 单例 */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                ConversationListViewModel(
                    repository = app.chatRepository,
                    characterCardDao = app.characterCardDao,
                )
            }
        }
    }
}
