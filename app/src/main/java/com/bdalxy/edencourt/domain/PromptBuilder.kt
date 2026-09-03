package com.bdalxy.edencourt.domain

import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.local.entity.UserIdentity
import com.bdalxy.edencourt.data.remote.ChatMessage
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Prompt 构建器（T3.6，参考企划书 3.4.2/3.5.1/3.6.2）。
 * 组装 messages=[system（角色卡+记忆）, 最近 N 条历史, 当前 user]。
 * 阶段 4 起：system 由会话绑定的真实角色卡生成（T4.6）；记忆注入留待阶段 5。
 */
object PromptBuilder {

    /** 默认 system 提示词（未绑定角色卡时的默认助手） */
    const val DEFAULT_SYSTEM_PROMPT =
        "你是「永恒庭院」里的 AI 助手，请用温暖、亲切、自然的中文语气回答用户的问题，保持简洁友好。"

    /** 短期记忆条数默认值（企划书 5.3.5 分区 2 默认 20；本阶段常量，后续可配置） */
    const val DEFAULT_SHORT_TERM_LIMIT = 20

    /** 长期记忆注入条数默认值（企划书 3.5.2：取 top-K，默认 3 条） */
    const val DEFAULT_MEMORY_LIMIT = 3

    /**
     * 由角色卡构建 system 提示词（T4.6，参考企划书 3.4.2）。
     * 拼接规则：「你是{name}。你的性格：{personality}…请始终以该角色身份回复」，
     * 并附加背景/说话风格/示例对话/约束（有则拼入）。
     * @param card 会话绑定的角色卡
     * @param userIdentityText 用户自设身份注入片段（T8.13，可为空），所有角色默认知道用户
     * @return system 提示词文本
     */
    fun buildCardSystemPrompt(card: CharacterCard, userIdentityText: String = ""): String = buildString {
        append("你是「").append(card.name).append("」。\n")
        append("你的性格：").append(card.personality).append("\n")
        if (card.background.isNotBlank()) {
            append("你的背景：").append(card.background).append("\n")
        }
        if (card.speakingStyle.isNotBlank()) {
            append("你的说话风格：").append(card.speakingStyle).append("\n")
        }
        if (card.exampleDialogue.isNotEmpty()) {
            append("参考示例对话：\n")
            card.exampleDialogue.forEach { d ->
                append("用户：「").append(d.user).append("」\n")
                append("你：「").append(d.assistant).append("」\n")
            }
        }
        if (card.constraints.isNotBlank()) {
            append("行为约束：").append(card.constraints).append("\n")
        }
        // T8.13：注入用户自设身份（统一称呼用户），所有角色默认知道
        if (userIdentityText.isNotBlank()) {
            append("\n").append(userIdentityText)
        }
        append("请始终以「").append(card.name).append("」的身份、用上述风格回复用户，不要跳出角色。")
    }

    /**
     * 构建「会话独有人格」注入片段（T13.5 角色差异化/自补充方案）。
     * 让每个会话角色在对话中表现「这一份」独有的偏好与人设差异，同一张卡的不同会话角色因此言行有区别：
     * ① 印象摘要（identityBlurb）——第三人称概括，强化角色自我认同与可区分特征；
     * ② 表面特质（平时）→ 依恋度 ≥ 阈值时流露隐藏特质（反差对，§9-A 数值联动优先）；
     * ③ 自补充引导——未知偏好按性格推断并自述，沉淀记忆保持一致（T13.7 闭环入口）。
     * @param traits 会话独有人格记录；null（默认助手/老会话无 traits）返回空串，不改变既有 prompt
     * @param attachmentThreshold 反差触发阈值（依恋度 ≥ 此值允许流露隐藏特质，默认 65）
     * @return 注入片段文本（为空则不注入，完全兼容旧行为）
     */
    fun buildTraitsPrompt(
        traits: ConversationTraits?,
        attachmentThreshold: Int = DEFAULT_ATTACHMENT_THRESHOLD,
    ): String {
        if (traits == null) return ""
        return buildString {
            append("【你的独有气质】\n")
            // ① 印象摘要：强化角色此刻的自我认知与可区分特征
            if (traits.identityBlurb.isNotBlank()) {
                append("别人眼中的你：").append(traits.identityBlurb).append("\n")
            }
            // ② 反差对：表面特质平时外显；依恋度达标时允许流露隐藏特质
            if (traits.contrastSurface.isNotBlank()) {
                append("你平时给人的感觉是").append(traits.contrastSurface).append("。")
                if (traits.contrastInner.isNotBlank()) {
                    if (traits.attachment >= attachmentThreshold) {
                        // 依恋度关键技术：关系亲近时，才在特定情境流露反差面（§9-A 数值联动优先）
                        append("但你们已经很亲近，当")
                            .append(traits.contrastTrigger.ifBlank { "某些情境" })
                            .append("时，你会自然流露出不一样的自己：")
                            .append(traits.contrastInner)
                            .append("。")
                    } else {
                        // 未达阈值：仅在内心，克制不外露（反差尚未浮现）
                        append("但内心深处你其实").append(traits.contrastInner).append("，只是现在还不完全展露。")
                    }
                }
                append("\n")
            }
            // ③ 自补充引导：未知偏好按性格合理推断并自述，沉淀的记忆保持一致（T13.7）
            append("对用户没有提到过的你的个人偏好（如喜欢的音乐、食物、生活方式），")
                .append("请按你的性格合情合理地自我设定，并在对话中自然地表明；")
                .append("一旦你定了说法，后续就一直保持一致，不要前后矛盾。")
        }.trim()
    }

