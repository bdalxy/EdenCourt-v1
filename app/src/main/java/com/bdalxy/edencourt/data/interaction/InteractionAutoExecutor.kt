package com.bdalxy.edencourt.data.interaction

import android.util.Log
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.InteractionSource
import com.bdalxy.edencourt.data.local.entity.MemoryImportance
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.repository.RelationshipRepository
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.schedule.scheduleTrait
import com.bdalxy.edencourt.data.setting.InteractionConfigStore
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import kotlin.random.Random

/**
 * 互动自动驱动执行器（v1.2b 互动系统，T11.7）。
 * 在每日窗口内被定时广播触发：从「已开放互动」的会话中两两配对，
 * 过滤掉冷却期内已自动互动过的对，优先选「距上次互动最久 / 从未互动」的一对（像角色忽然想起久未联系的朋友），
 * 按配置概率决定是否执行，最终复用 InteractionExecutor.start() 跑一场完整双向互动。
 * T11.11：角色当前处于「睡眠中」时不参与自动串门（真人深夜也不会去串门）。
 * T11.12：升级为「事件+动机」驱动——优先为每对从双方记忆里挑一件「事」（用户提过的主题/约定）作「因由」，
 *         开场白注入该因由（可解释「因为…」），定时+概率降级为兜底；同一件事冷却期内不重复驱动同一对。
 * - 用户角度：不打扰（无 push/不注入正文打断），结果沉淀为互动记录 + 双方会话各一条摘要 + 好感度照常累计（T11.6）。
 */
