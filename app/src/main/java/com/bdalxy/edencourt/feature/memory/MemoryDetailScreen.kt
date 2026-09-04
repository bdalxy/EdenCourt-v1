package com.bdalxy.edencourt.feature.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.MemoryImportance
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.local.entity.MemoryVisibility
import com.bdalxy.edencourt.data.local.entity.MemorySourceType
import com.bdalxy.edencourt.data.local.entity.TimeScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记忆详情页（阶段二 T10.2/T10.3，参考企划书 5.3.4 + 设计文档「记忆详情与编辑」）。
 * - 全字段展示：内容/关键词/分类/时间范围/归属/可见性/来源/置信度/创建/更新时间
 * - 通用卡片操作：置顶 / 重要 / 可见性(共享↔私密) / 锁定；编辑（覆盖即更正）；软删除（二次确认）
 * - 已删除时自动返回上一层，列表由 observeActiveCount 变化触发刷新
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailScreen(memoryId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EdenCourtApp
    val viewModel: MemoryDetailViewModel = viewModel(
        key = memoryId.toString(),
        factory = MemoryDetailViewModel.factory(app, memoryId),
    )
    val item by viewModel.item.collectAsStateWithLifecycle()
    // 订阅角色卡流：stateIn(WhileSubscribed) 需被订阅才会填充，否则 displayNameFor 解析不到卡片名
    viewModel.characters.collectAsStateWithLifecycle()

    // 编辑 / 删除确认对话框开关
    var showEdit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 区分「加载中(null)」与「已删除(null)」：加载完成后若为空则返回
    var firstLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        if (item != null) firstLoaded = true
        else if (firstLoaded) onBack()
    }

    // 系统返回：无内部弹窗时拦截为「返回上一级」，避免直接退出到主界面（bugfix）
    BackHandler(enabled = !showEdit && !confirmDelete) { onBack() }

    val memory = item
    if (memory == null) {
        Scaffold { inner ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inner),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // 内容大段
            Text(
                text = memory.content,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(6.dp))
            // 关键词
            if (memory.keywords.isNotBlank()) {
                Text(
                    text = "关键词：${memory.keywords}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // 元信息区
            DetailMetaRow("分类", memory.type.label)
            DetailMetaRow("时间范围", memory.timeScope.label)
            DetailMetaRow("可见性", memory.visibility.label)
            DetailMetaRow("归属", viewModel.displayNameFor(memory.characterCardId) ?: "手动/未归属")
            // 来源：会话 id + 消息 id（可回溯定位；跳转交互留后续）
            val src = buildString {
                if (memory.sourceType == MemorySourceType.USER_EDIT) append("手动编辑")
                else append("会话").apply {
                    memory.sourceConversationId?.let { append("#$it") }
                    memory.sourceMessageId?.let { append("·消息$it") }
                }
            }
            DetailMetaRow("来源", src)
            DetailMetaRow("置信度", String.format(Locale.getDefault(), "%.2f", memory.confidence))
            DetailMetaRow("创建", formatFullTime(memory.createdAt))
            DetailMetaRow("更新", formatFullTime(memory.updatedAt))

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // 通用操作区（T10.2：状态在按钮上实时反映）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::togglePinned) {
                    Text(if (memory.isPinned) "📍置顶中" else "置顶")
                }
                OutlinedButton(onClick = viewModel::toggleImportant) {
                    Text(if (memory.importance == MemoryImportance.IMPORTANT) "⭐重要" else "常规")
                }
                OutlinedButton(onClick = viewModel::toggleVisibility) {
                    Text(if (memory.visibility == MemoryVisibility.PRIVATE) "私密" else "共享")
                }
                OutlinedButton(onClick = viewModel::toggleLocked) {
                    Text(if (memory.locked) "🔒已锁" else "锁定")
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // 危险区：编辑 / 删除
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showEdit = true }) {
                    Text("编辑")
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // 编辑对话框（T10.3：编辑即更正，覆盖 content/keywords）
    if (showEdit) {
        MemoryEditDialog(
            initialContent = memory.content,
            initialKeywords = memory.keywords,
            onConfirm = { content, keywords ->
                viewModel.editContent(content, keywords)
                showEdit = false
            },
            onDismiss = { showEdit = false },
        )
    }

    // 删除二次确认（T10.3）
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条记忆？") },
            text = { Text("删除后将从列表隐藏（软删除，可回溯），此操作不可直接撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete() // 删除后 item 变 null，LaunchedEffect 触发 onBack
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

/** 元信息行：标签 + 值 */
@Composable
private fun DetailMetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 编辑记忆对话框：改内容 + 关键词 */
@Composable
private fun MemoryEditDialog(
    initialContent: String,
    initialKeywords: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf(initialContent) }
    var keywords by remember { mutableStateOf(initialKeywords) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记忆") },
        text = {
            Column {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("关键词（逗号分隔）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(content, keywords) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 分类中文标签（详情页私有） */
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

/** 时间范围中文标签（详情页私有） */
private val TimeScope.label: String
    get() = when (this) {
        TimeScope.SHORT_TERM -> "短期"
        TimeScope.LONG_TERM -> "长期"
    }

/** 可见性中文标签（详情页私有） */
private val MemoryVisibility.label: String
    get() = when (this) {
        MemoryVisibility.SHARED -> "共享"
        MemoryVisibility.PRIVATE -> "私密"
    }

/** 完整时间格式化（详情页私有） */
private fun formatFullTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))