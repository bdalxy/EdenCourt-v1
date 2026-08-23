package com.bdalxy.edencourt.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completion 请求/响应 DTO（T3.2，参考企划书 3.6.2/3.6.3）。
 * 字段与 OpenAI / DeepSeek API 对齐；流式与非流式共用同一套模型。
 */

/** 单条消息：角色 + 内容（system/user/assistant） */
@Serializable
data class ChatMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
)

/** Chat Completion 请求体（非流式与流式共用，stream 字段控制） */
@Serializable
data class ChatCompletionRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ChatMessage>,
    @SerialName("temperature") val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 2048,
    @SerialName("top_p") val topP: Double = 1.0,
    @SerialName("stream") val stream: Boolean = true,
)

/** 用量统计（可选，流式 chunk 通常不返回） */
@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
)

/** 非流式响应中的单个 choice */
@Serializable
data class Choice(
    @SerialName("index") val index: Int = 0,
    @SerialName("message") val message: ChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

/** 非流式 Chat Completion 响应（T3.9 测试连接用） */
@Serializable
data class ChatCompletionResponse(
    @SerialName("id") val id: String = "",
    @SerialName("object") val objectType: String = "",
    @SerialName("created") val created: Long = 0L,
    @SerialName("model") val model: String = "",
    @SerialName("choices") val choices: List<Choice> = emptyList(),
    @SerialName("usage") val usage: Usage? = null,
) {
    /** 便捷取首条助手回复文本（无则空串，调用方兜底） */
    val firstContent: String get() = choices.firstOrNull()?.message?.content.orEmpty()
}

/**
 * 流式响应中的增量 delta。
 * reasoning_content：推理模型（如 deepseek-v4-flash）先输出思考过程，content 为空；
 * 思考结束后才输出正式答案（content 非空）。两字段互斥出现，需兼容处理（T3.4 修正）。
 */
@Serializable
data class Delta(
    @SerialName("role") val role: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

/** 流式响应中的单个 choice（含 delta） */
@Serializable
data class ChunkChoice(
    @SerialName("index") val index: Int = 0,
    @SerialName("delta") val delta: Delta = Delta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

/**
 * 流式增量（T3.4 修正）：区分正式答案与推理模型思考过程。
 * 推理模型（如 deepseek-v4-flash）思考阶段 content 为空、reasoning 有值；
 * 思考结束后反之。非推理模型 reasoning 恒为空。
 */
data class ChatDelta(
    /** 正式答案增量（可能为空串） */
    val content: String = "",
    /** 思考过程增量（可能为空串） */
    val reasoning: String = "",
)

/** 流式 Chat Completion chunk（SSE data: 行内容） */
@Serializable
data class ChatChunkResponse(
    @SerialName("id") val id: String = "",
    @SerialName("object") val objectType: String = "",
    @SerialName("created") val created: Long = 0L,
    @SerialName("model") val model: String = "",
    @SerialName("choices") val choices: List<ChunkChoice> = emptyList(),
) {
    /** 本 chunk 的正式答案增量（无则空串） */
    val deltaContent: String get() = choices.firstOrNull()?.delta?.content.orEmpty()

    /** 本 chunk 的思考过程增量（无则空串） */
    val deltaReasoning: String get() = choices.firstOrNull()?.delta?.reasoningContent.orEmpty()
}
