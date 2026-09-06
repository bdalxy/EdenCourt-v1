package com.bdalxy.edencourt.data.debug

import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.local.dao.MessageDao
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.repository.ChatRepository
import kotlinx.coroutines.delay

/**
 * 性能压测种子数据生成器（M3「千条数据 60fps」验收前提，仅 debug 构建由设置页「开发者工具」区调用）。
 * 本实现只存在于 debug 源集（release 构建不含本类）；main 侧通过 SeedDataGenerator 接口解耦，
 * 由 DebugEdenCourtApp 注入到 EdenCourtApp.seedDataGenerator。
 * - 只新建独立测试会话【压测·N】，生成千条消息/记忆，不触碰任何正式会话数据。
 * - 消息/记忆均分批落库 + 每批让出一帧，避免长事务期间主线程卡顿。
 * 用途：真机实测 聊天页长会话滚动（G-① 窗口分页）与 记忆页分页 在千条规模下的流畅度。
 */
class SeedDataTool(
    private val chatRepository: ChatRepository,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
) : SeedDataGenerator {

    /** 批大小：每次入库条数（一次性事务，控制单次时长与内存峰值） */
    private val batchSize = 200

    /**
     * 生成一组压测种子数据：
     * - 新建一个测试会话（无角色卡=默认助手），插入 messageCount 条历史消息（用户/助手交替）
     * - 额外插入 memoryCount 条记忆（归属该测试会话，便于后续识别/清理）
     * @return 生成的会话 id（失败抛异常由调用方提示）
     */
    override suspend fun generate(
        tag: Int,
        messageCount: Int,
        memoryCount: Int,
    ): Long {
        // 1. 新建一次性测试会话（标题【压测】便于识别与清理）
        val convId = chatRepository.createConversation(characterCardId = null)
        val base = System.currentTimeMillis()

        // 2. 千条历史消息：用户/助手交替，内容有序便于肉眼核对滚动
        (0 until messageCount).chunked(batchSize).forEach { batch ->
            messageDao.insertAll(
                batch.map { i ->
                    Message(
                        conversationId = convId,
                        role = if (i % 2 == 0) Role.USER else Role.ASSISTANT,
                        content = if (i % 2 == 0) {
                            "压测用户消息 #$i"
                        } else {
                            "压测助手回复 #$i —— 用于验证千条消息滚动流畅度（G-① 窗口分页）。"
                        },
                        createdAt = base + i, // 时间递增保证顺序稳定
                    )
                },
            )
            delay(5) // 每批让出一帧，防止一次性占满主线程
        }

        // 3. 千条记忆：类型轮转，内容有序，便于验证记忆页分页/检索
        (0 until memoryCount).chunked(batchSize).forEach { batch ->
            memoryDao.insertAll(
                batch.map { i ->
                    val type = MemoryType.entries[i % MemoryType.entries.size]
                    MemoryItem(
                        type = type,
                        content = "压测记忆 #$i（$type）——用于验证千条记忆分页检索流畅度。",
                        keywords = "压测,条目$i",
                        characterCardId = null,
                        sourceConversationId = convId,
                        createdAt = base + i + 1_000_000,
                        updatedAt = base + i + 1_000_000,
                    )
                },
            )
            delay(5)
        }
        return convId
    }
}
