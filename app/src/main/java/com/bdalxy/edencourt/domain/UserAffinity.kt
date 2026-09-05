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

    /**
     * 浓度轴回落（v2.0 关系引擎 R2：给你"几天没聊会淡一点、但不会莫名消失"的真人感）。
     * 规则（2026-09-04 初版定）：距上次互动 **>2 天**开始缓降，**每天降 1~3 点（本可注入随机源）**；
     * **保底到当前档位起始值，不破档**——即降到所在档位（陌生/相识/友好/亲密）的起始分即停，不会跌出当前档位。
     * 方向是慢变量，不因冷落骤变，故本函数只动浓度。
     * @param prev        当前浓度（用户↔角色亲密度）
     * @param daysSince   距上次互动天数（向下取整；<=2 天视为仍在热络期，不回落）
     * @param stepRandom  单日回落值生成器，默认固定 2（可注入固定种子便于测试），范围 [1,3]
     * @return 回落后的浓度；若不足回落或已在档位底部则原样返回
     */
    fun decay(prev: Int, daysSince: Long, stepRandom: () -> Int = { 2 }): Int {
        if (prev <= MIN) return prev
        if (daysSince <= 2) return prev                // 仍在热络期，不回落
        val floor = tierFloor(prev)                    // 当前档位起始值 = 保底线
        var cur = prev
        (3..daysSince).forEach {                       // 第 3 天起逐天回落
            cur -= stepRandom().coerceIn(1, 3)
            if (cur <= floor) return floor             // 触底即停，不破档
        }
        return cur.coerceAtMost(prev)
    }

    /** 当前浓度所在档位的起始分（保底线）：陌生0 < 相识20 < 友好40 < 亲密70 */
    private fun tierFloor(score: Int): Int = when {
        score < ConversationRelationship.AFFINITY_ACQUAINTANCE -> 0
        score < ConversationRelationship.AFFINITY_FRIENDLY -> ConversationRelationship.AFFINITY_ACQUAINTANCE
        score < ConversationRelationship.AFFINITY_CLOSE -> ConversationRelationship.AFFINITY_FRIENDLY
        else -> ConversationRelationship.AFFINITY_CLOSE
    }
}