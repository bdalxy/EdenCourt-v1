package com.bdalxy.edencourt.feature.worldbook

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.WorldInfoDao
import com.bdalxy.edencourt.data.local.entity.WorldInfoBook
import com.bdalxy.edencourt.data.local.entity.WorldInfoEntry
import com.bdalxy.edencourt.domain.TokenEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 世界书编辑页 ViewModel（C 阶段）。
 * bookId<=0 表示「新建世界书」；>0 表示编辑既有书（书名/简介 + 条目管理）。
 * 读写 DB 放 IO，用 StateFlow 暴露最新书名/条目，避免主线程阻塞。
 */
class WorldBookEditViewModel(
    private val bookId: Long,
    private val dao: WorldInfoDao,
) : ViewModel() {

    /** 当前书（新建场景 bookId<=0 时为 null，创建成功后再回填） */
    private val _book = MutableStateFlow(if (bookId > 0) dao.getBook(bookId) else null)
    val book: StateFlow<WorldInfoBook?> = _book.asStateFlow()

    /** 该书条目列表 */
    private val _entries = MutableStateFlow(if (bookId > 0) dao.getEntriesByBook(bookId) else emptyList())
    val entries: StateFlow<List<WorldInfoEntry>> = _entries.asStateFlow()

    /** 一次性操作反馈（保存/删除后提示） */
    val toast = MutableStateFlow<String?>(null)

    /** 一次性读取即重查条目（新增/删除/更新后调用保证列表最新） */
    private fun reloadEntries() {
        val current = _book.value?.id
        if (current != null) _entries.value = dao.getEntriesByBook(current)
    }

    /** 新建世界书（bookId<=0 时调用）。返回新 id，成功后写入 _book 并重查条目 */
    fun createBook(name: String, description: String) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                dao.insertBook(WorldInfoBook(name = name.trim(), description = description.trim()))
            }
            _book.value = dao.getBook(id)
            reloadEntries()
            toast.value = "已创建"
        }
    }

    /** 更新书名/简介（既有书） */
    fun updateBook(name: String, description: String) {
        val current = _book.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateBook(current.copy(name = name.trim(), description = description.trim()))
            }
            _book.value = current.copy(name = name.trim(), description = description.trim())
            toast.value = "已保存"
        }
    }

    /** 新增条目 */
    fun addEntry(
        title: String,
        keys: String,
        secondaryKeys: String,
        content: String,
        priority: Int,
        constant: Boolean,
        enabled: Boolean,
    ) {
        val current = _book.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.insertEntry(
                    WorldInfoEntry(
                        bookId = current.id,
                        title = title.trim(),
                        keys = keys.trim(),
                        secondaryKeys = secondaryKeys.trim(),
                        content = content.trim(),
                        priority = priority,
                        constant = constant,
                        enabled = enabled,
                    )
                )
            }
            reloadEntries()
            toast.value = "已添加"
        }
    }

    /** 更新条目 */
    fun updateEntry(
        entryId: Long,
        title: String,
        keys: String,
        secondaryKeys: String,
        content: String,
        priority: Int,
        constant: Boolean,
        enabled: Boolean,
    ) {
        val target = _entries.value.firstOrNull { it.id == entryId } ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.updateEntry(
                    target.copy(
                        title = title.trim(),
                        keys = keys.trim(),
                        secondaryKeys = secondaryKeys.trim(),
                        content = content.trim(),
                        priority = priority,
                        constant = constant,
                        enabled = enabled,
                    )
                )
            }
            reloadEntries()
            toast.value = "已更新"
        }
    }

    /** 删除条目 */
    fun deleteEntry(entry: WorldInfoEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { dao.deleteEntry(entry) }
            reloadEntries()
            toast.value = "已删除"
        }
    }

    /** 删除整本世界书（仅管理页提供） */
    fun deleteBook() {
        val current = _book.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _entries.value.forEach { dao.deleteEntry(it) }
                dao.deleteBook(current)
            }
            toast.value = "已删除"
        }
    }

    fun clearToast() {
        toast.value = null
    }

    companion object {
        fun Factory(bookId: Long, app: EdenCourtApp) = viewModelFactory {
            initializer {
                WorldBookEditViewModel(bookId, app.worldInfoDao)
            }
        }
    }
}

