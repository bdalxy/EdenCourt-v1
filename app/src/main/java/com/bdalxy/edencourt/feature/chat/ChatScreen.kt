package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.core.ui.LifeStateIndicator
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.schedule.ScheduleTrait
import com.bdalxy.edencourt.data.schedule.scheduleTrait
import com.bdalxy.edencourt.data.local.entity.InteractionStatus
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.local.entity.Message
import com.bdalxy.edencourt.data.local.entity.MessageStatus
import com.bdalxy.edencourt.data.local.entity.MessageType
import com.bdalxy.edencourt.data.local.entity.Role
import com.bdalxy.edencourt.data.remote.ApiErrorKind
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.Locale
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
    onOpenInteraction: () -> Unit,
    onOpenMemoryManagement: () -> Unit,
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
    // T11.10：角色生活作息配置（顶部「此刻状态」按此计算，设置页改动实时联动）
    val scheduleConfig by viewModel.scheduleConfig.collectAsStateWithLifecycle()
    // T11.10 深化：全局作息开关 + 本会话实体（作息面板/状态显示条件）
    val scheduleEnabled by viewModel.scheduleEnabled.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    // v1.2b T8.11：顶部「互动」图标未读红点
    val unreadInteractions by viewModel.unreadInteractions.collectAsStateWithLifecycle()

    // v1.2b T8.11：进入会话即把本会话互动标记已读，消除顶部红点
    LaunchedEffect(conversationId) {
        viewModel.markInteractionsRead()
    }

    // T11.10 深化：聊天页顶部作息面板（开启该会话角色作息 + 单独调节起床/入睡）
    var showLifeSheet by rememberSaveable { mutableStateOf(false) }

    // 输入框内容状态（旋转/重建后保留）
    var input by rememberSaveable { mutableStateOf("") }

    // 会话记忆面板开关（T7.4：共享 + 私有两区）
    var showMemorySheet by rememberSaveable { mutableStateOf(false) }
    val sharedMemories by viewModel.sharedMemories.collectAsStateWithLifecycle()
    val privateMemories by viewModel.privateMemories.collectAsStateWithLifecycle()

    // v1.2b T8.11：互动面板（本会话互动记录 + 开放互动开关 + 前往互动中心）
    var showInteractionSheet by rememberSaveable { mutableStateOf(false) }
    val interactionEnabled by viewModel.interactionEnabled.collectAsStateWithLifecycle()
    val interactionRecords by viewModel.interactionRecords.collectAsStateWithLifecycle()
    // T11.6 好感度细分：本会话角色的关系一览（互动面板「我的关系（好感度）」区）
    val myRelationships by viewModel.myRelationships.collectAsStateWithLifecycle()

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
                        // T6.8 + T11.10：生成中显示「正在输入…」，否则显示角色作息状态（或未开启提示）
                        if (isGenerating) {
                            Text(
                                text = "正在输入…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 40.dp),
                            )
                        } else if (roleCard != null) {
                            // T11.10 深化：全局开关 && 会话开关都开启才显示「此刻状态」；否则提示未开启，点击进入作息面板
                            val lifeOn = scheduleEnabled && conversation?.lifeEnabled == true
                            if (lifeOn) {
                                LifeStateIndicator(
                                    state = roleLifeState(scheduleConfig, roleCard, conversation),
                                    modifier = Modifier
                                        .padding(start = 40.dp)
                                        .clickable { showLifeSheet = true },
                                )
                            } else {
                                Text(
                                    text = if (scheduleEnabled) "作息未开启 · 点此开启" else "角色作息未开启",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .padding(start = 40.dp)
                                        .clickable { showLifeSheet = true },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // v1.2b T8.11：互动入口（带未读红点），点击弹出本会话互动面板
                    IconButton(onClick = { showInteractionSheet = true }) {
                        BadgedBox(badge = {
                            if (unreadInteractions > 0) {
                                Badge { Text("${unreadInteractions.coerceAtMost(99)}") }
                            }
                        }) {
                            Icon(Icons.Filled.Email, contentDescription = "角色互动")
                        }
                    }
                    // T7.4：会话记忆入口（共享 + 本会话私有），点击先刷新数据再弹出
                    IconButton(onClick = {
                        showMemorySheet = true
                        viewModel.refreshSessionMemories()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "记忆")
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
    // T7.4：会话记忆面板（共享 + 本会话私有），点顶部「记忆」图标弹出
    if (showMemorySheet) {
        SessionMemorySheet(
            sharedMemories = sharedMemories,
            privateMemories = privateMemories,
            cardName = roleName,
            onOpenMemoryManagement = onOpenMemoryManagement,
            onDismiss = { showMemorySheet = false },
        )
    }
    // v1.2b T8.11：互动面板（本会话互动记录 + 开放互动开关 + 前往互动中心）
    if (showInteractionSheet) {
        InteractionSheet(
            roleName = roleName,
            enabled = interactionEnabled,
            onEnabledChange = { viewModel.setInteractionEnabled(it) },
            records = interactionRecords,
            relationships = myRelationships,
            onOpenCenter = {
                showInteractionSheet = false
                onOpenInteraction()
            },
            onDismiss = { showInteractionSheet = false },
        )
    }
    // T11.10 深化：作息面板（开启该会话角色作息 + 单独调节起床/入睡）
    if (showLifeSheet) {
        LifeScheduleSheet(
            roleName = roleName,
            globalEnabled = scheduleEnabled,
            enabled = conversation?.lifeEnabled == true,
            onEnabledChange = { viewModel.setConversationLife(it) },
            scheduleConfig = scheduleConfig,
            card = roleCard,
            conv = conversation,
            onScheduleChange = { viewModel.updateConversationLifeSchedule(it) },
            onDismiss = { showLifeSheet = false },
        )
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

/**
 * 角色「此刻状态」（T11.10 替代 T6.8 的「在线/睡觉中」二分标签）。
 * 按角色生效作息（ScheduleConfig）+ 真实时间计算多态生活状态；每分钟刷新一次。
 * T11.10 深化：经 resolveConfig 解析生效作息（会话自定义 > 角色自定义 > 个性偏移 > 基础）。
 */
@Composable
private fun roleLifeState(config: ScheduleConfig, card: CharacterCardEntity?, conv: Conversation?): LifeState {
    // 解析该角色最终生效作息（会话自定义 > 角色自定义 > 个性偏移 > 基础），保证与会话列表一致
    val resolved = CharacterSchedule.resolveConfig(config, card, conv)
    var state by remember { mutableStateOf(CharacterSchedule.computeState(resolved, LocalTime.now())) }
    LaunchedEffect(config, card, conv) {
        while (true) {
            state = CharacterSchedule.computeState(resolved, LocalTime.now())
            delay(60_000L)
        }
    }
    return state
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

/**
 * T7.4 + T11.6 会话记忆面板（会话级私有定稿 + 升级）：
 * - 「共享记忆」区：该角色卡的全部共享记忆（卡片式分区 + 计数角标）
 * - 「本会话私密记忆」区：仅本会话的私有记忆，跨会话隔离
 * - 顶部搜索框本地过滤两区；每条显示类型 chip + 内容 + 时间；底部「前往记忆管理页」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionMemorySheet(
    sharedMemories: List<MemoryItem>,
    privateMemories: List<MemoryItem>,
    cardName: String,
    onOpenMemoryManagement: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 搜索关键词：本地过滤共享/私有两区（按内容或关键词匹配）
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val sharedFiltered =
        if (q.isEmpty()) sharedMemories
        else sharedMemories.filter { it.content.contains(q) || it.keywords.contains(q) }
    val privateFiltered =
        if (q.isEmpty()) privateMemories
        else privateMemories.filter { it.content.contains(q) || it.keywords.contains(q) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "会话记忆 · $cardName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 搜索框
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索记忆…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 共享记忆分区卡片
            MemorySectionCard(
                title = "共享记忆 · $cardName",
                count = sharedFiltered.size,
                accent = false,
            ) {
                if (sharedFiltered.isEmpty()) {
                    EmptyMemoryText(if (q.isEmpty()) "暂无共享记忆" else "无匹配结果")
                } else {
                    sharedFiltered.forEach { MemorySheetItem(it) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 本会话私密记忆分区卡片
            MemorySectionCard(
                title = "本会话私密记忆",
                count = privateFiltered.size,
                accent = true,
            ) {
                if (privateFiltered.isEmpty()) {
                    EmptyMemoryText(if (q.isEmpty()) "本会话暂无私密记忆" else "无匹配结果")
                } else {
                    privateFiltered.forEach { MemorySheetItem(it) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 前往记忆管理页（搜索/分类/时间轴/热词/图谱全功能）
            TextButton(
                onClick = onOpenMemoryManagement,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往记忆管理页")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** 记忆分区卡片：标题 + 计数角标 + 内容区（T11.6 记忆面板布局升级，卡片式清晰分隔两区） */
@Composable
private fun MemorySectionCard(
    title: String,
    count: Int,
    accent: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                // 计数角标（区分共享/私有用主题色/错误色）
                Surface(
                    shape = CircleShape,
                    color = if (accent) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

/** 记忆面板空态提示 */
@Composable
private fun EmptyMemoryText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** 记忆面板单条：类型 chip + 内容 + 时间（T11.6 升级：类型标识 + 时间右置一行） */
@Composable
private fun MemorySheetItem(memory: MemoryItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 类型 chip：浅色圆角小标签
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            ) {
                Text(
                    text = memory.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatMemoryTime(memory.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(memory.content, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 记忆类型中文标签（与记忆管理页一致，供会话记忆面板类型 chip 用） */
private val MemoryType.label: String
    get() = when (this) {
        MemoryType.PERSONAL_INFO -> "个人信息"
        MemoryType.PREFERENCE -> "偏好"
        MemoryType.EVENT -> "事件"
        MemoryType.SKILL -> "技能"
        MemoryType.SITUATION -> "情景"
        MemoryType.RELATION -> "关系"
        MemoryType.OTHER -> "其他"
    }

/**
 * 互动面板（v1.2b T8.11，聊天页顶部「互动」图标弹出）。
 * - 「开放互动」开关：控制本会话能否成为互动成员（persist 到 conversations.interactionEnabled）
 * - 「本会话互动记录」：对方名 + 该会话视角摘要
 * - 「前往互动中心」：看全部互动、发起新互动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractionSheet(
    roleName: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    records: List<InteractionRecordItem>,
    relationships: List<RelationshipItem>,
    onOpenCenter: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            // 开放互动开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开放角色互动", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "开启后会被其他会话选中互动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 本会话互动记录
            Text(
                text = "本会话互动记录 · $roleName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (records.isEmpty()) {
                Text(
                    text = "暂无互动记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                records.forEach { r ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "与 ${r.peerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = interactionStatusText(r.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (r.summary.isNotBlank()) {
                            Text(
                                text = r.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 我的关系（好感度）：本会话角色与各会话角色的好感度数值 + 档位（T11.6 好感度细分到会话角色）
            Text(
                text = "我的关系（好感度） · $roleName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (relationships.isEmpty()) {
                Text(
                    text = "暂无好感度数据，去互动中心发起互动吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                relationships.forEach { r ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "与 ${r.peerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            // 好感度档位色：亲密=暖色系 / 友好 / 相识 / 陌生 逐级递减
                            val tierColor = when (r.tierLabel) {
                                "亲密" -> MaterialTheme.colorScheme.error
                                "友好" -> MaterialTheme.colorScheme.tertiary
                                "相识" -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.outline
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = tierColor.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = "${r.affinityScore} · ${r.tierLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tierColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = "已互动 ${r.interactionCount} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 前往互动中心
            TextButton(
                onClick = onOpenCenter,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往互动中心")
            }
        }
    }
}

/** 互动状态中文标签（互动面板记录用） */
private fun interactionStatusText(status: String): String = when (status) {
    InteractionStatus.COMPLETED.name -> "已完成"
    InteractionStatus.RUNNING.name -> "进行中"
    InteractionStatus.SKIPPED.name -> "已跳过"
    InteractionStatus.FAILED.name -> "未完成"
    else -> "待发起"
}

/** 记忆时间格式化：MM-dd HH:mm（区别于消息气泡的 formatTime） */
private fun formatMemoryTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

/**
 * 聊天页顶部作息调节面板（T11.10 深化：每个会话角色单独调整）。
 * - 会话级开关：控制本会话是否显示角色「此刻状态」（第二级开关，需全局开关开启）
 * - 起床/入睡两个滑杆：单独调节本会话角色的作息，保存后覆盖角色卡/基础作息
 * - 「恢复默认」：清除会话级自定义，回到跟随默认（基础作息 + 个性偏移）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeScheduleSheet(
    roleName: String,
    globalEnabled: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    scheduleConfig: ScheduleConfig,
    card: CharacterCardEntity?,
    conv: Conversation?,
    onScheduleChange: (ScheduleConfig?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 跟随默认时的展示作息 = 基础作息 + 角色个性偏移（用于滑杆初始值 / 恢复默认）
    val baseCfg = CharacterSchedule.applyTrait(scheduleConfig, card?.scheduleTrait() ?: ScheduleTrait.NORMAL)
    // 会话级自定义作息（null=未单独设置）
    val convCfg = conv?.customSchedule
    var wakeUp by remember { mutableStateOf(convCfg?.wakeUpMinute ?: baseCfg.wakeUpMinute) }
    var sleep by remember { mutableStateOf(convCfg?.sleepMinute ?: baseCfg.sleepMinute) }
    val isCustom = convCfg != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "${roleName}的作息设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 会话级开关（第二级开关）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开启本会话作息", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (globalEnabled) "开启后顶部显示「此刻状态」" else "需先在「设置 → 角色作息」开启全局开关",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (globalEnabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = globalEnabled,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            // 起床/入睡 2 滑杆（入睡支持跨天，最晚次日 01:59）
            TimeSlider(
                label = "起床",
                minute = wakeUp,
                range = 4 * 60..10 * 60,
                onValueChange = { wakeUp = it },
            )
            TimeSlider(
                label = "入睡",
                minute = sleep,
                range = 20 * 60..ScheduleConfig.MAX_SLEEP_MINUTE,
                onValueChange = { sleep = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 已单独设置过才显示「恢复默认」（清除自定义，跟随角色卡/默认）
                if (isCustom) {
                    TextButton(onClick = {
                        wakeUp = baseCfg.wakeUpMinute
                        sleep = baseCfg.sleepMinute
                    }) {
                        Text("恢复默认")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = {
                        // 与跟随默认一致则视为未自定义（清除会话级作息）
                        val newCfg = if (wakeUp == baseCfg.wakeUpMinute && sleep == baseCfg.sleepMinute) {
                            null
                        } else {
                            ScheduleConfig(wakeUp, sleep)
                        }
                        onScheduleChange(newCfg)
                        onDismiss()
                    },
                ) {
                    Text("保存")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 作息时间滑杆（T11.10 深化，风格对齐设置页/角色卡 TimeSlider）：
 * 显示 HH:mm，拖动即时回调更新作息分钟数，5 分钟一档。
 */
@Composable
private fun TimeSlider(
    label: String,
    minute: Int,
    range: IntRange, // 分钟范围（起点为整点，如 4*60..10*60）
    onValueChange: (Int) -> Unit,
) {
    // T11.10 优化：从整点起每 5 分钟一格。用「格索引」作为滑块值（离散、无 steps 细分），
    // 拖动顺滑且每次变化都精确落在整点+5分钟倍数的格点上，不再大量 steps 导致卡顿。
    val start = range.first
    val slotMin = 5
    val maxSlot = (range.last - range.first) / slotMin
    // 当前分钟值吸附到最近的 5 分钟格索引（用于滑块定位）
    val currentSlot = ((minute - start).coerceIn(0, range.last - range.first) + slotMin / 2) / slotMin

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label：${formatMinute(minute)}")
        Slider(
            value = currentSlot.toFloat(),
            onValueChange = { onValueChange(start + it.toInt() * slotMin) },
            valueRange = 0f..maxSlot.toFloat(),
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 分钟数 → HH:mm（T11.10 作息滑杆标签显示；>=24 点表示为次日，如 25:00→次日 01:00） */
private fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return if (h >= 24) {
        "次日 %02d:%02d".format(h - 24, m)
    } else {
        String.format(Locale.US, "%02d:%02d", h, m)
    }
}
