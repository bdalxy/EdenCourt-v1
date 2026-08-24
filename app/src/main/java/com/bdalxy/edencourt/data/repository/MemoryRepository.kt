package com.bdalxy.edencourt.data.repository

import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 记忆仓库（企划书 3.5.2 / T5.3）。
 * 职责：
 * - 提取：每轮对话后异步调 API，从「用户消息+AI 回复」提取结构化记忆条目
 * - 冲突处理：入库前按内容/关键词查已有记忆，冲突则更新置信度与时间
 * - 只入高置信度条目（confidence >= 阈值）
 * - 检索：供 T5.4 生成前关键词检索注入；软删除：供 T5.5 管理页删除
 */
class MemoryRepository(
    private val dao: MemoryDao,
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
) {

    /** 提取结果 DTO：AI 返回的 JSON 数组元素（T5.3） */
    @Serializable
    private data class ExtractedMemory(
        @SerialName("type") val type: String = "other",
        @SerialName("content") val content: String = "",
        @SerialName("keywords") val keywords: String = "",
        @SerialName("confidence") val confidence: Float = 1f,
    )

    /** 提取 JSON 解析器：忽略未知字段，容忍 AI 输出不规范 */
    private val json = Json { ignoreUnknownKeys = true }

    /** 高置信度入库阈值（企划书 3.5.2：高置信度才入库） */
    companion object {
        const val MIN_CONFIDENCE = 0.7f
        /** 单角色卡注入记忆条数上限（T6.13，防止 prompt 过长） */
        const val DEFAULT_MEMORY_INJECT_LIMIT = 30
        private const val TAG = "MemoryRepo"
    }

    // ---------- 查询（供管理页/检索注入） ----------

    /** 观察全部未删除记忆（最新在前，T5.5 管理页数据源） */
    fun observeAll(): Flow<List<MemoryItem>> = dao.observeAll()

    /** 检索未删除记忆（T5.4 生成前注入用，按关键词匹配 keywords/content） */
    suspend fun search(keyword: String): List<MemoryItem> = dao.searchByKeyword(keyword)

    /** 多关键词检索（T5.4 改进）：用户输入常为整句，直接整串 LIKE 匹配几乎永不命中。
     * 这里按空格/标点拆成多个词分别检索，合并去重后按置信度排序返回。 */
    suspend fun searchByTerms(terms: List<String>): List<MemoryItem> {
        if (terms.isEmpty()) return emptyList()
        val result = LinkedHashMap<Long, MemoryItem>()
        terms.forEach { term ->
            if (term.isNotBlank()) {
                dao.searchByKeyword(term).forEach { result.putIfAbsent(it.id, it) }
            }
        }
        return result.values.sortedWith(compareByDescending<MemoryItem> { it.confidence }.thenByDescending { it.createdAt })
    }

    /**
     * 按归属角色卡取未删除记忆（T6.13 按角色卡隔离注入）。
     * 注入不再依赖用户输入分词 LIKE 匹配（中文整句几乎无法命中），
     * 直接取该角色卡名下高置信度/最近的记忆注入，保证「AI 记得用户告知过的事实」。
     */
    suspend fun getActiveByCard(cardId: Long, limit: Int = DEFAULT_MEMORY_INJECT_LIMIT): List<MemoryItem> =
        dao.getActiveByCard(cardId, limit)

    // ---------- 写入 ----------

    /** 手动添加记忆（T5.5 管理页）：置信度默认 1.0（用户明确填写） */
    suspend fun addManual(type: MemoryType, content: String, keywords: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            MemoryItem(
                type = type,
                content = content.trim(),
                keywords = keywords.trim(),
                confidence = 1f,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** 软删除记忆（T5.5 管理页，二次确认后调用；不物理删除，可回溯） */
    suspend fun softDelete(id: Long) = dao.softDeleteById(id)

    // ---------- 提取（T5.3） ----------

    /**
     * 每轮对话后异步提取长期记忆（企划书 3.5.2）。
     * 输入=用户消息+AI 回复；调 API 返回结构化 JSON 数组；入库前查冲突。
     * @param userContent 用户消息
     * @param aiContent AI 回复
     * @param cardId 归属角色卡 id（T6.13 按角色卡隔离；null 则不归类）
     * 全程不抛错：任何失败都静默返回（记忆提取是附加能力，不影响主对话）。
     */
    suspend fun extractAndStore(userContent: String, aiContent: String, cardId: Long?) {
        val config = apiConfigStore.config.firstOrNull() ?: run {
            Log.w(TAG, "记忆提取跳过：未读到 API 配置")
            return
        }
        if (config.apiKey.isBlank()) {
            Log.w(TAG, "记忆提取跳过：API Key 为空")
            return
        }
        if (userContent.isBlank() && aiContent.isBlank()) {
            Log.w(TAG, "记忆提取跳过：对话内容为空")
            return
        }

        // 1. 组装提取 prompt：要求输出 JSON 数组，只提取明确信息、不编造
        val prompt = buildString {
            append("你是记忆提取助手。请从下面的对话中，提取关于用户的值得长期记住的信息。\n")
            append("规则：只提取对话中明确出现的个人信息、偏好或重要事件；不要编造；不要提取一次性寒暄。\n")
            append("输出格式：仅输出 JSON 数组，不要任何额外文字。每项格式：\n")
            append("[{\"type\":\"personal_info\"|\"preference\"|\"event\"|\"other\",\"content\":\"一句话内容\",\"keywords\":\"关键词1,关键词2\",\"confidence\":0.0~1.0}]\n")
            append("若没有值得记住的信息，输出 []。\n\n")
            append("用户：").append(userContent).append("\n")
            append("AI：").append(aiContent)
        }

        // 2. 调 API（非流式），解析结构化结果
        val raw = runCatching {
            aiChatService.completeChat(
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                config = config,
            )
        }.getOrElse { e ->
            Log.w(TAG, "记忆提取失败：completeChat 异常 ${e.javaClass.simpleName}: ${e.message}")
            return
        }

        // 3. 剥掉可能包裹的 markdown 代码块后解析
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val extracted = runCatching {
            json.decodeFromString<List<ExtractedMemory>>(cleaned)
        }.getOrElse { e ->
            Log.w(TAG, "记忆提取失败：JSON 解析异常，原文=[${raw.take(200)}], err=${e.message}")
            return
        }
        Log.d(TAG, "记忆提取成功：共 ${extracted.size} 条")

        // 4. 逐条入库：只收高置信度，且入库前查冲突（内容相近则更新而非重复插入）
        extracted.forEach { item ->
            val content = item.content.trim()
            if (content.isBlank()) return@forEach
            if (item.confidence < MIN_CONFIDENCE) return@forEach
            upsertWithConflictCheck(item, content, cardId)
        }
    }

    /** 冲突检查入库：内容相似(关键词或内容包含关系)时更新，否则新增（企划书 3.5.2） */
    private suspend fun upsertWithConflictCheck(item: ExtractedMemory, content: String, cardId: Long?) {
        val type = runCatching { MemoryType.valueOf(item.type.uppercase()) }.getOrDefault(MemoryType.OTHER)
        val now = System.currentTimeMillis()

        // 冲突判定：关键词任一命中已有记忆的 keywords 或 content，视为同一条记忆
        val candidateKeywords = item.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val existing = candidateKeywords.firstNotNullOfOrNull { kw ->
            dao.searchByKeyword(kw).firstOrNull()
        }

        if (existing != null) {
            // 冲突：更新内容与置信度（取更高者），刷新时间；归属角色卡同步为新卡（T6.13）
            dao.update(
                existing.copy(
                    type = type,
                    content = content,
                    keywords = item.keywords,
                    confidence = maxOf(existing.confidence, item.confidence),
                    characterCardId = cardId,
                    updatedAt = now,
                ),
            )
        } else {
            dao.insert(
                MemoryItem(
                    type = type,
                    content = content,
                    keywords = item.keywords,
                    confidence = item.confidence,
                    characterCardId = cardId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
