package com.bdalxy.edencourt.feature.conversationlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.core.ui.LifeStateIndicator
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.ConversationTraits
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 会话列表页（T2.6 + T4.7，参考企划书 5.3.1/5.8.1 原型）。
 * - LazyColumn 按 updatedAt 降序展示会话（头像+标题+预览+时间）
 * - FAB 新建：弹出底部选卡窗（默认助手 + 角色卡），选中后建会话并跳转（T4.7）
 * - 长按弹出二次确认删除（消息级联删除）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    onNavigateToChat: (Long) -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenMemory: () -> Unit,
    // T11.15：互动中心直达入口（不再藏二级菜单/设置页）
    onOpenInteraction: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    // 取 Application 中的仓库单例，构造 ViewModel
    val app = LocalContext.current.applicationContext as EdenCourtApp
    val viewModel: ConversationListViewModel = viewModel(factory = ConversationListViewModel.factory(app))
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val characterCards by viewModel.characterCards.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    // T11.10：作息配置 + 角色「此刻状态」（每分钟刷新，会话列表角色卡片显示）
    val scheduleConfig by viewModel.scheduleConfig.collectAsStateWithLifecycle()
    // T11.10 深化：全局开关（两级开关第一级：关闭则不显示任何状态）
    val scheduleEnabled by viewModel.scheduleEnabled.collectAsStateWithLifecycle()
    // T13.6：各会话「独有人格」标签（同一张卡的不同会话一眼区分）
    val traitsByConversation by viewModel.traitsByConversation.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    // 长按操作目标会话（T6.10：弹「置顶/取消置顶 + 删除」操作菜单）
    var actionTarget by remember { mutableStateOf<Conversation?>(null) }
    // 待编辑备注的会话（T6.14：长按菜单「备注」弹编辑对话框）
    var remarkTarget by remember { mutableStateOf<Conversation?>(null) }
    // 新建会话选卡弹窗开关（T4.7）
    var showCardPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话") },
                actions = {
                    // T11.15 入口提级：记忆/互动中心顶栏直达（一屏可见，不再藏二级菜单）
                    IconButton(onClick = onOpenMemory) {
                        Icon(Icons.Filled.Star, contentDescription = "记忆管理")
                    }
                    IconButton(onClick = onOpenInteraction) {
                        Icon(Icons.Filled.Favorite, contentDescription = "互动中心")
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("角色卡管理") },
                            onClick = { menuExpanded = false; onOpenCharacter() },
                        )
                        DropdownMenuItem(
                            text = { Text("记忆管理") },
                            onClick = { menuExpanded = false; onOpenMemory() },
                        )
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = { menuExpanded = false; onOpenSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("关于") },
                            onClick = { menuExpanded = false; onOpenAbout() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCardPicker = true },
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新建对话")
            }
        },
    ) { innerPadding ->
        if (isLoading) {
            // 加载中：进度圈（T6.1，企划书 5.5.1）
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (conversations.isEmpty()) {
            // 空状态：无会话，友好引导（T11.14：只指向已有功能「右下角 + 建对话」）
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "还没有对话",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "点击右下角「+」，选择一位角色，\n开启你们的第一段对话。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(items = conversations, key = { it.id }) { conversation ->
                    // 会话绑定的角色卡（未绑定为 null → 显示默认助手，T6.2A）
                    val card = characterCards.firstOrNull { it.id == conversation.characterCardId }
                    ConversationListItem(
                        conversation = conversation,
                        card = card,
                        // T13.6：该会话的「独有人格」（印象标签行；无则 null 不显示，兼容老会话）
                        traits = traitsByConversation[conversation.id],
                        // T11.10 深化：两级开关（全局 && 会话）都开启才显示「此刻状态」；
                        // 生效作息按 resolveConfig 会话级解析（会话自定义 > 角色自定义 > 个性偏移 > 基础）
                        lifeState = if (scheduleEnabled && conversation.lifeEnabled && conversation.characterCardId != null) {
                            rememberRoleLifeState(
                                CharacterSchedule.resolveConfig(scheduleConfig, card, conversation),
                            )
                        } else {
                            null
                        },
                        onClick = { onNavigateToChat(conversation.id) },
                        // T6.10：长按改为弹操作菜单（置顶/取消置顶、删除），不再直接弹删除确认
                        onLongClick = { actionTarget = conversation },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }

    // 新建会话选卡弹窗（T4.7）：默认助手 + 全部角色卡
    if (showCardPicker) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showCardPicker = false },
            sheetState = sheetState,
        ) {
            Text(
                text = "选择角色",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                // 默认助手：不绑定角色卡
                item {
                    CardPickerItem(
                        avatarPath = null,
                        name = "默认助手",
                        description = "永恒庭院 AI 助手",
                        onClick = {
                            viewModel.createConversation(cardId = null, onCreated = onNavigateToChat)
                            showCardPicker = false
                        },
                    )
                }
                items(items = characterCards, key = { it.id }) { card ->
                    CardPickerItem(
                        avatarPath = card.avatar,
                        name = card.name,
                        description = card.description.ifEmpty { "暂无简介" },
                        onClick = {
                            // 同一角色卡每次新建都是独立会话（绑定规则见企划书 2.2.2）
                            viewModel.createConversation(cardId = card.id, onCreated = onNavigateToChat)
                            showCardPicker = false
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 长按操作菜单（T6.10 + 6.5 体验修正）：底部弹窗「置顶/取消置顶 + 删除」（更贴近微信长按交互）
    actionTarget?.let { conversation ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                // 置顶 / 取消置顶
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.togglePin(conversation)
                            actionTarget = null
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (conversation.isPinned) "取消置顶" else "置顶",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                // 备注（T6.14：类微信备注显示名，改后列表展示备注别名）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            remarkTarget = conversation
                            actionTarget = null
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "备注",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                // 删除（仍走二次确认，保留安全保护）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            pendingDelete = conversation
                            actionTarget = null
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "删除",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    // 长按删除的二次确认对话框
    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定删除「${conversation.title}」？该会话的全部消息将一并删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(conversation)
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    // 会话备注编辑对话框（T6.14）：留空或输入新备注
    remarkTarget?.let { conversation ->
        RemarkEditDialog(
            initial = conversation.remark,
            onConfirm = { newRemark ->
                viewModel.updateRemark(conversation, newRemark)
                remarkTarget = null
            },
            onDismiss = { remarkTarget = null },
        )
    }
}

/**
 * 单个会话列表项（T6.2A 角色化展示）：圆形头像 + 名称 + 预览 + 时间。
 * 绑定角色卡的会话显示角色头像/角色名；未绑定显示「默认助手」（企划书 5.8.1）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationListItem(
    conversation: Conversation,
    card: CharacterCardEntity?,
    traits: ConversationTraits?,
    lifeState: LifeState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // 展示名：会话备注 > 角色备注 > 角色名 > 默认助手（T6.14，类微信备注；企划书 5.8.1）
    val cardDisplayName = card?.remark?.ifBlank { card.name } ?: "默认助手"
    val displayName = conversation.remark.ifBlank { cardDisplayName }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像：角色卡自定义头像/默认首字；未绑定用默认助手头像（T6.2A）
        CharacterAvatar(
            avatarPath = card?.avatar,
            name = displayName,
            modifier = Modifier.size(48.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 右侧：名称 + 预览（纵向），时间（右侧对齐）
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // T6.10：置顶会话在名称前显示「置顶」小标签（避免引 extended icons 增大体积）
                if (conversation.isPinned) {
                    Text(
                        text = "置顶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTime(conversation.lastMessageTime ?: conversation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            // T11.10：绑定角色卡的会话显示多态「此刻状态」（睡眠中/晨起/专注中…）
            if (lifeState != null) {
                LifeStateIndicator(state = lifeState)
                Spacer(modifier = Modifier.height(2.dp))
            }
            // T13.6：会话「独有人格」标签行（同一张卡不同会话一眼区分；无则跳过）
            val traitTags = traits?.identityTags?.let(::parseTagsJson).orEmpty()
            if (traitTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    traitTags.take(2).forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = conversation.lastMessagePreview.ifEmpty { "暂无消息" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 会话备注编辑对话框（T6.14）：底部弹窗输入备注，留空则回退原名。
 */
@Composable
private fun RemarkEditDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var remark by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置备注") },
        text = {
            OutlinedTextField(
                value = remark,
                onValueChange = { remark = it },
                label = { Text("备注（显示名，留空用原有名称）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(remark) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/** 解析 tags JSON 数组（如 `["温柔","要强"]`）为标签列表；非数组/失败返回空（T13.6） */
private fun parseTagsJson(tagsJson: String): List<String> {
    return runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(tagsJson)
            .let { it as? kotlinx.serialization.json.JsonArray ?: return emptyList() }
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
    }.getOrElse { emptyList() }
}

/** 时间显示规则：当天只显 HH:mm；昨天显「昨天」；更早显日期 */
private fun formatTime(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val zoned = Instant.ofEpochMilli(timestamp).atZone(zone)
    val day = zoned.toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> zoned.format(DateTimeFormatter.ofPattern("HH:mm"))
        today.minusDays(1) -> "昨天"
        else -> zoned.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }
}

/**
 * 角色「此刻状态」（T11.10）：按角色生效作息 + 真实时间计算多态生活状态，每分钟刷新一次。
 * T11.10 深化：传入的 config 已是该角色解析后的生效作息（自定义优先，其次个性偏移）。
 */
@Composable
private fun rememberRoleLifeState(config: ScheduleConfig): LifeState {
    var state by remember { mutableStateOf(CharacterSchedule.computeState(config, LocalTime.now())) }
    LaunchedEffect(config) {
        while (true) {
            state = CharacterSchedule.computeState(config, LocalTime.now())
            delay(60_000L)
        }
    }
    return state
}

/** 角色卡选择项（T4.7）：圆形头像（自定义图片/首字）+ 名称 + 简介，点击回调（T6.12 头像改 CharacterAvatar） */
@Composable
private fun CardPickerItem(
    avatarPath: String?,
    name: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像：自定义图片或默认首字，与会话列表/角色页样式一致（T6.12 修复选角弹窗不显示自定义头像）
        CharacterAvatar(
            avatarPath = avatarPath,
            name = name,
            modifier = Modifier.size(44.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
