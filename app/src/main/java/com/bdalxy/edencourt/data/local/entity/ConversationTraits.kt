package com.bdalxy.edencourt.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 会话角色「独有人格」表 conversation_traits（T13.1，角色差异化与自补充方案 v2）。
 * 同一张角色卡创建的多个会话角色，在同一人设维度范围内各自随机取值（建会话时生成一次，之后固定不漂移），
 * 形成"同一张卡、不同会话角色、多多少少不一样"的差异底座；反差对 + 印象摘要用于会话列表/聊天页的可视化区分。
 * 约束：conversationId 唯一（每会话至多一条）；characterCardId 冗余存卡 id 便于按卡检索。
 */
@Entity(
    tableName = "conversation_traits",
    indices = [Index(value = ["conversationId"], unique = true)],
)
data class ConversationTraits(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 所属会话 id（唯一，一个会话角色只生成一次人格） */
    val conversationId: Long,
    /** 来源角色卡 id（建会话时冗余，便于按卡分组/重生成） */
    val characterCardId: Long,
    // ===== 6 维人设数值（范围 0~100，建会话时在卡派生范围内随机落点，固定不漂移）=====
    /** 外向度：主动搭话 vs 被动回应 */
    val sociability: Int,
    /** 主动度：话题推进 vs 跟随 */
    val initiative: Int,
    /** 温度：热情 vs 冷淡 */
    val warmth: Int,
    /** 依恋度：亲近倾向 vs 距离感 */
    val attachment: Int,
    /** 幽默感：爱玩 vs 正经 */
    val humor: Int,
    /** 随性度：随意 vs 讲究 */
    val casualness: Int,
    // ===== 反差对（表面特质 vs 隐藏特质，特定情境流露另一面）=====
    /** 表面特质：平时给人的感觉（如「温柔体贴的学姐」） */
    val contrastSurface: String = "",
    /** 隐藏特质：特定情境下流露的另一面（如「很要强」） */
    val contrastInner: String = "",
    /** 触发情境描述（如「被误解或低估时」「熟络之后」） */
    val contrastTrigger: String = "",
    // ===== 印象摘要（会话级画像，第三人称概括，用于区分同卡多会话）=====
    /** 印象摘要：一段第三人称的概括（如「慢热·暖心｜平时温柔体贴，但初识话不多…」），生成失败降级为模板 */
    val identityBlurb: String = "",
    /** 印象标签：2~3 个标签，JSON 数组字符串（如 ["慢热","暖心"]），供会话列表标签行渲染 */
    val identityTags: String = "[]",
    /** 印象摘要生成来源：template=模板降级 / llm=大模型生成 / user=用户手动编辑（用户改后不被覆盖，T13.4） */
    val generatedBy: String = GENERATED_BY_TEMPLATE,
    /** 生成时间 */
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 印象摘要来源：模板降级生成 */
        const val GENERATED_BY_TEMPLATE = "template"
        /** 印象摘要来源：大模型生成 */
        const val GENERATED_BY_LLM = "llm"
        /** 印象摘要来源：用户手动编辑 */
        const val GENERATED_BY_USER = "user"
    }
}
