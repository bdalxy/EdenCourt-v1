package com.bdalxy.edencourt.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bdalxy.edencourt.data.setting.ScheduleConfig

/**
 * 用户↔会话角色的关系「方向」轴（v2.0 关系引擎 R1）。
 * 与 affinityScore（浓度/亲疏档位）正交：方向表达"这段关系偏什么性质"，浓度表达"有多亲近"。
 * - NONE=初始未定；更亲密的方向（如文爱类）因「无限制基调」不硬编码，交 AI 自然表达、方向字段不设限。
 * - 仅作内部状态，默认不在 UI 主动展示（用户 2026-09-04：现实里关系也多是默认、不需外显）。
 */
enum class RelationDirection {
    NONE,        // 未定/初始
    FRIEND,      // 朋友
    COMRADE,     // 同好
    AMBIGUOUS,   // 暧昧
    LOVER,       // 恋人·网恋
    INTIMATE,    // 深交
}

/**
 * 会话表（企划书 4.1.1 / 4.2.1）。
 * characterCardId 为空表示默认助手；lastMessageTime 为空表示尚无消息。
 * code 建会话随机短码（v1.2 会话唯一身份组成部分，T8.0）。
 * T11.10 深化：lifeEnabled + 自定义作息（起床/入睡）= 会话级作息（每个会话角色单独调整）。
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"], orders = [Index.Order.DESC])],
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    /** 会话备注别名（T6.14 用户自定义显示名；空=用 title，类微信备注） */
    @ColumnInfo(defaultValue = "''")
    val remark: String = "",
    val characterCardId: Long? = null,
    /** 建会话随机短码（v1.2 会话唯一身份，T8.0） */
    val code: String = newShortCode(),
    /** 是否开放参与角色间互动（v1.2b 互动资格开关，T8.7；新建默认 false=不参与互动） */
    @ColumnInfo(defaultValue = "0")
    val interactionEnabled: Boolean = false,
    /** 是否开启本会话角色的生活作息（T11.10 深化二级开关；默认 false=不显示「此刻状态」） */
    @ColumnInfo(defaultValue = "0")
    val lifeEnabled: Boolean = false,
    /** 会话级自定义作息·起床时刻（T11.10 深化；null=跟随角色卡/默认） */
    val scheduleWakeUpMinute: Int? = null,
    /** 会话级自定义作息·入睡时刻（T11.10 深化；null=跟随角色卡/默认） */
    val scheduleSleepMinute: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val lastMessagePreview: String = "",
    val lastMessageTime: Long? = null,
    /** 用户与本会话角色的亲密度（A1，0~100，随对话自然非线性增长；用于反哺对话口吻） */
    @ColumnInfo(defaultValue = "0")
    val affinityScore: Int = 0,
    /** 用户与本会话角色的关系「方向」轴（v2.0 关系引擎 R1，默认 NONE 未定；仅内部状态，不主动展示） */
    @ColumnInfo(defaultValue = "'NONE'")
    val relationDirection: String = RelationDirection.NONE.name,
    /** 候选方向（R3：AI 最近一次判断建议的方向，枚举名；未到切换阈值前仅是候选，不生效） */
    @ColumnInfo(defaultValue = "'NONE'")
    val candidateDirection: String = RelationDirection.NONE.name,
    /** 候选方向连续命中次数（R3：判到同候选 +1，判回当前方向 -1 且≥0，判到新方向重置为 1） */
    @ColumnInfo(defaultValue = "0")
    val candidateCount: Int = 0,
    /** 最近一次方向判断时间戳（R3：触发频率"距上次判断"判定用；null=尚未判断过） */
    val candidateLastSeen: Long? = null,
    /** 上次方向切换时间戳（R3：切换冷却期判定用；null=尚未切换过） */
    val lastDirectionSwitch: Long? = null,
    /** 本会话挂载的世界书 id（A3，null=未挂载；第一版一个会话最多挂 1 本） */
    val worldBookId: Long? = null,
    /** 是否开启关系图谱（B4：会话级图谱开关；true=该会话角色出现在「我」的下方关系图中，并与其它开启角色成两两行）。「我」本身恒开启无需此字段 */
    @ColumnInfo(defaultValue = "0")
    val graphEnabled: Boolean = false,
    /** 是否为群聊会话（v2.0 A6-1：true=群聊，可挂多个参与者；false=普通 1v1 会话） */
    @ColumnInfo(defaultValue = "0")
    val isGroup: Boolean = false,
) {
    /**
     * 该会话手动设置的自定义作息（T11.10 深化：会话级）。
     * 两列全为 null = 未单独设置（返回 null，跟随角色卡/默认作息）。
     */
    val customSchedule: ScheduleConfig?
        get() = if (scheduleWakeUpMinute == null && scheduleSleepMinute == null) {
            null
        } else {
            ScheduleConfig(
                wakeUpMinute = scheduleWakeUpMinute ?: ScheduleConfig.DEFAULT_WAKE_UP,
                sleepMinute = scheduleSleepMinute ?: ScheduleConfig.DEFAULT_SLEEP,
            )
        }
}
