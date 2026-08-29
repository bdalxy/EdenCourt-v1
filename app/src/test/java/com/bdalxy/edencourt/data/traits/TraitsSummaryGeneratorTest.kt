package com.bdalxy.edencourt.data.traits

import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import com.bdalxy.edencourt.data.remote.RemoteApiChatService
import com.bdalxy.edencourt.data.setting.ApiConfig
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话角色印象摘要生成单测（T13.4）。
 * 覆盖：LLM 生成成功（llm 来源）/ LLM 未配置 API Key → 模板降级 / LLM 抛异常 → 模板降级 / LLM 坏 JSON → 模板降级 /
 *      模板降级必有非空摘要与 2~3 标签 / 用户编辑（generatedBy='user'）永不产生于生成器。
 */
class TraitsSummaryGeneratorTest {

    private val service = mockk<RemoteApiChatService>()
    private val store = mockk<ApiConfigStore>()

    /** 6 维数值（TraitsGenerator 落点样例：整体偏温柔黏人） */
    private val traits = mapOf(
        "sociability" to 70,
        "initiative" to 60,
        "warmth" to 85,
        "attachment" to 80,
        "humor" to 55,
        "casualness" to 45,
    )

    private fun gen(apiKey: String = "test-key"): TraitsSummaryGenerator {
        every { store.config } returns flowOf(ApiConfig(apiKey = apiKey))
        return TraitsSummaryGenerator(service, store)
    }

    @Test
    fun `LLM生成成功_来源为llm_摘要与标签非空`() = runBlocking {
        coEvery { service.completeChat(any(), any()) } returns
            """{"blurb":"她平时温柔体贴，但被质疑时会显露要强的一面。","tags":["温柔","要强","暖心"]}"""
        val (blurb, tags, source) = gen().generate(
            name = "光羽", personality = "温柔", background = "", traits = traits,
            surface = "温柔体贴", inner = "很要强", trigger = "被质疑时",
        )
        assertEquals(ConversationTraits.GENERATED_BY_LLM, source)
        assertTrue(blurb.contains("温柔"))
        assertTrue(tags.contains("温柔"))
    }

    @Test
    fun `LLM未配置APIKey_降级模板`() = runBlocking {
        // apiKey 空 → 不调 LLM 直接模板
        val (blurb, tags, source) = gen(apiKey = "").generate(
            name = "光羽", personality = "温柔", background = "", traits = traits,
            surface = "温柔体贴", inner = "很要强", trigger = "被质疑时",
        )
        assertEquals(ConversationTraits.GENERATED_BY_TEMPLATE, source)
        assertTrue(blurb.isNotBlank())
        // 模板标签 2~3 个（JSON 数组解析）
        val tagsJson = Json.parseToJsonElement(tags) as JsonArray
        assertTrue("模板标签应 2~3 个，实际 ${tagsJson.size}", tagsJson.size in 2..3)
    }

    @Test
    fun `LLM抛异常_降级模板_不吞错`() = runBlocking {
        coEvery { service.completeChat(any(), any()) } throws RuntimeException("网络不可用")
        val (blurb, tags, source) = gen().generate(
            name = "光羽", personality = "温柔", background = "", traits = traits,
            surface = "温柔体贴", inner = "", trigger = "",
        )
        assertEquals(ConversationTraits.GENERATED_BY_TEMPLATE, source)
        assertTrue(blurb.isNotBlank())
        assertTrue(tags.isNotBlank())
    }

    @Test
    fun `LLM返回坏JSON_降级模板`() = runBlocking {
        coEvery { service.completeChat(any(), any()) } returns "这不是JSON，是一些废话"
        val (_, _, source) = gen().generate(
            name = "光羽", personality = "温柔", background = "", traits = traits,
            surface = "温柔体贴", inner = "很要强", trigger = "被质疑时",
        )
        assertEquals(ConversationTraits.GENERATED_BY_TEMPLATE, source)
    }

    @Test
    fun `LLM返回缺字段JSON_降级模板`() = runBlocking {
        // tags 缺失 → 不符合 2~3 标签要求 → 降级
        coEvery { service.completeChat(any(), any()) } returns """{"blurb":"只有摘要没有标签"}"""
        val (_, _, source) = gen().generate(
            name = "光羽", personality = "温柔", background = "", traits = traits,
            surface = "温柔体贴", inner = "很要强", trigger = "被质疑时",
        )
        assertEquals(ConversationTraits.GENERATED_BY_TEMPLATE, source)
    }
}
