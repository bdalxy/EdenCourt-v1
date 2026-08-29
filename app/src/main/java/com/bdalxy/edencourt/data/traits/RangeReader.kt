package com.bdalxy.edencourt.data.traits

import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.remote.RemoteApiChatService
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.domain.TraitRange
import kotlinx.coroutines.flow.first

/**
 * 角色卡范围读取器（T13.2 补接线：感觉党 LLM 读卡生成范围）。
 * 纯函数 [TraitRangeResolver] 只负责解析规则；真正的「LLM 读卡 → 6 维范围 JSON」调用在此完成。
 * 建会话时若卡未填 traitsRange（感觉党/中间党缺维），调用本读取器生成范围后交给 resolve() 归并。
 * 失败（未配置 API / 网络 / 解析失败）返回 null → 调用方走启发式/默认兜底。
 */
class RangeReader(
    private val aiChatService: RemoteApiChatService,
    private val apiConfigStore: ApiConfigStore,
) {

    /**
     * 读卡生成 6 维范围（LLM 调用）。
     * @param personality 卡性格文本
     * @param background  卡背景文本
     * @return LLM 输出的范围子集（可能部分维度）；不可用/失败返回 null
     */
    suspend fun readRange(personality: String, background: String): Map<String, TraitRange>? {
        val config = apiConfigStore.config.first()
        if (config.apiKey.isBlank()) return null // 未配置 API Key → null 走兜底
        return runCatching {
            val messages = listOf(
                ChatMessage("system", "你是角色卡性格量化助手。只输出合法 JSON，不要任何多余文字。"),
                ChatMessage("user", TraitRangeResolver.buildPrompt(personality, background)),
            )
            val raw = aiChatService.completeChat(messages, config)
            TraitRangeResolver.parseLlmJson(raw)
        }.getOrElse { null }
    }
}
