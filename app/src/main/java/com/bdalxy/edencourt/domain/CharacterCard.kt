package com.bdalxy.edencourt.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 角色卡（T4.1，参考企划书 3.4.1）。
 * 定义了角色卡的 JSON 结构与必填校验；字段与企划书 JSON Schema 一致。
 */
@Serializable
data class CharacterCard(
    /** 角色名（必填） */
    val name: String,
    /** 性格（必填，转 system prompt 的核心） */
    val personality: String,
    /** 背景（建议填；Tavern CCv3 导入时映射自 description） */
    val background: String = "",
    /** 说话风格（建议填；JSON 字段 speaking_style） */
    @SerialName("speaking_style")
    val speakingStyle: String = "",
    /** 示例对话（可选，强化模仿；JSON 字段 example_dialogue） */
    @SerialName("example_dialogue")
    val exampleDialogue: List<ExampleDialogue> = emptyList(),
    /** 开场白（可选；Tavern CCv3 导入时映射自 first_mes，用于新对话主动问候） */
    val firstMessage: String = "",
    /** 审核硬约束（可选） */
    val constraints: String = "",
    /** 性别（可选；T12.11 新增，取值「女」/「男」/空。供跨会话转述/角色互提时正确使用「她/他」称谓） */
    val gender: String = "",
    /** 分类标签（可选） */
    val tags: List<String> = emptyList(),
) {
    /**
     * 必填校验（企划书 3.4.1）：name/personality 均非空才算合法角色卡。
     * 导入/选卡前调用，不合法则拒绝并提示。
     */
    fun isValid(): Boolean = name.isNotBlank() && personality.isNotBlank()
}

/**
 * 示例对话对（企划书 3.4.1 example_dialogue 数组元素）。
 * 用于强化模型模仿角色口吻；user/assistant 成对出现。
 */
@Serializable
data class ExampleDialogue(
    /** 用户发言 */
    val user: String = "",
    /** 助手（角色）发言 */
    val assistant: String = "",
)
