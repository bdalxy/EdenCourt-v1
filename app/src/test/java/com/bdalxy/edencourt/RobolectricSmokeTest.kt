package com.bdalxy.edencourt

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * UI 检查流水线——版本兼容验证用的最小冒烟测试。
 *
 * 目的：验证 Robolectric（Roborazzi 的底层依赖）在 AGP 8.7.0 / Kotlin 2.0.20 / JDK17 组合下能否正常启动，
 * 同时验证 roborazzi 依赖可被解析（已声明 testImplementation，编译本测试时其 jar 必须能下载成功）。
 *
 * 注意：项目 targetSdk=36，而 Robolectric 4.14 上限为 SDK 35，故此处显式 @Config(sdk=[34])，
 * 避免 Robolectric 尝试按 targetSdk 36 触发不支持的 SDK。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricSmokeTest {

    @Test
    fun smoke_robolectric_boots() {
        // 最小断言：Robolectric 沙箱能启动并拿到 Application，即证明整条环境链路通
        assertNotNull(RuntimeEnvironment.getApplication())
    }
}