package com.bdalxy.edencourt.data.repository

import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.MessageDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.Message
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

    /** 新建会话，返回生成的会话 id */
    suspend fun createConversation(title: String = "新对话"): Long =
        conversationDao.insert(Conversation(title = title))

    /** 更新会话 */
    suspend fun updateConversation(conversation: Conversation) = conversationDao.update(conversation)

    /** 删除会话（消息由外键级联删除） */
    suspend fun deleteConversation(conversation: Conversation) = conversationDao.delete(conversation)

    // ---------- 消息 ----------

    /** 观察某会话的消息流（按创建时间正序），聊天页数据源 */
    fun observeMessages(conversationId: Long): Flow<List<Message>> =
        messageDao.observeByConversation(conversationId)

    /** 新增消息，返回生成的 id */
    suspend fun insertMessage(message: Message): Long = messageDao.insert(message)

    /** 更新消息（流式生成中逐段更新、失败置 FAILED 等） */
    suspend fun updateMessage(message: Message) = messageDao.update(message)

    /** 删除单条消息 */
    suspend fun deleteMessage(message: Message) = messageDao.delete(message)

    /** 按 id 取单条消息（一次性） */
    suspend fun getMessage(id: Long): Message? = messageDao.getById(id)
}
