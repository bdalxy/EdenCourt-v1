package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bdalxy.edencourt.data.setting.ScheduleConfig

/**
 * 生成会话/角色卡的全局唯一随机短码（v1.2 会话唯一身份底座，T8.0）。
 * - 建档/建会话时生成，作为该个体"独一无二身份验证"的一部分
 * - 取 UUID 连续 12 位十六进制，碰撞概率极低
 */
internal fun newShortCode(): String =
    java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)

/**
 * 角色卡表（企划书 4.1.3 / 4.2.3，T4.2）。
 * - jsonContent 保存角色卡完整 JSON（可回溯、可再编辑后整体回写）
 * - isBuiltIn=true 表示内置卡（不可删除，见 T4.3）
 * - name 建索引便于按名检索
 * - code 建档随机短码（v1.2 会话唯一身份组成部分，T8.0）
 */
@Entity(
    tableName = "character_cards",
    indices = [Index(value = ["name"])],
)
data class CharacterCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 角色名（内置卡索引名） */
    val name: String,
    /** 备注别名（T6.14 用户自定义显示名；空=用 name，类微信备注） */
    val remark: String = "",
    /** 简介（列表页展示用一句话描述） */
    val description: String = "",
    /** 角色卡完整 JSON（CharacterCard 序列化结果） */
    val jsonContent: String,
    /** 自定义头像本地文件路径（T6.2B；null=使用默认首字头像） */
    val avatar: String? = null,
    /** 立绘大图本地文件路径（B3：置顶大卡背景用；null=暂无立绘，用主题色+头像占位） */
    val artwork: String? = null,
    /** 是否置顶（B3：置顶卡独占顶部横滑大卡区，取消置顶回落普通网格；0=否） */
    val isPinned: Boolean = false,
    /** 是否内置卡（内置卡不可删除） */
    val isBuiltIn: Boolean = false,
    /** 建档随机短码（v1.2 会话唯一身份，T8.0） */
    val code: String = newShortCode(),
    /** 创建时间（毫秒时间戳） */
    val createdAt: Long = System.currentTimeMillis(),
    /** 更新时间（毫秒时间戳，编辑后刷新） */
    val updatedAt: Long = System.currentTimeMillis(),
    /** 自定义作息·起床时刻（T11.10 深化；null=跟随默认，见 customSchedule） */
    val scheduleWakeUpMinute: Int? = null,
    /** 自定义作息·入睡时刻 */
    val scheduleSleepMinute: Int? = null,
    /** 自定义作息·工作开始时刻 */
    val scheduleWorkStartMinute: Int? = null,
    /** 自定义作息·工作结束时刻 */
    val scheduleWorkEndMinute: Int? = null,
    /** 自定义作息·午休开始时刻 */
    val scheduleBreakStartMinute: Int? = null,
    /** 自定义作息·午休结束时刻 */
    val scheduleBreakEndMinute: Int? = null,
) {
    /**
     * 该角色手动设置的自定义作息（T11.10 深化：全简化版，仅起床+入睡两列）。
     * 两列全为 null = 未单独设置（返回 null，跟随默认：基础作息+个性偏移）；
     * 否则用已设置时刻 + 默认值兜底构造成完整 ScheduleConfig。
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
