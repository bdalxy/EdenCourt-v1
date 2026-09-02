package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.ConversationRelationship

/**
 * 用户↔会话角色亲密度模型（v1.5-A1 好感度反哺对话）。
 *
 * 与既有角色间好感度（conv_relationship）相互独立：conv_relationship 表达两个角色会话之间的关系，
 * 本对象反映「用户与当前会话角色的亲近程度」，随对话自然、非线性推进，供 ChatPromptAssembler
 * 注入 prompt 反哺 AI 口吻——越亲近越发自然温情、越生疏越守分寸。
 *
 * 非线性：非每轮固定加分，而是「离上限越近单次推进越小」的饱和曲线（前期涨快、后期趋缓），
 * 贴合用户「随聊天自然变化，而非纯线性增长」的要求；命中「谈到共同经历/记忆」等深化时刻
 * 时可给予额外加成（boost，由调用方判定后传入，初版默认不加）。
 */
object UserAffinity {

    /** 亲密度上限 */
    const val MAX = 100

    /** 亲密度下限 */
    const val MIN = 0

    /**
     * 推进亲密度（非线性饱和）。
     * @param prev 当前亲密度（越界会自动夹到合集）
     * @param boost 深化加成（命中共同经历/记忆等时传入加速，默认 0 = 普通推进）
     * @return 推进后的值，夹在 [MIN, MAX]
     */
    fun advance(prev: Int, boost: Int = 0): Int =
        prev.coerceIn(MIN, MAX - 1)
            .let { it + 1 + (MAX - it) / 8 + boost.coerceAtLeast(0) }
            .coerceAtMost(MAX)

    /**
     * 生成亲密度注入片段（A1 反哺对话口吻）。
     * 分档复用既有 ConversationRelationship.affinityTierLabel（陌生/相识/友好/亲密），不做重复实现。
     * @param score 当前亲密度
     * @return 注入文本
     */
    fun buildAffinityFragment(score: Int): String {
        val tier = ConversationRelationship.affinityTierLabel(score.coerceAtLeast(MIN))
        return "【你和用户的关系】你们现在处于「$tier」阶段（亲密度 $score/100）。" +
            "请按这个亲疏程度自然地调整你与用户说话的语气——" +
            "亲近就不用拘谨、可更温情自在；还生疏就保持礼貌与分寸，随对话逐渐拉近距离。"
    }
}