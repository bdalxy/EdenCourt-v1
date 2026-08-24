package com.bdalxy.edencourt.data.consult

import android.util.Log
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.CrossSessionConsultService
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.setting.ApiConfig
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.setting.DailyConsultConfigStore
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.PromptBuilder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * 每日定时 + 概率 + 角色自驱动打听执行器（v1.2 P2，T8.5/T8.6）。
 * 流程：读配置（默认可配）→ 取"当天活跃会话"（updatedAt 属于今天，最多 N 个）→
 * 逐个以概率决定是否发起 → 发起时让该会话角色基于自身上下文的 curiosity 话题 → 拆词定位候选 B → 旁路打听，
 * 成功则沉淀为 A 会话一条 AI「今日见闻」消息（T8.6：来源标记），失败则不写。
 */
class DailyConsultExecutor(
    private val chatRepository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val memoryRepository: MemoryRepository,
    private val crossSessionConsultService: CrossSessionConsultService,
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
    private val configStore: DailyConsultConfigStore,
) {

    /** 执行摘要（供日志/测试/人工审核观测） */
    data class ExecutionSummary(
        /** 是否读到配置并尝试执行 */
        val executed: Boolean = false,
        /** 当天活跃会话数（候选池大小） */
        val activeCount: Int = 0,
        /** 实际发起打听的次数（命中概率者） */
        val attemptedCount: Int = 0,
        /** 成功打听到并沉淀为见闻的次数 */
        val successCount: Int = 0,
        /** 打听到的见闻文本列表（每条对应一次成功） */
        val news: List<String> = emptyList(),
    )

    /**
     * 执行一次每日打听（整个窗口内调用一次，逐个活跃会话按概率触发）。
     * @param forcedProbability 仅测试/调试时用：覆盖配置概率；null 则用配置值
     * @param topicOverride 仅测试/调试时用：跳过 AI 话题生成，直接使用指定主题（用于确认「定位→旁路→沉淀」链路）
     */
    suspend fun runOnce(forcedProbability: Float? = null, topicOverride: String? = null): ExecutionSummary {
        // 1. 读配置：未启用或未读到则直接返回
        val cfg = configStore.config.firstOrNull()
        if (cfg == null || !cfg.enabled) return ExecutionSummary()
        // 2. 读 API 配置：无 Key 则不执行（与旁路打听一致）
        val config = apiConfigStore.config.firstOrNull()
        if (config == null || config.apiKey.isBlank()) return ExecutionSummary()
        val probability = forcedProbability ?: cfg.probability

        // 3. 取"当天活跃会话"（updatedAt 属于今天），最多 N 个
        val active = chatRepository.observeConversations().first()
            .filter { isToday(it.updatedAt) }
            .take(MAX_ACTIVE_CONVERSATIONS)

        var attempted = 0
        val news = mutableListOf<String>()
        val random = Random(System.currentTimeMillis())
        for (conv in active) {
            // 4. 按概率决定是否发起
            if (random.nextFloat() > probability) continue
            attempted++
            // 5. 话题：调试可强制定向，否则让该角色基于自身上下文的 curiosity 自驱动生成
            val topic = topicOverride ?: generateCuriosityTopic(config, conv)
            // 6. 拆词定位目标会话 B（排除自身），取首个候选；打印话题与候选数便于回溯
            val candidates = memoryRepository.locateTargetConversationIds(splitTerms(topic), excludeConversationId = conv.id)
            val targetId = candidates.firstOrNull()
            Log.d(TAG, "会话${conv.id} 话题「$topic」→ 候选B${candidates.size}个 命中${targetId?.toString() ?: "无"}")
            if (targetId == null) continue
            // 7. 旁路打听：visitor = A 角色显示名（备注优先）
            val result = crossSessionConsultService.consult(targetId, topic, resolveVisitorName(conv))
            if (!result.success) continue
            // 8. 成功 → 沉淀为 A 会话一条 AI「今日见闻」（T8.6 来源标记）
            val content = buildString {
                append(PREFIX)
                append(result.answer)
                append("…来自")
                append(result.sourceName.ifBlank { "对方" })
            }
            chatRepository.insertMessage(
                Message(conversationId = conv.id, role = Role.ASSISTANT, content = content),
            )
            news.add("会话${conv.id} 打听到：「$content」")
        }
        Log.d(TAG, "每日打听执行完成：活跃${active.size} 尝试$attempted 成功${news.size}")
        return ExecutionSummary(
            executed = true,
            activeCount = active.size,
            attemptedCount = attempted,
            successCount = news.size,
            news = news,
        )
    }

    /** 让会话 A 的角色基于自身上下文的 quiet 时刻自驱动生成一句"此刻想打听"的话题；失败兜底 */
    private suspend fun generateCuriosityTopic(config: ApiConfig, conversation: Conversation): String {
        // A 的人格底座（绑定角色卡则用其 system，否则默认助手）
        val systemPrompt = conversation.characterCardId?.let { cardId ->
            characterCardDao.getById(cardId)?.let { entity ->
                runCatching { CharacterCardParser.parse(entity.jsonContent) }.getOrNull()
                    ?.let { PromptBuilder.buildCardSystemPrompt(it) }
            }
        } ?: PromptBuilder.DEFAULT_SYSTEM_PROMPT
        // 最近对话历史（控制上下文长度）
        val history = chatRepository.getMessages(conversation.id).takeLast(HISTORY_LIMIT)
            .map { msg -> ChatMessage(role = if (msg.role == Role.USER) "user" else "assistant", content = msg.content) }
        val system = ChatMessage(role = "system", content = "$systemPrompt\n\n【这是一个只有你自己在的安静时刻】")
        val probe = ChatMessage(
            role = "user",
            content = "此刻没有任何人看到你。你心里有没有一件此刻想打听、想知道或感到好奇的事（关于其他会话里的人物、或外面世界的新鲜事）？" +
                "请只用一句话说出你此刻最想打听的那件事，直接给出这句话，不要解释、不要寒暄。",
        )
        val raw = runCatching { aiChatService.completeChat(listOf(system) + history + probe, config) }
            .getOrNull()?.trim()?.trim('"', '“', '”', ' ', '　')
        return raw?.ifBlank { FALLBACK_TOPIC } ?: FALLBACK_TOPIC
    }

    /** 发起会话 A 的角色显示名：备注优先，其次卡名；无卡则回退会话标题 */
    private suspend fun resolveVisitorName(conversation: Conversation): String {
        val card = conversation.characterCardId?.let { characterCardDao.getById(it) }
        return card?.let { it.remark.ifBlank { it.name } } ?: conversation.title.ifBlank { DEFAULT_VISITOR_NAME }
    }

    /** 拆词：按中英文标点与空白切分，供定位搜索（中文整句 LIKE 几乎无法命中） */
    private fun splitTerms(text: String): List<String> =
        text.split(Regex("[，。！？、；：,.!?;:，\\s]+")).map { it.trim() }.filter { it.isNotBlank() }

    /** 时间戳是否属于今天（按系统默认时区） */
    private fun isToday(millis: Long): Boolean =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

    private companion object {
        const val TAG = "DailyConsult"
        /** 当次最多处理的活跃会话数（防骚扰，避免一次挂起太多网络请求） */
        const val MAX_ACTIVE_CONVERSATIONS = 5
        /** 生成话题时携带的最近消息条数 */
        const val HISTORY_LIMIT = 20
        /** 话题生成解析失败时的兜底词 */
        const val FALLBACK_TOPIC = "最近大家身上又发生了什么新鲜事"
        /** 无角色卡时的兜底来客名 */
        const val DEFAULT_VISITOR_NAME = "一位访客"
        /** 见闻消息固定前缀（T8.6 来源标记） */
        const val PREFIX = "[今日见闻]（今天打听来的话）"
    }
}