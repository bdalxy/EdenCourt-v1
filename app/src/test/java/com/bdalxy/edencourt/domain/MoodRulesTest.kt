package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.ConversationMood
import com.bdalxy.edencourt.data.local.entity.MoodSource
import com.bdalxy.edencourt.data.schedule.LifeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MoodRules 实时情绪规则单测（A2）。
 * 覆盖：关键词触发、基线派生、到期回落、注入文本分档。
 */
class MoodRulesTest {

    @Test
    fun `命中关键词触发对应情绪`() {
        assertEquals(MoodTag.HAPPY, MoodRules.react("我喜欢你")?.tag)
        assertEquals(MoodTag.EXCITED, MoodRules.react("哈哈哈好开心")?.tag)
        assertEquals(MoodTag.SAD, MoodRules.react("你别理我了")?.tag)
        assertEquals(MoodTag.ANNOYED, MoodRules.react("你太过分了")?.tag)
        assertEquals(MoodTag.SHY, MoodRules.react("你脸红什么呀")?.tag)
    }

    @Test
    fun `未命中关键词返回 null`() {
        assertNull(MoodRules.react("今天天气怎么样"))
        assertNull(MoodRules.react(""))
    }

    @Test
    fun `自主基线按作息派生`() {
        assertEquals(MoodTag.DROWSY, MoodRules.baselineFor(LifeState.SLEEPING))
        assertEquals(MoodTag.EXCITED, MoodRules.baselineFor(LifeState.ACTIVE))
        assertEquals(MoodTag.PEACEFUL, MoodRules.baselineFor(LifeState.WAKING))
        assertEquals(MoodTag.PEACEFUL, MoodRules.baselineFor(LifeState.EVENING))
        assertEquals(MoodTag.PEACEFUL, MoodRules.baselineFor(null))
    }

    @Test
    fun `无交互情绪时回落自主基线`() {
        val now = System.currentTimeMillis()
        val baseline = MoodRules.baselineFor(LifeState.SLEEPING)
        val out = MoodRules.resolve(null, now, baseline)
        assertEquals(MoodTag.DROWSY, out.tag)
        assertEquals(MoodRules.BASELINE_INTENSITY, out.intensity)
    }

    @Test
    fun `未过期交互情绪优先`() {
        val now = System.currentTimeMillis()
        val mood = ConversationMood(
            conversationId = 1L,
            moodTag = MoodTag.HAPPY.name,
            intensity = 80,
            source = MoodSource.REACT.name,
            updatedAt = now,
            expiresAt = now + 60_000,
        )
        val out = MoodRules.resolve(mood, now, MoodRules.baselineFor(null))
        assertEquals(MoodTag.HAPPY, out.tag)
        assertEquals(80, out.intensity)
    }

    @Test
    fun `过期交互情绪回落基线`() {
        val now = System.currentTimeMillis()
        val mood = ConversationMood(
            conversationId = 1L,
            moodTag = MoodTag.HAPPY.name,
            intensity = 80,
            source = MoodSource.REACT.name,
            updatedAt = now - 120_000,
            expiresAt = now - 60_000, // 已过期
        )
        val out = MoodRules.resolve(mood, now, MoodRules.baselineFor(null))
        assertEquals(MoodTag.PEACEFUL, out.tag)
    }

    @Test
    fun `强度夹在 0~100 之间`() {
        val now = System.currentTimeMillis()
        val mood = ConversationMood(
            conversationId = 1L,
            moodTag = MoodTag.HAPPY.name,
            intensity = 999,
            source = MoodSource.REACT.name,
            updatedAt = now,
            expiresAt = now + 60_000,
        )
        val out = MoodRules.resolve(mood, now, MoodRules.baselineFor(null))
        assertEquals(100, out.intensity)
    }

    @Test
    fun `注入文本含情绪名与强度`() {
        val text = MoodRules.buildMoodText(MoodTag.HAPPY, 70)
        assertTrue(text.contains("欢喜"))
        assertTrue(text.contains("70/100"))
        // 各情绪均有非空提示
        assertFalse(MoodRules.buildMoodText(MoodTag.ANNOYED, 60).isBlank())
    }
}