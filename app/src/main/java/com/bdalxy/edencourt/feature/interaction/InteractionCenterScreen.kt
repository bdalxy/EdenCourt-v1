package com.bdalxy.edencourt.feature.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.InteractionStatus
import com.bdalxy.edencourt.data.local.entity.InteractionTurn

/**
 * 互动中心（v1.2b 角色间互动，T8.11/T8.12）。
 * - 顶部：返回 + 全局总开关
 * - 列表：每场互动（双方名 + 视角摘要 + 未读红点）
 * - 底部：「发起互动」按钮 → 选 A（发起方）/ 选 B（目标）→ 调 InteractionExecutor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractionCenterScreen(
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as EdenCourtApp
    val viewModel: InteractionCenterViewModel = viewModel(
        factory = InteractionCenterViewModel.Factory(app),
    )
    val listItems by viewModel.listItems.collectAsStateWithLifecycle()
    val relationships by viewModel.relationships.collectAsStateWithLifecycle()
    val globalEnabled by viewModel.globalEnabled.collectAsStateWithLifecycle()
    val selectable by viewModel.selectableConversations.collectAsStateWithLifecycle()
    val convNames by viewModel.convNames.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    // 发起互动选择：pickerA=当前选中的发起方；pickerB=true 表示进入选目标阶段
    var pickerA by remember { mutableStateOf<Conversation?>(null) }
    var pickerB by remember { mutableStateOf<Conversation?>(null) }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        toast?.let { snackbarHost.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("角色互动") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (globalEnabled) "开启" else "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(checked = globalEnabled, onCheckedChange = { viewModel.setGlobalEnabled(it) })
                    }
                },
            )
        },
        bottomBar = {
            TextButton(
                onClick = { pickerA = selectable.firstOrNull() },
                enabled = globalEnabled && selectable.size >= 2,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text("发起互动", style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { padding ->
        if (listItems.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.size(40.dp))
                Text("还没有角色互动", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "先为至少两个会话打开「可互动」，\n再点下方按钮发起一场互动。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                // T11.6 关系一览：每对会话的好感度/档位/次数（置于互动列表顶部，随互动实时刷新）
                if (relationships.isNotEmpty()) {
                    item {
                        Text(
                            text = "关系一览（好感度）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(relationships.size) { i ->
                        val r = relationships[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = r.title,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${r.tierLabel} · ${r.affinityScore} 分 · ${r.interactionCount} 次",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }
                }
                items(listItems, key = { it.interaction.id }) { item ->
                    InteractionRow(
                        title = item.title,
                        summary = item.summary,
                        status = item.interaction.status,
                        unread = item.unread,
                        onClick = { viewModel.openDetail(item.interaction) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // 选发起方 A
    pickerA?.let { a ->
        ConversationPickerDialog(
            title = "先选发起方（A）",
            conversations = selectable,
            names = convNames,
            onPick = { chosen ->
                pickerA = null
                pickerB = chosen
            },
            onDismiss = { pickerA = null },
        )
    }
    // 选目标 B
    pickerB?.let { a ->
        ConversationPickerDialog(
            title = "再选互动对象（B）",
            conversations = selectable.filter { it.id != a.id },
            names = convNames,
            onPick = { b ->
                pickerB = null
                viewModel.startInteraction(a.id, b.id)
            },
            onDismiss = { pickerB = null },
        )
    }
    // T8.11：互动详情（点击列表行弹出，展示完整 A↔B 多轮台词）
    detail?.let { d ->
        InteractionDetailSheet(
            title = d.title,
            participants = d.participants,
            turns = d.turns,
            onDismiss = { viewModel.closeDetail() },
        )
    }
}

/** 互动详情弹窗：双方名 + 完整台词轮次（T8.11 详情） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InteractionDetailSheet(
    title: String,
    participants: Map<Long, String>,
    turns: List<InteractionTurn>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (turns.isEmpty()) {
                Text("这场互动没有台词")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    turns.forEach { t ->
                        val speakerName = participants[t.speakerConvId] ?: "?"
                        Text(
                            text = "$speakerName：${t.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 单场互动行：双方名 + 视角摘要 + 状态 + 未读红点；点击打开详情 */
@Composable
private fun InteractionRow(
    title: String,
    summary: String,
    status: String,
    unread: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                text = statusLabel(status),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (unread) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE57373)),
            )
        }
    }
}

/** 互动状态中文标签 */
private fun statusLabel(status: String): String = when (status) {
    InteractionStatus.COMPLETED.name -> "已完成"
    InteractionStatus.RUNNING.name -> "进行中"
    InteractionStatus.SKIPPED.name -> "已跳过"
    InteractionStatus.FAILED.name -> "未完成"
    else -> "待发起"
}

/** 会话选择弹窗：点选一个可互动会话（显示角色名，便于区分不同会话角色） */
@Composable
private fun ConversationPickerDialog(
    title: String,
    conversations: List<Conversation>,
    names: Map<Long, String>,
    onPick: (Conversation) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (conversations.isEmpty()) {
                Text("暂无可选会话（需先在会话设置打开「可互动」）")
            } else {
                Column {
                    conversations.forEach { conv ->
                        TextButton(onClick = { onPick(conv) }) {
                            Text(names[conv.id] ?: conv.remark.ifBlank { conv.title })
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}