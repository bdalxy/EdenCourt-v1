package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.ChatMessage

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

    /**
     * 由角色卡构建 system 提示词（T4.6，参考企划书 3.4.2）。
     * 拼接规则：「你是{name}。你的性格：{personality}…请始终以该角色身份回复」，
     * 并附加背景/说话风格/示例对话/约束（有则拼入）。
     * @param card 会话绑定的角色卡
     * @return system 提示词文本
     */
    fun buildCardSystemPrompt(card: CharacterCard): String = buildString {
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
        append("请始终以「").append(card.name).append("」的身份、用上述风格回复用户，不要跳出角色。")
    }

    /**
     * 构建请求消息列表。
     * @param history 该会话历史消息（按时间正序）
     * @param userInput 当前用户输入
     * @param shortTermLimit 短期记忆条数：只取最近 N 条历史（默认 20）
     * @param systemPrompt system 提示词（默认助手；绑定角色卡时传 buildCardSystemPrompt 结果）
     */
    fun build(
        history: List<Message>,
        userInput: String,
        shortTermLimit: Int = DEFAULT_SHORT_TERM_LIMIT,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    ): List<ChatMessage> {
        // 1. system 消息（角色卡或默认助手）
        val system = ChatMessage(role = "system", content = systemPrompt)

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
}
