package com.bdalxy.edencourt.data.repository

import android.util.Log
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.PromptBuilder
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable

/**
 * 跨会话打听（v1.2 套话）· 旁路暗访执行器（T8.2）。
 * 一个会话（≈一个真人）A 出于好奇，向另一个【具体会话】B 打听信息。
 * - 形态 = 旁路暗访：把 B 当"来客代问"，在 B 上下文中注入一次提问拿回答，不污染 B 正文消息流。
 * - 隐私：B 侧只注入「共享且非绝密」的记忆（绝密记忆 B 根本不知道，故无法外泄=硬性兜底）；是否透露私密由 B 的 LLM 自主决定。
 * - 成败不保证：没问到或超时，如实回传「没问到」，不捏造。
 */
class CrossSessionConsultService(
    private val chatRepository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val memoryRepository: MemoryRepository,
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
) {

    /** 打听结果（T8.2/8.3 回传 A 用） */
    @Serializable
    data class ConsultResult(
        /** 是否拿到可回传的回复 */
        val success: Boolean,
        /** B 的回复原文（success=true 时有效） */
        val answer: String = "",
        /** 回传失败原因/「没人知道」说明（success=false 时有效） */
        val reason: String? = null,
        /** 目标会话绑定的角色卡名称（供 A 侧小字「↑打听到：来自X」展示，T8.3） */
        val sourceName: String = "",
    )

    /** B 侧旁路上下文里最多携带最近多少条消息（控制上下文长度，参考 PromptBuilder 默认 20） */
    private val targetHistoryLimit = 20

    /**
     * 执行一次旁路暗访：让目标会话 B 就 `question` 作答。
     * @param targetConversationId 目标会话 B 的 id
     * @param question 打听内容（一段自然语言的询问主题）
     * @param visitorName 发起会话 A 的角色名（B 视角的"来客"是谁）
     * @return ConsultResult：success=true 表示拿到可回传的回复；否则为「没问到」及原因
     */
    suspend fun consult(targetConversationId: Long, question: String, visitorName: String): ConsultResult {
        val config = apiConfigStore.config.firstOrNull()
        if (config == null || config.apiKey.isBlank()) {
            return ConsultResult(success = false, reason = "未配置 API，无法打听")
        }
        val target = chatRepository.getConversation(targetConversationId)
            ?: return ConsultResult(success = false, reason = "对方不在，问不到")
        val q = question.trim()
        if (q.isEmpty()) return ConsultResult(success = false, reason = "没想好打听什么")

        // 1. 目标会话 B 的人格底座：绑定角色卡则用其 system，否则默认助手
        val cardId = target.characterCardId
        val systemPrompt = if (cardId != null) {
            val entity = characterCardDao.getById(cardId)
            val card = entity?.let { runCatching { CharacterCardParser.parse(it.jsonContent) }.getOrNull() }
            if (card != null) PromptBuilder.buildCardSystemPrompt(card) else PromptBuilder.DEFAULT_SYSTEM_PROMPT
        } else {
            PromptBuilder.DEFAULT_SYSTEM_PROMPT
        }
        val sourceName = if (cardId != null) {
            characterCardDao.getById(cardId)?.name ?: ""
        } else {
            ""
        }

        // 2. 只注入 B 「可向外打捞」的记忆（共享且非绝密）——绝密记忆 B 不感知，硬性兜底
        val consultableMemories = cardId?.let { memoryRepository.getConsultableMemories(it) } ?: emptyList()
        val memoryFragment = if (consultableMemories.isEmpty()) "" else buildString {
            append("[关于对方的事，你记得]\n")
            consultableMemories.forEach { m -> append("- ").append(m.content).append("\n") }
        }.trim()

        // 3. 组装旁路 prompt：B 人格 + B 最近对话历史 + 一条「来客代问」
        val finalSystem = buildString {
            append(systemPrompt)
            if (memoryFragment.isNotBlank()) append("\n\n").append(memoryFragment)
            append("\n\n【本条是一条“旁路探询”，不是用户本人发言】")
            append("现在有另一个会话里的《").append(visitorName).append("》向你打听一件事。")
            append("请只基于你确实知道的内容如实回答，可简单自然；")
            append("如果是你的私密心事、或对方绝不可知道的事，就委婉拒绝或说不知道，绝不要编造；")
            append("实在不知道就说「我不清楚」。")
        }
        // T12.6：数据库层定向取最近 targetHistoryLimit 条，不再 getMessages 拉全量再 takeLast
        val history = chatRepository.getRecentMessages(targetConversationId, targetHistoryLimit)
            .map { msg -> ChatMessage(role = if (msg.role == com.bdalxy.edencourt.data.local.entity.Role.USER) "user" else "assistant", content = msg.content) }
        val probe = ChatMessage(role = "user", content = "（来客代问）$visitorName 想打听：$q")

        val messages = listOf(ChatMessage(role = "system", content = finalSystem)) + history + probe

        // 4. 调 LLM 拿 B 作答（非流式即可）
        val raw = runCatching { aiChatService.completeChat(messages, config) }.getOrElse { e ->
            Log.w(TAG, "旁路打听失败：${e.javaClass.simpleName}: ${e.message}")
            return ConsultResult(success = false, reason = "打听到一半对方没接上", sourceName = sourceName)
        }
        val answer = raw.trim()
        if (answer.isBlank()) {
            return ConsultResult(success = false, reason = "对方没回应", sourceName = sourceName)
        }
        return ConsultResult(success = true, answer = answer, sourceName = sourceName)
    }

    private companion object {
        private const val TAG = "CrossSessionConsult"
    }
}