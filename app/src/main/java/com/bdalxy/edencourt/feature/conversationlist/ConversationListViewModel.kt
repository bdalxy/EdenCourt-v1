package com.bdalxy.edencourt.feature.conversationlist

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 会话列表页 ViewModel（T2.6）。
 * 数据源：repository.observeConversations()（Room 响应式 Flow → StateFlow），
 * 数据库变化时列表自动刷新，无需手动通知。
 */
class ConversationListViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    /** 会话列表 UI 状态（按更新时间降序，数据库自动维护） */
    val conversations: StateFlow<List<Conversation>> =
        repository.observeConversations()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 新建会话（FAB），返回新会话 id 供跳转聊天页 */
    fun createConversation(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createConversation()
            onCreated(id)
        }
    }

    /** 删除会话（长按二次确认后调用；消息由外键级联删除） */
    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            repository.deleteConversation(conversation)
        }
    }

    companion object {
        /** 工厂：从 Application 中取 ChatRepository 单例 */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                ConversationListViewModel(
                    repository = app.chatRepository,
                )
            }
        }
    }
}
