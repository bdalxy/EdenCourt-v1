package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.local.entity.UserIdentity
import com.bdalxy.edencourt.data.remote.ChatMessage
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prompt 构建器（T3.6，参考企划书 3.4.2/3.5.1/3.6.2）。
 * 组装 messages=[system（角色卡+记忆）, 最近 N 条历史, 当前 user]。
 * 阶段 4 起：system 由会话绑定的真实角色卡生成（T4.6）；记忆注入留待阶段 5。
 */
object PromptBuilder {

    /** 默认 system 提示词（未绑定角色卡时的默认助手） */
    const val DEFAULT_SYSTEM_PROMPT =
        "你是「永恒庭院」里的 AI 助手，请用温暖、亲切、自然的中文语气回答用户的问题，保持简洁友好。"

    /** 短期记忆条数默认值（企划书 5.3.5 分区 2 默认 20；本阶段常量，后续可配置） */
    const val DEFAULT_SHORT_TERM_LIMIT = 20

    /** 长期记忆注入条数默认值（企划书 3.5.2：取 top-K，默认 3 条） */
    const val DEFAULT_MEMORY_LIMIT = 3

    /**
     * 由角色卡构建 system 提示词（T4.6，参考企划书 3.4.2）。
     * 拼接规则：「你是{name}。你的性格：{personality}…请始终以该角色身份回复」，
     * 并附加背景/说话风格/示例对话/约束（有则拼入）。
     * @param card 会话绑定的角色卡
     * @param userIdentityText 用户自设身份注入片段（T8.13，可为空），所有角色默认知道用户
     * @return system 提示词文本
     */
    fun buildCardSystemPrompt(card: CharacterCard, userIdentityText: String = ""): String = buildString {
        append("你是「").append(card.name).append("」。\n")
        append("你的性格：").append(card.personality).append("\n")
        if (card.background.isNotBlank()) {
            append("你的背景：").append(card.background).append("\n")
        }
        if (card.speakingStyle.isNotBlank()) {
            append("你的说话风格：").append(card.speakingStyle).append("\n")
        }
        if (card.exampleDialogue.isNotEmpty()) {
            append("参考示例对话：\n")
            card.exampleDialogue.forEach { d ->
                append("用户：「").append(d.user).append("」\n")
                append("你：「").append(d.assistant).append("」\n")
            }
        }
        if (card.constraints.isNotBlank()) {
            append("行为约束：").append(card.constraints).append("\n")
        }
        // T8.13：注入用户自设身份（统一称呼用户），所有角色默认知道
        if (userIdentityText.isNotBlank()) {
            append("\n").append(userIdentityText)
        }
        append("请始终以「").append(card.name).append("」的身份、用上述风格回复用户，不要跳出角色。")
    }

    /**
     * 构建「用户自设身份」注入片段（T8.13）。
     * 所有角色默认知道用户身份：统一的称呼/名字/自设描述，随角色卡 persona 注入，保证全角色统一称呼用户。
     * @param identity 用户自设身份（唯一一条，id=1）
     * @return 注入片段文本（恒非空，调用方可直接拼接）
     */
    fun buildUserIdentityPrompt(identity: UserIdentity): String = buildString {
        append("【关于用户】你认识用户「").append(identity.name).append("」，你会称呼他为「").append(identity.calling).append("」。\n")
        if (identity.description.isNotBlank()) append("用户的自述：").append(identity.description).append("\n")
        if (identity.personality.isNotBlank()) append("用户的性格：").append(identity.personality).append("\n")
        if (identity.background.isNotBlank()) append("用户的背景：").append(identity.background).append("\n")
    }

    /**
     * 构建请求消息列表。
     * @param history 该会话历史消息（按时间正序）
     * @param userInput 当前用户输入（连发攒批时多条以换行合并为一次「来信」，T6.9）
     * @param shortTermLimit 短期记忆条数：只取最近 N 条历史（默认 20）
     * @param systemPrompt system 提示词（默认助手；绑定角色卡时传 buildCardSystemPrompt 结果）
     * @param longTermMemories 长期记忆文本（已检索到的「[关于用户的长期记忆]- …」片段，
     *   拼接到 system 末尾供 AI 自然提及；为空则不影响，默认空串，T5.4）
     * @param userBatchCount 本次来信合并的用户消息条数（>1 表示连发攒批，
     *   在 system 中提示 AI 按性格决定逐条/综合/分组回应，T6.9）
     * @param lifeStateText 当前角色生活状态注入片段（T11.11：睡眠/晨起/活跃/晚间，影响语气与内容；为空则不注入）
     */
    fun build(
        history: List<Message>,
        userInput: String,
        shortTermLimit: Int = DEFAULT_SHORT_TERM_LIMIT,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        longTermMemories: String = "",
        userBatchCount: Int = 1,
        lifeStateText: String = "",
    ): List<ChatMessage> {
        // 1. system 消息（角色卡或默认助手）+ 当前时间注入（T6.11）+ 长期记忆（T5.4）+ 攒批提示（T6.9）
        val finalSystem = buildString {
            // T6.11：注入当前现实时间，让 AI 明确感知三次元时间（每次发送时刷新）
            append("【当前现实时间】").append(currentTimeText()).append("\n\n")
            append(systemPrompt)
            // T11.11：注入当前角色生活状态，让 AI 按状态调整语气与内容（深夜被叫醒会困倦、忙碌时简短等）
            if (lifeStateText.isNotBlank()) {
                append("\n\n【当前角色状态】").append(lifeStateText)
            }
            if (longTermMemories.isNotBlank()) {
                append("\n\n").append(longTermMemories)
            }
            // T6.9：连发多条时提示 AI 像真人一样按性格决定回应方式（不逐条秒回）
            if (userBatchCount > 1) {
                append("\n\n【对话提醒】用户刚连续发来了 ")
                    .append(userBatchCount)
                    .append(" 条消息（见下一条用户消息，多条以换行分隔）。")
                    .append("请像真人聊天一样，按你的性格决定回应方式：可以逐条回应、综合回应，或把有关联的合起来回、无关的分开回。")
            }
        }
        val system = ChatMessage(role = "system", content = finalSystem)

        // 2. 最近 N 条历史（超长时只保留窗口内消息，控制上下文长度）
        val recent = history.takeLast(shortTermLimit).map { msg ->
            ChatMessage(
                role = if (msg.role == Role.USER) "user" else "assistant",
                content = msg.content,
            )
        }

        // 3. 当前用户输入
        val user = ChatMessage(role = "user", content = userInput)

        return listOf(system) + recent + user
    }

    /**
     * 当前现实时间文本（T6.11）：如「2026年8月24日 星期一 14:30」。
     * 每次构建 Prompt 时调用，保证 AI 拿到的永远是当前时间。
     */
    private fun currentTimeText(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE))
}