class InteractionAutoExecutor(
    private val conversationDao: ConversationDao,
    private val interactionDao: InteractionDao,
    private val relationshipRepository: RelationshipRepository,
    private val interactionConfigStore: InteractionConfigStore,
    private val interactionExecutor: InteractionExecutor,
    // T11.11：作息配置存储 + 角色卡 DAO，用于判断角色是否处于睡眠状态
    private val scheduleConfigStore: ScheduleConfigStore,
    private val characterCardDao: CharacterCardDao,
    // T11.12：记忆仓库，用于挑选「因由」事件候选
    private val memoryRepository: MemoryRepository,
) {

    /** 同一对会话自动互动的最小间隔（天）：避免同一对角色频繁被自动撮合，显机械 */
    private val cooldownDays = 3

    /** 因由去重窗口（天）：同一件事在该窗口内不重复当由头（T11.12）。
     * 必须大于配对冷却期——否则能成为候选（=冷却期内无 AUTO 记录）时去重查询恒为 0，去重永不生效；
     * 用独立更长的窗口，才能在冷却期过后避免「换汤不换药」地反复拿同一条记忆当由头。 */
    private val motiveCooldownDays = 7

    /** 事件候选：每方最多取多少条记忆作为「事」素材（T11.12） */
    private val eventMemoryLimit = 5

    /** 「因由」注入长度上限（避免把整段记忆塞进开场白，取前 40 字足够点题） */
    private val motiveMaxLen = 40

    /** 自动驱动执行结果（供接收器日志/提示） */
    data class AutoOutcome(
        val success: Boolean,
        val message: String = "",
    )

    /**
     * 执行一次自动驱动。
     * @param forcedProbability 调试通道（force=true）传 1f 强制触发，跳过概率判定；null 时用配置的 autoProbability。
     */
    suspend fun runOnce(forcedProbability: Float? = null): AutoOutcome {
        // 1. 候选：已开放互动的会话（interactionEnabled=true，T8.7 资格开关）
        //    T11.11：过滤掉「此刻正在睡觉」的角色（睡眠中不串门）
        val convs = conversationDao.observeAll().first()
            .filter { it.interactionEnabled }
            .filterNot { isSleeping(it) }
        if (convs.size < 2) return AutoOutcome(false, "可互动会话不足 2 个，或角色们此刻都在休息，暂无法自动串门")
        val now = System.currentTimeMillis()
        val cooldownBefore = now - cooldownDays * 24L * 3600_000L

        // 2. 两两配对 + 过滤冷却期 + 记录「距上次互动时间」+ 为该对挑选「因由」（T11.12 事件候选）
        data class Candidate(val a: Long, val b: Long, val lastInteractionAt: Long, val motive: String?)
        val candidates = mutableListOf<Candidate>()
        for (i in convs.indices) {
            for (j in i + 1 until convs.size) {
                val a = convs[i]
                val b = convs[j]
                // 冷却期内已自动互动过 → 跳过（避免同一对频繁被自动撮合）
                val lastAuto = interactionDao.lastAutoTimeBetween(a.id, b.id)
                if (lastAuto != null && lastAuto >= cooldownBefore) continue
                // 距上次（任意）互动时间：从未互动过 = -1（最优先被想起）
                val lastAny = relationshipRepository.getBetween(a.id, b.id)?.lastInteractionAt ?: -1L
                // T11.12：从双方记忆里挑「最该被记得、且去重窗口内未用过」的一件事作为因由；无可用则 null（走定时兜底）
                val motive = pickMotive(a, b, now - motiveCooldownDays * 24L * 3600_000L)
                candidates.add(Candidate(a.id, b.id, lastAny, motive))
            }
        }
        if (candidates.isEmpty()) return AutoOutcome(false, "所有角色对都在冷却期内，今天没有可自动互动的对象")

        // 3. 排序（T11.12 事件优先）：有「因由」的事件候选排前（久未联系者先被想起），无因由的定时兜底排后
        candidates.sortWith(compareByDescending<Candidate> { it.motive != null }.thenBy { it.lastInteractionAt })
        val pick = candidates.first()

        // 4. 概率判定：force（调试）跳过概率；平时按配置概率（默认 30%）
        val prob = forcedProbability ?: interactionConfigStore.config.first().autoProbability
        if (Random.nextFloat() > prob) return AutoOutcome(false, "角色们今天没有串门的兴致（概率未命中）")

        // 5. 执行一场双向互动（复用现有执行器：多轮 LLM + 摘要 + 好感度累计），标记来源为自动驱动，事件驱动时携带因由
        return interactionExecutor.start(pick.a, pick.b, source = InteractionSource.AUTO, motive = pick.motive).let {
            if (it.success) {
                Log.d(TAG, "自动互动完成 #${it.interactionId}（因由=${pick.motive?.take(16)}）")
                AutoOutcome(true, "自动互动完成")
            } else {
                AutoOutcome(false, it.message)
            }
        }
    }

    /**
     * 为该对会话挑选「因由」（T11.12 事件候选生成器）。
     * 取双方「可注入记忆」（该角色共享 + 本会话私有，排除绝密），按「置顶 > 重要 > 更新时间」排序，
     * 返回第一条「冷却期内未用过」的记忆内容（截断到 motiveMaxLen）作为开场由头；
     * 若双方都没有可用记忆，返回 null（该对走定时兜底）。
     */
    private suspend fun pickMotive(a: Conversation, b: Conversation, since: Long): String? {
        val memories = mutableListOf<MemoryItem>()
        a.characterCardId?.let { cid -> memories += memoryRepository.getInjectableMemories(cid, a.id, eventMemoryLimit) }
        b.characterCardId?.let { cid -> memories += memoryRepository.getInjectableMemories(cid, b.id, eventMemoryLimit) }
        val ranked = memories
            .filter { !it.isTopSecret }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<MemoryItem> { it.isPinned }
                    .thenByDescending { it.importance == MemoryImportance.IMPORTANT }
                    .thenByDescending { it.updatedAt },
            )
        for (m in ranked) {
            val motiveText = m.content.trim().take(motiveMaxLen)
            if (motiveText.isBlank()) continue
            // 同一件事在去重窗口内已驱动过这对 → 换下一条（去重升级）
            if (interactionDao.countMotiveUsedBetween(a.id, b.id, motiveText, since) > 0) continue
            return motiveText
        }
        return null
    }

    /**
     * 判断会话绑定的角色当前是否处于「睡眠中」（T11.11）。
     * 需两级开关（全局 + 本会话）都开启才按作息判断，否则状态体系未生效、不算睡眠；
     * 生效作息按 resolveConfig 会话级解析，与聊天页「此刻状态」一致。
     */
    private suspend fun isSleeping(conv: Conversation): Boolean {
        if (!scheduleConfigStore.enabled.first()) return false
        if (!conv.lifeEnabled) return false
        val card = conv.characterCardId?.let { characterCardDao.getById(it) } ?: return false
        val cfg = CharacterSchedule.resolveConfig(scheduleConfigStore.config.first(), card, conv)
        return CharacterSchedule.computeState(cfg, LocalTime.now()) == LifeState.SLEEPING
    }

    private companion object {
        const val TAG = "InteractionAuto"
    }
}
