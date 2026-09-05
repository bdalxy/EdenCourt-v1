package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.MessageType
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 单条消息气泡：AI 靠左、用户靠右，带头像（AI）与时间戳；失败消息带错误提示与重试 */
@Composable
internal fun MessageBubble(
    message: Message,
    error: ChatError?,
    assistantAvatar: String?,
    assistantName: String,
    /** 群聊发送者显示名（v2.0 A6-5：非 null 时显示在气泡上方，1v1 会话为 null） */
    senderName: String? = null,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val isUser = message.role == Role.USER
    // 思考过程折叠开关：AI 消息且存在思考过程时显示，默认折叠（T3.4 修正）
    var showReasoning by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // AI 消息左侧显示角色头像（T6.6：跟随会话绑定角色卡，与顶部一致；未绑定显示默认助手）
        if (!isUser) {
            CharacterAvatar(
                avatarPath = assistantAvatar,
                name = assistantName,
                modifier = Modifier.size(36.dp).align(Alignment.Top),
                textSize = 14.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 气泡内容（文字 + 时间戳 + 失败提示/重试）
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            // v2.0 A6-5：群聊中显示发送者名（非用户消息 + senderName 非空）
            if (!isUser && !senderName.isNullOrBlank()) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                // 富类型渲染骨架（T5.7）：按消息类型分支渲染，当前仅 TEXT 完整实现，
                // ACTION/RESULT 预留样式，供远期 AI 设备控制接入
                when (message.type) {
                    MessageType.TEXT -> {
                        // T6.8：生成中空内容不再显示「对方正在输入…」气泡（提示已移至顶部），
                        // 渲染空占位保持消息位置稳定，避免连发时气泡消失造成跳动
                        if (message.status == MessageStatus.GENERATING && message.content.isBlank()) {
                            Text(
                                text = "",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        } else {
                            // 动作神态与正文分离（T5.8）：动作段用斜体浅色小字独立一行，正文保持纯对话
                            val parsed = remember(message.content) { ActionSegmentParser.parse(message.content) }
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                parsed.actions.forEach { action ->
                                    Text(
                                        text = action,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = if (isUser) {
                                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                                if (parsed.body.isNotBlank()) {
                                    Text(
                                        text = parsed.body,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isUser) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                    // 设备操作消息（预留骨架）：带操作图标 + 简短标题，正文为操作说明
                    MessageType.ACTION -> Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "🔧 ${message.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "操作待执行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    // 操作结果消息（预留骨架）：带状态色点 + 结果内容
                    MessageType.RESULT -> Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "✅ ${message.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "操作结果",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    // 互动摘要（v1.2b，T8.10）：角色间互动的一行摘要，居中浅色小字
                    MessageType.INTERACTION_DIGEST -> Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            // 思考过程折叠区：AI 消息且存在思考过程时展示（默认折叠，点击展开/收起）
            if (!isUser && message.reasoning.isNotBlank()) {
                TextButton(
                    onClick = { showReasoning = !showReasoning },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = if (showReasoning) "收起思考过程" else "思考过程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (showReasoning) {
                    Text(
                        text = message.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            // 失败消息：错误提示（红叹号 + 文案）+ 关闭/去设置/重试（T6.1 企划书 5.5.3）。
            // 同时校验 error 非空：用户关闭后整块隐藏，避免残留 fallback 文案
            if (message.status == MessageStatus.FAILED && error != null) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = error?.text ?: "生成失败",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    // 错误提示可关闭（不影响已发送内容）
                    TextButton(onClick = onDismissError, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("关闭")
                    }
                }
                Row {
                    // Key 无效时提供「去设置」入口（T6.1 企划书 5.5.3）
                    if (error?.kind == ApiErrorKind.AUTH) {
                        TextButton(onClick = onOpenSettings) {
                            Text("去设置")
                        }
                    }
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = formatTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 时间显示：HH:mm */
private fun formatTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))