package com.bdalxy.edencourt.feature.chat

/**
 * 代际闸门（P1 竞态修复，企划书 3.6.4 并发控制）决策纯逻辑，供 JVM 单测。
 *
 * 背景：ChatViewModel 用单调递增的 generationCounter 为每次「新的生成」分配代际号；
 * streamReply 收到请求后先校验「本次请求的 generation 是否仍等于当前最新计数」，
 * 不相等说明期间又发起了更新的生成（重试/新消息），本次请求应直接作废，
 * 避免旧请求覆盖新结果（竞态）。
 *
 * 此处把该**无副作用**校验抽为纯函数 [isCurrent]，
 * 并把「分配下一个代际号」的逻辑收敛为 [next]，供调用方统一推进计数。
 */
object GenerationGate {

    /**
     * 本次请求是否为「当前最新代际」。
     * @param requestGeneration 请求发起时携带的代际号
     * @param currentGeneration 当前最新代际号（全局单调递增）
     * @return true 表示未被更新的请求覆盖，可继续执行；false 表示已过期，应作废
     */
    fun isCurrent(requestGeneration: Long, currentGeneration: Long): Boolean =
        requestGeneration == currentGeneration

    /**
     * 从当前代际推进出下一个代际号（自增 1）。
     * 调用方在「发起一次新生成」时调用，并把返回的号传给被发起的请求。
     */
    fun next(currentGeneration: Long): Long = currentGeneration + 1
}