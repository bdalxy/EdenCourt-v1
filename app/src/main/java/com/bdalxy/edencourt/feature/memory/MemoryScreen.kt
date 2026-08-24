package com.bdalxy.edencourt.feature.memory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
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
    val memories by viewModel.filteredMemories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val selectedCardId by viewModel.selectedCardId.collectAsStateWithLifecycle()

    // 待删除确认的记忆（长按触发）
    var pendingDelete by remember { mutableStateOf<MemoryItem?>(null) }
    // 手动添加对话框开关
    var showAddDialog by remember { mutableStateOf(false) }

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
            // 搜索框
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

            if (memories.isEmpty()) {
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = memories, key = { it.id }) { memory ->
                        MemoryListItem(
                            memory = memory,
                            // 归属名：角色备注/角色名，null=手动添加/未归属
                            ownerName = memory.characterCardId?.let { id ->
                                characters.firstOrNull { it.id == id }?.let {
                                    it.remark.ifBlank { it.name }
                                }
                            },
                            onLongClick = { pendingDelete = memory },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
}

/** 单个记忆列表项：类型标签 + 内容 + 时间 + 归属角色名（T6.13） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryListItem(
    memory: MemoryItem,
    ownerName: String?,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { /* 暂无详情，点击无操作 */ }, onLongClick = onLongClick)
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
            // 归属角色名（null=手动添加/未归属） + 时间
            Text(
                text = buildString {
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

/** 特殊占位 id：表示「手动添加/未归属」筛选（MemoryItem.characterCardId 为 null 那些） */
private const val MANUAL_CARD_ID = -1L

/** 记忆类型中文标签（企划书 4.1.4：个人信息/偏好/事件/其他） */
private val MemoryType.label: String
    get() = when (this) {
        MemoryType.PERSONAL_INFO -> "个人信息"
        MemoryType.PREFERENCE -> "偏好"
        MemoryType.EVENT -> "事件"
        MemoryType.OTHER -> "其他"
    }

/** 时间格式化：MM-dd HH:mm（列表展示用） */
private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

/** 手动添加记忆对话框：类型下拉 + 内容 + 关键词 */
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
