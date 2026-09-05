package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.MessageType
import com.bdalxy.edencourt.data.local.entity.Role
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 群聊「一幕」结构化输出 DTO（v2.0 A6-3）。
 * AI 返回一段群聊场景，包含多条带发言者的消息 + 幕尾终止标记。
 */
@Serializable
data class GroupChatScene(
    /** 一幕中的消息行 */
    @SerialName("lines") val lines: List<SceneLine> = emptyList(),
    /** 幕尾终止标记（true=本幕结束，等待用户发言） */
    @SerialName("scene_end") val sceneEnd: Boolean = true,
)

/** 一幕中的单条消息 */
@Serializable
data class SceneLine(
    /** 发言者显示名（必须与 prompt 中给出的成员名一致） */
    @SerialName("speaker") val speaker: String = "",
    /** 消息内容 */
    @SerialName("content") val content: String = "",
)

/** 群聊结构化输出解析器（v2.0 A6-3） */
object GroupChatParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 最大硬上限：一幕最多 6 条消息（兜底防无限接话） */
    const val MAX_LINES = 6

    /**
     * 解析 AI 返回的群聊场景 JSON。
     * 剥 markdown 代码块 → 解析 → 截断硬上限 ≤6 条。
     * 失败返回 null（由调用方静默忽略，不崩溃）。
     */
    fun parse(raw: String): GroupChatScene? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val scene = runCatching { json.decodeFromString<GroupChatScene>(cleaned) }.getOrNull() ?: return null
        // 硬上限兜底
        if (scene.lines.size > MAX_LINES) {
            return scene.copy(lines = scene.lines.take(MAX_LINES), sceneEnd = true)
        }
        return scene
    }

    /**
     * 将解析后的场景行转为 Message 列表。
     * @param scene 解析后的场景
     * @param groupConversationId 群聊会话 id
     * @param speakerMap 发言者显示名 → 参与者会话 id 的映射
     * @return 消息列表（每条消息 senderConversationId 指向对应参与者，role=ASSISTANT）
     */
    fun toMessages(
        scene: GroupChatScene,
        groupConversationId: Long,
        speakerMap: Map<String, Long>,
    ): List<Message> = scene.lines.mapNotNull { line ->
        val senderId = speakerMap[line.speaker]
        if (senderId == null || line.content.isBlank()) return@mapNotNull null
        Message(
            conversationId = groupConversationId,
            role = Role.ASSISTANT,
            type = MessageType.TEXT,
            content = line.content,
            status = MessageStatus.DONE,
            senderConversationId = senderId,
        )
    }
}

/**
 * 组装群聊 system prompt（v2.0 A6-3）。
 * @param identityText 成员身份块文本（由 GroupPromptAssembler.buildGroupIdentityText 生成）
 * @param recentMessages 群内最近消息拼接文本
 * @param userMessage 用户最新发言
 */
fun buildGroupChatPrompt(
    identityText: String,
    recentMessages: String,
    userMessage: String,
): String = buildString {
    append("你是 EdenCourt 群聊的场景导演。现在有一个群聊，里面有多个 AI 角色，用户刚刚发了一条消息。")
    append("你需要生成这个群聊的「一幕」——即各个角色看到用户消息后自由接话的一小段群聊。\n\n")

    if (identityText.isNotBlank()) {
        append(identityText).append("\n\n")
    }

    append("## 群内最近消息\n")
    if (recentMessages.isNotBlank()) {
        append(recentMessages).append("\n")
    } else {
        append("（暂无历史消息）\n")
    }
    append("\n")

    append("## 用户最新发言\n")
    append("用户：").append(userMessage).append("\n\n")

    append("## 任务\n")
    append("根据用户的最新发言，让群里的角色们自然地接话。要求：\n")
    append("1. 角色们按自己的性格、亲密度、情绪自由决定是否接话——不是每个人都要说话，只让合适的人开口。\n")
    append("2. 一个角色可以连发多条（像真人会连发几句），但不要一个人说太多（最多 2~3 条）。\n")
    append("3. 消息要自然、口语化，像真人聊天群里的对话。\n")
    append("4. 最多生成 6 条消息，你觉得合适的条数就停，不要凑满。\n")
    append("5. 如果没有任何角色适合接话（比如用户只是说了个\"嗯\"），就只生成 1 条简短回应或不生成。\n\n")

    append("## 输出格式（严格 JSON，不要任何额外文字）\n")
    append("{\n")
    append("  \"lines\": [\n")
    append("    {\"speaker\": \"角色名\", \"content\": \"消息内容\"},\n")
    append("    {\"speaker\": \"角色名\", \"content\": \"消息内容\"}\n")
    append("  ],\n")
    append("  \"scene_end\": true\n")
    append("}\n\n")
    append("注意：speaker 必须与上面「群聊成员」中的名字完全一致，不要用昵称或简称。\n")
    append("scene_end 始终为 true，表示本幕结束、等待用户发言。\n")
}