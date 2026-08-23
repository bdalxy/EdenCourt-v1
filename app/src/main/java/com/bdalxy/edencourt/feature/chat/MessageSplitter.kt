package com.bdalxy.edencourt.feature.chat

/**
 * 消息拆句器（T5.9）。
 * 类微信定位：AI 长回复按句拆成 2~3 条短消息连发，模拟真人打字节奏。
 */
object MessageSplitter {

    // 句末分隔符（lookbehind 保留分隔符在句尾）：中文句号/问号/感叹号/分号 + 半角 + 换行
    private val SENTENCE_SPLIT = Regex("(?<=[。！？；;!?\\n])")

    // 单条回复最多拆成的消息条数
    private const val MAX_CHUNKS = 3

    /**
     * 将一段回复拆成最多 [MAX_CHUNKS] 条短消息。
     * - 无句末分隔符或只有 1 句：原样返回（不拆）
     * - 句数 ≤ 3：每句一条
     * - 句数 > 3：按顺序均匀合并成 3 组，保持语义顺序
     */
    fun split(content: String): List<String> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()
        val segments = trimmed
            .split(SENTENCE_SPLIT)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (segments.size <= 1) return listOf(trimmed)
        if (segments.size <= MAX_CHUNKS) return segments
        // 超过上限：按顺序分组成 3 组，组内拼接保留原句序
        val groupSize = (segments.size + MAX_CHUNKS - 1) / MAX_CHUNKS
        return segments.chunked(groupSize).map { it.joinToString("") }
    }
}
