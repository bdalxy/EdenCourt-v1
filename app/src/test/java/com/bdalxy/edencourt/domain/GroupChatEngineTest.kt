package com.bdalxy.edencourt.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * 群聊场景解析器单测（v2.0 A6-3）。
 */
class GroupChatEngineTest {

    // ── 解析 ──

    @Test
    fun `parse 正常 JSON 返回场景`() {
        val json = """
            {
              "lines": [
                {"speaker": "小夏", "content": "哈哈，今天天气不错"},
                {"speaker": "阿羽", "content": "是啊，适合出去走走"}
              ],
              "scene_end": true
            }
        """.trimIndent()
        val scene = GroupChatParser.parse(json)
        assertNotNull(scene)
        assertEquals(2, scene!!.lines.size)
        assertEquals("小夏", scene.lines[0].speaker)
        assertEquals("阿羽", scene.lines[1].speaker)
        assertTrue(scene.sceneEnd)
    }

    @Test
    fun `parse 超过硬上限 6 条截断`() {
        val lines = (1..8).joinToString(",\n") { """{"speaker": "小夏", "content": "消息$it"}""" }
        val json = """{"lines": [$lines], "scene_end": true}"""
        val scene = GroupChatParser.parse(json)
        assertNotNull(scene)
        assertEquals(6, scene!!.lines.size)
        assertTrue(scene.sceneEnd)
    }

    @Test
    fun `parse 空 lines 正常`() {
        val json = """{"lines": [], "scene_end": true}"""
        val scene = GroupChatParser.parse(json)
        assertNotNull(scene)
        assertEquals(0, scene!!.lines.size)
    }

    @Test
    fun `parse markdown 包裹 JSON 正常解析`() {
        val json = """
            ```json
            {"lines": [{"speaker": "小夏", "content": "你好"}], "scene_end": true}
            ```
        """.trimIndent()
        val scene = GroupChatParser.parse(json)
        assertNotNull(scene)
        assertEquals(1, scene!!.lines.size)
    }

    @Test
    fun `parse 非法 JSON 返回 null`() {
        val scene = GroupChatParser.parse("这不是 JSON")
        assertNull(scene)
    }

    // ── toMessages ──

    @Test
    fun `toMessages 正常映射消息`() {
        val scene = GroupChatScene(
            lines = listOf(
                SceneLine("小夏", "今天天气真好"),
                SceneLine("阿羽", "是啊"),
            ),
            sceneEnd = true,
        )
        val speakerMap = mapOf("小夏" to 101L, "阿羽" to 102L)
        val messages = GroupChatParser.toMessages(scene, 1L, speakerMap)
        assertEquals(2, messages.size)
        assertEquals(101L, messages[0].senderConversationId)
        assertEquals("今天天气真好", messages[0].content)
        assertEquals(102L, messages[1].senderConversationId)
        assertEquals("是啊", messages[1].content)
    }

    @Test
    fun `toMessages 过滤空内容行`() {
        val scene = GroupChatScene(
            lines = listOf(
                SceneLine("小夏", "有效消息"),
                SceneLine("阿羽", ""),  // 空内容
                SceneLine("小夏", "  "), // 空白
            ),
            sceneEnd = true,
        )
        val speakerMap = mapOf("小夏" to 101L, "阿羽" to 102L)
        val messages = GroupChatParser.toMessages(scene, 1L, speakerMap)
        assertEquals(1, messages.size)
        assertEquals("有效消息", messages[0].content)
    }

    @Test
    fun `toMessages 过滤未知发言者`() {
        val scene = GroupChatScene(
            lines = listOf(
                SceneLine("小夏", "有效"),
                SceneLine("陌生人", "无效"),  // 不在 speakerMap 中
            ),
            sceneEnd = true,
        )
        val speakerMap = mapOf("小夏" to 101L)
        val messages = GroupChatParser.toMessages(scene, 1L, speakerMap)
        assertEquals(1, messages.size)
        assertEquals(101L, messages[0].senderConversationId)
    }

    // ── 提示词组装 ──

    @Test
    fun `buildGroupChatPrompt 包含所有要素`() {
        val prompt = buildGroupChatPrompt(
            identityText = "【群聊成员】\n- 小夏：亲密度 45\n- 阿羽：亲密度 60",
            recentMessages = "小夏：昨天那部电影好看吗\n阿羽：还不错！",
            userMessage = "今天天气真好",
        )
        assertTrue(prompt.contains("群聊成员"))
        assertTrue(prompt.contains("小夏"))
        assertTrue(prompt.contains("阿羽"))
        assertTrue(prompt.contains("最近消息"))
        assertTrue(prompt.contains("昨天那部电影"))
        assertTrue(prompt.contains("今天天气真好"))
        assertTrue(prompt.contains("scene_end"))
    }
}