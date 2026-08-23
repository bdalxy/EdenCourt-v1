package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.MessageType
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 聊天页（T2.7，参考企划书 5.3.2/5.8.2 原型）。
 * - 用户消息右气泡、AI 消息左气泡（样式区分）
 * - LazyColumn 消息按时间正序，新消息自动滚到底部
 * - 每条消息带时间戳
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    conversationId: Long,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as EdenCourtApp
    val viewModel: ChatViewModel = viewModel(
        key = "chat_$conversationId",
        factory = ChatViewModel.factory(conversationId, app),
    )
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    // T6.2A：顶部显示会话绑定角色卡（头像+角色名，企划书 5.3.2「返回+角色名+更多」）
    val roleCard by viewModel.roleCard.collectAsStateWithLifecycle()
    val roleName by viewModel.roleName.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val errorMessages by viewModel.errorMessages.collectAsStateWithLifecycle()

    // 输入框内容状态（旋转/重建后保留）
    var input by rememberSaveable { mutableStateOf("") }

    // 滚动状态：消息/错误提示/键盘弹出时自动滚到底部（T6.1 + T6.7 修复）
    // - 键盘弹出：以 IME 高度为 key，动画期间每次高度变化都重新定位到底部；
    //   动画结束高度稳定后最终定位一次，保证最新消息完整可见（避免视口压缩时旧 offset 停在中间）
    // - 消息/错误变化：平滑滚到底（animateScrollToItem），保留 AI 回复的跟随感
    val listState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // 键盘弹出（IME 高度 > 0）：滚动到列表底部，保证最新消息完整可见
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }
    // 消息/错误提示变化：平滑滚到底部（发送后/失败提示出现时重新定位，避免「重试」被输入栏遮挡）
    LaunchedEffect(messages.size, errorMessages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // T6.2A + T6.8：顶部左侧显示角色头像 + 角色名；下方小字显示「正在输入…」或角色状态（企划书 5.3.2）
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CharacterAvatar(
                                avatarPath = roleCard?.avatar,
                                name = roleName,
                                modifier = Modifier.size(32.dp),
                                textSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(roleName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        // T6.8：生成中显示「正在输入…」，否则显示角色当前状态（在线/睡觉中，轻量版）
                        Text(
                            text = if (isGenerating) "正在输入…" else roleStatusText(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 40.dp),
                        )
                    }
                },
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
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            ) {
                items(items = messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        error = errorMessages[message.id],
                        // T6.6：把会话绑定角色卡的头像/角色名传给气泡，AI 头像跟随角色卡（自定义/默认首字）
                        assistantAvatar = roleCard?.avatar,
                        assistantName = roleName,
                        onRetry = { viewModel.retry(message.id) },
                        onDismissError = { viewModel.dismissError(message.id) },
                        onOpenSettings = onOpenSettings,
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
    error: ChatError?,
    assistantAvatar: String?,
    assistantName: String,
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

/**
 * 角色当前状态文本（T6.8 轻量版，用户需求「角色状态（在线/睡觉中）」）。
 * 按时间段自动切换：6:00~22:59 为「在线」，其余为「睡觉中」；每分钟刷新一次。
 * 后续深入开发时可改为随性格/对话/角色卡属性驱动。
 */
@Composable
private fun roleStatusText(): String {
    var text by remember { mutableStateOf(currentRoleStatus()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            text = currentRoleStatus()
        }
    }
    return text
}

/** 计算当前角色状态：白天在线、深夜睡觉中（T6.8 轻量规则） */
private fun currentRoleStatus(): String {
    val hour = LocalTime.now().hour
    return if (hour in 6..22) "在线" else "睡觉中"
}

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
