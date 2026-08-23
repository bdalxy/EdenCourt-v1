package com.bdalxy.edencourt.feature.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 聊天页 ViewModel（T2.7）。
 * - 按 conversationId 观察该会话消息流与标题
 * - 空会话时插入一条 AI 欢迎语（默认助手定位，供 UI 展示与后续对话）
 */
class ChatViewModel(
    private val conversationId: Long,
    private val repository: ChatRepository,
) : ViewModel() {

    /** 该会话的消息流（按创建时间正序，数据库变化自动刷新） */
    val messages: StateFlow<List<Message>> =
        repository.observeMessages(conversationId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 会话标题（顶部栏显示） */
    val conversationTitle: StateFlow<String?> =
        repository.observeConversation(conversationId)
            .map { it?.title }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

    init {
        viewModelScope.launch {
            // 空会话时插入 AI 欢迎语，让新会话有开场（后续角色卡阶段会替换为角色开场白）
            val existing = repository.observeMessages(conversationId).first()
            if (existing.isEmpty()) {
                repository.insertMessage(
                    Message(
                        conversationId = conversationId,
                        role = Role.ASSISTANT,
                        content = "你好呀，我是永恒庭院。今天想聊些什么呢？",
                    ),
                )
            }
        }
    }

    /** 发送用户消息：入库 + 更新会话标题/预览/时间（T2.8） */
    fun sendMessage(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            // 1. 用户消息入库（触发消息流刷新 → 自动滚底）
            repository.insertMessage(
                Message(
                    conversationId = conversationId,
                    role = Role.USER,
                    content = content,
                ),
            )
            // 2. 更新会话摘要（企划书 4.1.1：标题默认取首条用户消息截断）
            val conv = repository.getConversation(conversationId) ?: return@launch
            val now = System.currentTimeMillis()
            val preview = content.replace('\n', ' ')
            val isUnnamed = conv.title.isBlank() || conv.title == "新对话"
            repository.updateConversation(
                conv.copy(
                    title = if (isUnnamed) preview.take(20) else conv.title,
                    lastMessagePreview = preview.take(50),
                    lastMessageTime = now,
                    updatedAt = now,
                ),
            )
        }
    }

    companion object {
        /** 工厂：传入路由参数 conversationId，从 Application 取仓库单例 */
        fun factory(conversationId: Long, application: Application): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ChatViewModel(
                        conversationId = conversationId,
                        repository = (this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp)
                            .chatRepository,
                    )
                }
            }
    }
}
