package com.bdalxy.edencourt.data.remote

import kotlinx.serialization.json.Json

/**
 * SSE 流式解析器（T3.4，参考企划书 3.6.3）。
 * - 负责把 OkHttp 响应的 SSE 文本行解析为增量 content
 * - 识别结束标志 `data: [DONE]`
 * - 纯函数、无 IO，调用方需在后台线程执行（不阻塞主线程）
 */
object SseStreamParser {

    /** SSE 结束标志：服务端发完所有 chunk 后发此行 */
    const val DONE_MARKER = "[DONE]"

    /** SSE 数据行前缀 */
    private const val DATA_PREFIX = "data:"

    /** 共享 Json 实例：忽略未知字段，兼容不同服务端返回 */
    val json: Json = Json {
        ignoreUnknownKeys = true
    }

    /** 判断该行是否为结束标志（trim 后正好等于 [DONE]） */
    fun isDone(line: String): Boolean =
        line.trim().substringAfter(DATA_PREFIX, "").trim() == DONE_MARKER

    /**
     * 解析单行 SSE：返回该行携带的增量 content。
     * - 非 data: 行、空 data、结束行 → 返回 null
     * - JSON 解析失败（如热更新等非标准行）→ 返回 null（容忍，不崩溃）
     */
    fun parseDataContent(line: String): String? {
        val trimmed = line.trim()
        if (!trimmed.startsWith(DATA_PREFIX)) return null
        val payload = trimmed.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty() || payload == DONE_MARKER) return null
        return runCatching {
            json.decodeFromString<ChatChunkResponse>(payload).deltaContent
        }.getOrNull()
    }
}