    /** 反差触发阈值默认值（依恋度 ≥ 此值允许流露隐藏特质，对应 6 维 attachment，T13.5） */
    const val DEFAULT_ATTACHMENT_THRESHOLD = 65

    /**
     * 构建「用户自设身份」注入片段（T8.13）。
     * 所有角色默认知道用户身份：统一的称呼/名字/自设描述，随角色卡 persona 注入，保证全角色统一称呼用户。
     * @param identity 用户自设身份（唯一一条，id=1）
     * @return 注入片段文本（恒非空，调用方可直接拼接）
     */
    fun buildUserIdentityPrompt(identity: UserIdentity): String = buildString {
        append("【关于用户】你认识用户「").append(identity.name).append("」，你会称呼他为「").append(identity.calling).append("」。\n")
        if (identity.description.isNotBlank()) append("用户的自述：").append(identity.description).append("\n")
        if (identity.personality.isNotBlank()) append("用户的性格：").append(identity.personality).append("\n")
        if (identity.background.isNotBlank()) append("用户的背景：").append(identity.background).append("\n")
    }

    /**
     * 构建请求消息列表。
     * @param history 该会话历史消息（按时间正序）
     * @param userInput 当前用户输入（连发攒批时多条以换行合并为一次「来信」，T6.9）
     * @param shortTermLimit 短期记忆条数：只取最近 N 条历史（默认 20）
     * @param systemPrompt system 提示词（默认助手；绑定角色卡时传 buildCardSystemPrompt 结果）
     * @param longTermMemories 长期记忆文本（已检索到的「[关于用户的长期记忆]- …」片段，
     *   拼接到 system 末尾供 AI 自然提及；为空则不影响，默认空串，T5.4）
     * @param userBatchCount 本次来信合并的用户消息条数（>1 表示连发攒批，
     *   在 system 中提示 AI 按性格决定逐条/综合/分组回应，T6.9）
     * @param lifeStateText 当前角色生活状态注入片段（T11.11：睡眠/晨起/活跃/晚间，影响语气与内容；为空则不注入）
     * @param relationshipEventsText 共同经历事件注入片段（T11.13：本会话与其他会话角色共同经历过的事，
     *   让对话有「我们经历过」的连续性；为空则不注入）
     * @param watchModeText 陪看模式注入片段（T11.16：用户主动分享内容时，角色以陪看口吻回应；为空则不注入）
     * @param traitsPrompt 会话独有人格注入片段（T13.5：印象摘要+反差对+自补充引导；为空则不注入）
     * @param affinityText 用户↔角色亲密度注入片段（A1：角色按亲疏自然调整口吻；为空则不注入）
     * @param moodText 角色此刻情绪注入片段（A2：自主/交互双源合成，反哺语气；为空则不注入）
     * @param worldInfoText 世界书命中条目注入片段（A3：聊到相关关键词才注入对应条目；为空则不注入）
     * @param constantWorldInfoText 世界书恒定条目（constant=true）注入片段（v1.5 上下文注入层③恒久世界规则；
     *   每次必注入的核心世界规则；为空则不注入）
     * @param budget 上下文注入预算统一分配器（v1.5：按优先级链 ①~⑤ 分配并可配；传 null=不启用预算截断，旧行为完全不变）
     */
    fun build(
        history: List<Message>,
        userInput: String,
        shortTermLimit: Int = DEFAULT_SHORT_TERM_LIMIT,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        longTermMemories: String = "",
        userBatchCount: Int = 1,
        lifeStateText: String = "",
        relationshipEventsText: String = "",
        watchModeText: String = "",
        traitsPrompt: String = "",
        affinityText: String = "",
        moodText: String = "",
        worldInfoText: String = "",
        constantWorldInfoText: String = "",
        budget: ContextBudget? = null,
    ): List<ChatMessage> {
        // 1. system 消息。按「优先级链」组装（v1.5 上下文注入层）：
        //   ①时间（忽略预算）→ ②恒久身份(systemPrompt+traitsPrompt) → ③恒久世界规则(constantWorldInfoText)
        //   → ④当下状态(lifeState+watchMode+affinity+mood) → ⑤相关性知识(关系事件→长期记忆→世界书命中)。
        //   budget 非空时，对⑤弹性块按 remaining=flexible 统一截断（整块保留/丢弃，不中途切字）。
        val finalSystem = buildString {
            // ① 环境·当前现实时间（T6.11：每次发送时刷新，让 AI 感知三次元时间）
            append("【当前现实时间】").append(currentTimeText()).append("\n\n")
            // ② 恒久身份：角色卡核心 + 用户自设身份
            append(systemPrompt)
            // ② 恒久身份：会话独有人格——印象摘要+反差对+自补充引导（T13.5 角色差异化）
            if (traitsPrompt.isNotBlank()) {
                append("\n\n").append(traitsPrompt)
            }
            // ③ 恒久世界规则：世界书 constant=true 条目——核心世界规则每次必注入，不随关键词命中浮动
            if (constantWorldInfoText.isNotBlank()) {
                append("\n\n").append(constantWorldInfoText)
            }
            // ④ 当下状态（固定短句，开销自负）：生活状态（T11.11）+ 陪看（T11.16）+ 亲密度（A1）+ 情绪（A2）
            if (lifeStateText.isNotBlank()) {
                append("\n\n【当前角色状态】").append(lifeStateText)
            }
            if (watchModeText.isNotBlank()) {
                append("\n\n").append(watchModeText)
            }
            if (affinityText.isNotBlank()) {
                append("\n\n").append(affinityText)
            }
            if (moodText.isNotBlank()) {
                append("\n\n").append(moodText)
            }
            // ⑤ 相关性知识（弹性配额）：优先关系事件→长期记忆→世界书命中；预算充足则全注入，不足按此序截断
            if (budget != null) {
                val remainingIds = budget.truncate(
                    listOf(relationshipEventsText, longTermMemories, worldInfoText).filter { it.isNotBlank() },
                    maxToken = budget.flexibleBudget,
                )
                // 按优先级顺序拼接被保留的块（truncate 保持原顺序）
                val joined = remainingIds.joinToString("\n\n")
                if (joined.isNotBlank()) append("\n\n").append(joined)
            } else {
                // 未传 budget：维持旧拼接行为（关系事件→世界书→长期记忆）
                if (relationshipEventsText.isNotBlank()) {
                    append("\n\n").append(relationshipEventsText)
                }
                if (worldInfoText.isNotBlank()) {
                    append("\n\n").append(worldInfoText)
                }
                if (longTermMemories.isNotBlank()) {
                    append("\n\n").append(longTermMemories)
                }
            }
            // T6.9：连发多条时提示 AI 像真人一样按性格决定回应方式（不逐条秒回）
            if (userBatchCount > 1) {
                append("\n\n【对话提醒】用户刚连续发来了 ")
                    .append(userBatchCount)
                    .append(" 条消息（见下一条用户消息，多条以换行分隔）。")
                    .append("请像真人聊天一样，按你的性格决定回应方式：可以逐条回应、综合回应，或把有关联的合起来回、无关的分开回。")
            }
        }
        val system = ChatMessage(role = "system", content = finalSystem)

        // 2. 最近 N 条历史（超长时只保留窗口内消息，控制上下文长度）
        val recent = history.takeLast(shortTermLimit).map { msg ->
            ChatMessage(
                role = if (msg.role == Role.USER) "user" else "assistant",
                content = msg.content,
            )
        }

        // 3. 当前用户输入
        val user = ChatMessage(role = "user", content = userInput)

        return listOf(system) + recent + user
    }

    /**
     * 当前现实时间文本（T6.11）：如「2026年8月24日 星期一 14:30」。
     * 每次构建 Prompt 时调用，保证 AI 拿到的永远是当前时间。
     */
    private fun currentTimeText(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm", Locale.CHINESE))
}
