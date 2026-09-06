package com.bdalxy.edencourt.data.remote

import com.bdalxy.edencourt.data.setting.ApiConfig
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * API 错误类型（T3.8，参考企划书 3.6.4 错误分支）。
 * 便于上层按类型给出明确提示与处理策略。
 */
enum class ApiErrorKind {
    /** 网络不可用/连接失败 */
    NETWORK,
    /** Key 无效/无权限（401/403） */
    AUTH,
    /** 请求过于频繁（429） */
    RATE_LIMIT,
    /** 连接或读取超时 */
    TIMEOUT,
    /** 服务端其他错误（5xx 等） */
    SERVER,
}

/** 带错误类型的 API 异常，由 RemoteApiChatService 抛出 */
class ApiException(
    val kind: ApiErrorKind,
    message: String,
) : IOException(message)

/**
 * 远程 API 流式对话实现（T3.5，参考企划书 3.6.3）。
 * - OkHttp POST {baseUrl}/chat/completions，stream=true，Accept: text/event-stream
 * - 逐行读取 SSE，经 SseStreamParser 解析后逐段吐出增量文本
 * - IO 线程执行，不阻塞主线程；错误统一包装为 ApiException 抛出（T3.8）
 */
class RemoteApiChatService(
    private val okHttpClient: OkHttpClient,
) : AiChatService {

    override fun streamChat(messages: List<ChatMessage>, config: ApiConfig): Flow<ChatDelta> = flow {
        // 1. 构造请求体：OpenAI 兼容 JSON（stream 开启）
        val requestBody = SseStreamParser.json.encodeToString(
            ChatCompletionRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                maxTokens = config.maxTokens,
                topP = config.topP,
                stream = true,
            ),
        )

        // 2. 构造请求：POST + 鉴权头 + SSE 接受头
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "text/event-stream")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        // 3. 保存 Call 引用并注册取消联动（T12.x P1 修复）：协程被取消时立即 call.cancel()，
        //    中断阻塞读（此前最长等 60s 超时），避免关闭聊天页/切换会话后连接与 IO 线程被长期占用
        val call = okHttpClient.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

        try {
            // 4. 同步执行（在 IO 线程），逐行读取 SSE 并逐段 emit
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    // 按状态码归类错误类型（T3.8 错误分支）
                    throw when (response.code) {
                        401, 403 -> ApiException(ApiErrorKind.AUTH, "HTTP ${response.code}")
                        429 -> ApiException(ApiErrorKind.RATE_LIMIT, "HTTP ${response.code}")
                        else -> ApiException(ApiErrorKind.SERVER, "HTTP ${response.code}")
                    }
                }
                val source = response.body?.source()
                while (true) {
                    val line = source?.readUtf8Line() ?: break
                    if (SseStreamParser.isDone(line)) break
                    // 分别解析思考过程与正式答案增量（T3.4 修正：推理模型两字段互斥出现）
                    val reasoning = SseStreamParser.parseDataReasoning(line)
                    val content = SseStreamParser.parseDataContent(line)
                    if (reasoning == null && content == null) continue
                    emit(ChatDelta(content = content ?: "", reasoning = reasoning ?: ""))
                }
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: SocketTimeoutException) {
            throw ApiException(ApiErrorKind.TIMEOUT, "请求或读取超时")
        } catch (e: ApiException) {
            throw e // 已归类，原样上抛
        } catch (e: IOException) {
            throw ApiException(ApiErrorKind.NETWORK, "网络不可用：${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(config: ApiConfig): Boolean = withContext(Dispatchers.IO) {
        // 最小请求体：stream=false，max_tokens 极小，仅验证连通性与 Key
        val requestBody = SseStreamParser.json.encodeToString(
            ChatCompletionRequest(
                model = config.model,
                messages = listOf(ChatMessage(role = "user", content = "ping")),
                temperature = 0.7,
                maxTokens = 8,
                topP = 1.0,
                stream = false,
            ),
        )
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Accept", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        // 取消联动（T12.x P1 修复）：协程取消时立即断开，不空等 IO 超时
        val call = okHttpClient.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    // 与流式一致的错误归类
                    throw when (response.code) {
                        401, 403 -> ApiException(ApiErrorKind.AUTH, "HTTP ${response.code}")
                        429 -> ApiException(ApiErrorKind.RATE_LIMIT, "HTTP ${response.code}")
                        else -> ApiException(ApiErrorKind.SERVER, "HTTP ${response.code}")
                    }
                }
                true
            }
        } catch (e: CancellationException) {
            call.cancel()
            throw e
        } catch (e: SocketTimeoutException) {
            throw ApiException(ApiErrorKind.TIMEOUT, "连接超时")
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException(ApiErrorKind.NETWORK, "网络不可用：${e.message}")
        }
    }

    override suspend fun completeChat(messages: List<ChatMessage>, config: ApiConfig): String =
        withContext(Dispatchers.IO) {
            // 非流式请求：stream=false，期望一次返回完整答案
            val requestBody = SseStreamParser.json.encodeToString(
                ChatCompletionRequest(
                    model = config.model,
                    messages = messages,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    topP = config.topP,
                    stream = false,
                ),
            )
            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Accept", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            // 取消联动（T12.x P1 修复）：协程取消时立即断开，不空等 IO 超时
            val call = okHttpClient.newCall(request)
            coroutineContext[Job]?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw when (response.code) {
                            401, 403 -> ApiException(ApiErrorKind.AUTH, "HTTP ${response.code}")
                            429 -> ApiException(ApiErrorKind.RATE_LIMIT, "HTTP ${response.code}")
                            else -> ApiException(ApiErrorKind.SERVER, "HTTP ${response.code}")
                        }
                    }
                    val body = response.body?.string().orEmpty()
                    // 解析 JSON 响应取首条助手消息内容；失败抛出明确错误而非静默返回空串，
                    // 让调用方（记忆提取/跨会话打听等）能感知失败，避免把空串当有效结果
                    runCatching {
                        SseStreamParser.json.decodeFromString<ChatCompletionResponse>(body).firstContent
                    }.getOrElse { e ->
                        throw ApiException(ApiErrorKind.SERVER, "响应解析失败：${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                call.cancel()
                throw e
            } catch (e: SocketTimeoutException) {
                throw ApiException(ApiErrorKind.TIMEOUT, "连接超时")
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException(ApiErrorKind.NETWORK, "网络不可用：${e.message}")
            }
        }

    companion object {
        /**
         * 默认客户端：连接 10s、写入 30s、读取 60s。
         * 读取超时放宽为 60s：SSE 长等待（如思考过程）期间可能长时间无数据。
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
