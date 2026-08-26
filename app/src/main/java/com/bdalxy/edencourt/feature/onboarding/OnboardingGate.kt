package com.bdalxy.edencourt.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bdalxy.edencourt.EdenCourtApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 单步引导内容：图标 + 主标题 + 说明文案（只指向已有功能，T11.14） */
private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/** 引导步骤：你是谁 → 她们是谁 → 能做什么 → 她们的节奏（T11.15 增补：指向已落地的此刻状态/自动驱动/关系故事） */
private val ONBOARDING_STEPS = listOf(
    OnboardingStep(
        icon = Icons.Filled.Person,
        title = "先认识一下自己",
        body = "这是你与她们相处的世界。\n在「设置」里可以设定你的名字与身份，她们会以此称呼你。",
    ),
    OnboardingStep(
        icon = Icons.Filled.Star,
        title = "她们是谁",
        body = "光羽、阿杰、小夏……\n每位角色都有各自的性格、记忆与作息。点右下角「+」选一位，开启第一段对话。",
    ),
    OnboardingStep(
        icon = Icons.Filled.Favorite,
        title = "能做什么",
        body = "她们会记住你说过的话，可在「记忆管理」查看；\n没有你时，她们之间也会自然往来，可在「互动中心」回顾。",
    ),
    OnboardingStep(
        icon = Icons.Filled.Home,
        title = "她们有自己的节奏",
        body = "她们会按时起床入睡、工作小憩，「此刻状态」写在会话卡片上；\n你不在时，她们也会彼此走动（互动中心可设「自动驱动」），共同经历会记进「关系故事」。",
    ),
)

/**
 * 首启引导门（T11.14）：首次启动展示 2~3 步引导，看完或跳过都标记「已看过」，之后不再弹。
 * 未读取到状态前先展示内容，避免首帧闪烁。
 */
@Composable
fun OnboardingGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EdenCourtApp
    // 是否已看过引导：null 表示尚未读到存储值（加载中）
    var seen by remember { mutableStateOf<Boolean?>(null) }
    // 协程作用域：标记「已看过」落盘用
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        seen = app.onboardingConfigStore.config.first().seen
    }

    val s = seen
    if (s == null || s) {
        // 未读到（防闪烁）或已看过 → 直接展示正文
        content()
    } else {
        OnboardingDialog(
            onFinish = {
                // 看完或跳过都标记已看过，保证只弹一次
                scope.launch { app.onboardingConfigStore.markSeen() }
                seen = true
            },
        )
    }
}

/** 首启引导全屏弹窗：三步分页，底部「跳过 / 下一步 / 开始使用」 */
@Composable
private fun OnboardingDialog(onFinish: () -> Unit) {
    // 当前步索引（0~2）
    var step by remember { mutableIntStateOf(0) }
    val current = ONBOARDING_STEPS[step]
    val isLast = step == ONBOARDING_STEPS.lastIndex

    Dialog(
        onDismissRequest = { /* 引导期不允许点击外部关闭，只能显式跳过 */ },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 步骤指示点（当前步高亮）
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ONBOARDING_STEPS.forEachIndexed { index, _ ->
                        val active = index == step
                        Surface(
                            modifier = Modifier.size(if (active) 10.dp else 8.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = MaterialTheme.shapes.small,
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 步骤图标（柔和主题色）
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Icon(
                        imageVector = current.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(22.dp),
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 主标题
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 说明文案
                Text(
                    text = current.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.4f,
                )

                Spacer(modifier = Modifier.weight(1f))

                // 底部操作区：跳过 / 下一步 / 开始使用
                if (isLast) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("开始使用", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Button(
                        onClick = { step += 1 },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("下一步", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("跳过", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
