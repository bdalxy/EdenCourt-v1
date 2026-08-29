package com.bdalxy.edencourt.data.traits

import com.bdalxy.edencourt.domain.TraitRange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.max
import kotlin.math.min

/**
 * 人设维度范围解析器（T13.2 角色差异化/自补充方案）。
 * 解决「卡范围从哪来」：同一张角色卡开多个会话 → 每个会话角色在卡范围内随机落点，形成"同卡不同魂"。
 * 支持三类捏卡用户（§3.1.1 / §9-B 定案），按维度逐级兜底：
 *   ① 数据党：卡上 traitsRange 直接取值（精确填，confidence=1.0 最高优先）
 *   ② 感觉党：LLM 读卡 personality/description 输出 6 维范围 JSON（带 confidence）
 *   ③ 中间党：卡上部分维度填了 + 其余由 LLM/启发式补全（逐维合并）
 *   ④ 兜底：任意来源都缺的维度 → 关键词启发式依据 personality 给偏移 → 再无信号 → 50±25 默认
 * 纯函数、无 IO，方便 JVM 单测覆盖三态。
 */
object TraitRangeResolver {

    /** 6 维人设维度名（固定，与 conversation_traits 实体字段名一致） */
    val DIMENSION_KEYS: List<String> = listOf(
        "sociability", "initiative", "warmth", "attachment", "humor", "casualness",
    )

    /** 默认范围中心（§3.1.1 三级兜底：无任何信号时 50±25） */
    private const val DEFAULT_CENTER = 50
    /** 默认范围半宽 */
    private const val DEFAULT_HALF = 25

    /** 来源可靠度门槛：低于此的 LLM 范围视为不可信，向下一级兜底 */
    private const val CONFIDENCE_THRESHOLD = 0.35

    /** 关键词启发式表：personality 命中哪个词 → 对哪个维度加多少偏移（-40..40） */
    private val KEYWORD_SIGNALS: List<Triple<String, String, Int>> = listOf(
        // 温度 warmth
        Triple("温柔", "warmth", 20),
        Triple("体贴", "warmth", 15),
        Triple("热情", "warmth", 15),
        Triple("冷淡", "warmth", -20),
        Triple("高冷", "warmth", -15),
        Triple("冷漠", "warmth", -20),
        // 幽默 humor
        Triple("幽默", "humor", 20),
        Triple("爱开玩笑", "humor", 15),
        Triple("毒舌", "humor", 15),
        Triple("正经", "humor", -20),
        Triple("严肃", "humor", -15),
        // 外向度 sociability
        Triple("开朗", "sociability", 20),
        Triple("外向", "sociability", 15),
        Triple("活泼", "sociability", 15),
        Triple("内向", "sociability", -20),
        Triple("害羞", "sociability", -15),
        Triple("沉默", "sociability", -15),
        // 主动度 initiative
        Triple("主动", "initiative", 20),
        Triple("话痨", "initiative", 15),
        Triple("被动", "initiative", -15),
        Triple("含蓄", "initiative", -15),
        // 依恋度 attachment
        Triple("粘人", "attachment", 20),
        Triple("依赖", "attachment", 15),
        Triple("亲近", "attachment", 15),
        Triple("独立", "attachment", -15),
        Triple("疏离", "attachment", -20),
        Triple("高冷", "attachment", -10),
        // 随性度 casualness
        Triple("讲究", "casualness", -20),
        Triple("精致", "casualness", -15),
        Triple("随意", "casualness", 20),
        Triple("不拘小节", "casualness", 15),
        Triple("邋遢", "casualness", 10),
    )

    /**
     * 解析角色卡 6 维人设范围（主入口，纯函数）。
     * @param dataRange   数据党/中间党卡上填的 traitsRange（可能 null / 部分维度）
     * @param llmRange    感觉党 LLM 读卡输出的范围（可能 null / 部分维度 / 带 confidence）
     * @param personality 角色卡性格文本（关键词启发式兜底用）
     * @return 6 维完整范围映射（每维必有值：数据 → LLM → 启发式 → 50±25）
     */
    fun resolve(
        dataRange: Map<String, TraitRange>?,
        llmRange: Map<String, TraitRange>?,
        personality: String,
    ): Map<String, IntRange> {
        // 逐维归并：优先数据党精确值 → LLM → 启发式 → 默认
        return DIMENSION_KEYS.associateWith { dim ->
            dataRange?.get(dim)?.takeIf { it.confidence >= CONFIDENCE_THRESHOLD }?.clamped()
                ?: llmRange?.get(dim)?.takeIf { it.confidence >= CONFIDENCE_THRESHOLD }?.clamped()
                ?: heuristicRange(personality, dim)
                ?: DEFAULT_CENTER - DEFAULT_HALF..DEFAULT_CENTER + DEFAULT_HALF
        }
    }

