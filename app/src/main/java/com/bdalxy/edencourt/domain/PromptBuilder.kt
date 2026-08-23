package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.ChatMessage

/**
 * Prompt 构建器（T3.6，参考企划书 3.4.2/3.5.1/3.6.2）。
 * 组装 messages=[system（角色卡+记忆占位）, 最近 N 条历史, 当前 user]。
 * 本阶段角色卡/记忆为最简占位，阶段 4/5 替换为真实角色卡与记忆注入。
 */
object PromptBuilder {

    /** 默认 system 提示词（角色卡/记忆占位） */
    const val DEFAULT_SYSTEM_PROMPT =
        "你是「永恒庭院」里的 AI 助手，请用温暖、亲切、自然的中文语气回答用户的问题，保持简洁友好。"

    /** 短期记忆条数默认值（企划书 5.3.5 分区 2 默认 20；本阶段常量，后续可配置） */
    const val DEFAULT_SHORT_TERM_LIMIT = 20

    /**
     * 构建请求消息列表。
     * @param history 该会话历史消息（按时间正序）
     * @param userInput 当前用户输入
     * @param shortTermLimit 短期记忆条数：只取最近 N 条历史（默认 20）
     * @param systemPrompt system 提示词（角色卡/记忆占位）
     */
    fun build(
        history: List<Message>,
        userInput: String,
        shortTermLimit: Int = DEFAULT_SHORT_TERM_LIMIT,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    ): List<ChatMessage> {
        // 1. system 消息（角色卡+记忆占位）
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
