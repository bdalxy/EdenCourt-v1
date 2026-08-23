package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.MessageType
import com.bdalxy.edencourt.data.local.entity.Role
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 聊天页（T2.7，参考企划书 5.3.2/5.8.2 原型）。
 * - 用户消息右气泡、AI 消息左气泡（样式区分）
 * - LazyColumn 消息按时间正序，新消息自动滚到底部
 * - 每条消息带时间戳
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as EdenCourtApp
    val viewModel: ChatViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatViewModel.factory(conversationId, app),
    )
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val title by viewModel.conversationTitle.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val errorMessages by viewModel.errorMessages.collectAsStateWithLifecycle()

    // 输入框内容状态（旋转/重建后保留）
    var input by rememberSaveable { mutableStateOf("") }

    // 滚动状态：消息变化时自动滚到底部（发送后即生效）
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title ?: "对话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        bottomBar = {
            // 底部输入栏：多行输入 + 发送（生成中切换为停止）
            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                isGenerating = isGenerating,
                onSend = {
                    viewModel.sendMessage(input)
                    input = ""
                },
                onStop = { viewModel.stopGenerating() },
            )
        },
    ) { innerPadding ->
        if (messages.isEmpty()) {
            // 空状态：无消息提示
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有消息，开始对话吧")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(items = messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        errorText = errorMessages[message.id],
                        onRetry = { viewModel.retry(message.id) },
                    )
                }
            }
        }
    }
}

/** 单条消息气泡：AI 靠左、用户靠右，带头像（AI）与时间戳；失败消息带错误提示与重试 */
@Composable
private fun MessageBubble(
    message: Message,
    errorText: String?,
    onRetry: () -> Unit,
) {
    val isUser = message.role == Role.USER
    // 思考过程折叠开关：AI 消息且存在思考过程时显示，默认折叠（T3.4 修正）
    var showReasoning by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        // AI 消息左侧显示圆形头像（后续角色卡阶段替换为角色头像）
        if (!isUser) {
            Surface(
                modifier = Modifier.size(36.dp).align(Alignment.Top),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "永",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 气泡内容（文字 + 时间戳 + 失败提示/重试）
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
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
                        // 生成中且尚无内容时显示「对方正在输入…」（T5.9 类微信体验）
                        if (message.status == MessageStatus.GENERATING && message.content.isBlank()) {
                            Text(
                                text = "对方正在输入…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
            // 失败消息：错误提示 + 重试按钮（T3.8）
            if (message.status == MessageStatus.FAILED) {
                Text(
                    text = errorText ?: "生成失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onRetry) {
                    Text("重试")
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

/** 底部输入栏：多行输入框（1~5 行自动扩展）+ 发送按钮；生成中额外显示停止按钮（T5.10） */
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 多行输入框：内容变多自动扩展，最多 5 行
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 生成中额外显示「停止」按钮（仅停止，不发送）；发送按钮始终存在，
            // 生成中点发送 = 打断当前回复并发送新消息（T5.10 双向可打断）
            if (isGenerating) {
                FilledIconButton(onClick = onStop) {
                    Icon(Icons.Filled.Close, contentDescription = "停止生成")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}
