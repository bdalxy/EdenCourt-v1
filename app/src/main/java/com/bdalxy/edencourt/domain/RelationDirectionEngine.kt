package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.RelationDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * v2.0 关系引擎 R3：方向轴 AI 自动判断的纯逻辑（无 IO、无副作用）。
 *
 * 职责边界（正交分权，2026-09-05 已定）：本模块只判断「方向」（关系性质，慢变量），
 * 写回 conversations.relationDirection；**不写浓度 affinityScore**（浓度归 A1 增长 + R2 回落，见 [UserAffinity]）。
 *
 * 本文件集中四部分：方向等级 rank 映射、结构化输出 DTO、方向判断提示词、候选裁决纯函数（含四件套阈值）。
 */

/** 方向等级排名（2026-09-05 专家建议，与枚举声明顺序解耦）：NONE(0) < FRIEND/COMRADE(1) < AMBIGUOUS(2) < INTIMATE(3) < LOVER(4) */
fun RelationDirection.rank(): Int = when (this) {
    RelationDirection.NONE -> 0
    RelationDirection.FRIEND, RelationDirection.COMRADE -> 1
    RelationDirection.AMBIGUOUS -> 2
    RelationDirection.INTIMATE -> 3
    RelationDirection.LOVER -> 4
}

/**
 * R3 方向判断的结构化输出 DTO（AI 必须返回的证据化判断）。
 * 字段对齐专家建议：suggested_direction / confidence / reason / signals。
 */
@Serializable
data class DirectionJudgment(
    @SerialName("suggested_direction") val suggestedDirection: String = "NONE",
    @SerialName("confidence") val confidence: Float = 0f,
    @SerialName("reason") val reason: String = "",
    @SerialName("signals") val signals: List<String> = emptyList(),
) {
    /** 把建议方向解析为枚举（大小写/首尾空格容错；未知值或中文等无法识别的回落 null，由调用方忽略本次判断） */
    val direction: RelationDirection?
        get() = runCatching { RelationDirection.valueOf(suggestedDirection.trim().uppercase()) }.getOrNull()
}

/** 方向判断 JSON 解析器：剥掉 AI 可能包裹的 markdown 代码块，容忍未知字段；失败返回 null（不落库、不更状态） */
object RelationDirectionParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** 解析 AI 返回的 raw 文本；任何异常（剥壳后仍是坏 JSON / 缺字段）都返回 null，由调用方静默忽略 */
    fun parse(raw: String): DirectionJudgment? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        return runCatching { json.decodeFromString<DirectionJudgment>(cleaned) }.getOrNull()
    }
}

/**
 * 组装方向判断系统提示词（中文）。
 * @param currentDirection 当前方向（注入后让模型只在有充分证据时才建议改变，防随意跳档）
 * @param context          最近 N 条消息的拼接文本（由调用方在数据库层 LIMIT 后拼好）
 */
fun buildDirectionPrompt(currentDirection: RelationDirection, context: String): String = buildString {
    append("你是 EdenCourt 关系引擎的方向判断模块。你将收到一段用户与 AI 角色的对话片段，以及当前的关系方向。你的任务是判断这段关系当前应该处于哪个方向。\n\n")
    append("## 背景\n")
    append("- 关系方向是一个慢变量，代表这段关系的整体性质，而非单次对话的情绪。\n")
    append("- 可选方向：NONE（尚未明确）、FRIEND（朋友）、COMRADE（同好）、AMBIGUOUS（暧昧）、INTIMATE（深交）、LOVER（恋人·网恋）。\n")
    append("- 方向不会频繁变化。只有在对话中出现了明确、一致、长期的信号时，才建议改变方向。\n")
    append("- 注意：对话内容可能包含玩笑、梗、引用、角色扮演或夸张表达，这些未必代表关系方向改变；不要仅因出现一次亲昵称呼就升级。\n\n")
    append("## 输入\n")
    append("当前方向：").append(currentDirection.name).append("\n")
    append("最近对话片段（按时间顺序）：\n").append(context).append("\n\n")
    append("## 任务\n")
    append("1. 分析对话中双方的语言模式、称呼、话题、情感倾向、排他性、承诺等信号。\n")
    append("2. 判断这段关系当前最符合的方向。\n")
    append("3. 如果你认为方向需要改变，给出建议的新方向、置信度和理由；如果维持不变，则建议方向等于当前方向，并给置信度和理由。\n")
    append("4. confidence 表示你对判断的把握程度（0~1）。证据不足或信号矛盾时应给较低置信度。\n\n")
    append("## 输出格式（严格 JSON，不要任何额外文字、不要 markdown 代码块）\n")
    append("{\"suggested_direction\":\"方向枚举值之一\",\"confidence\":0.0~1.0,\"reason\":\"一句话理由\",\"signals\":[\"信号1\",\"信号2\"]}\n\n")
    append("## 判断准则\n")
    append("- 只有出现至少两个独立且一致的信号时，才建议改变方向；signals 必须列出这些证据。\n")
    append("- 对以下情况应忽略或降低权重：开玩笑、玩梗、引用台词、故意测试系统、单次夸张表达。\n")
    append("- 若上下文不足，维持当前方向并给 confidence ≤ 0.5。\n")
}

