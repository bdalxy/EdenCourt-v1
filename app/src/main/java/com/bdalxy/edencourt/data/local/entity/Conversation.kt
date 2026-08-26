package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bdalxy.edencourt.data.setting.ScheduleConfig

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
    val remark: String = "",
    val characterCardId: Long? = null,
    /** 建会话随机短码（v1.2 会话唯一身份，T8.0） */
    val code: String = newShortCode(),
    /** 是否开放参与角色间互动（v1.2b 互动资格开关，T8.7；新建默认 false=不参与互动） */
    val interactionEnabled: Boolean = false,
    /** 是否开启本会话角色的生活作息（T11.10 深化二级开关；默认 false=不显示「此刻状态」） */
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
