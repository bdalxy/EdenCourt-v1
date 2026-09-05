package com.bdalxy.edencourt.feature.chat

import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationParticipantDao
import com.bdalxy.edencourt.data.local.dao.ConversationTraitsDao
import com.bdalxy.edencourt.data.local.dao.MoodDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.RelationDirection
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import com.bdalxy.edencourt.domain.CharacterCardParser
import com.bdalxy.edencourt.domain.MoodRules
import com.bdalxy.edencourt.domain.UserAffinity
import com.bdalxy.edencourt.domain.buildDirectionFragment
import kotlinx.coroutines.flow.first
import java.time.LocalTime

/**
 * 群聊多角色身份块组装器（v2.0 A6-2）。
 * 对群聊的每个参与者，读取其独立 1v1 会话中的亲密度/关系方向/情绪/作息/独有人格，
 * 拼装成每个成员的身份状态块，供 A6-3 群聊 prompt 使用。
 *
 * 与 ChatPromptAssembler 的关系：本类复用相同的依赖与组装逻辑，但针对"多个参与者"场景，
 * 对每个参与者独立读取状态，保证同卡多实例（同名不同会话）状态互不干扰。
 */
class GroupPromptAssembler(
    private val groupConversationId: Long,
    private val repository: ChatRepository,
    private val characterCardDao: CharacterCardDao,
    private val conversationTraitsDao: ConversationTraitsDao,
    private val moodDao: MoodDao,
    private val participantDao: ConversationParticipantDao,
    private val scheduleConfigStore: ScheduleConfigStore,
) {

    /** 一个群聊参与者的身份状态快照（供 prompt 组装用） */
    data class ParticipantBlock(
        /** 参与者对应的 1v1 会话 id */
        val conversationId: Long,
        /** 角色显示名（备注 > 角色卡名，同卡多实例加权区分） */
        val displayName: String,
        /** 角色卡名（原始名，同卡多实例时用于区分） */
        val cardName: String,
        /** 亲密度注入片段 */
        val affinityFragment: String,
        /** 关系方向注入片段 */
        val directionFragment: String,
        /** 情绪状态摘要 */
        val moodText: String,
        /** 生活状态文本 */
        val lifeStateText: String,
        /** 独有人格摘要 */
        val traitsSummary: String,
    )

    /**
     * 组装所有参与者的身份状态块列表。
     * 每个参与者以自己独立的 1v1 会话状态参与群聊——同卡多实例各自独立、互不干扰。
     */
    suspend fun assemble(): List<ParticipantBlock> {
        val participants = participantDao.getByConversation(groupConversationId)
        return participants.map { participant ->
            buildBlock(participant.participantConversationId)
        }
    }

    /** 为一个参与者会话构建身份状态块 */
    private suspend fun buildBlock(convId: Long): ParticipantBlock {
        val conv = repository.getConversation(convId)
        val cardEntity = conv?.characterCardId?.let { characterCardDao.getById(it) }
        val card = cardEntity?.let { runCatching { CharacterCardParser.parse(it.jsonContent) }.getOrNull() }
        val name = card?.name ?: "未知角色"
        val remark = cardEntity?.remark ?: ""

        // 显示名：会话备注 > 角色卡备注 > 角色卡名
        val displayName = conv?.remark?.takeIf { it.isNotBlank() }
            ?: remark.takeIf { it.isNotBlank() }
            ?: name

        // 生活状态计算一次，复用给情绪和状态文本
        val lifeState = computeLifeState(conv)

        return ParticipantBlock(
            conversationId = convId,
            displayName = displayName,
            cardName = name,
            affinityFragment = conv?.let { UserAffinity.buildAffinityFragment(it.affinityScore) }.orEmpty(),
            directionFragment = conv?.let {
                runCatching { RelationDirection.valueOf(it.relationDirection) }.getOrNull()
                    ?.let { d -> buildDirectionFragment(d) }
            }.orEmpty(),
            moodText = buildMoodText(convId, lifeState),
            lifeStateText = buildLifeStateText(lifeState),
            traitsSummary = buildTraitsSummary(convId),
        )
    }

    /** 读取参与者会话的情绪状态 */
    private suspend fun buildMoodText(convId: Long, lifeState: LifeState?): String {
        val mood = moodDao.get(convId)
        val baseline = MoodRules.baselineFor(lifeState)
        val resolved = MoodRules.resolve(mood, System.currentTimeMillis(), baseline)
        return "${MoodRules.tagName(resolved.tag)} · ${resolved.intensity}"
    }

    /** 读取参与者会话的独有人格摘要 */
    private suspend fun buildTraitsSummary(convId: Long): String {
        val traits = conversationTraitsDao.getByConversation(convId) ?: return ""
        return traits.identityBlurb.take(80)
    }

    /** 计算参与者当前生活状态 */
    private suspend fun computeLifeState(conv: Conversation?): LifeState? {
        if (conv == null || !conv.lifeEnabled) return null
        val cardEntity = conv.characterCardId?.let { characterCardDao.getById(it) } ?: return null
        val cfg = CharacterSchedule.resolveConfig(scheduleConfigStore.config.first(), cardEntity, conv)
        return CharacterSchedule.computeState(cfg, LocalTime.now())
    }

    /** 构建生活状态文本 */
    private fun buildLifeStateText(state: LifeState?): String = when (state) {
        LifeState.SLEEPING -> "睡眠中"
        LifeState.WAKING -> "刚起床"
        LifeState.ACTIVE -> "活跃"
        LifeState.EVENING -> "晚间放松"
        null -> ""
    }

    /** 为群聊 prompt 组装「所有成员身份块」文本（供 A6-3 使用） */
    fun buildGroupIdentityText(blocks: List<ParticipantBlock>): String = buildString {
        if (blocks.isEmpty()) return@buildString
        append("【群聊成员身份状态】\n")
        blocks.forEach { b ->
            append("- ${b.displayName}")
            append("：亲密度 ${b.affinityFragment.take(30)}")
            if (b.directionFragment.isNotBlank()) append("，${b.directionFragment.take(30)}")
            if (b.moodText.isNotBlank()) append("，情绪 ${b.moodText}")
            if (b.lifeStateText.isNotBlank()) append("，${b.lifeStateText}")
            append("\n")
        }
    }
}