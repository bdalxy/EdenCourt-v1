package com.bdalxy.edencourt.data.traits

import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.remote.RemoteApiChatService
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 会话角色印象摘要生成器（T13.4 角色差异化/自补充方案）。
 * 建会话时把「卡设定 + 6 维数值 + 反差对」交 LLM 生成第三人称概括 + 2~3 标签，
 * 用于会话列表/聊天页区分同一张卡开出的不同会话角色（§3.3 可视化区分）。
 * 生成来源见 [ConversationTraits.GENERATED_BY_*]：llm=模型生成 / template=模板降级 / user=用户手动编辑（不被覆盖）。
 * 失败策略：LLM 不可用/解析失败 → 降级纯模板（标签库 + 维度区间拼装），保证每会话必有可展示的摘要。
 */
class TraitsSummaryGenerator(
    private val aiChatService: RemoteApiChatService,
    private val apiConfigStore: ApiConfigStore,
) {

    /** 维度关键词：用于模板摘要拼装（外部按需取维度名） */
    private val DIM_LABELS = mapOf(
        "sociability" to "外向",
        "initiative" to "主动",
        "warmth" to "暖意",
        "attachment" to "依恋",
        "humor" to "幽默",
        "casualness" to "随性",
    )

    /** LLM 输出解析 JSON */
    private val json = Json { ignoreUnknownKeys = true }

    /** 降级标签库（避免 map key 冲突，用列表） */
    private data class TagRule(val dim: String, val threshold: Int, val label: String, val high: Boolean)

    private val TAG_RULES: List<TagRule> = listOf(
        TagRule("sociability", 65, "外向", true),
        TagRule("sociability", 35, "内敛", false),
        TagRule("initiative", 60, "主动", true),
        TagRule("initiative", 40, "含蓄", false),
        TagRule("warmth", 65, "暖心", true),
        TagRule("warmth", 35, "冷淡", false),
        TagRule("attachment", 65, "粘人", true),
        TagRule("attachment", 35, "独立", false),
        TagRule("humor", 60, "有趣", true),
        TagRule("humor", 40, "正经", false),
        TagRule("casualness", 60, "随性", true),
        TagRule("casualness", 40, "讲究", false),
    )

    /**
     * 生成会话角色的印象摘要 + 标签（建会话时调用一次；失败不抛异常，降级模板）。
     * @param name        角色名
     * @param personality 卡性格文本
     * @param background  卡背景文本
     * @param traits      6 维数值（TraitsGenerator 落点结果）
     * @param surface     反差对·表面特质
     * @param inner       反差对·隐藏特质
     * @param trigger     反差对·触发情境
     * @return 生成的 (blurb, tagsJson, generatedBy)；来源 llm 或 template，绝不会是 user（那是用户编辑专用）
     */
    suspend fun generate(
        name: String,
        personality: String,
        background: String,
        traits: Map<String, Int>,
        surface: String,
        inner: String,
        trigger: String,
    ): Triple<String, String, String> {
        // 1. 尝试 LLM 生成
        val llm = runCatching { generateWithLlm(name, personality, background, traits, surface, inner, trigger) }
            .getOrNull()
        if (llm != null) return llm

        // 2. 降级：纯模板
        return generateTemplate(name, traits, surface, inner, trigger)
    }

    /** LLM 生成：非流式调用 + 解析 JSON；失败（无 Key/网络/解析）返回 null 走降级 */
    private suspend fun generateWithLlm(
        name: String,
        personality: String,
        background: String,
        traits: Map<String, Int>,
        surface: String,
        inner: String,
        trigger: String,
    ): Triple<String, String, String>? {
        val config = apiConfigStore.config.first()
        if (config.apiKey.isBlank()) return null // 未配置 API Key → 模板

        val messages = buildList {
            add(ChatMessage("system", LLM_SYSTEM_PROMPT))
            add(ChatMessage("user", buildUserPrompt(name, personality, background, traits, surface, inner, trigger)))
        }
        val raw = aiChatService.completeChat(messages, config)
        return parseLlmOutput(raw) ?: return null
    }

    /** 解析 LLM 输出：期望 { "blurb": "...", "tags": ["慢热","暖心"] }；不合规返回 null */
    private fun parseLlmOutput(raw: String): Triple<String, String, String>? {
        val trimmed = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val obj = runCatching { json.parseToJsonElement(trimmed) as JsonObject }.getOrNull() ?: return null
        val blurb = (obj["blurb"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val tags = (obj["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.filter { it.isNotBlank() }?.distinct()?.take(3)
        if (blurb.isBlank() || tags.isNullOrEmpty()) return null
        return Triple(blurb, json.encodeToString(JsonArray.serializer(), JsonArray(tags.map { JsonPrimitive(it) })), ConversationTraits.GENERATED_BY_LLM)
    }

    /** 纯模板降级：标签库按维度阈值挑 2~3 个 + 维度区间拼摘要 */
    private fun generateTemplate(
        name: String,
        traits: Map<String, Int>,
        surface: String,
        inner: String,
        trigger: String,
    ): Triple<String, String, String> {
        // 标签：从 TAG_RULES 挑命中的（每个维度至多一个，取 2~3 个）
        val picked = TAG_RULES.asSequence()
            .filter { rule -> (traits[rule.dim] ?: 50) >= rule.threshold == rule.high }
            .map { it.label }
            .distinct()
            .take(3)
            .toList()
            // 不足 2 个时补「温柔」「认真」兜底
            .let { if (it.size < 2) (it + listOf("温柔", "认真")).distinct().take(3) else it }
        val tagsJson = json.encodeToString(JsonArray.serializer(), JsonArray(picked.map { JsonPrimitive(it) }))

        // 摘要：表面特质 + 维度特征 + 反差
        val dims = traits.entries
            .sortedByDescending { it.value }
            .take(2)
            .joinToString("、") { (dim, v) -> "${DIM_LABELS[dim] ?: dim}${if (v >= 65) "感强" else if (v <= 35) "感弱" else "感中等"}" }
        val blurb = buildString {
            append("「$name」看起来是${surface.ifBlank { "沉稳内敛" }}的样子")
            if (inner.isNotBlank()) append("，但${trigger}时会$inner")
            if (dims.isNotBlank()) append("（$dims）")
            append("。")
        }
        return Triple(blurb, tagsJson, ConversationTraits.GENERATED_BY_TEMPLATE)
    }

    private fun buildUserPrompt(
        name: String,
        personality: String,
        background: String,
        traits: Map<String, Int>,
        surface: String,
        inner: String,
        trigger: String,
    ): String = buildString {
        appendLine("角色名：$name")
        appendLine("性格设定：$personality")
        if (background.isNotBlank()) appendLine("背景设定：$background")
        appendLine("六维数值：${traits.entries.joinToString("、") { "${it.key}=${it.value}" }}")
        appendLine("表面特质：$surface")
        if (inner.isNotBlank()) appendLine("隐藏特质（$trigger）：$inner")
    }

    private companion object {
        /** LLM 生成印象摘要的 system 提示词：要求第三人称、2~3 标签、JSON 输出 */
        val LLM_SYSTEM_PROMPT = """
            你是角色印象总结助手。根据给定的角色卡设定与六维人设数值、反差对，
            为该角色生成一段"第三人称"的印象摘要（1~2 句话，突出其最显著的性格特征与可能的反差面），
            以及 2~3 个简短标签（每个 2~4 字，用于列表一眼区分，避免与其他会话角色雷同）。
            只输出合法 JSON，不要任何多余文字，格式：
            {"blurb":"...","tags":["标签1","标签2"]}
        """.trimIndent()
    }
}
