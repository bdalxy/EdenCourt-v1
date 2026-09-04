package com.bdalxy.edencourt.feature.memory

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
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
    // 角色 Tab 数量（T11.2：全部/手动未归属/各角色，带角标）
    val cardTabCounts by viewModel.cardTabCounts.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    // 是否还有下一页（T9.5：改 StateFlow 可观察，修复普通 getter 快照导致滚动加载不触发）
    val hasMore by viewModel.hasMoreState.collectAsStateWithLifecycle()
    // 分页滚动状态（滚动到底触发加载下一页，T9.5）
    val listState = rememberLazyListState()

    // 手动添加对话框开关
    var showAddDialog by remember { mutableStateOf(false) }
    // 当前正在查看详情的记忆 id（null=列表/时间轴；T10.4 点条目进详情）
    var detailId by remember { mutableStateOf<Long?>(null) }
    // 是否显示时间轴视图（T10.7 顶栏入口切换，与详情/列表互斥）
    var showTimeline by remember { mutableStateOf(false) }
    // 是否显示共现热词页（阶段三 T11.1 顶栏入口切换）
    var showHotwords by remember { mutableStateOf(false) }
    // 是否显示关系图谱页（阶段三 T11.5：共现网络可视化，与列表/时间轴/热词互斥）
    var showGraph by remember { mutableStateOf(false) }
    // B4：是否显示「角色关系图谱」页（我置顶独占行 + 其它开启角色两两成行，与列表/时间轴/热词/共现图互斥）
    var showRoleGraph by remember { mutableStateOf(false) }
    // 批量管理多选状态（T11.3：长按进入多选，勾选多项后批量删除/设重要/置顶）
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    // 批量删除二次确认框开关（T11.3）
    var pendingBatchDelete by remember { mutableStateOf(false) }
    // 「更多操作」菜单开关（T11.3：设重要/取消重要/置顶/取消置顶）
    var moreMenuExpanded by remember { mutableStateOf(false) }
    // 「备份/恢复」菜单开关（T11.4：导出/导入记忆 JSON）
    var backupMenuExpanded by remember { mutableStateOf(false) }

    // 导出：SAF 让用户选保存位置（默认 Download），写记忆备份 JSON 文件（T11.4）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup { json ->
                val ok = runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
                Toast.makeText(context, if (ok) "记忆已导出" else "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 导入：SAF 选备份 JSON 文件，解析入库并刷新列表（T11.4）
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "读取文件失败或为空", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.importBackup(text) { count ->
                    Toast.makeText(
                        context,
                        if (count > 0) "成功导入 $count 条记忆" else "未导入：文件为空或格式不对",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    // 多选模式：系统返回键退出多选（T11.3，避免误回上一页）
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    val detailIdValue = detailId
    if (detailIdValue != null) {
        // 详情页全屏覆盖（自身含 TopAppBar 返回）
        MemoryDetailScreen(memoryId = detailIdValue, onBack = { detailId = null })
    } else if (showTimeline) {
        // 时间轴视图（阶段二可视化 T10.7：按天分组的记忆演化）
        TimelineScreen(onBack = { showTimeline = false })
    } else if (showHotwords) {
        // 共现热词页（阶段三 T11.1：关键词排行 → 筛相关记忆）
        HotwordScreen(onBack = { showHotwords = false })
    } else if (showGraph) {
        // 关系图谱页（阶段三 T11.5：共现网络可视化 → 点节点看关联记忆）
        GraphScreen(onBack = { showGraph = false })
    } else if (showRoleGraph) {
        // B4 角色关系图谱页（我置顶独占行 + 其它开启角色两两成行 + 点角色看关系）
        RoleRelationGraphScreen(onBack = { showRoleGraph = false })
    } else {
        Scaffold(
        topBar = {
            if (selectionMode) {
                // 批量管理操作栏（T11.3）：显示已选数量 + 全选 + 删除 + 更多操作
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        // 全选/全不选
                        IconButton(onClick = {
                            val all = memories.map { it.id }.toSet()
                            selectedIds = if (selectedIds.containsAll(all)) emptySet() else all
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "全选/全不选")
                        }
                        // 删除选中项（二次确认）
                        IconButton(
                            onClick = { pendingBatchDelete = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除选中")
                        }
                        // 更多操作：设重要/取消重要/置顶/取消置顶
                        Box {
                            IconButton(onClick = { moreMenuExpanded = true }, enabled = selectedIds.isNotEmpty()) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = moreMenuExpanded,
                                onDismissRequest = { moreMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("设为重要") },
                                    onClick = {
                                        viewModel.batchSetImportance(selectedIds.toList(), MemoryImportance.IMPORTANT)
                                        moreMenuExpanded = false
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("取消重要") },
                                    onClick = {
                                        viewModel.batchSetImportance(selectedIds.toList(), MemoryImportance.NORMAL)
                                        moreMenuExpanded = false
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("置顶") },
                                    onClick = {
                                        viewModel.batchSetPinned(selectedIds.toList(), true)
                                        moreMenuExpanded = false
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("取消置顶") },
                                    onClick = {
                                        viewModel.batchSetPinned(selectedIds.toList(), false)
                                        moreMenuExpanded = false
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    },
                                )
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("记忆") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    // T11.4 备份/恢复：导出记忆为 JSON、从 JSON 导入记忆
                    actions = {
                        Box {
                            IconButton(onClick = { backupMenuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "备份/恢复")
                            }
                            DropdownMenu(
                                expanded = backupMenuExpanded,
                                onDismissRequest = { backupMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导出记忆（备份 JSON）") },
                                    onClick = {
                                        backupMenuExpanded = false
                                        exportLauncher.launch("EdenCourt-记忆备份-${System.currentTimeMillis()}.json")
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("导入记忆（从 JSON 恢复）") },
                                    onClick = {
                                        backupMenuExpanded = false
                                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            // 多选模式下隐藏 FAB，避免误触手动添加（T11.3）
            if (!selectionMode) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "手动添加记忆")
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 概览统计条（T10.6：全库 总量/置顶/重要/私密 一屏看懂）
            OverviewBar(overview)

            // 可视化快捷入口（T10.7/T11.1：带文字便于发现，弥补顶栏纯图标难找）
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { showTimeline = true },
                    label = { Text("时间轴") },
                    leadingIcon = {
                        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = { showHotwords = true },
                    label = { Text("共现热词") },
                    leadingIcon = {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = { showGraph = true },
                    label = { Text("关系图谱") },
                    leadingIcon = {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
                AssistChip(
                    onClick = { showRoleGraph = true },
                    label = { Text("角色关系") },
                    leadingIcon = {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }

            // 搜索框（T9.4 实时检索）
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("搜索记忆") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // 角色 Tab（T11.2：全部/各角色/手动未归属，带记忆数量角标，点击即筛）
            CardTabBar(
                characters = characters,
                selectedCardId = selectedCardId,
                counts = cardTabCounts,
                onSelect = { viewModel.setSelectedCard(it) },
            )

            // 分类 Tab（T9.4：7 类 + 全部，带数量）
            CategoryTabBar(
                selected = selectedType,
                counts = tabCounts,
                onSelect = { viewModel.setSelectedType(it) },
            )

            if (totalCount == 0) {
                // 空状态：有搜索词则提示无结果，否则提示暂无记忆（企划书 5.5.2 / T11.14 增强引导）
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "没有匹配的记忆" else "还没有记忆",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (searchQuery.isBlank()) {
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "AI 会在对话中自动提取你们的经历，\n一起聊过的事都会在这里被记住。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                // 滚动监听：接近末尾且还有下一页 → 追加加载（T9.5）
                // 用 lastVisibleItemIndex 判断「触底」，避免 firstVisibleIndex 差值恒等于屏显数导致永不到阈值
                LaunchedEffect(listState, memories, hasMore) {
                    snapshotFlow {
                        val info = listState.layoutInfo
                        val total = info.totalItemsCount
                        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total to last
                    }
                        .distinctUntilChanged()
                        .collect { (total, last) ->
                            val cond = total > 0 && last >= total - 5 && hasMore
                            Log.d("MEM_PAGING", "scroll total=$total last=$last hasMore=$hasMore trigger=$cond")
                            if (cond) viewModel.loadMore()
                        }
                }

                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                    items(items = memories, key = { it.id }) { memory ->
                        // 归属角色卡（T11.2：头像 + 归属名；未归属=手动添加/无角色）
                        val ownerCard = memory.characterCardId?.let { id ->
                            characters.firstOrNull { it.id == id }
                        }
                        MemoryListItem(
                            memory = memory,
                            // 归属名：角色备注/角色名，null=手动添加/未归属
                            ownerName = ownerCard?.let { it.remark.ifBlank { it.name } },
                            avatarPath = ownerCard?.avatar,
                            // 多选模式（T11.3）：显示勾选框 + 选中态；点击切换勾选而非进详情
                            selected = selectionMode && memory.id in selectedIds,
                            showCheckbox = selectionMode,
                            onItemClick = {
                                Log.d("MEM_SEL", "onItemClick id=${memory.id} selectionMode=$selectionMode")
                                if (selectionMode) {
                                    selectedIds = if (memory.id in selectedIds) selectedIds - memory.id else selectedIds + memory.id
                                } else {
                                    detailId = memory.id
                                }
                            },
                            // 长按进入多选并勾选该项（替代原单条删除确认，T11.3）
                            onLongClick = {
                                Log.d("MEM_SEL", "onLongClick id=${memory.id}")
                                selectionMode = true
                                selectedIds = setOf(memory.id)
                            },
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

    // 批量删除二次确认对话框（T11.3）
    if (pendingBatchDelete) {
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = false },
            title = { Text("删除记忆") },
            text = { Text("确定删除选中的 ${selectedIds.size} 条记忆？删除后无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchDelete(selectedIds.toList())
                        pendingBatchDelete = false
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchDelete = false }) { Text("取消") }
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

/** 单个记忆列表项：多选勾选 + 角色头像 + 类型标签 + 内容 + 时间 + 归属角色名（T6.13 + T11.2 + T11.3） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryListItem(
    memory: MemoryItem,
    ownerName: String?,
    avatarPath: String?,
    selected: Boolean = false,
    showCheckbox: Boolean = false,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 多选模式下高亮选中行（T11.3）
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)) else Modifier)
            .combinedClickable(onClick = onItemClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 多选勾选框（T11.3：进入多选模式后显示在最前）
        if (showCheckbox) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onItemClick() },
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        // 归属角色头像（T11.2：有归属显示头像，一眼看出记忆是谁的；未归属无头像）
        if (ownerName != null) {
            CharacterAvatar(
                avatarPath = avatarPath,
                name = ownerName,
                modifier = Modifier.size(28.dp),
                textSize = MaterialTheme.typography.labelMedium.fontSize,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
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
 * 记忆归属角色 Tab（T11.2）：横向一排「全部 / 各角色 / 手动未归属」chip，带记忆数量角标，点击即筛。
 * 替代原下拉筛选条（T6.13），让「记忆是谁的」一屏可见。
 */
@Composable
private fun CardTabBar(
    characters: List<CharacterCardEntity>,
    selectedCardId: Long?,
    counts: MemoryViewModel.CardTabCounts,
    onSelect: (Long?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCardId == null,
                onClick = { onSelect(null) },
                label = { Text("全部 ${counts.total}") },
            )
        }
        items(characters, key = { it.id }) { card ->
            FilterChip(
                selected = selectedCardId == card.id,
                onClick = { onSelect(card.id) },
                label = { Text("${card.remark.ifBlank { card.name }} ${counts.byCard[card.id] ?: 0}") },
            )
        }
        item {
            FilterChip(
                selected = selectedCardId == MANUAL_CARD_ID,
                onClick = { onSelect(MANUAL_CARD_ID) },
                label = { Text("手动/未归属 ${counts.manual}") },
            )
        }
    }
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
        MemoryType.EMOTION -> "情感"
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
