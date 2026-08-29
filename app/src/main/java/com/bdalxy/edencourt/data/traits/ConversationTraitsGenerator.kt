package com.bdalxy.edencourt.data.traits

import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import com.bdalxy.edencourt.domain.CharacterCardParser

/**
 * 会话角色人格生成统筹器（T13.1~T13.4 串联：建会话时生成一次并落库）。
 * 流水线（纯逻辑均在 data/traits 下，本类只做编排 + 写库）：
 *   1. 卡 JSON → CharacterCard（CharacterCardParser.parse）
 *   2. 范围：卡 traitsRange（数据党）→ RangeReader LLM 读卡（感觉党/中间党缺维）→ TraitRangeResolver.resolve 归并 6 维
 *   3. 落点：TraitsGenerator 在范围内随机 → 6 维数值
 *   4. 反差：ContrastLibrary 生成表面/隐藏/触发
 *   5. 摘要：TraitsSummaryGenerator LLM 生成（失败降级模板）→ blurb + tags + generatedBy
 * 之后由调用方（建会话流程）把结果 upsert 到 conversation_traits。
 * 设计约束：LLM 调用全部 runCatching 兜底，任何失败都不阻塞建会话（用启发式/模板降级）。
 */
class ConversationTraitsGenerator(
    private val rangeReader: RangeReader,
    private val summaryGenerator: TraitsSummaryGenerator,
) {

    /**
     * 为一个会话生成完整人格记录（不写库，由调用方 upsert）。
     * @param conversationId 新会话 id
     * @param characterCardId 来源角色卡 id
     * @param cardJson 角色卡完整 JSON（character_cards.jsonContent）
     * @return 完整的 ConversationTraits（含 6 维数值 + 反差 + 摘要），供 upsert
     */
    suspend fun generateForConversation(
        conversationId: Long,
        characterCardId: Long,
        cardJson: String,
    ): ConversationTraits {
        // 解析卡：拿 personality/background（感觉党读卡 + 反差派生 + 摘要生成的输入）
        val card = runCatching { CharacterCardParser.parse(cardJson) }.getOrNull()
        val personality = card?.personality.orEmpty()
        val background = card?.background.orEmpty()

        // 1. 范围：数据党卡上 traitsRange → 感觉党 LLM 读卡 → 归并
        val llmRange = if (card?.traitsRange.isNullOrEmpty() || card?.traitsRange == null) {
            // 卡上无范围（感觉党/中间党全缺）→ LLM 读卡；有部分则仍尝试补全其余维度
            runCatching { rangeReader.readRange(personality, background) }.getOrNull()
        } else {
            null
        }
        // 中间党：卡上部分维度有值，其余缺 → 仍尝试 LLM 补全（resolve 逐维归并时缺的自动降级）
        val dataRange = card?.traitsRange
        val mergedRanges = TraitRangeResolver.resolve(dataRange, llmRange, personality)

        // 2. 落点：范围内均匀随机 → 6 维数值
        val traits = TraitsGenerator.generate(mergedRanges)

        // 3. 反差对：卡派生表面 + 受限反差库选隐藏
        val contrast = ContrastLibrary.generate(personality, background)

        // 4. 摘要：LLM 生成（失败降级模板）
        val (blurb, tagsJson, generatedBy) = summaryGenerator.generate(
            name = card?.name.orEmpty(),
            personality = personality,
            background = background,
            traits = traits,
            surface = contrast.surface,
            inner = contrast.inner,
            trigger = contrast.trigger,
        )

        return ConversationTraits(
            conversationId = conversationId,
            characterCardId = characterCardId,
            sociability = traits["sociability"] ?: 50,
            initiative = traits["initiative"] ?: 50,
            warmth = traits["warmth"] ?: 50,
            attachment = traits["attachment"] ?: 50,
            humor = traits["humor"] ?: 50,
            casualness = traits["casualness"] ?: 50,
            contrastSurface = contrast.surface,
            contrastInner = contrast.inner,
            contrastTrigger = contrast.trigger,
            identityBlurb = blurb,
            identityTags = tagsJson,
            generatedBy = generatedBy,
        )
    }
}
