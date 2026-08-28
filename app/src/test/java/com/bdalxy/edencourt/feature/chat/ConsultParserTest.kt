package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.entity.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 跨会话打听「意图识别」纯逻辑单测（G-③ 自动化回归·补聊天核心链路缺口）。
 * 覆盖 T8.4：触发词命中/普通对话不误触发/点名定位目标会话/主题剥离/未点名时目标为空。
 */
class ConsultParserTest {

    /** 构造一条测试会话（只关心 id / characterCardId） */
    private fun conv(id: Long, cardId: Long?): Conversation = Conversation(id = id, characterCardId = cardId)

    /** 角色名表：卡 id → 显示名 */
    private val names = mapOf(10L to "小夏", 20L to "阿杰")

    /** 当前会话 id=1（自己是会话1），其余会话为打听目标候选 */
    private val conversations = listOf(conv(1, 10L), conv(2, 20L))

    // ---------- 未触发（普通对话） ----------

    @Test
    fun `普通对话不含触发词_返回null_不误触发`() {
        assertNull(ConsultParser.identify("今天天气如何", conversations, conversationId = 1L, cardNameById = names))
    }

    @Test
    fun `含角色名但无触发词_仍不触发`() {
        // 提到小夏但不是「去问」，不触发打听
        assertNull(ConsultParser.identify("小夏真可爱", conversations, conversationId = 1L, cardNameById = names))
    }

    // ---------- 命中触发词 ----------

    @Test
    fun `命中触发词_未点名目标_返回请求且目标为空`() {
        val result = ConsultParser.identify("去问问她在干嘛", conversations, conversationId = 1L, cardNameById = names)
        // 剥离触发词「去问问」；未点名任何角色，目标为空
        assertEquals(ConsultRequest(targetConversationId = null, question = "她在干嘛"), result)
    }

    @Test
    fun `命中触发词_主题剥离触发词与语气词`() {
        val result = ConsultParser.identify("帮我打听一下她最近好吗", conversations, conversationId = 1L, cardNameById = names)
        // 剥离全部命中触发词 + 口语语气词「一下」；未点名任何角色，目标为空
        assertEquals(ConsultRequest(targetConversationId = null, question = "她最近好吗"), result)
    }

    // ---------- 点名定位目标会话 ----------

    @Test
    fun `点名其他角色_定位到其会话并剥离主题`() {
        // 点名「阿杰」，应定位到会话2（cardId=20），主题去掉触发词与角色名
        val result = ConsultParser.identify("去问问阿杰在忙什么", conversations, conversationId = 1L, cardNameById = names)
        assertEquals(ConsultRequest(targetConversationId = 2L, question = "在忙什么"), result)
    }

    @Test
    fun `点名自己会话角色_不作为打听目标`() {
        // 「小夏」是当前会话(1)的角色，循环跳过自己 → 目标为空；且当前角色名也已从主题剥离
        val result = ConsultParser.identify("去问问小夏吃饭没", conversations, conversationId = 1L, cardNameById = names)
        assertEquals(ConsultRequest(targetConversationId = null, question = "吃饭没"), result)
    }

    @Test
    fun `点名目标但主题剥空_回退用原文`() {
        // 主题去掉触发词+角色名后为空 → question 兜底用原文
        val result = ConsultParser.identify("去问问阿杰", conversations, conversationId = 1L, cardNameById = names)
        assertEquals(ConsultRequest(targetConversationId = 2L, question = "去问问阿杰"), result)
    }

    // ---------- splitTerms 分词 ----------

    @Test
    fun `分词_按标点与空白切分并过滤空`() {
        assertEquals(
            listOf("她", "最近", "好吗"),
            ConsultParser.splitTerms("她，最近。好吗？"),
        )
    }

    @Test
    fun `分词_空串返回空列表`() {
        assertEquals(emptyList<String>(), ConsultParser.splitTerms(""))
    }
}