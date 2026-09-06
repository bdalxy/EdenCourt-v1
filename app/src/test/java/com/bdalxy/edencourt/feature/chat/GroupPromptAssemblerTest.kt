package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.feature.chat.GroupPromptAssembler.ParticipantBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 群聊同名 speaker 修复回归单测（T12.x P1）。
 * 覆盖 disambiguateNames：保证群内参与者显示名唯一，
 * 避免下游 speakerMap = associate { displayName -> conversationId } 遇重名只剩最后一个、
 * 导致同名者发言全被算到一人、其余实例消息丢失/错记。
 */
class GroupPromptAssemblerTest {

    /** 构造一个参与者块；affinityFragment/directionFragment/moodText/lifeStateText/traitsSummary 非本用例关注点，填空串 */
    private fun block(conversationId: Long, displayName: String, cardName: String) = ParticipantBlock(
        conversationId = conversationId,
        displayName = displayName,
        cardName = cardName,
        affinityFragment = "",
        directionFragment = "",
        moodText = "活跃",
        lifeStateText = "",
        traitsSummary = "",
    )

    @Test
    fun `无重名时原样返回不改写`() {
        val blocks = listOf(
            block(1, "小夏", "小夏酱"),
            block(2, "羽", "羽"),
            block(3, "阿杰", "阿杰"),
        )
        val result = disambiguateNames(blocks)
        assertEquals(blocks, result) // 显示名一字不改
        assertEquals(listOf("小夏", "羽", "阿杰"), result.map { it.displayName })
    }

    @Test
    fun `同卡多实例重名时用卡名区分`() {
        // 同一角色卡「小夏」开两实例，都无会话备注 → displayName 均为「小夏」→ 重名
        val blocks = listOf(
            block(1, "小夏", "小夏"),
            block(2, "小夏", "小夏"),
        )
        val result = disambiguateNames(blocks)
        val names = result.map { it.displayName }
        assertEquals(2, names.distinct().size) // 群内唯一
        // 首个不带序号，后续带序号；且 conversationId 映射必须一一对应（下游 associate 不再丢）
        val speakerMap = result.associate { it.displayName to it.conversationId }
        assertEquals(2, speakerMap.size)
        assertTrue(names.all { it.contains("小夏") })
    }

    @Test
    fun `备注相同时卡名连同序号去重`() {
        // 两个不同角色的会话备注重叠（都叫「邻居」）→ displayName 相同；用卡名「小明/小红」区分后仍可能碰撞场景
        val blocks = listOf(
            block(1, "邻居", "小明"),
            block(2, "邻居", "小红"),
        )
        val result = disambiguateNames(blocks)
        val names = result.map { it.displayName }
        assertEquals(2, names.distinct().size)
        val speakerMap = result.associate { it.displayName to it.conversationId }
        assertEquals(2, speakerMap.size)
        // 两者都含原显示名「邻居」
        assertTrue(names.all { it.contains("邻居") })
    }

    @Test
    fun `同名且同卡名的极端case保证序号兜底唯一`() {
        // displayName 已含卡名（"小夏（小夏）"），cardName 无法再课堂，需靠序号保证唯一
        val blocks = listOf(
            block(1, "小夏（小夏）", "小夏"),
            block(2, "小夏（小夏）", "小夏"),
            block(3, "小夏（小夏）", "小夏"),
        )
        val result = disambiguateNames(blocks)
        val names = result.map { it.displayName }
        assertEquals(3, names.distinct().size)
        assertEquals(3, result.associate { it.displayName to it.conversationId }.size)
    }
}