package com.bdalxy.edencourt.data.remote

/**
 * 流式生成「重试/节流」决策纯逻辑（T12.2 节流 + 企划书 3.6.4 429 退避抽离，供 JVM 单测）。
 *
 * 背景：ChatViewModel.streamReply 内联了「是否该重试 429」「退避多久」「是否该批量落库」三个
 * 决策，无法单测。此处把三个**无副作用**的决策抽为纯函数：
 *  1. [shouldRetry]：仅 RATE_LIMIT 且重试次数未达上限才重试
 *  2. [backoffDelayMs]：第 n 次退避等待 n 秒（1s、2s…，随重试次数递增）
 *  3. [shouldPersist]：距上次落库超过节流阈值才批量写（避免每 token 一次 Room 写）
 *
 * 三个函数均不触达 Room/DataStore/网络，输入→输出确定，可直接 JVM 断言，
 * 覆盖「429 退避至多 2 次」「非 429 不重试」「节流阈值内不落库/达标落库」等回归场景。
 */
object StreamPolicy {

    /** 429 最多重试次数（企划书 3.6.4：退避重试 1~2 次） */
    const val MAX_RATE_LIMIT_RETRIES = 2

    /** 流式写入节流阈值（T12.2：从每 token 1 次降到每 ~120ms 1 次） */
    const val DEFAULT_THROTTLE_MS = 120L

    /**
     * 是否应该重试本次请求。
     * 仅限 429（RATE_LIMIT）且已重试次数未达上限；网络/Auth/超时/服务端错误直接失败提示。
     */
    fun shouldRetry(kind: ApiErrorKind, attempt: Int): Boolean =
        kind == ApiErrorKind.RATE_LIMIT && attempt < MAX_RATE_LIMIT_RETRIES

    /** 第 attempt 次重试前的退避等待（毫秒）：1s、2s…随次数递增，避免加重服务端压力 */
    fun backoffDelayMs(attempt: Int): Long = 1000L * attempt

    /**
     * 是否到达批量落库时机。
     * @param now 当前单调时钟（elapsedRealtime）
     * @param lastPersistAt 上次落库时刻；0 表示尚未落库（首段直接落库）
     * @param throttleMs 节流阈值（默认 [DEFAULT_THROTTLE_MS]）
     */
    fun shouldPersist(now: Long, lastPersistAt: Long, throttleMs: Long = DEFAULT_THROTTLE_MS): Boolean =
        now - lastPersistAt >= throttleMs
}