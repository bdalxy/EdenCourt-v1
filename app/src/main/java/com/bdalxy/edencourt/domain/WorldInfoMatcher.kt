package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.WorldInfoBook
import com.bdalxy.edencourt.data.local.entity.WorldInfoEntry

/**
 * 世界书命中与预算逻辑（v1.5-A3，纯函数可单测）。
 * 机制：扫描最近对话文本，初始命中主关键词 → 按副关键词逻辑收窄 → 恒定条目恒注 →
 * 按 priority 升序 + token 预算截断，最终拼成注入片段。
 */
object WorldInfoMatcher {

    /** 每轮注入最大条数（默认；预算与条数双上限） */
    const val DEFAULT_MAX_ENTRIES = 5

    /** 每轮注入 token 预算默认值（≈ 中文 1 字 ≈ 1 token） */
    const val DEFAULT_MAX_TOKEN = 2000

    /** 预算下限/上限（设置页可配范围，A3 初版用默认；可配由 B3 设置页落库） */
    const val MIN_MAX_TOKEN = 500
    const val MAX_MAX_TOKEN = 5000

    /**
     * 规则命中：在主关键词范围里，判断复关键词逻辑是否收窄。
     * AND_ANY：主命中的前提下，任一复关键字命中即通过（无复关键字也通过）；
     * AND_ALL：须全部复关键字命中才通过。
     */
    fun passesSecondary(entry: WorldInfoEntry, secondaryHits: (String) -> Boolean): Boolean {
        val secondaries = parseList(entry.secondaryKeys)
        if (secondaries.isEmpty()) return true
        val hits = secondaries.count(secondaryHits)
        return if (entry.logic == "AND_ALL") {
            hits == secondaries.size
        } else {
            hits >= 1
        }
    }

    /**
     * 从某书全部条目中筛出「本轮应注入」的条目：
     * 1. constant 恒定条目恒注入；
     * 2. 其余需主关键词命中（texts 为扫描文本集合，任一段命中即算）；
     * 3. 恒定与命中条目合并后按 priority 升序（恒定也参与排序，核心更靠前）。
     * 不含 token 预算截断（截断见 budget）。
     */
    fun matched(entries: List<WorldInfoEntry>, texts: List<String>): List<WorldInfoEntry> {
        val anyHit = { key: String -> texts.any { it.contains(key) } }
        return entries
            .filter { e -> e.enabled }
            .filter { e ->
                e.constant || (e.primaryHits(anyHit) && passesSecondary(e, anyHit))
            }
            .sortedBy { e -> e.priority }
    }

    /**
     * token 预算截断：按已排序注入序逐条累计，累计超出 maxToken 后立即停止（顺序截断，
     * 保证高优先级条目优先注入；而非「跳过超限条目、放行后续低优先级条目」）。
     * @param maxEntries 最多条数
     * @param maxToken 最多 token
     */
    fun budget(sorted: List<WorldInfoEntry>, maxEntries: Int = DEFAULT_MAX_ENTRIES, maxToken: Int = DEFAULT_MAX_TOKEN): List<WorldInfoEntry> {
        var used = 0
        val result = mutableListOf<WorldInfoEntry>()
        for (e in sorted.take(maxEntries)) {
            val t = TokenEstimator.count(e.content)
            if (used + t > maxToken) break // 顺序截断：超限即停
            result.add(e)
            used += t
        }
        return result
    }

    /**
     * 拼接注入片段：内容自包含（条目 title 不入文），按注入序换行。
     */
    fun buildFragment(injected: List<WorldInfoEntry>): String {
        if (injected.isEmpty()) return ""
        return buildString {
            append("【世界书 · 背景设定】以下是应当生效的世界观设定，请自然地融入你的言行，但不要逐字复述：\n")
            injected.forEach { e -> append("· ").append(e.content.trim()).append("\n") }
        }.trim()
    }

    private fun parseList(text: String): List<String> =
        text.split('，', '、', ',', ';', '；', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun WorldInfoEntry.primaryHits(hit: (String) -> Boolean): Boolean =
        parseList(keys).any(hit)
}