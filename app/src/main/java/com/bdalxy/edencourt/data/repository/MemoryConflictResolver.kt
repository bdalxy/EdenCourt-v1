package com.bdalxy.edencourt.data.repository

import com.bdalxy.edencourt.data.local.entity.MemoryItem

/**
 * 记忆冲突判定（T11.8b 事故修复的产物，T12.5 抽为纯函数以便单测固化）。
 * 判定「新提取的事实」与候选记忆（pool）是否为「同一条事实」：
 * - 是 → 返回应被合并（更新）的旧记忆，由调用方覆盖 content/keywords/置信度；
 * - 否 → 返回 null，调用方按「新事实」新增（保住旧记忆）。
 * 纯函数无副作用，只依赖入参判定，可在 JVM 单测中直接覆盖三态（合并/新增/宽泛词不误覆盖）。
 */
internal object MemoryConflictResolver {

    /**
     * 内容级冲突判定时，较长一方内容长度的最低信任阈值（T11.8b 修复）。
     * 避免「喜欢」「图书馆」等短词误判为同一事实而把无关旧记忆整体覆盖。
     * 较短一方即使被包含，若较长一方不足此长度也不视为同一条事实。
     */
    const val CONTENT_MATCH_MIN_LEN = 5

    /**
     * 在给定候选记忆池中判定新事实是否命中「同一条旧记忆」。
     * 判定采用「宁可新增、不可误覆盖」原则（T11.8b）：
     * @param pool     候选记忆（通常已按角色卡缩小范围；可含已删除记忆，判定内会跳过）
     * @param content  新事实的一句话内容
     * @param keywords 新事实的关键词（逗号分隔，证据2 使用）
     * @return 命中则返回待更新的旧记忆，未命中返回 null（应新增）
     */
    fun resolveConflict(
        pool: List<MemoryItem>,
        content: String,
        keywords: String,
    ): MemoryItem? {
        val candidateKeywords = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        // 证据1「内容重叠」：同一事实的重复表述（如「喜欢奶茶」vs「用户喜欢喝奶茶」）
        val byContent = pool.firstOrNull { old ->
            !old.isDeleted && content.isNotEmpty() && old.content.isNotEmpty() &&
                (old.content.length >= CONTENT_MATCH_MIN_LEN || content.length >= CONTENT_MATCH_MIN_LEN) &&
                (old.content.contains(content) || content.contains(old.content))
        }

        // 证据2「多关键词命中」：>=2 个关键词同时命中同一条记忆才可信（单宽泛关键词不足信）
        val byKeywords = if (byContent == null && candidateKeywords.size >= 2) {
            pool.firstOrNull { old ->
                !old.isDeleted && candidateKeywords.count { kw ->
                    kw.length >= 2 && (old.content.contains(kw) || old.keywords.contains(kw))
                } >= 2
            }
        } else null

        return byContent ?: byKeywords
    }

    /**
     * 显式覆盖定位（T6.13.2）：用户更正条目携带的 replace_content → 在既有记忆中定位被更正的目标。
     *
     * 匹配采用「完全相等 或 任一方包含另一方」的宽松规则，与既有内联逻辑逐字等价（T15.7 抽为纯函数以便单测固化）。
     * 注意与 resolveConflict 的三点差异，调用方须保持原语义：
     * 1. 不设最小长度阈值（replace 是用户明确指认，不按「宁可新增」保守）；
     * 2. 不主动跳过已删除项——是否应用由调用方复核（!isDeleted），命中已删除视为放弃本次覆盖；
     * 3. 仅返回第一条命中（firstOrNull），不涉及多证据优先级。
     * @param existing      既有记忆（通常为当前角色卡范围）
     * @param replaceContent 用户指认要被替换的旧内容（首个命中被覆盖）
     * @return 命中的旧记忆；未命中返回 null
     */
    fun findReplaceTarget(
        existing: List<MemoryItem>,
        replaceContent: String,
    ): MemoryItem? =
        existing.firstOrNull {
            it.content == replaceContent ||
                replaceContent.contains(it.content) ||
                it.content.contains(replaceContent)
        }
}