/**
 * 世界书编辑页：新建书（bookId<=0）/ 编辑书（书信息 + 条目列表 + 增删改）。
 * worldBookId 由挂载流程（B1）写入会话，本页只管书与条目的 CRUD。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookEditScreen(
    bookId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as EdenCourtApp
    val viewModel: WorldBookEditViewModel = viewModel(
        key = "worldbook_edit_$bookId",
        factory = WorldBookEditViewModel.Factory(bookId, app),
    )
    val book by viewModel.book.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()

    val isNew = bookId <= 0
    // 书名/简介输入（编辑态以既有值预填）
    var name by remember(bookId) { mutableStateOf(book?.name.orEmpty()) }
    var description by remember(bookId) { mutableStateOf(book?.description.orEmpty()) }
    // 条目编辑对话框
    var editingEntry by remember { mutableStateOf<WorldInfoEntry?>(null) }          // 非 null = 编辑
    var showAddEntry by remember { mutableStateOf(false) }                          // true = 新增

    // 删除键
    val hasUnsavedName = bookId > 0 && (name.trim() != book?.name || description.trim() != book?.description)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建世界书" else (book?.name?.ifBlank { "世界书" } ?: "世界书")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            if (isNew || book !== null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .weight(1f, fill = false)
                        .padding(16.dp),
                ) {
                    Text("书名", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        singleLine = true,
                        placeholder = { Text("如：幻想大陆设定集") },
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text("简介（不注入，仅管理页展示）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        singleLine = true,
                        placeholder = { Text("一句话描述这本世界书") },
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    OutlinedButton(
                        onClick = {
                            if (isNew) {
                                if (name.isBlank()) {
                                    viewModel.toast.value = "请先填写书名"
                                } else {
                                    viewModel.createBook(name, description)
                                }
                            } else {
                                if (name.isBlank()) {
                                    viewModel.toast.value = "书名不能为空"
                                } else {
                                    viewModel.updateBook(name, description)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isNew) "创建世界书" else "保存书信息")
                    }

                    if (!isNew) {
                        Spacer(modifier = Modifier.size(20.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.size(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "条目（${entries.size}）",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedButton(onClick = { showAddEntry = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" 添加条目")
                            }
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        if (entries.isEmpty()) {
                            Text(
                                text = "还没有条目。条目通过「关键词」触发，聊到相关词才注入对应设定。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        entries.forEach { entry ->
                            EntryRow(
                                entry = entry,
                                onEdit = { editingEntry = entry },
                                onDelete = { viewModel.deleteEntry(entry) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(24.dp))
                }
            }

            // 底部操作：删除整本书（仅既有书）
            if (!isNew) {
                TextButton(
                    onClick = {
                        viewModel.deleteBook()
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("删除这本世界书", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // 新增条目对话框
    if (showAddEntry) {
        EntryEditDialog(
            title = "添加条目",
            initial = null,
            onDismiss = { showAddEntry = false },
            onConfirm = { t, k, sk, c, p, const, en ->
                viewModel.addEntry(t, k, sk, c, p, const, en)
                showAddEntry = false
            },
        )
    }
    // 编辑条目对话框
    editingEntry?.let { entry ->
        EntryEditDialog(
            title = "编辑条目",
            initial = entry,
            onDismiss = { editingEntry = null },
            onConfirm = { t, k, sk, c, p, const, en ->
                viewModel.updateEntry(entry.id, t, k, sk, c, p, const, en)
                editingEntry = null
            },
        )
    }
}

/** 单条世界书条目行：标题 + 关键词摘要 + token + 常驻/启用标记 + 点编辑 + 删除 */
@Composable
private fun EntryRow(
    entry: WorldInfoEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { "（未命名条目）" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = summaryOf(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = "~${TokenEstimator.count(entry.content)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** 条目的关键词/状态摘要文案 */
private fun summaryOf(entry: WorldInfoEntry): String {
    val flags = buildString {
        if (entry.constant) append("常驻 ")
        if (!entry.enabled) append("停用 ")
    }
    val keys = entry.keys.trim()
    return if (keys.isNotBlank()) "${flags}触发：「$keys」" else flags.ifBlank { "无关键词（仅常驻可命中）" }
}

/** 条目新增/编辑对话框（title/主关键词/副关键词/内容/优先级/常驻/启用全部可编辑） */
@Composable
private fun EntryEditDialog(
    title: String,
    initial: WorldInfoEntry?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, keys: String, secondaryKeys: String, content: String, priority: Int, constant: Boolean, enabled: Boolean) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var keys by remember(initial?.id) { mutableStateOf(initial?.keys.orEmpty()) }
    var secondaryKeys by remember(initial?.id) { mutableStateOf(initial?.secondaryKeys.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var priority by remember(initial?.id) { mutableStateOf(initial?.priority ?: 50) }
    var constant by remember(initial?.id) { mutableStateOf(initial?.constant ?: false) }
    var enabled by remember(initial?.id) { mutableStateOf(initial?.enabled ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("标题") },
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = keys,
                    onValueChange = { keys = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("主关键词（逗号分隔）") },
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = secondaryKeys,
                    onValueChange = { secondaryKeys = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("副关键词（可选，收窄命中）") },
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("条目内容（聊到关键词时注入）") },
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = priority.toString(),
                    onValueChange = { priority = it.toIntOrNull() ?: 50 },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("优先级（数值越小越靠前，默认 50）") },
                )
                Spacer(modifier = Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("常驻（每次必注入）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = constant, onCheckedChange = { constant = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name, keys, secondaryKeys, content, priority.coerceIn(0, 999), constant, enabled)
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}