package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.ConversationMood
import com.bdalxy.edencourt.data.local.entity.MoodSource
import com.bdalxy.edencourt.data.schedule.LifeState

/**
 * 角色情绪类别。
 */
enum class MoodTag { PEACEFUL, HAPPY, EXCITED, ANNOYED, SAD, SHY, DROWSY }

/**
 * 实时情绪规则（v1.5-A2 实时情绪系统，初版轻量规则跑通；可视化留作后续面板插件）。
 *
 * 双情绪源：
 * - 自主情绪（AUTO/基线）：由作息状态派生（睡眠被吵醒→困倦、活跃→兴奋…），不落库，随时计算；
 * - 交互情绪（REACT）：由用户言行的轻量关键词规则触发（喜欢→欢喜、对不起→缓和、讨厌→低落…），
 *   带有效期，到期回落自主基线。
 *
 * 注入：ChatPromptAssembler 读取后经 resolve 合成「最终情绪」，反哺 AI 语气。
 */
object MoodRules {

    /** 交互情绪的默认有效时长（毫秒）：10 分钟 */
    const val DEFAULT_REACT_DURATION_MS = 10 * 60 * 1000L

    /** 基线强度（未触发交互时给自主情绪的默认强度） */
    const val BASELINE_INTENSITY = 45

    /** 一次交互触发的情绪结果（命中则写库） */
    data class MoodReaction(
        val tag: MoodTag,
        val intensity: Int,
        val durationMs: Long = DEFAULT_REACT_DURATION_MS,
    )

    /** 合成后的最终情绪（注入用） */
    data class OutputMood(
        val tag: MoodTag,
        val intensity: Int,
    )

    /** 情绪类别 → 中文名（注入文本展示） */
    fun tagName(tag: MoodTag): String = when (tag) {
        MoodTag.PEACEFUL -> "平静"
        MoodTag.HAPPY -> "欢喜"
        MoodTag.EXCITED -> "兴奋"
        MoodTag.ANNOYED -> "烦躁"
        MoodTag.SAD -> "低落"
        MoodTag.SHY -> "羞怯"
        MoodTag.DROWSY -> "困倦"
    }

    /**
     * 自主基线：作息状态 → 默认情绪（当时刻基线；作息关闭/未知 → 平静）。
     */
    fun baselineFor(lifeState: LifeState?): MoodTag = when (lifeState) {
        LifeState.SLEEPING -> MoodTag.DROWSY
        LifeState.WAKING -> MoodTag.PEACEFUL
        LifeState.ACTIVE -> MoodTag.EXCITED
        LifeState.EVENING -> MoodTag.PEACEFUL
        null -> MoodTag.PEACEFUL
    }

    private val reactions: List<Pair<List<String>, MoodReaction>> = listOf(
        listOf("喜欢", "爱你", "想你", "想你了", "抱抱", "抱你", "亲") to MoodReaction(MoodTag.HAPPY, 72, 10 * 60 * 1000L),
        listOf("好开心", "哈哈", "太好了", "有趣", "好玩", "开心") to MoodReaction(MoodTag.EXCITED, 70, 8 * 60 * 1000L),
        listOf("对不起", "抱歉", "我的错", "别生气", "道歉") to MoodReaction(MoodTag.PEACEFUL, 40, 8 * 60 * 1000L),
        listOf("别理", "讨厌", "走开", "烦你", "不想说", "滚") to MoodReaction(MoodTag.SAD, 68, 10 * 60 * 1000L),
        listOf("生气", "过分", "太过分", "惹恼", "你真坏") to MoodReaction(MoodTag.ANNOYED, 62, 8 * 60 * 1000L),
        listOf("害羞", "脸红", "撩", "心动了") to MoodReaction(MoodTag.SHY, 60, 8 * 60 * 1000L),
    )

    /**
     * 轻量关键词规则触发交互情绪（初版）。
     * 命中任一关键词返回对应情绪；未命中返回 null（维持当前情绪，不主动改）。
     */
    fun react(userInput: String): MoodReaction? {
        if (userInput.isBlank()) return null
        return reactions.firstOrNull { (keywords, _) -> keywords.any { userInput.contains(it) } }?.second
    }

    /**
     * 合成最终情绪（注入用）：
     * 无交互情绪 或 交互情绪已过期 → 回落自主基线；
     * 交互情绪未过期 → 以交互情绪为主（体现「被用户言行带动」）。
     */
    fun resolve(mood: ConversationMood?, now: Long, baseline: MoodTag): OutputMood {
        val expired = mood == null ||
            (mood.source == MoodSource.REACT.name && mood.expiresAt != null && mood.expiresAt <= now)
        if (expired) {
            return OutputMood(baseline, BASELINE_INTENSITY)
        }
        val tag = runCatching { MoodTag.valueOf(mood.moodTag) }.getOrDefault(baseline)
        return OutputMood(tag, mood.intensity.coerceIn(0, 100))
    }

    /** 生成最终情绪注入片段（反哺 AI 语气） */
    fun buildMoodText(tag: MoodTag, intensity: Int): String {
        val name = tagName(tag)
        val hint = when (tag) {
            MoodTag.PEACEFUL -> "语气平和安稳，不刻意渲染情绪。"
            MoodTag.HAPPY -> "心情很好，语气更温暖轻盈，藏不住的笑意。"
            MoodTag.EXCITED -> "有些兴奋和雀跃，语气轻快有活力。"
            MoodTag.ANNOYED -> "有些烦躁不耐，语气透露出轻微的不耐烦。"
            MoodTag.SAD -> "情绪有些低落，语气寡淡，需要被安慰。"
            MoodTag.SHY -> "有点害羞羞怯，语气吞吐、带点不好意思。"
            MoodTag.DROWSY -> "困倦朦胧，语气慢吞吞、迷迷糊糊的。"
        }
        return "【你此刻的心情】你现在感到「$name」（情绪强度 $intensity/100）。$hint 请自然地把这份心情融进你要说的话里。"
    }
}