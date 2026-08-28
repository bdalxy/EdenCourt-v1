package com.bdalxy.edencourt

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 核心链路 UI 冒烟（方案 G-③ Phase3，可达性 smoke）。
 * 目标：核心页面/入口在真实 UI 上可到达、可返回、不崩溃——不依赖具体业务数据。
 * 覆盖链路：会话列表 → 记忆页 → 互动中心 → 设置页 → 聊天页（新建对话→选默认助手）。
 *
 * 执行注意（红线）：instrumented 测试，绝不在带正式数据的真机跑 `connectedDebugAndroidTest`
 * （AGP 会卸载重装正式包）。仅在模拟器/干净设备执行，或 `assembleDebugAndroidTest` 只编译验证。
 */
@RunWith(AndroidJUnit4::class)
class CoreChainSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** 模拟物理返回键（走 onBackPressedDispatcher，逐级回退，Compose 导航栈会正确处理） */
    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun coreChain_reachesAllPages() {
        // 0. 首启引导兜底：干净设备首次启动会弹 OnboardingGate，出现则点「跳过」进入主界面
        if (composeRule.onAllNodesWithText("跳过").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("跳过").performClick()
            composeRule.waitForIdle()
        }

        // 1. 会话列表页可达（App 根页面）
        composeRule.onNodeWithText("对话").assertIsDisplayed()

        // 2. 记忆页可达 + 返回
        composeRule.onNodeWithContentDescription("记忆管理").performClick()
        composeRule.onNodeWithText("记忆").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("对话").assertIsDisplayed()

        // 3. 互动中心可达 + 返回
        composeRule.onNodeWithContentDescription("互动中心").performClick()
        composeRule.onNodeWithText("角色互动").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("对话").assertIsDisplayed()

        // 4. 设置页可达（顶栏菜单 → 设置）+ 返回
        composeRule.onNodeWithContentDescription("菜单").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("对话").assertIsDisplayed()

        // 5. 聊天链路可达：新建对话 → 选「默认助手」→ 聊天页输入区（发送按钮）可见 → 返回
        composeRule.onNodeWithContentDescription("新建对话").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("默认助手").performClick()
        // 新建会话为异步 Room 写入，等待进入聊天页（发送按钮出现）
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithContentDescription("发送").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("发送").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithText("对话").assertIsDisplayed()
    }
}
