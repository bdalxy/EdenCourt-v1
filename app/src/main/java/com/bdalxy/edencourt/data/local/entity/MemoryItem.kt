package com.bdalxy.edencourt.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 记忆类型（企划书 4.1.4 + 阶段一扩为 7 类，T9.1）。 */
enum class MemoryType {
    /** 个人信息（如用户名字、职业、所在地） */
    PERSONAL_INFO,
    /** 偏好（如喜欢喝奶茶、讨厌吃香菜） */
    PREFERENCE,
    /** 事件（如「上周提到要去杭州出差」） */
    EVENT,
    /** 技能（如「会调青蓝釉、Python 爬虫」） */
    SKILL,
    /** 情景（如特定场景/当下的氛围、场合记忆） */
    SITUATION,
    /** 关系（如「小夏是我的青梅竹马」） */
    RELATION,
    /** 情感（A4 新增：角色对用户的情感态度/亲密感受，如「小夏其实很在意我」。注入时优先，让角色口吻带温度） */
    EMOTION,
    /** 其他（未归类） */
    OTHER,
}

/** 记忆生命周期（阶段一新增，T9.1）。 */
enum class TimeScope {
    /** 短期记忆：可设到期时间（expiresAt），到期后不再注入 */
    SHORT_TERM,
    /** 长期记忆：常驻，无到期 */
    LONG_TERM,
}

/** 记忆重要程度（阶段一新增，T9.1；注入排序时优先）。 */
enum class MemoryImportance {
    /** 普通 */
    NORMAL,
    /** 重要（注入排前、置顶展示） */
    IMPORTANT,
}

/** 记忆来源类型（阶段一新增，T9.1）。 */
enum class MemorySourceType {
    /** 来自聊天对话提取 */
    CHAT,
    /** 用户手动编辑/添加 */
    USER_EDIT,
    /** 系统生成 */
    SYSTEM,
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
        // 阶段一组合索引（T9.1）：分类+生命周期+角色+可见性，支撑管理页/注入检索缩候选集
        Index(
            value = ["type", "timeScope", "characterCardId", "visibility"],
            name = "index_memory_type_scope",
        ),
    ],
)
data class MemoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 记忆类型（个人信息/偏好/事件/技能/情景/关系/其他） */
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
    @ColumnInfo(defaultValue = "'SHARED'")
    val visibility: MemoryVisibility = MemoryVisibility.SHARED,
    /** 绝密标记（v1.2 跨会话打听硬性兜底，T8.2）：true=绝不可被任何打听外泄。
     * 旁路暗访时绝密记忆不注入目标会话，目标根本不知道，故无法泄露（硬性保障）。默认 false。 */
    @ColumnInfo(defaultValue = "0")
    val isTopSecret: Boolean = false,
    /** 来源会话 id（可空：手动添加无来源） */
    val sourceConversationId: Long? = null,
    /** 来源消息 id（可空） */
    val sourceMessageId: Long? = null,
    /** 生命周期（阶段一新增，T9.1）：长期/短期 */
    @ColumnInfo(defaultValue = "'LONG_TERM'")
    val timeScope: TimeScope = TimeScope.LONG_TERM,
    /** 到期时间（阶段一新增，T9.1；短期记忆可设，到期后不再注入） */
    val expiresAt: Long? = null,
    /** 是否置顶（阶段一新增，T9.1；管理页置顶展示） */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    /** 重要程度（阶段一新增，T9.1；注入排序优先） */
    @ColumnInfo(defaultValue = "'NORMAL'")
    val importance: MemoryImportance = MemoryImportance.NORMAL,
    /** 是否锁定防误删（阶段一新增，T9.1；锁定后删除需先解锁） */
    @ColumnInfo(defaultValue = "0")
    val locked: Boolean = false,
    /** 来源类型（阶段一新增，T9.1）：聊天/手动/系统 */
    @ColumnInfo(defaultValue = "'CHAT'")
    val sourceType: MemorySourceType = MemorySourceType.CHAT,
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 更新时间（毫秒时间戳） */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 软删除标记（true=已删除，默认 false） */
    val isDeleted: Boolean = false,
)
