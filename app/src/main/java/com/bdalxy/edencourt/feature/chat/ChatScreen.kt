package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import kotlinx.serialization.json.contentOrNull

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
    // C：聊天页内「世界书」管理入口（知识库抽屉「管理」/ 挂载弹层空态引导）
    onOpenWorldBook: () -> Unit,
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
    // A2：右侧「知识库」抽屉开关（恒久身份/世界书/记忆/关系与状态/预算 可视化）
    var showKnowledgeDrawer by rememberSaveable { mutableStateOf(false) }
    // B1：世界书挂载/卸下弹层开关（从知识库抽屉「世界书」行进入）
    var showWorldBookMountSheet by rememberSaveable { mutableStateOf(false) }
    // A2：知识库抽屉聚合快照（打开时刷新一次；null=尚未生成，卷面各值回落默认占位）
    val knowledgeDrawerState by viewModel.knowledgeDrawer.collectAsStateWithLifecycle()
    // A2：打开「知识库」抽屉时刷新聚合数据（世界书命中/记忆/情绪等随最近对话变化）
    LaunchedEffect(showKnowledgeDrawer) {
        if (showKnowledgeDrawer) viewModel.refreshKnowledgeDrawer()
    }
    // B1：供挂载弹层选书的全部世界书（含挂载本标识）
    val worldBooks by viewModel.worldBooks.collectAsStateWithLifecycle()
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
            // T15.6：顶部栏抽为独立组件（角色头像名 / 正在输入 / 此刻状态 / 独有印象 / 开抽屉）
            ChatTopBar(
                roleAvatar = roleCard?.avatar,
                roleName = roleName,
                isGenerating = isGenerating,
                scheduleEnabled = scheduleEnabled,
                lifeEnabled = conversation?.lifeEnabled == true,
                scheduleConfig = scheduleConfig,
                card = roleCard,
                conv = conversation,
                traitsBlurb = traits?.identityBlurb.orEmpty(),
                onBack = onBack,
                onOpenLife = { showLifeSheet = true },
                onEditTraits = { showTraitsEdit = true },
                onOpenMore = { showKnowledgeDrawer = true },
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
                    // v2.0 A6-5：群聊中解析发送者名（senderConversationId → 显示名）
                    var senderName by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(message.senderConversationId) {
                        senderName = viewModel.senderNameFor(message.senderConversationId)
                    }
                    MessageBubble(
                        message = message,
                        error = errorMessages[message.id],
                        // T6.6：把会话绑定角色卡的头像/角色名传给气泡，AI 头像跟随角色卡（自定义/默认首字）
                        assistantAvatar = roleCard?.avatar,
                        assistantName = roleName,
                        senderName = senderName,
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
    // A2：右侧「知识库」抽屉（恒久身份/世界书/记忆/关系与状态/注入预算 可视化，显示与注入口径一致）
    // 互动/记忆行的点击把入口回收到抽屉内；世界书挂载(B1)/管理页(C)待后续阶段接入
    KnowledgeDrawer(
        visible = showKnowledgeDrawer,
        roleName = roleName,
        avatarPath = roleCard?.avatar,
        characterTokens = knowledgeDrawerState?.characterTokens ?: 0,
        worldBookLabel = knowledgeDrawerState?.worldBookLabel ?: "未挂载",
        worldTokens = knowledgeDrawerState?.worldTokens ?: 0,
        worldHitCount = knowledgeDrawerState?.worldHitCount ?: 0,
        memoryLabel = knowledgeDrawerState?.memoryLabel ?: "",
        memoryTokens = knowledgeDrawerState?.memoryTokens ?: 0,
        affinityText = knowledgeDrawerState?.affinityText ?: "0 / 100",
        lifeStateText = knowledgeDrawerState?.lifeStateText ?: "未开启",
        moodText = knowledgeDrawerState?.moodText ?: "—",
        unreadInteractions = unreadInteractions,
        budgetUsed = knowledgeDrawerState?.budgetUsed ?: 0,
        onOpenMemory = {
            showKnowledgeDrawer = false
            showMemorySheet = true
            viewModel.refreshSessionMemories()
        },
        onOpenInteraction = {
            showKnowledgeDrawer = false
            showInteractionSheet = true
        },
        onMountWorldBook = {
            // B1：世界书挂载弹层——关闭抽屉后打开选书弹层
            showKnowledgeDrawer = false
            showWorldBookMountSheet = true
        },
        onManageWorldBook = {
            // C：世界书管理页导航
            showKnowledgeDrawer = false
            onOpenWorldBook()
        },
        onDismiss = { showKnowledgeDrawer = false },
    )
    // B1：世界书挂载/卸下弹层（关抽屉后弹出；选书即挂载、卸下置空）
    if (showWorldBookMountSheet) {
        WorldBookMountSheet(
            books = worldBooks,
            currentBookId = conversation?.worldBookId,
            onSelect = { id ->
                viewModel.mountWorldBook(id)
                showWorldBookMountSheet = false
            },
            onUnmount = { viewModel.mountWorldBook(null) },
            onDismiss = { showWorldBookMountSheet = false },
            onManage = {
                showWorldBookMountSheet = false
                onOpenWorldBook()
            },
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