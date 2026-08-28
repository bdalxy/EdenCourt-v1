package com.bdalxy.edencourt.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式生成「重试/节流」决策纯逻辑单测（G-③ 自动化回归·补聊天核心链路缺口）。
 * 覆盖企划书 3.6.4 的 429 退避重试（至多 2 次）与 T12.2 流式写入节流（每 ~120ms 一批）决策，
 * 防止重构 streamReply 状态机时回归。
 */
class StreamPolicyTest {

    // ---------- shouldRetry 分支 ----------

    @Test
    fun `RATE_LIMIT且未达上限_允许重试`() {
        assertTrue(StreamPolicy.shouldRetry(ApiErrorKind.RATE_LIMIT, 0))
        assertTrue(StreamPolicy.shouldRetry(ApiErrorKind.RATE_LIMIT, 1))
    }

    @Test
    fun `RATE_LIMIT但已达上限_不再重试`() {
        assertFalse(StreamPolicy.shouldRetry(ApiErrorKind.RATE_LIMIT, 2))
        assertFalse(StreamPolicy.shouldRetry(ApiErrorKind.RATE_LIMIT, 3))
    }

    @Test
    fun `非RATE_LIMIT错误_一律不重试_直接失败提示`() {
        assertFalse(StreamPolicy.shouldRetry(ApiErrorKind.NETWORK, 0))
        assertFalse(StreamPolicy.shouldRetry(ApiErrorKind.AUTH, 0))
        assertFalse(StreamPolicy.shouldRetry(ApiErrorKind.TIMEOUT, 0))
        assertFalse(StreamPolicy.shouldRetry(ApiErrorKind.SERVER, 0))
    }

    // ---------- backoffDelayMs 分支 ----------

    @Test
    fun `退避延迟随重试次数递增_第1次1秒第2次2秒`() {
        assertEquals(1000L, StreamPolicy.backoffDelayMs(1))
        assertEquals(2000L, StreamPolicy.backoffDelayMs(2))
    }

    // ---------- shouldPersist 分支 ----------

    @Test
    fun `距上次落库不足阈值_不落库`() {
        // 上次落库后仅 50ms：未达 120ms 阈值
        assertFalse(StreamPolicy.shouldPersist(now = 1050L, lastPersistAt = 1000L))
    }

    @Test
    fun `距上次落库刚好达到阈值_落库`() {
        // 恰好 120ms
        assertTrue(StreamPolicy.shouldPersist(now = 1120L, lastPersistAt = 1000L))
    }

    @Test
    fun `距上次落库超过阈值_落库`() {
        assertTrue(StreamPolicy.shouldPersist(now = 1300L, lastPersistAt = 1000L))
    }

    @Test
    fun `首次尚未落库_同样受节流阈值约束`() {
        // lastPersistAt=0 表示尚未落库：未满阈值（10ms<120ms）不落库，满阈值（130ms）才落库
        assertFalse(StreamPolicy.shouldPersist(now = 10L, lastPersistAt = 0L))
        assertTrue(StreamPolicy.shouldPersist(now = 130L, lastPersistAt = 0L))
    }

    @Test
    fun `自定义阈值生效`() {
        assertFalse(StreamPolicy.shouldPersist(now = 1050L, lastPersistAt = 1000L, throttleMs = 500L))
        assertTrue(StreamPolicy.shouldPersist(now = 1600L, lastPersistAt = 1000L, throttleMs = 500L))
    }
}