    /**
     * 关键词启发式：从 personality 文本里找该维度的偏移信号，返回 0..100 内的范围。
     * 无命中返回 null（交给 50±25 兜底）。
     */
    private fun heuristicRange(personality: String, dim: String): IntRange? {
        var offset = 0
        KEYWORD_SIGNALS.forEach { (keyword, targetDim, delta) ->
            if (targetDim == dim && personality.contains(keyword)) {
                offset += delta
            }
        }
        if (offset == 0) return null
        // 偏移后的中心夹紧到 0..100，半宽保持 DEFAULT_HALF
        val center = (DEFAULT_CENTER + offset).coerceIn(0, 100)
        val left = max(0, center - DEFAULT_HALF)
        val right = min(100, center + DEFAULT_HALF)
        return left..right
    }

    /**
     * 解析感觉党 LLM 读卡输出的 6 维范围 JSON（T13.2，§9-B 送审结论）。
     * LLM 约定输出形如：{ "warmth": {"min":70,"max":95,"confidence":0.8}, "humor": {...}, ... }
     * (1) 能解析的非空字段留下；(2) 拒绝混入的非 6 维 key；(3) 剩余维度（解析失败/缺省）由调用方走启发式/默认兜底。
     * @return 合法维度子集映射；整体解析失败（非 JSON / 空对象）返回 emptyMap。
     */
    fun parseLlmJson(raw: String): Map<String, TraitRange> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyMap()
        // 尝试去掉可能的 markdown 代码围栏/前后噪音（LLM 偶尔会包 ```json ... ```）
        val cleaned = trimmed
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        return runCatching {
            val node = Json.parseToJsonElement(cleaned).jsonObject
            DIMENSION_KEYS.mapNotNull { dim ->
                node[dim]?.let { el ->
                    // 兼容两种形态：纯数字范围(50) / 对象{min,max,confidence}
                    val (minV, maxV, conf) = when {
                        el is JsonPrimitive && el.isString -> {
                            // "70..95" 字符串形态
                            val parts = el.content.replace("..", "-").replace(" ", "")
                            val nums = parts.split("-").mapNotNull { it.toIntOrNull() }
                            if (nums.size == 2) Triple(nums[0], nums[1], 0.9) else null
                        }
                        el is JsonPrimitive && el.contentOrNull?.toIntOrNull() != null -> {
                            val v = el.content.toInt().coerceIn(0, 100); Triple(v, v, 0.9)
                        }
                        el is JsonObject -> Triple(
                            (el["min"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 50,
                            (el["max"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 50,
                            (el["confidence"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.9,
                        )
                        else -> null
                    } ?: return@mapNotNull null
                    dim to TraitRange(min = minV.coerceIn(0, 100), max = maxV.coerceIn(0, 100), confidence = conf.coerceIn(0.0, 1.0))
                }
            }.toMap()
        }.getOrElse { emptyMap() }
    }

    /**
     * 生成本次 LLM 读卡的 system 提示词（T13.2 感受党路径；供调用方交给 AiChatService.completeChat）。
     * 只读 card 的 personality/description，要求输出 6 维范围 JSON（带 confidence）。
     */
    fun buildPrompt(personality: String, background: String): String =
        """
        你是角色卡性格量化助手。请阅读以下角色设定，推断该角色在 6 个性格维度上的可能取值区间。
        维度名（固定）：sociability(外向度) / initiative(主动度) / warmth(温度) / attachment(依恋度) / humor(幽默感) / casualness(随性度)。
        每维取值为 0~100 的整数区间 min..max，并给出 confidence(0~1 表示你对该推断的把握)。
        设定里描述强烈的维度给较窄区间，模糊或未提及的给宽区间，但每维必须覆盖 50±35 内。
        只输出合法 JSON，不要任何多余文字，格式示例：
        {"sociability":{"min":45,"max":65,"confidence":0.6},"initiative":{"min":50,"max":75,"confidence":0.5},"warmth":{"min":70,"max":95,"confidence":0.8},"attachment":{"min":55,"max":85,"confidence":0.6},"humor":{"min":20,"max":50,"confidence":0.7},"casualness":{"min":25,"max":55,"confidence":0.6}}
        角色性格：$personality
        角色背景：$background
        """.trimIndent()
}