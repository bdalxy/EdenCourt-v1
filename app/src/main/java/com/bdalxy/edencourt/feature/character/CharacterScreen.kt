package com.bdalxy.edencourt.feature.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.core.ui.saveAvatarFromUri
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import kotlinx.coroutines.launch

/**
 * 角色卡管理页（T4.3，参考企划书 5.3.3/5.8.3 原型）。
 * - 列表：头像+角色名+描述+「内置/导入」标签；内置卡优先
 * - FAB 导入 JSON 文件（基础解析；Tavern 兼容映射 T4.5 增强）
 * - 点击进详情：可编辑名称/简介，JSON 只读
 * - 长按删除：内置卡不可删，非内置二次确认
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CharacterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as EdenCourtApp
    val viewModel: CharacterViewModel = viewModel(factory = CharacterViewModel.factory(app))
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 待删除确认的角色卡（长按触发）
    var pendingDelete by remember { mutableStateOf<CharacterCardEntity?>(null) }
    // 待编辑详情的角色卡（点击触发）
    var editingCard by remember { mutableStateOf<CharacterCardEntity?>(null) }

    // JSON 文件选择器：选中后读取内容并导入
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jsonText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (jsonText.isNullOrBlank()) {
                snackbarHostState.showSnackbar("文件为空或无法读取")
            } else {
                viewModel.importCard(jsonText) { error ->
                    val msg = error ?: "导入成功"
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            }
        }
    }

    // 头像选择器（T6.2B）：系统 Photo Picker 免权限选图，选中后自动圆形裁剪保存到私有目录
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null || editingCard == null) return@rememberLauncherForActivityResult
        val card = editingCard!!
        scope.launch {
            val path = saveAvatarFromUri(context, uri)
            if (path != null) {
                viewModel.updateAvatar(card, path)
                snackbarHostState.showSnackbar("头像已更新")
            } else {
                snackbarHostState.showSnackbar("头像处理失败，请重试")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // 导入中忽略重复点击
                    if (!importing) filePicker.launch("application/json")
                },
            ) {
                if (importing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = "导入角色卡")
                }
            }
        },
    ) { innerPadding ->
        if (cards.isEmpty()) {
            // 空状态：引导导入（企划书 5.5.2）
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有角色卡，点击右下角导入")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(items = cards, key = { it.id }) { card ->
                    CharacterCardListItem(
                        card = card,
                        onClick = { editingCard = card },
                        onLongClick = {
                            if (card.isBuiltIn) {
                                // 内置卡不可删（企划书 3.4.3），轻提示
                                scope.launch { snackbarHostState.showSnackbar("内置角色卡不可删除") }
                            } else {
                                pendingDelete = card
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }

    // 长按删除二次确认对话框
    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除角色卡") },
            text = { Text("确定删除「${card.name}」？该卡将不再可用于新对话。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCard(card)
                        pendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    // 详情编辑对话框：头像（可更换/恢复默认）+ 名称/简介可编辑 + JSON 只读
    editingCard?.let { card ->
        // 用列表里的实时卡片数据渲染详情（头像更新后对话框内即时生效，T6.2B）
        val liveCard = cards.firstOrNull { it.id == card.id } ?: card
        CharacterDetailDialog(
            card = liveCard,
            onSave = { name, description ->
                viewModel.updateCardInfo(liveCard, name, description)
                editingCard = null
            },
            onPickAvatar = {
                // 系统图片选择器（仅图片）
                avatarPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onClearAvatar = { viewModel.updateAvatar(liveCard, null) },
            onDismiss = { editingCard = null },
        )
    }
}

/** 单个角色卡列表项：圆形头像（首字符）+ 名称 + 简介 + 内置/导入标签 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterCardListItem(
    card: CharacterCardEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 头像：有自定义头像显示图片，否则默认主题色圆形色块 + 角色名首字（T6.2B）
        CharacterAvatar(
            avatarPath = card.avatar,
            name = card.name,
            modifier = Modifier.size(48.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 右侧：名称 + 标签（纵向），简介（下一行）
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 内置/导入标签
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (card.isBuiltIn) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                ) {
                    Text(
                        text = if (card.isBuiltIn) "内置" else "导入",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (card.isBuiltIn) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.description.ifEmpty { "暂无简介" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 详情编辑对话框（企划书 5.3.3）：
 * 头像（可更换/恢复默认，T6.2B）+ 名称/简介可编辑并保存；JSON 内容只读滚动展示。
 */
@Composable
private fun CharacterDetailDialog(
    card: CharacterCardEntity,
    onSave: (name: String, description: String) -> Unit,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(card.id) { mutableStateOf(card.name) }
    var description by remember(card.id) { mutableStateOf(card.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("角色卡详情") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 头像区域：当前头像 + 更换/恢复默认（T6.2B）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterAvatar(
                        avatarPath = card.avatar,
                        name = card.name,
                        modifier = Modifier.size(64.dp),
                        textSize = 24.sp,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        TextButton(onClick = onPickAvatar) {
                            Text(if (card.avatar != null) "更换头像" else "设置头像")
                        }
                        if (card.avatar != null) {
                            TextButton(onClick = onClearAvatar) {
                                Text("恢复默认头像")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("简介") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "JSON（只读）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // JSON 内容只读：固定高度内滚动查看
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = card.jsonContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 名称不允许为空，空则提示不保存
                    if (name.isBlank()) return@TextButton
                    onSave(name.trim(), description.trim())
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
