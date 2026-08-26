package com.bdalxy.edencourt.feature.interaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.local.entity.InteractionSource
import com.bdalxy.edencourt.data.local.entity.InteractionStatus
import com.bdalxy.edencourt.data.local.entity.InteractionTurn
import kotlin.math.roundToInt

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
    val relationshipEvents by viewModel.relationshipEvents.collectAsStateWithLifecycle()
    val globalEnabled by viewModel.globalEnabled.collectAsStateWithLifecycle()
    val autoConfig by viewModel.autoConfig.collectAsStateWithLifecycle()
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
    // T11.7：自动驱动配置变化（开关/窗口/概率）→ 立即重排当日闹钟，修改即时生效
    LaunchedEffect(autoConfig) {
        app.interactionAutoScheduler.schedule(app)
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
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // T11.7 自动驱动设置区（即便尚无互动记录也可先配置）
                AutoDriveSection(
                    autoConfig = autoConfig,
                    onAutoEnabledChange = viewModel::setAutoEnabled,
                    onWindowChange = viewModel::setAutoWindow,
                    onProbabilityChange = viewModel::setAutoProbability,
                )
                HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
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
                // T11.7 自动驱动设置区（置于列表最顶部）
                item {
                    AutoDriveSection(
                        autoConfig = autoConfig,
                        onAutoEnabledChange = viewModel::setAutoEnabled,
                        onWindowChange = viewModel::setAutoWindow,
                        onProbabilityChange = viewModel::setAutoProbability,
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }
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
                // T11.13 关系故事：共同经历事件时间线（里程碑叙事，不替代好感度数值，置于关系一览下方）
                if (relationshipEvents.isNotEmpty()) {
                    item {
                        Text(
                            text = "关系故事（共同经历）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(relationshipEvents.size) { i ->
                        RelationshipEventRow(event = relationshipEvents[i])
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }
                }
                items(listItems, key = { it.interaction.id }) { item ->
                    InteractionRow(
                        title = item.title,
                        summary = item.summary,
                        status = item.interaction.status,
                        isAuto = item.interaction.source == InteractionSource.AUTO.name,
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

/** 单场互动行：双方名 + 视角摘要 + 状态（自动驱动场次带「自动」标记）+ 未读红点；点击打开详情 */
@Composable
private fun InteractionRow(
    title: String,
    summary: String,
    status: String,
    isAuto: Boolean,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                // T11.7：自动驱动场次标记（角色自发串门，非手动发起）
                if (isAuto) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "· 自动",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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

/**
 * T11.13 关系故事时间线行：一场「共同经历事件」的里程碑叙事。
 * 展示 双方名 + 氛围档位（+ 自动标记）+ 场景/主题 + 要点（2 行截断）+ 时间。
 */
@Composable
private fun RelationshipEventRow(event: RelationshipEventOverview) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = event.mood,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (event.source == InteractionSource.AUTO.name) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "· 自动",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        // 场景 + 主题（一行，超长截断）
        val headline = buildString {
            append(event.scene)
            if (event.topic.isNotBlank()) append("：").append(event.topic)
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        // 要点（双方视角摘要，最多 2 行）
        if (event.points.isNotBlank()) {
            Text(
                text = event.points.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = eventTimeText(event.happenedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** 事件时间文案（T11.13）：「8月26日 23:30」 */
private fun eventTimeText(ts: Long): String {
    val fmt = java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINESE)
    return fmt.format(java.util.Date(ts))
}

/** T11.7 小时选择目标：开始 / 结束 */
private enum class WindowField { START, END }

/** 分钟 → "HH:00" 文案（T11.7 自动窗口展示用） */
private fun windowText(minute: Int): String = "%02d:00".format(minute.coerceIn(0, 1439) / 60)

/**
 * T11.7 互动自动驱动设置区：开关 + 说明 + 窗口 chips + 概率滑块。
 * 配置变化由上层 LaunchedEffect(autoConfig) 监听并重排当日闹钟，修改即时生效。
 */
@Composable
private fun AutoDriveSection(
    autoConfig: AutoDriveConfig,
    onAutoEnabledChange: (Boolean) -> Unit,
    onWindowChange: (Int, Int) -> Unit,
    onProbabilityChange: (Float) -> Unit,
) {
    // 当前正在编辑的窗口字段（null=未编辑）；概率滑块本地状态（拖动只改本地，抬起才写 store，避免拖动卡顿）
    var editing by remember { mutableStateOf<WindowField?>(null) }
    var prob by remember { mutableStateOf(autoConfig.probability) }
    LaunchedEffect(autoConfig.probability) { prob = autoConfig.probability }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 标题 + 开关
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "自动驱动",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = autoConfig.autoEnabled, onCheckedChange = onAutoEnabledChange)
        }
        // 说明
        Text(
            text = "每天 ${windowText(autoConfig.windowStartMinute)}–${windowText(autoConfig.windowEndMinute)} 角色们可能自发互相串门；自动互动同样计入好感度。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        // 窗口 chips（点开始/结束分别选小时）
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = { editing = WindowField.START }, label = { Text("开始 ${windowText(autoConfig.windowStartMinute)}") })
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(onClick = { editing = WindowField.END }, label = { Text("结束 ${windowText(autoConfig.windowEndMinute)}") })
        }
        Spacer(modifier = Modifier.height(4.dp))
        // 概率滑块（0.1~1.0，默认 0.3）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "串门概率 ${(prob * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Slider(
                value = prob,
                onValueChange = { prob = it },
                onValueChangeFinished = { onProbabilityChange(prob) },
                valueRange = 0.1f..1f,
                modifier = Modifier.weight(1f),
            )
        }
    }

    // 小时选择弹窗
    editing?.let { field ->
        HourPickerDialog(
            title = if (field == WindowField.START) "选择开始时间" else "选择结束时间",
            currentMinute = if (field == WindowField.START) autoConfig.windowStartMinute else autoConfig.windowEndMinute,
            onPick = { hour ->
                val start = if (field == WindowField.START) hour * 60 else autoConfig.windowStartMinute
                val end = if (field == WindowField.END) hour * 60 else autoConfig.windowEndMinute
                onWindowChange(start, end)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** 小时选择弹窗（0~23 时，T11.7 自动窗口时段设置；高亮当前值前置于列表可滚动选择） */
@Composable
private fun HourPickerDialog(
    title: String,
    currentMinute: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(24) { h ->
                    TextButton(onClick = { onPick(h) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "%02d:00".format(h),
                            color = if (h * 60 == currentMinute) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
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