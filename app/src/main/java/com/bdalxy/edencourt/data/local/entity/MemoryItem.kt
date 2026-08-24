package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 记忆类型（企划书 4.1.4：personal_info/preference/event/other）。 */
enum class MemoryType {
    /** 个人信息（如用户名字、职业、所在地） */
    PERSONAL_INFO,
    /** 偏好（如喜欢喝奶茶、讨厌吃香菜） */
    PREFERENCE,
    /** 事件（如「上周提到要去杭州出差」） */
    EVENT,
    /** 其他（未归类） */
    OTHER,
}

/** 记忆可见性（v1.1 底座，用于角色套话/群聊的前置分层，默认共享）。
 * SHARED=一般事实，将来可被其他角色「套话」得知；PRIVATE=该角色专属（私密心事），仅本角色可见。 */
enum class MemoryVisibility {
    /** 共享：可被其他角色通过套话得知（一般事实） */
    SHARED,
    /** 私有：仅记住它的角色自己可见（私密/保密内容） */
    PRIVATE,
}

/**
 * 记忆条目表（企划书 4.1.4 / 4.2.4，T5.1）。
 * - keywords 逗号分隔，供关键词检索注入（T5.4）
 * - confidence 置信度 0~1，仅高置信度条目入库（T5.3）
 * - isDeleted 软删除：管理页删除仅标记，不物理删，保证可回溯
 * - visibility 可见性（T7.1）：共享/私有，v1.1 新增，默认 SHARED；区分度低不建索引
 * - 索引：type / keywords / isDeleted（企划书 4.1.4）
 */
@Entity(
    tableName = "memory_items",
    indices = [
        Index(value = ["type"]),
        Index(value = ["keywords"]),
        Index(value = ["isDeleted"]),
        Index(value = ["characterCardId"]),
    ],
)
data class MemoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 记忆类型（个人信息/偏好/事件/其他） */
    val type: MemoryType = MemoryType.OTHER,
    /** 记忆内容（自然语言一句话） */
    val content: String,
    /** 关键词，逗号分隔（用于检索匹配） */
    val keywords: String = "",
    /** 置信度 0~1（提取模型给出，>= 阈值才入库） */
    val confidence: Float = 1f,
    /** 归属角色卡 id（T6.13 按角色卡隔离；null=旧数据/未归类） */
    val characterCardId: Long? = null,
    /** 可见性（v1.1 新增，T7.1）：共享/私有，默认共享；v2.x 套话/群聊的分层依据 */
    val visibility: MemoryVisibility = MemoryVisibility.SHARED,
    /** 来源会话 id（可空：手动添加无来源） */
    val sourceConversationId: Long? = null,
    /** 来源消息 id（可空） */
    val sourceMessageId: Long? = null,
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 更新时间（毫秒时间戳） */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 软删除标记（true=已删除，默认 false） */
    val isDeleted: Boolean = false,
)
