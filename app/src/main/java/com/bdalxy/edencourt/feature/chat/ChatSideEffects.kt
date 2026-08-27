package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.CrossSessionConsultService
import com.bdalxy.edencourt.data.repository.MemoryRepository
import kotlinx.coroutines.flow.first

/**
 * 跨会话打听（暗访）副作用执行器（T8.3 回传 + T8.4 装配；T12.9 从 ChatViewModel 抽出）。
 *
 * 职责：① 检测用户消息是否为「跨会话打听」意图（关键词触发 + 点名角色卡定位目标会话）；
 * ② 执行旁路打听：未点名目标时按主题检索候选会话 B，插 AI 占位消息（"我去打听一下…"），
 * 完成后原地更新为回传结果（正文自然带来源），用户全程可见、不打断正文。
 *
 * 与 ChatViewModel 的边界：本类只做数据访问与逻辑，不持有任何 UI 状态；
 * 生成状态（isGenerating）变化通过 onGeneratingChanged 回调通知调用方。
 */
class ChatSideEffects(
    private val conversationId: Long,
    private val repository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val memoryRepository: MemoryRepository,
    private val crossSessionConsultService: CrossSessionConsultService,
) {

    /** 用户触发跨会话打听的请求（v1.2，T8.4） */
    data class ConsultRequest(
        val targetConversationId: Long?,
        val question: String,
    )

    /**
     * 检测用户消息是否为「跨会话打听」意图（T8.4，MVP 用关键词触发）：
     * - 命中触发词（去问问/帮我打听/打听一下…）即视为打听；
     * - 若能点名目标角色卡（消息里出现某角色名）→ 直接定位其某会话；
     * - 主题 = 去掉触发词与点名的角色名后的剩余文本。
     */
    suspend fun detectConsult(text: String, conversations: List<Conversation>): ConsultRequest? {
        val trigger = CONSULT_TRIGGERS.firstOrNull { text.contains(it) } ?: return null
        val cardNameById = characterCardDao.observeAll().first()
            .mapNotNull { it.id to (it.remark.ifBlank { it.name }) }
            .toMap()
        var targetId: Long? = null
        var matchedName: String? = null
        // 在非当前会话中找「被点名角色」的任一会话作为打听目标
        for (conv in conversations) {
            if (conv.id == conversationId) continue
            val cid = conv.characterCardId ?: continue
            val name = cardNameById[cid] ?: continue
            if (text.contains(name)) {
                matchedName = name
                targetId = conv.id
                break
            }
        }
        var question = text.replace(trigger, "").trim()
        matchedName?.let { question = question.replace(it, "").trim() }
        return ConsultRequest(targetConversationId = targetId, question = question.ifBlank { text })
    }

    /**
     * 执行一次用户触发的旁路打听（T8.3 回传 + T8.4 装配）：
     * - 未点名目标时用 T8.1 按主题检索候选会话 B；
     * - 插一条 AI 占位消息（复用现有消息流渲染："我去打听一下…"），完成后原地更新为回传结果，用户全程可见、不打断正文。
     *
     * @param visitor 发起打听的角色名（会话绑定角色名，注入回传正文表述 "我去问了X，X说…"）
     * @param onGeneratingChanged 生成状态回调（UI 层借此同步 isGenerating）
     */
    suspend fun runConsult(
        req: ConsultRequest,
        visitor: String,
        onGeneratingChanged: (Boolean) -> Unit,
    ) {
        var targetId = req.targetConversationId
        if (targetId == null) {
            val candidates = memoryRepository.locateTargetConversationIds(splitTerms(req.question), conversationId)
            targetId = candidates.firstOrNull()
        }
        // AI 占位消息（GENERATING，UI 顶部显示「正在输入…」同款处理）
        val aiId = repository.insertMessage(
            Message(
                conversationId = conversationId,
                role = Role.ASSISTANT,
                content = "（我去打听一下…）",
                status = MessageStatus.GENERATING,
            ),
        )
        onGeneratingChanged(true)
        try {
            if (targetId == null) {
                updateConsultMessage(aiId, "我去问了一圈，没人知道呀。", MessageStatus.SENT)
                return
            }
            val result = crossSessionConsultService.consult(targetId, req.question, visitor)
            if (result.success) {
                // T12.11 小夏转述：以发起角色（A）自己的口吻/动作把打听结果转述给用户，
                // 避免把 B 的原话（含 B 的动作描写）直接贴出、视觉上变成 B 在跟用户说话
                val relayed = crossSessionConsultService.relayAsVisitor(
                    visitorConversationId = conversationId,
                    visitorName = visitor,
                    question = req.question,
                    answer = result.answer,
                    sourceName = result.sourceName,
                )
                updateConsultMessage(aiId, relayed, MessageStatus.SENT)
            } else {
                updateConsultMessage(aiId, result.reason ?: "没问到", MessageStatus.SENT)
            }
        } finally {
            onGeneratingChanged(false)
        }
    }

    /**
     * 把打听占位消息原地更新为最终回传文本（T8.3 结果回传 A），并同步刷新会话列表预览
     * （T12.11：类微信——预览/时间跟随最新一条回传，避免列表仍显示上一句）。
     */
    private suspend fun updateConsultMessage(id: Long, content: String, status: MessageStatus) {
        repository.getMessage(id)?.let { repository.updateMessage(it.copy(content = content, status = status)) }
        val conv = repository.getConversation(conversationId) ?: return
        val now = System.currentTimeMillis()
        repository.updateConversation(
            conv.copy(
                lastMessagePreview = content.replace('\n', ' ').take(50),
                lastMessageTime = now,
                updatedAt = now,
            ),
        )
    }

    /** 打听主题拆词（按常见标点/空白切分，供 T8.1 检索） */
    private fun splitTerms(question: String): List<String> =
        question.split(Regex("[，。,.！!？?、\\s]+")).filter { it.isNotBlank() }

    companion object {
        /** 跨会话打听触发词（v1.2，T8.4 意图识别：命中任一即视为用户触发打听） */
        private val CONSULT_TRIGGERS = listOf(
            "去打听", "帮我打听", "打听一下", "去问问", "帮我问",
            "打探", "探探口风", "问问", "去问",
        )
    }
}
