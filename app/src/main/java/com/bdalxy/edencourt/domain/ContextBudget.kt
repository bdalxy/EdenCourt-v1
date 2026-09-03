package com.bdalxy.edencourt.domain

/**
 * 上下文注入预算统一分配器（v1.5 上下文注入层，参考 Narratium 优先级链 + budget 统一预算）。
 *
 * 背景：此前 system 的各级片段（角色卡/状态/关系/亲密度/情绪/世界书/记忆）各自为政、
 * 各掐各的预算，导致重复注入、预算割裂。此层把注入切成「优先级链」+ 「统一预算」：
 *
 *  ① 当前时间           — 环境，忽略预算
 *  ② 恒久身份【硬配额】 — 角色卡核心 + 用户自设身份 + 会话独有人格（每次必注入，不参与弹性裁剪）
 *  ③ 恒久世界规则【硬配额】— 世界书 constant=true 条目（核心世界规则，每次必注入）
 *  ④ 当下状态【固定短句】  — 亲密度 + 情绪 + 生活状态 + 陪看（短句，基本恒定）
 *  ⑤ 相关性知识【弹性配额】— 情感记忆 → 事实记忆 → 关系事件 → 世界书命中条目（按需检索，吃剩余预算）
 *
 * 设计原则：显示值 = 注入值（UI 与注入共用 TokenEstimator.count），可控/可调/可回溯。
 *
 * @param totalBudget     总注入预算（②③④⑤ 之和的兜底上限，默认 3200）
 * @param identityBudget  恒久身份硬配额（默认 1400，范围 600~2000）
 * @param constantWorldBudget 恒久世界规则硬配额（默认 400，范围 0~800）
 *
 * 系数模型：让「相关性知识（⑤）」始终吃到足够弹性，同时限制恒久身份暴走。
 */
data class ContextBudget(
    val totalBudget: Int = DEFAULT_TOTAL_BUDGET,
    val identityBudget: Int = DEFAULT_IDENTITY_BUDGET,
    val constantWorldBudget: Int = DEFAULT_CONSTANT_WORLD_BUDGET,
) {

    /** 恒久身份占比（identityBudget ≤ total 时按比例；兜底 40%） */
    val identityRatio: Double
        get() = if (totalBudget > 0 && identityBudget <= totalBudget) {
            identityBudget.toDouble() / totalBudget
        } else {
            DEFAULT_IDENTITY_RATIO
        }

    /** 恒久世界规则占比（constantWorldBudget ≤ total 时按比例；兜底 12%） */
    val constantWorldRatio: Double
        get() = if (totalBudget > 0 && constantWorldBudget <= totalBudget) {
            constantWorldBudget.toDouble() / totalBudget
        } else {
            DEFAULT_CONSTANT_RATIO
        }

    /** 相关性知识（⑤）预算 = 总预算 − 恒久身份 − 恒久世界规则（④ 当下状态短句开销自负，不占此配额） */
    val flexibleBudget: Int
        get() = (totalBudget - identityBudget - constantWorldBudget).coerceAtLeast(0)

    /**
     * 把一个文本按 token 预算截断：累计超出 maxToken 后不再追加更多条目（整条保留/丢弃，
     * 不做字符级切断，保证条目语义完整）。返回被保留的条目顺序不变。
     * @param texts 待注入文本（已按优先级排好序）
     * @param maxToken 该等级预算；若为 0，则整组跳过
     * @return 预算内可注入的文本列表
     */
    fun truncate(texts: List<String>, maxToken: Int): List<String> {
        if (maxToken <= 0) return emptyList()
        var used = 0
        return texts.filter { t ->
            val cost = TokenEstimator.count(t)
            if (used + cost > maxToken) false else { used += cost; true }
        }
    }

    companion object {
        const val DEFAULT_TOTAL_BUDGET = 3200
        const val DEFAULT_IDENTITY_BUDGET = 1400
        const val DEFAULT_CONSTANT_WORLD_BUDGET = 400
        private const val DEFAULT_IDENTITY_RATIO = 0.40
        private const val DEFAULT_CONSTANT_RATIO = 0.12

        /** 可配范围（面向后续 B3 设置页） */
        const val MIN_TOTAL_BUDGET = 1200
        const val MAX_TOTAL_BUDGET = 8000
        const val MIN_IDENTITY_BUDGET = 600
        const val MAX_IDENTITY_BUDGET = 2000
        const val MIN_CONSTANT_WORLD_BUDGET = 0
        const val MAX_CONSTANT_WORLD_BUDGET = 800
    }
}