package com.bdalxy.edencourt.data.interaction

import android.util.Log
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.entity.InteractionSource
import com.bdalxy.edencourt.data.setting.InteractionConfigStore
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/**
 * 互动自动驱动执行器（v1.2b 互动系统，T11.7）。
 * 在每日窗口内被定时广播触发：从「已开放互动」的会话中两两配对，
 * 过滤掉冷却期内已自动互动过的对，优先选「距上次互动最久 / 从未互动」的一对（像角色忽然想起久未联系的朋友），
 * 按配置概率决定是否执行，最终复用 InteractionExecutor.start() 跑一场完整双向互动。
 * - 用户角度：不打扰（无 push/不注入正文打断），结果沉淀为互动记录 + 双方会话各一条摘要 + 好感度照常累计（T11.6）。
 */
class InteractionAutoExecutor(
    private val conversationDao: ConversationDao,
    private val interactionDao: InteractionDao,
    private val relationshipDao: RelationshipDao,
    private val interactionConfigStore: InteractionConfigStore,
    private val interactionExecutor: InteractionExecutor,
) {

    /** 同一对会话自动互动的最小间隔（天）：避免同一对角色频繁被自动撮合，显机械 */
    private val cooldownDays = 3

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
        val convs = conversationDao.observeAll().first().filter { it.interactionEnabled }
        if (convs.size < 2) return AutoOutcome(false, "可互动会话不足 2 个，暂无法自动串门")
        val now = System.currentTimeMillis()
        val cooldownBefore = now - cooldownDays * 24L * 3600_000L

        // 2. 两两配对 + 过滤冷却期 + 记录「距上次互动时间」用于排序
        data class Candidate(val a: Long, val b: Long, val lastInteractionAt: Long)
        val candidates = mutableListOf<Candidate>()
        for (i in convs.indices) {
            for (j in i + 1 until convs.size) {
                val a = convs[i].id
                val b = convs[j].id
                // 冷却期内已自动互动过 → 跳过（避免同一对频繁被自动撮合）
                val lastAuto = interactionDao.lastAutoTimeBetween(a, b)
                if (lastAuto != null && lastAuto >= cooldownBefore) continue
                // 距上次（任意）互动时间：从未互动过 = -1（最优先被想起）
                val lastAny = relationshipDao.getBetween(a, b)?.lastInteractionAt ?: -1L
                candidates.add(Candidate(a, b, lastAny))
            }
        }
        if (candidates.isEmpty()) return AutoOutcome(false, "所有角色对都在冷却期内，今天没有可自动互动的对象")

        // 3. 优先选「距上次互动最久 / 从未互动」的一对（久未联系的老朋友最先被想起）
        candidates.sortBy { it.lastInteractionAt }
        val pick = candidates.first()

        // 4. 概率判定：force（调试）跳过概率；平时按配置概率（默认 30%）
        val prob = forcedProbability ?: interactionConfigStore.config.first().autoProbability
        if (Random.nextFloat() > prob) return AutoOutcome(false, "角色们今天没有串门的兴致（概率未命中）")

        // 5. 执行一场双向互动（复用现有执行器：多轮 LLM + 摘要 + 好感度累计），标记来源为自动驱动
        return interactionExecutor.start(pick.a, pick.b, source = InteractionSource.AUTO).let {
            if (it.success) {
                Log.d(TAG, "自动互动完成 #${it.interactionId}")
                AutoOutcome(true, "自动互动完成")
            } else {
                AutoOutcome(false, it.message)
            }
        }
    }

    private companion object {
        const val TAG = "InteractionAuto"
    }
}
