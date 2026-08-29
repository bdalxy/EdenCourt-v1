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
    /**
     * 人设维度可选范围（T13.2 角色差异化/自补充方案）。key=fixed 6 维名（sociability/initiative/warmth/attachment/humor/casualness）。
     * 数据党精确填；感觉党/中间党由 LLM 读卡生成（建卡时存卡上，见 TraitRangeResolver）；不填 = 让 LLM/启发式兜底。
     */ 
    val traitsRange: Map<String, TraitRange>? = null,
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

/**
 * 人设维度范围（T13.2 角色差异化/自补充方案）。
 * 存卡上 traitsRange 的单个维度取值：min..max 区间内由 TraitsGenerator 随即落点生成会话级数值。
 * - 数据党：精确填 min/max；
 * - 感觉党：LLM 读卡输出（带 confidence 表示该范围可靠度，越低越倾向降级兜底）；
 * attention：min>max 或越界由 [clamped] 归一化到 0..100。
 */
@Serializable
data class TraitRange(
    /** 维度取值下界（0~100） */
    val min: Int = 50,
    /** 维度取值上界（0~100） */
    val max: Int = 50,
    /** 可靠度（感觉党 LLM 输出用；数据党不填默认 1.0=完全可信） */
    val confidence: Double = 1.0,
) {
    /** 归一化到 0..100 且保证 min<=max 的合法范围 */
    fun clamped(): IntRange {
        val lo = min.coerceIn(0, 100)
        val hi = max.coerceAtLeast(lo).coerceIn(lo, 100)
        return lo..hi
    }
}
