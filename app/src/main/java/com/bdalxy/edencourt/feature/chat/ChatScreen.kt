package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import java.time.LocalTime

/**
 * 聊天页（T2.7，参考企划书 5.3.2/5.8.2 原型）。
 * - 用户消息右气泡、AI 消息左气泡（样式区分）
 * - LazyColumn 消息按时间正序，新消息自动滚到底部
 * - 每条消息带时间戳
 *
 * T15.6 拆分：本文件仅保留聊天主界面（顶部栏 / 消息列表 / 各种面板的挂载点），
 * 子组件抽到同包独立文件：
 * - MessageBubble（ChatMessageBubble.kt）
 * - ChatInputBar（ChatInputBar.kt）
 * - TraitsEditDialog（ChatTraitsDialog.kt）
 * - SessionMemorySheet（ChatMemorySheet.kt）
 * - InteractionSheet（ChatInteractionSheet.kt）
 * - LifeScheduleSheet（ChatLifeSheet.kt）
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
    // G-①：展示窗口分页——UI 只渲染展示子集 displayMessages（含已加载的更多历史），空态判断仍用全量 messages
    val displayMessages by viewModel.displayMessages.collectAsStateWithLifecycle()
    val hasMoreOlder by viewModel.hasMoreOlder.collectAsStateWithLifecycle()
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
    // T13.6：本会话「独有人格」（印象摘要 + 反差 + 编辑入口）
    val traits by viewModel.traits.collectAsStateWithLifecycle()

    // v1.2b T8.11：进入会话即把本会话互动标记已读，消除顶部红点
    LaunchedEffect(conversationId) {
        viewModel.markInteractionsRead()
    }

    // T11.10 深化：聊天页顶部作息面板（开启该会话角色作息 + 单独调节起床/入睡）
    var showLifeSheet by rememberSaveable { mutableStateOf(false) }
    // T13.6：印象摘要编辑对话框（点击顶部「独有印象」弹出，用户可改摘要/标签）
    var showTraitsEdit by rememberSaveable { mutableStateOf(false) }

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
        if (imeBottom > 0 && displayMessages.isNotEmpty()) {
            listState.scrollToItem(displayMessages.size - 1)
        }
    }
    // 消息/错误提示变化：平滑滚到底部（发送后/失败提示出现时重新定位，避免「重试」被输入栏遮挡）。
    // 注意：只依赖全量 messages.size 变化（新消息/流式的唯一信号），不能用 displayMessages.size——
    //      后者在「向上加载更早历史」时也会变，会导致翻历史时被迫跳回底部。
    LaunchedEffect(messages.size, errorMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }
    // G-① 窗口分页：向上滚动接近窗口顶部（且还有更早历史）时，加载更早的消息。
    // 用 firstVisibleItemIndex 接近 0 触发；窗口前插历史时 LazyColumn 按 key 保持当前可见项，不跳动。
    LaunchedEffect(listState, displayMessages, hasMoreOlder) {
        snapshotFlow {
            listState.firstVisibleItemIndex
        }.collect { first ->
            if (first <= 2 && hasMoreOlder && displayMessages.lastIndex >= first) {
                viewModel.loadOlderMessages()
            }
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
                        // T13.6：会话「独有印象」——顶部显示本会话特有的印象摘要（同一张卡不同会话一眼区分）
                        // 点击弹编辑对话框：用户可改摘要/标签（generatedBy→'user'，LLM 后续不再覆盖）
                        val currentTraits = traits
                        if (currentTraits?.identityBlurb?.isNotBlank() == true) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 40.dp, top = 2.dp)
                                    .clickable { showTraitsEdit = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = currentTraits.identityBlurb,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "编辑印象",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.secondary,
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
                items(items = displayMessages, key = { it.id }) { message ->
                    // G-①：只渲染展示窗口子集 displayMessages（含向上加载的更多历史）
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
    // T13.6：印象摘要编辑对话框（点击顶部「独有印象」弹出）
    if (showTraitsEdit) {
        TraitsEditDialog(
            initialBlurb = traits?.identityBlurb.orEmpty(),
            initialTags = traits?.identityTags?.let(::parseTagsJson).orEmpty().joinToString("、"),
            onConfirm = { blurb, tags ->
                viewModel.saveTraitsIdentity(blurb, tags)
                showTraitsEdit = false
            },
            onDismiss = { showTraitsEdit = false },
        )
    }
}

/** 解析 tags JSON 数组为标签列表（T13.6，供聊天页编辑对话框预填） */
private fun parseTagsJson(tagsJson: String): List<String> {
    return runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(tagsJson)
            .let { it as? kotlinx.serialization.json.JsonArray ?: return emptyList() }
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
    }.getOrElse { emptyList() }
}

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