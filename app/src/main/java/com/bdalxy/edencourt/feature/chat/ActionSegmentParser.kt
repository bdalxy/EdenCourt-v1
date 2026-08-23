package com.bdalxy.edencourt.feature.chat

/**
 * 解析后的消息：动作神态段与纯正文分离（T5.8）。
 * - actions：识别出的动作神态段列表，如「（轻声笑）」「（轻轻摇头）」，保留出现顺序
 * - body：剔除动作段后的纯对话正文（已 trim），若原本无动作段则等于原内容
 */
data class ParsedMessage(
    val actions: List<String>,
    val body: String,
)

/**
 * 动作神态解析器（T5.8）。
 * 类微信定位：动作神态不写进正文，而是识别出来交给 UI 以斜体小字独立展示。
 * 这里按全角/半角括号识别动作段，做成独立纯函数，后续可复用/升级为结构化解析。
 */
object ActionSegmentParser {

    // 全角括号（不嵌套）与半角括号（不嵌套）
    private val ACTION_REGEX = Regex("（[^（）]*）|\\([^()]*\\)")

    /**
     * 从 AI 回复中提取动作神态段，返回动作列表与剩余正文。
     * 无括号时 actions 为空、body 等于原内容（保持渲染不变）。
     */
    fun parse(content: String): ParsedMessage {
        val actions = mutableListOf<String>()
        // 用 replace 的 lambda 收集每个匹配到的括号段并替换为空，实现正文与动作分离
        val body = ACTION_REGEX.replace(content) { match ->
            actions.add(match.value)
            ""
        }
        return ParsedMessage(actions, body.trim())
    }
}
