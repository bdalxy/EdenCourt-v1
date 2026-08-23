package com.bdalxy.edencourt.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 角色卡解析器（T4.5，参考企划书 3.4.1 兼容层）。
 * 支持两种导入格式：
 * 1. EdenCourt 标准格式（CharacterCard 直接序列化）
 * 2. Tavern/SillyTavern CCv3 格式（字段映射 + 校验）
 * 解析失败抛 IllegalArgumentException，由调用方统一提示「JSON 格式错误」。
 */
object CharacterCardParser {

    /** JSON 序列化器：忽略未知字段，兼容更多第三方角色卡文件 */
    private val json = Json { ignoreUnknownKeys = true }

    /** Tavern CCv3 原始字段（只取本项目需要的映射字段） */
    @Serializable
    private data class TavernCard(
        val name: String = "",
        val description: String = "",
        val personality: String = "",
        val scenario: String = "",
        @kotlinx.serialization.SerialName("first_mes")
        val firstMes: String = "",
        @kotlinx.serialization.SerialName("mes_example")
        val mesExample: String = "",
    )

    /** Tavern 示例对话段分隔符 */
    private const val START_MARKER = "<START>"

    /**
     * 解析导入 JSON 为合法角色卡。
     * 流程：先按标准格式解析；缺必填字段或解析失败时，回退尝试 Tavern CCv3 映射。
     * @param text 导入文件完整文本
     * @return 合法 CharacterCard
     * @throws IllegalArgumentException 无法识别或缺少必填字段
     */
    fun parse(text: String): CharacterCard {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("文件内容为空")

        // 1. 优先按标准格式解析（含必填校验）
        val standard = runCatching { json.decodeFromString<CharacterCard>(trimmed) }.getOrNull()
        if (standard != null && standard.isValid()) return standard

        // 2. 回退尝试 Tavern CCv3 映射
        val tavern = runCatching { json.decodeFromString<TavernCard>(trimmed) }.getOrNull()
        if (tavern != null && tavern.name.isNotBlank()) {
            val mapped = CharacterCard(
                name = tavern.name,
                personality = tavern.personality.ifBlank { "未填写性格" },
                // description→background；scenario 追加为「场景：」说明
                background = buildString {
                    if (tavern.description.isNotBlank()) append(tavern.description)
                    if (tavern.scenario.isNotBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("场景：").append(tavern.scenario)
                    }
                },
                // first_mes→开场白
                firstMessage = tavern.firstMes,
                // mes_example→example_dialogue
                exampleDialogue = parseMesExample(tavern.mesExample),
            )
            if (mapped.isValid()) return mapped
        }

        throw IllegalArgumentException("无法识别的角色卡 JSON 格式")
    }

    /**
     * 序列化角色卡为标准 JSON（编辑回写 jsonContent 时使用）。
     * @param card 角色卡
     * @return 标准格式 JSON 文本
     */
    fun toJson(card: CharacterCard): String =
        json.encodeToString(CharacterCard.serializer(), card)

    /**
     * 解析 Tavern mes_example 文本为示例对话列表。
     * Tavern 格式：多段以 <START> 分隔；段内「{{user}}: 内容」为用户发言、「角色名: 内容」为助手发言。
     * 无法解析时返回空列表（不阻断导入）。
     */
    private fun parseMesExample(raw: String): List<ExampleDialogue> {
        if (raw.isBlank()) return emptyList()
        val dialogues = mutableListOf<ExampleDialogue>()
        // 按行处理，累积 user 与 assistant 发言，成对配对
        var pendingUser = ""
        var pendingAssistant = ""

        // 将已配对的 user/assistant 写入列表并清空缓存（供下方 forEach 使用）
        fun flush() {
            if (pendingUser.isNotEmpty() && pendingAssistant.isNotEmpty()) {
                dialogues.add(ExampleDialogue(user = pendingUser, assistant = pendingAssistant))
            }
            pendingUser = ""
            pendingAssistant = ""
        }

        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed == START_MARKER) {
                // 段分隔：结算当前未配对内容
                flush()
                return@forEach
            }
            when {
                trimmed.startsWith("{{user}}:") -> {
                    flush()
                    pendingUser = trimmed.removePrefix("{{user}}:").trim()
                }
                else -> {
                    // 形如「角色名: 内容」的行视为助手发言
                    val colon = trimmed.indexOf(':')
                    if (colon > 0) {
                        pendingAssistant = trimmed.substring(colon + 1).trim()
                        flush()
                    }
                }
            }
        }
        flush()
        return dialogues
    }
}
