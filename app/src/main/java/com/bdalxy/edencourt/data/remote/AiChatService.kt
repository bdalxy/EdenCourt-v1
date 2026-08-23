package com.bdalxy.edencourt.data.remote

import com.bdalxy.edencourt.data.setting.ApiConfig
import kotlinx.coroutines.flow.Flow

/**
 * AI 对话服务接口（T3.5，参考企划书 3.2.2）。
 * 接口化：当前用远程 API 实现，后期可无缝替换为本地模型实现。
 */
interface AiChatService {

    /**
     * 流式对话：逐段吐出回复增量（区分正式答案与思考过程）。
     * @param messages 完整消息列表（system + 历史 + 当前用户输入）
     * @param config   本次请求的 API 配置（每次调用传入，实时反映设置变更）
     */
    fun streamChat(messages: List<ChatMessage>, config: ApiConfig): Flow<ChatDelta>

    /**
     * 测试连接（T3.9，参考企划书 5.3.5）：发最小 stream=false 请求。
     * @return true 表示连接成功；失败时抛 ApiException（携带错误类型）
     */
    suspend fun testConnection(config: ApiConfig): Boolean

    /**
     * 非流式一次完整回复（T5.3 记忆提取用）：stream=false 拿整段文本。
     * @return 首条助手回复完整文本；失败抛 ApiException
     */
    suspend fun completeChat(messages: List<ChatMessage>, config: ApiConfig): String
}