/**
 * 各方向「典型浓度下限」（R3 降档判定中"浓度跌破当前方向下限"的依据）。
 * 初版预设，与 [UserAffinity] 分档对齐（陌生0/相识20/友好40/亲密70）；深交比暧昧更近一档取 60。
 * ⚠️ 数值为初版预设，需经产品确认后微调。
 */
fun RelationDirection.typicalAffinityFloor(): Int = when (this) {
    RelationDirection.NONE -> 0
    RelationDirection.FRIEND, RelationDirection.COMRADE -> 20
    RelationDirection.AMBIGUOUS -> 40
    RelationDirection.INTIMATE -> 60
    RelationDirection.LOVER -> 70
}

/** R3 四件套切换阈值（2026-09-05 专家建议） */
object RelationDirectionThreshold {
    /** 升档：单次置信度门槛 */
    const val CONF_UP = 0.7f

    /** 降档：单次置信度门槛（对应"平均≥0.8"——因只有达标者才进入候选计数，平均自然达标） */
    const val CONF_DOWN = 0.8f

    /** 升档：候选连续命中次数 */
    const val CANDIDATE_UP = 2

    /** 降档：候选连续命中次数 */
    const val CANDIDATE_DOWN = 3

    /** 升档冷却（距上次切换 ≥12h） */
    const val COOLDOWN_UP_MS = 12L * 3600_000

    /** 降档冷却（距上次切换 ≥24h） */
    const val COOLDOWN_DOWN_MS = 24L * 3600_000
}

/** 一次候选裁决的输入快照：来自 Conversation 的方向状态 */
data class DirectionState(
    val current: RelationDirection = RelationDirection.NONE,
    val candidate: RelationDirection = RelationDirection.NONE,
    val candidateCount: Int = 0,
    val lastDirectionSwitchAt: Long? = null,
)

/** 一次候选裁决的结果（由调用方依据 switched 决定是否写回 conversation 的方向字段） */
data class DirectionDecision(
    /** 本次是否真正切换了方向 */
    val switched: Boolean,
    /** 裁决后的当前方向 */
    val newDirection: RelationDirection,
    /** 裁决后的候选方向 */
    val newCandidate: RelationDirection,
    /** 裁决后的候选命中次数 */
    val newCandidateCount: Int,
    /** 本次切换时间戳（未切换沿用原值） */
    val lastDirectionSwitchAt: Long?,
)

/**
 * 候选裁决纯函数（四件套：候选方向 + 连续命中 + 置信度门槛 + 冷却）。
 * 调用时机：每次后台方向判断返回后调用；返回 [DirectionDecision]，由调用方决定是否写回 DB。
 *
 * 升档：连续 2 次 + conf≥0.7 + 距上次切换 ≥12h；
 * 降档：连续 3 次 + conf≥0.8 + 浓度跌破当前方向下限 + 距上次切换 ≥24h（更谨慎）。
 * 注：初版冷却只按时间（消息数维度需额外增量计数，留待后续）。
 *
 * @param state      当前方向状态（来自 Conversation）
 * @param suggestion AI 建议方向（已解析为枚举）
 * @param confidence 本次判断置信度 0~1
 * @param affinity   当前浓度 affinityScore（降档需"浓度跌破当前方向下限"）
 * @param now        当前时间戳（毫秒）
 */
fun adjudicateDirection(
    state: DirectionState,
    suggestion: RelationDirection,
    confidence: Float,
    affinity: Int,
    now: Long,
): DirectionDecision {
    val current = state.current

    // 1. 更新候选状态（专家算法：判回当前方向计 -1；判到同候选 +1；判到新方向重置为 1）
    var candidate = state.candidate
    var count = state.candidateCount
    when {
        suggestion == current -> count = (count - 1).coerceAtLeast(0)
        suggestion == state.candidate -> count += 1
        else -> {
            candidate = suggestion
            count = 1
        }
    }

    // 2. 判断升降档 + 检查切换条件
    val up = suggestion.rank() > current.rank()
    val down = suggestion.rank() < current.rank()
    val switched = when {
        up -> confidence >= RelationDirectionThreshold.CONF_UP &&
            count >= RelationDirectionThreshold.CANDIDATE_UP &&
            (state.lastDirectionSwitchAt == null || now - state.lastDirectionSwitchAt >= RelationDirectionThreshold.COOLDOWN_UP_MS)
        down -> confidence >= RelationDirectionThreshold.CONF_DOWN &&
            count >= RelationDirectionThreshold.CANDIDATE_DOWN &&
            affinity < current.typicalAffinityFloor() &&
            (state.lastDirectionSwitchAt == null || now - state.lastDirectionSwitchAt >= RelationDirectionThreshold.COOLDOWN_DOWN_MS)
        else -> false
    }

    return if (switched) {
        DirectionDecision(
            switched = true,
            newDirection = suggestion,
            newCandidate = suggestion,
            newCandidateCount = 0,
            lastDirectionSwitchAt = now,
        )
    } else {
        DirectionDecision(
            switched = false,
            newDirection = current,
            newCandidate = candidate,
            newCandidateCount = count,
            lastDirectionSwitchAt = state.lastDirectionSwitchAt,
        )
    }
}