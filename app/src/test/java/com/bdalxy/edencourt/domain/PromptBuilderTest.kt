package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PromptBuilder 人设注入单测（T13.5）。
 * 覆盖：无 traits 返回空串（兼容旧行为）/ 有 traits 注入印象摘要 · 自补充引导 /
 *      依恋度达标流露反差 / 依恋度未达标不流露（反差隐藏）。
 */
class PromptBuilderTest {

    /** 构造一个反差对 + 高依恋度（80）的会话人格 */
    private fun traitsWith(attachment: Int) = ConversationTraits(
        id = 1L,
        conversationId = 100L,
        characterCardId = 1L,
        sociability = 70,
        initiative = 60,
        warmth = 85,
        attachment = attachment,
        humor = 50,
        casualness = 45,
        contrastSurface = "温柔体贴",
        contrastInner = "很要强",
        contrastTrigger = "被质疑时",
        identityBlurb = "平时温柔暖心，但被误解时会显露要强的一面。",
        identityTags = "[\"温柔\",\"要强\"]",
        generatedBy = ConversationTraits.GENERATED_BY_LLM,
    )

    @Test
    fun `无traits_返回空串_不改变prompt`() {
        assertEquals("", PromptBuilder.buildTraitsPrompt(null))
    }

    @Test
    fun `有traits_注入印象摘要与自补充引导`() {
        val text = PromptBuilder.buildTraitsPrompt(traitsWith(80))
        assertTrue("应包含别人眼中的你", text.contains("别人眼中的你"))
        assertTrue("应包含印象摘要", text.contains("温柔暖心"))
        assertTrue("应包含自补充引导", text.contains("个人偏好"))
        assertTrue("应包含【你的独有气质】标题", text.contains("【你的独有气质】"))
    }

    @Test
    fun `依恋度达标_流露反差隐藏特质`() {
        val text = PromptBuilder.buildTraitsPrompt(traitsWith(80))
        assertTrue("达标应说明会流露隐藏", text.contains("自然流露出不一样的自己"))
        assertTrue("应包含隐藏特质内容", text.contains("很要强"))
    }

    @Test
    fun `依恋度未达标_反差仅隐藏不外露`() {
        val text = PromptBuilder.buildTraitsPrompt(traitsWith(40))
        assertTrue("未达标应提示仅内心", text.contains("内心深处"))
        assertTrue("应包含隐而不露表述", text.contains("不完全展露"))
        // 不应出现「已很亲近 / 自然流露」的达标措辞
        assertFalse("未达标不应说已很亲近", text.contains("你们已经很亲近"))
        assertFalse("未达标不应说很自然流露", text.contains("自然流露出不一样的自己"))
    }
}