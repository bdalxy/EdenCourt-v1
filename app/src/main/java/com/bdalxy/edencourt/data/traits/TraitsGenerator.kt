package com.bdalxy.edencourt.data.traits

import java.security.SecureRandom

/**
 * 6 维人设数值生成器（T13.3 角色差异化/自补充方案）。
 * 用途：同一角色卡开多个会话 → 每个会话角色在卡派生范围（TraitRangeResolver.resolve 结果）内均匀落点，
 * 形成"同卡不同魂"（§3.1）。每会话建社时生成一次，之后固定不漂移（数值写入 conversation_traits 保存）。
 * 用 SecureRandom 保证随机性；测试可注入随机源以确保确定性的边界用例。
 */
object TraitsGenerator {

    /**
     * 在 6 维范围内均匀落点，生成会话级数值。
     * @param ranges 6 维范围（key 与 TraitRangeResolver.DIMENSION_KEYS 对齐，缺省维用 [DEFAULT_RANGE]）
     * @return 6 维→0..100 整数（每维必有值）
     */
    fun generate(ranges: Map<String, IntRange>): Map<String, Int> =
        generate(ranges, SecureRandom())

    /**
     * 带随机源的版本（测试用，实现确定性）。
     * @param ranges 6 维范围；缺省维度退回 50..50（固定值，便于断言）
     * @param random 随机源（SecureRandom 生产 / 固定种子测试）
     */
    fun generate(ranges: Map<String, IntRange>, random: java.util.Random): Map<String, Int> =
        TraitRangeResolver.DIMENSION_KEYS.associate { dim ->
            val range = ranges[dim] ?: IntRange(50, 50)
            // 均匀落点：nextInt(right - left + 1) + left，保证闭区间内等概率
            val value = if (range.last == range.first) {
                range.first
            } else {
                range.first + random.nextInt(range.last - range.first + 1)
            }
            dim to value.coerceIn(0, 100)
        }

    /** 默认范围：无入参时各维 50..50（建会话未解析到范围时的安全值） */
    fun defaultRange(): Map<String, Int> = TraitRangeResolver.DIMENSION_KEYS.associateWith { 50 }
}