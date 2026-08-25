package com.bdalxy.edencourt.data.repository

import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.local.entity.MemoryVisibility
import com.bdalxy.edencourt.data.remote.AiChatService
import com.bdalxy.edencourt.data.remote.ChatMessage
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.search.MemorySearchService
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
    private val searchService: MemorySearchService,
    private val apiConfigStore: ApiConfigStore,
    private val aiChatService: AiChatService,
) {

    /** 提取结果 DTO：AI 返回的 JSON 数组元素（T5.3；replaceContent 用于用户更正时覆盖旧记忆 T6.13.2；visibility 记忆可见性 T7.2；topSecret 绝密兜底 T8.2） */
    @Serializable
    private data class ExtractedMemory(
        @SerialName("type") val type: String = "other",
        @SerialName("content") val content: String = "",
        @SerialName("keywords") val keywords: String = "",
        @SerialName("confidence") val confidence: Float = 1f,
        @SerialName("replace_content") val replaceContent: String? = null,
        @SerialName("visibility") val visibility: String = "shared",
        @SerialName("top_secret") val topSecret: Boolean = false,
    )

    /** 提取 JSON 解析器：忽略未知字段，容忍 AI 输出不规范 */
    private val json = Json { ignoreUnknownKeys = true }

    /** 记忆类型中文标签（提取 prompt 中展示已有记忆类型用） */
    private val MemoryType.label: String
        get() = when (this) {
            MemoryType.PERSONAL_INFO -> "个人信息"
            MemoryType.PREFERENCE -> "偏好"
            MemoryType.EVENT -> "事件"
            MemoryType.SKILL -> "技能"
            MemoryType.SITUATION -> "情景"
            MemoryType.RELATION -> "关系"
            MemoryType.OTHER -> "其他"
        }

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

    /** 轻量表变化信号（T9.5）：记忆条数变化即发，管理页用它触发分页重查（避免全量加载行） */
    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

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
     * 管理页分页检索（阶段一统一检索入口，T9.3/T9.5）。
     * 实际检索逻辑委托 MemorySearchService（统一入口：FTS 精确 + LIKE 兜底），
     * 本方法按「搜索词 + 角色 + 分类」筛选并翻页，避免全量进内存。
     * @param mapType 分类（null=全部）；manualOnly=true 表示「手动/未归属」筛选
     */
    suspend fun searchPaged(
        query: String,
        cardId: Long?,
        manualOnly: Boolean = false,
        mapType: MemoryType? = null,
        page: Int = 0,
        pageSize: Int = 20,
    ): List<MemoryItem> = searchService.searchPaged(query, cardId, manualOnly, mapType, page, pageSize)

    /** 管理页筛选计数（T9.3：分类 Tab 数量 / 分页总数），条件与 searchPaged 一致 */
    suspend fun countFiltered(
        query: String,
        cardId: Long?,
        manualOnly: Boolean = false,
        mapType: MemoryType? = null,
    ): Int = searchService.countFiltered(query, cardId, manualOnly, mapType)

    /**
     * 按归属角色卡取未删除记忆（T6.13 按角色卡隔离注入）。
     * 注入不再依赖用户输入分词 LIKE 匹配（中文整句几乎无法命中），
     * 直接取该角色卡名下高置信度/最近的记忆注入，保证「AI 记得用户告知过的事实」。
     */
    suspend fun getActiveByCard(cardId: Long, limit: Int = DEFAULT_MEMORY_INJECT_LIMIT): List<MemoryItem> =
        dao.getActiveByCard(cardId, limit)

    /** 取某角色卡的共享记忆（T7.4 会话记忆面板「共享区」） */
    suspend fun getSharedByCard(cardId: Long, limit: Int = DEFAULT_MEMORY_INJECT_LIMIT): List<MemoryItem> =
        dao.getSharedByCard(cardId, limit)

    /** 取某会话的私有记忆（T7.4 会话记忆面板「私有区」；T7.5 检索注入私有侧） */
    suspend fun getPrivateByConversation(conversationId: Long, limit: Int = DEFAULT_MEMORY_INJECT_LIMIT): List<MemoryItem> =
        dao.getPrivateByConversation(conversationId, limit)

    /**
     * 取可注入到 system prompt 的记忆（T7.5 会话级私有定稿）：
     * = 该角色全部共享记忆 + 当前会话自己的私有记忆。合并去重。
     */
    suspend fun getInjectableMemories(cardId: Long, conversationId: Long, limit: Int = DEFAULT_MEMORY_INJECT_LIMIT): List<MemoryItem> {
        val shared = dao.getSharedByCard(cardId, limit)
        val privates = dao.getPrivateByConversation(conversationId, limit)
        // 合并去重后走统一检索服务的 top-K 排序（置顶 > 重要 > 更新时间，T9.3），保证注入的是当前最该被记得的
        return searchService.rankByRelevance((shared + privates).distinctBy { it.id }, limit)
    }

    /**
     * 定位打听目标会话 B（v1.2 跨会话打听，T8.1）。
     * 按打听主题拆词、检索各会话的记忆概况，返回「可能知道该话题」的候选会话 id 列表。
     * @param terms 打听主题拆词（用户输入按逗号/空格拆分）
     * @param excludeConversationId 发起会话 A 自身（排除，避免向自己打听）
     * @return 候选会话 id，按「命中记忆数」降序（信息命中多者优先；MVP 先按此，后续再优化）
     */
    suspend fun locateTargetConversationIds(terms: List<String>, excludeConversationId: Long?): List<Long> {
        if (terms.isEmpty()) return emptyList()
        val freq = LinkedHashMap<Long, Int>()
        terms.filter { it.isNotBlank() }.forEach { word ->
            dao.findSourceConversationIdsByKeyword(word).forEach { cid ->
                if (cid != excludeConversationId) freq[cid] = (freq[cid] ?: 0) + 1
            }
        }
        return freq.entries.sortedByDescending { it.value }.map { it.key }
    }

    // ---------- 写入 ----------

    /** 手动添加记忆（T5.5 管理页）：置信度默认 1.0（用户明确填写）；T7.3 可指定可见性（默认共享）；T8.2 可标记绝密 */
    suspend fun addManual(type: MemoryType, content: String, keywords: String, visibility: MemoryVisibility = MemoryVisibility.SHARED, isTopSecret: Boolean = false): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            MemoryItem(
                type = type,
                content = content.trim(),
                keywords = keywords.trim(),
                confidence = 1f,
                visibility = visibility,
                isTopSecret = isTopSecret,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * 取某角色卡「可向外打捞」的记忆（v1.2 旁路暗访，T8.2）：
     * 仅该角色卡的【共享且非绝密】记忆——绝密记忆不注入目标会话，B 根本不知道，故无法外泄（硬性兜底）。
     * @return 可被旁路打听引用的记忆列表
     */
    suspend fun getConsultableMemories(cardId: Long, limit: Int = DEFAULT_MEMORY_INJECT_LIMIT): List<MemoryItem> =
        dao.getSharedByCard(cardId, limit).filter { !it.isTopSecret }

    /** 软删除记忆（T5.5 管理页，二次确认后调用；不物理删除，可回溯） */
    suspend fun softDelete(id: Long) = dao.softDeleteById(id)

    // ---------- 提取（T5.3） ----------

    /**
     * 每轮对话后异步提取长期记忆（企划书 3.5.2）。
     * 输入=用户消息+AI 回复；调 API 返回结构化 JSON 数组；入库前查冲突。
     * @param userContent 用户消息
     * @param aiContent AI 回复
     * @param cardId 归属角色卡 id（T6.13 按角色卡隔离；null 则不归类）
     * @param conversationId 来源会话 id（v1.2 P2 修复：记忆标注来源会话，供跨会话打听定位 T8.1 + 会话私有隔离 T7.4）
     * 全程不抛错：任何失败都静默返回（记忆提取是附加能力，不影响主对话）。
     */
    suspend fun extractAndStore(userContent: String, aiContent: String, cardId: Long?, conversationId: Long? = null) {
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

        // 1. 组装提取 prompt：要求输出 JSON 数组，只提取明确信息、不编造；
        //    并带上本角色已有记忆，让 AI 识别「用户更正覆盖了哪条旧记忆」(T6.13.2)
        // 取该角色已有的未删除记忆（含旧记忆原文，供覆盖判定）
        val existingMemories = cardId?.let { dao.getActiveByCard(it, DEFAULT_MEMORY_INJECT_LIMIT) } ?: emptyList()
        val prompt = buildString {
            append("你是记忆提取助手。请从下面的对话中，提取关于用户的值得长期记住的信息。\n")
            append("规则：只提取对话中明确出现的个人信息、偏好或重要事件；不要编造；不要提取一次性寒暄。\n")
            append("可见性规则（重要）：对每条记忆判断可见性——\n")
            append("  若用户明确要求保密/仅对当前角色说（如「别告诉别人」「你知我知」「这是我的秘密」），或属于私密心事，标 \"visibility\": \"private\"；\n")
            append("  一般事实（姓名/喜好/事件等）标 \"visibility\": \"shared\"。\n")
            append("绝密规则（v1.2 绝密兜底）：若用户明确要求「绝对保密/绝不可让任何人知道/死了都要瞒着」（比一般私密更强硬的保密，如「绝对不能说出去」「谁都不能告诉」），除标 private 外，再加 \"top_secret\": true。只有一般私密（可对亲近者说）不标 top_secret。\n")
            if (existingMemories.isNotEmpty()) {
                append("该角色已有记忆（原文+类型）：\n")
                existingMemories.forEach { m -> append("  - ").append(m.content).append("（").append(m.type.label).append("）\n") }
                append("规则补充：如果新信息是对某条已有记忆的「更正/覆盖」（同一事实被用户改了，如喜欢颜色从紫色改成蓝色），\n")
                append("请在对应新条目加字段 \"replace_content\": \"被覆盖的已有记忆的原文\"。仅同一事实被更正时使用，一般性补充不要加。\n\n")
            }
            append("输出格式：仅输出 JSON 数组，不要任何额外文字。每项格式：\n")
            append("[{\"type\":\"personal_info\"|\"preference\"|\"event\"|\"other\",\"content\":\"一句话内容\",\"keywords\":\"关键词1,关键词2\",\"confidence\":0.0~1.0,\"replace_content\":\"可选，被覆盖的旧记忆原文\",\"visibility\":\"shared\"|\"private\",\"top_secret\":true或省略}]\n")
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
            upsertWithConflictCheck(item, content, cardId, conversationId, existingMemories)
        }
    }

    /** 冲突检查入库：可覆盖旧记忆、或内容相似后合并更新，否则新增（企划书 3.5.2 / T6.13.2） */
    private suspend fun upsertWithConflictCheck(
        item: ExtractedMemory,
        content: String,
        cardId: Long?,
        conversationId: Long?,
        existingMemories: List<MemoryItem>,
    ) {
        val type = runCatching { MemoryType.valueOf(item.type.uppercase()) }.getOrDefault(MemoryType.OTHER)
        // 可见性解析（T7.2）：非法值兜底为共享；绝密标记透传（T8.2）
        val visibility = runCatching { MemoryVisibility.valueOf(item.visibility.uppercase()) }.getOrDefault(MemoryVisibility.SHARED)
        val topSecret = item.topSecret
        val now = System.currentTimeMillis()

        // 0. 显式覆盖：用户更正的条目带了 replace_content → 找到对应旧记忆并改为新内容（T6.13.2 修「记忆还是紫色」）
        val replaceContent = item.replaceContent?.trim()
        if (!replaceContent.isNullOrBlank()) {
            val target = existingMemories.firstOrNull {
                it.content == replaceContent || replaceContent.contains(it.content) || it.content.contains(replaceContent)
            }
            if (target != null && !target.isDeleted) {
                dao.update(
                    target.copy(
                        type = type,
                        content = content,
                        keywords = item.keywords,
                        confidence = maxOf(target.confidence, item.confidence),
                        characterCardId = cardId,
                        visibility = visibility,
                        isTopSecret = topSecret,
                        updatedAt = now,
                    ),
                )
                return
            }
        }

        // 1. 冲突判定：关键词任一命中已有记忆的 keywords 或 content，视为同一条记忆
        val candidateKeywords = item.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val existing = candidateKeywords.firstNotNullOfOrNull { kw ->
            dao.searchByKeyword(kw).firstOrNull()
        }

        if (existing != null) {
            // 2. 冲突：更新内容与置信度（取更高者），刷新时间；归属角色卡同步为新卡（T6.13）
            dao.update(
                existing.copy(
                    type = type,
                    content = content,
                    keywords = item.keywords,
                    confidence = maxOf(existing.confidence, item.confidence),
                    characterCardId = cardId,
                    visibility = visibility,
                    isTopSecret = topSecret,
                    updatedAt = now,
                ),
            )
        } else {
            // 3. 新增记忆：标注来源会话（P2 修复，供跨会话打听定位 + 会话私有隔离）
            dao.insert(
                MemoryItem(
                    type = type,
                    content = content,
                    keywords = item.keywords,
                    confidence = item.confidence,
                    characterCardId = cardId,
                    sourceConversationId = conversationId,
                    visibility = visibility,
                    isTopSecret = topSecret,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }
}
