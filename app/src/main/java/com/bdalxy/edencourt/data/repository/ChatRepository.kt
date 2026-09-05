package com.bdalxy.edencourt.data.repository

import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.MessageDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.domain.UserAffinity
import kotlinx.coroutines.flow.Flow

/**
 * 聊天数据仓库（企划书 3.2.3 数据流 / T2.5）。
 * 作用：唯一的数据出口，UI/ViewModel 只依赖本类，不直接触碰 DAO。
 * 后续接入网络层（阶段 3）时，本类负责组合「本地缓存 + 远程 API」的数据流。
 */
class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {

    // ---------- 会话 ----------

    /** 观察全部会话（按更新时间降序），会话列表数据源 */
    fun observeConversations(): Flow<List<Conversation>> = conversationDao.observeAll()

    /** 观察单个会话（聊天页标题实时跟随） */
    fun observeConversation(id: Long): Flow<Conversation?> = conversationDao.observeById(id)

    /** 按 id 取单个会话（一次性） */
    suspend fun getConversation(id: Long): Conversation? = conversationDao.getById(id)

    /** 统计某角色卡下挂了多少个会话（互动执行器判断同卡多会话个体用） */
    suspend fun countConversationsByCard(cardId: Long): Int = conversationDao.countByCardId(cardId)

    /**
     * 新建会话，返回生成的会话 id（T4.7：支持绑定角色卡）。
     * @param characterCardId 绑定的角色卡 id（可空 = 默认助手；绑定规则见企划书 2.2.2）
     */
    suspend fun createConversation(characterCardId: Long? = null, title: String? = null, isGroup: Boolean = false): Long =
        conversationDao.insert(Conversation(characterCardId = characterCardId, title = title ?: "", isGroup = isGroup))

    /** 更新会话 */
    suspend fun updateConversation(conversation: Conversation) = conversationDao.update(conversation)

    /**
     * A1：推进用户↔本会话角色亲密度（非线性饱和增长）。
     * 读取当前 affinityScore，经 UserAffinity.advance 计算新值后写回；无变化（封顶）则不落库。
     * @param conversationId 目标会话
     * @param boost 深化加成（命中共同经历/记忆时由调用方判定传入，初版默认 0）
     */
    suspend fun advanceAffinity(conversationId: Long, boost: Int = 0) {
        val conv = conversationDao.getById(conversationId) ?: return
        val next = UserAffinity.advance(conv.affinityScore, boost)
        if (next != conv.affinityScore) {
            conversationDao.update(conv.copy(affinityScore = next))
        }
    }

    /**
     * R2：浓度回落——进入会话时根据距上次互动天数应用衰减。
     * 读取 lastMessageTime 计算间隔天数，经 UserAffinity.decay 计算新值后写回；无变化不落库。
     * 规则：>2 天开始缓降，每天 1~3 点，保底到当前档位起始值不破档。
     */
    suspend fun decayAffinity(conversationId: Long) {
        val conv = conversationDao.getById(conversationId) ?: return
        val lastTime = conv.lastMessageTime ?: return
        val daysSince = (System.currentTimeMillis() - lastTime) / (24 * 3600_000L)
        if (daysSince <= 2) return
        val next = UserAffinity.decay(conv.affinityScore, daysSince)
        if (next != conv.affinityScore) {
            conversationDao.update(conv.copy(affinityScore = next))
        }
    }

    /** 删除会话（消息由外键级联删除） */
    suspend fun deleteConversation(conversation: Conversation) = conversationDao.delete(conversation)

    // ---------- 消息 ----------

    /** 观察某会话的消息流（按创建时间正序），聊天页数据源 */
    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.observeByConversation(conversationId)

    /** 按会话一次性取全部消息（按时间正序，供确需全量历史的场景用，T8.2） */
    suspend fun getMessages(conversationId: Long): List<Message> =
        messageDao.getByConversation(conversationId)

    /** 定向取某会话「最近 limit 条」消息（时间正序，T12.6：数据库层 LIMIT，供旁路暗访/每日打听等只需最近上下文的场景） */
    suspend fun getRecentMessages(conversationId: Long, limit: Int): List<Message> =
        messageDao.getRecentByConversation(conversationId, limit)

    /** 统计某会话自给定时间戳之后新增的用户消息条数（R3 方向判断触发条件「满5条用户消息」用） */
    suspend fun countUserMessagesSince(conversationId: Long, since: Long): Int =
        messageDao.countMessagesSince(conversationId, Role.USER, since)

    /** 观察分页（消息按时间正序第 offset 起的 limit 条，T12.6 基础设施） */
    fun observePagedMessages(conversationId: Long, limit: Int, offset: Int): Flow<List<Message>> =
        messageDao.observePaged(conversationId, limit, offset)

    /** 新增消息，返回生成的 id */
    suspend fun insertMessage(message: Message): Long = messageDao.insert(message)

    /** 更新消息（流式生成中逐段更新、失败置 FAILED 等） */
    suspend fun updateMessage(message: Message) = messageDao.update(message)

    /** 删除单条消息 */
    suspend fun deleteMessage(message: Message) = messageDao.delete(message)

    /** 按 id 取单条消息（一次性） */
    suspend fun getMessage(id: Long): Message? = messageDao.getById(id)
}
