package com.bdalxy.edencourt.feature.memory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.MemoryImportance
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.local.entity.MemoryVisibility
import com.bdalxy.edencourt.data.local.entity.TimeScope
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆管理页（T5.5，参考企划书 5.3.4）。
 * - 列表：类型标签 + 内容 + 时间
 * - 顶部搜索框：按内容/关键词过滤
 * - 长按删除（二次确认）
 * - FAB 手动添加（类型选择 + 内容 + 关键词）
 * - 空状态提示「暂无记忆…」
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EdenCourtApp
    val viewModel: MemoryViewModel = viewModel(factory = MemoryViewModel.factory(app))
    val memories by viewModel.pagedMemories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val selectedCardId by viewModel.selectedCardId.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val tabCounts by viewModel.tabCounts.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val hasMore = viewModel.hasMore
    // 分页滚动状态（滚动到底触发加载下一页，T9.5）
    val listState = rememberLazyListState()

    // 待删除确认的记忆（长按触发）
    var pendingDelete by remember { mutableStateOf<MemoryItem?>(null) }
    // 手动添加对话框开关
    var showAddDialog by remember { mutableStateOf(false) }
    // 当前正在查看详情的记忆 id（null=列表页；T10.4 点条目进详情）
    var detailId by remember { mutableStateOf<Long?>(null) }

    val detailIdValue = detailId
    if (detailIdValue != null) {
        // 详情页全屏覆盖（自身含 TopAppBar 返回）
        MemoryDetailScreen(memoryId = detailIdValue, onBack = { detailId = null })
    } else {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "手动添加记忆")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 概览统计条（T10.6：全库 总量/置顶/重要/私密 一屏看懂）
            OverviewBar(overview)

            // 搜索框（T9.4 实时检索）
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("搜索记忆") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // 角色筛选（T6.13：按记忆归属的角色过滤）
            CharacterFilterBar(
                characters = characters,
                selectedCardId = selectedCardId,
                onSelect = { viewModel.setSelectedCard(it) },
            )

            // 分类 Tab（T9.4：7 类 + 全部，带数量）
            CategoryTabBar(
                selected = selectedType,
                counts = tabCounts,
                onSelect = { viewModel.setSelectedType(it) },
            )

            if (totalCount == 0) {
                // 空状态：有搜索词则提示无结果，否则提示暂无记忆（企划书 5.5.2）
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "没有匹配的记忆" else "暂无记忆，AI 会在对话中自动提取",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 滚动监听：接近末尾且还有下一页 → 追加加载（T9.5）
                LaunchedEffect(listState, memories, hasMore) {
                    snapshotFlow {
                        val total = listState.layoutInfo.totalItemsCount
                        total to listState.firstVisibleItemIndex
                    }
                        .distinctUntilChanged()
                        .collect { (total, first) ->
                            if (total > 0 && total - first <= 5 && hasMore) viewModel.loadMore()
                        }
                }

                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                    items(items = memories, key = { it.id }) { memory ->
                        MemoryListItem(
                            memory = memory,
                            // 归属名：角色备注/角色名，null=手动添加/未归属
                            ownerName = memory.characterCardId?.let { id ->
                                characters.firstOrNull { it.id == id }?.let {
                                    it.remark.ifBlank { it.name }
                                }
                            },
                            onItemClick = { detailId = memory.id },
                            onLongClick = { pendingDelete = memory },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                    // 底部加载提示（T9.5：还有下一页时显示）
                    if (hasMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "加载中…，共 $totalCount 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 长按删除二次确认对话框
    pendingDelete?.let { memory ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除记忆") },
            text = { Text("确定删除这条记忆？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMemory(memory.id)
                        pendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    // 手动添加记忆对话框
    if (showAddDialog) {
        AddMemoryDialog(
            onConfirm = { type, content, keywords ->
                viewModel.addMemory(type, content, keywords)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
        } // 关闭 detailId==null 的 else 分支
}

/** 概览统计条（T10.6）：全库 总量/置顶/重要/私密，一屏看懂且随表变化实时刷新 */
@Composable
private fun OverviewBar(overview: MemoryViewModel.OverviewCounts) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            OverviewStat("共", overview.total)
            OverviewStat("置顶", overview.pinned)
            OverviewStat("重要", overview.important)
            OverviewStat("私密", overview.privateCount)
        }
    }
}

/** 概览统计中的单个数字项 */
@Composable
private fun OverviewStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 分类 Tab（T9.4）：全部 + 7 类，横向滚动，带数量角标 */
@Composable
private fun CategoryTabBar(
    selected: MemoryType?,
    counts: Map<MemoryType?, Int>,
    onSelect: (MemoryType?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部 ${counts[null] ?: 0}") },
            )
        }
        items(MemoryType.entries.toList()) { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(t) },
                label = { Text("${t.label} ${counts[t] ?: 0}") },
            )
        }
    }
}

/** 单个记忆列表项：类型标签 + 内容 + 时间 + 归属角色名（T6.13） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryListItem(
    memory: MemoryItem,
    ownerName: String?,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onItemClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 类型标签
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = memory.type.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        // 可见性标签（T7.3：共享/私密）
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (memory.visibility == MemoryVisibility.PRIVATE) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Text(
                text = memory.visibility.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (memory.visibility == MemoryVisibility.PRIVATE) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        // 内容 + 时间
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 归属角色名 + 阶段一标记（置顶/重要/锁定/短期） + 时间（T6.13 + T9.4）
            Text(
                text = buildString {
                    if (memory.isPinned) append("📍置顶 · ")
                    if (memory.importance == MemoryImportance.IMPORTANT) append("⭐重要 · ")
                    if (memory.locked) append("🔒锁定 · ")
                    if (memory.timeScope == TimeScope.SHORT_TERM) append("🕒短期 · ")
                    append(ownerName ?: "手动/未归属")
                    append(" · ")
                    append(formatTime(memory.createdAt))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 记忆归属角色筛选条（T6.13）：下拉选择「全部/某角色/手动未归属」。
 * 让用户明确每条记忆是「谁的」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterFilterBar(
    characters: List<CharacterCardEntity>,
    selectedCardId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // 当前筛选项标题
    val currentTitle = if (selectedCardId == null) {
        "全部记忆"
    } else if (selectedCardId == MANUAL_CARD_ID) {
        "手动/未归属"
    } else {
        characters.firstOrNull { it.id == selectedCardId }?.let { it.remark.ifBlank { it.name } }
            ?: "全部记忆"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            Text(
                text = "筛选：$currentTitle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .menuAnchor()
                    .clickable { expanded = true }
                    .padding(vertical = 8.dp),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("全部记忆") },
                    onClick = { onSelect(null); expanded = false },
                )
                dropdownDivider()
                characters.forEach { card ->
                    DropdownMenuItem(
                        text = { Text(card.remark.ifBlank { card.name }) },
                        onClick = { onSelect(card.id); expanded = false },
                    )
                }
                dropdownDivider()
                DropdownMenuItem(
                    text = { Text("手动/未归属") },
                    onClick = { onSelect(MANUAL_CARD_ID); expanded = false },
                )
            }
        }
    }
}

/** 记忆筛选条内的分隔线 */
@Composable
private fun dropdownDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** 记忆类型中文标签（企划书 4.1.4 + 阶段一扩 7 类，T9.1） */
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

/** 记忆可见性中文标签（T7.3：共享/私密） */
private val MemoryVisibility.label: String
    get() = when (this) {
        MemoryVisibility.SHARED -> "共享"
        MemoryVisibility.PRIVATE -> "私密"
    }

/** 时间格式化：MM-dd HH:mm（列表展示用） */
private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

/** 手动添加记忆对话框：类型下拉 + 内容 + 关键词（记忆管理页固定作为共享记忆，T7.3） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemoryDialog(
    onConfirm: (type: MemoryType, content: String, keywords: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(MemoryType.PERSONAL_INFO) }
    var content by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    // 类型下拉展开标志
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动添加记忆") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 类型下拉选择
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = type.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        MemoryType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                onClick = {
                                    type = t
                                    typeExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 内容
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 关键词（逗号分隔）
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("关键词（逗号分隔，可空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 内容不允许为空，空则提示不保存
                    if (content.isBlank()) return@TextButton
                    onConfirm(type, content.trim(), keywords.trim())
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
