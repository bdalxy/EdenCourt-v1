package com.bdalxy.edencourt.feature.character

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.scheduleTrait
import com.bdalxy.edencourt.data.setting.ScheduleConfig
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
    // T11.10 深化：全局基础作息（角色详情页「基于基础作息单独调节」的起点）
    val scheduleConfig by viewModel.scheduleConfig.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 待删除确认的角色卡（长按触发）
    var pendingDelete by remember { mutableStateOf<CharacterCardEntity?>(null) }
    // 待编辑详情的角色卡（点击触发）
    var editingCard by remember { mutableStateOf<CharacterCardEntity?>(null) }
    // 角色管理页 FAB 操作菜单：新建角色 / 导入 JSON（T6.12）
    var fabMenuExpanded by remember { mutableStateOf(false) }
    // 新建角色表单开关（T6.12）
    var creating by remember { mutableStateOf(false) }
    // 新建角色暂存头像路径（点保存时才随卡入库；null=用默认首字头像）
    var newAvatarPath by remember { mutableStateOf<String?>(null) }

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
    // 编辑详情时直接写到该卡；新建角色时暂存到 newAvatarPath（T6.12）
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = saveAvatarFromUri(context, uri)
            val card = editingCard
            when {
                card != null -> {
                    // 编辑已有角色卡：选中后立即生效
                    if (path != null) {
                        viewModel.updateAvatar(card, path)
                        snackbarHostState.showSnackbar("头像已更新")
                    } else {
                        snackbarHostState.showSnackbar("头像处理失败，请重试")
                    }
                }
                else -> {
                    // 新建角色：暂存头像，保存卡片时一并入库
                    newAvatarPath = path
                    if (path == null) snackbarHostState.showSnackbar("头像处理失败，请重试")
                }
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
                    // 导入中忽略重复点击；否则弹出「新建/导入」操作菜单（T6.12）
                    if (!importing) fabMenuExpanded = true
                },
            ) {
                if (importing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = "新建/导入角色卡")
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
            scheduleConfig = scheduleConfig,
            onSave = { name, description, remark, schedule ->
                viewModel.updateCardInfo(liveCard, name, description, remark)
                // 作息单独写库（schedule=null 表示恢复跟随默认；未改动时等价重写，幂等无害）
                viewModel.updateSchedule(liveCard, schedule)
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

    // 角色管理页 FAB 操作菜单（T6.12）：新建角色 / 导入 JSON 两条路并存
    if (fabMenuExpanded) {
        ModalBottomSheet(
            onDismissRequest = { fabMenuExpanded = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            fabMenuExpanded = false
                            newAvatarPath = null
                            creating = true
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text("新建角色", style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            fabMenuExpanded = false
                            filePicker.launch("application/json")
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text("导入 JSON 文件", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // 新建角色对话框（T6.12）：名称/简介/性格 + 头像，保存后生成非内置角色卡
    if (creating) {
        CreateCardDialog(
            avatarPath = newAvatarPath,
            onPickAvatar = {
                // 系统图片选择器（仅图片）
                avatarPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onSave = { name, personality, background ->
                viewModel.createCard(name, personality, background, newAvatarPath) { err ->
                    if (err == null) {
                        scope.launch { snackbarHostState.showSnackbar("角色创建成功") }
                        creating = false
                        newAvatarPath = null
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(err) }
                    }
                }
            },
            onDismiss = { creating = false },
        )
    }
}

/** 新建角色对话框（T6.12）：头像 + 名称/简介/性格输入，保存后创建一张非内置角色卡 */
@Composable
private fun CreateCardDialog(
    avatarPath: String?,
    onPickAvatar: () -> Unit,
    onSave: (name: String, personality: String, background: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建角色") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // 头像区域：预览（自定义图片或默认首字）+ 选择头像按钮
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterAvatar(
                        avatarPath = avatarPath,
                        name = name.ifBlank { "新" },
                        modifier = Modifier.size(64.dp),
                        textSize = 24.sp,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(onClick = onPickAvatar) {
                        Text(if (avatarPath != null) "更换头像" else "设置头像")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（必填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = { Text("性格设定（必填，决定 AI 口吻）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = background,
                    onValueChange = { background = it },
                    label = { Text("简介/背景（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, personality, background) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
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
                    // T6.14：备注别名展示，未设则用原名（头像首字仍取 card.name，类微信）
                    text = card.remark.ifBlank { card.name },
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
 * 头像（可更换/恢复默认，T6.2B）+ 备注/名称/简介可编辑并保存；JSON 内容只读滚动展示。
 * T11.10 深化：新增「作息设置」区——基于基础作息单独调节该角色，未调节跟随默认。
 */
@Composable
private fun CharacterDetailDialog(
    card: CharacterCardEntity,
    scheduleConfig: ScheduleConfig,
    onSave: (name: String, description: String, remark: String, schedule: ScheduleConfig?) -> Unit,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(card.id) { mutableStateOf(card.name) }
    var description by remember(card.id) { mutableStateOf(card.description) }
    var remark by remember(card.id) { mutableStateOf(card.remark) }
    // 作息调节区（T11.10 深化）：
    // 生效默认作息 = 基础作息 + 个性偏移（「跟随默认」时的展示值）。
    // 注意：必须用 applyTrait 而非 resolveConfig——resolveConfig 在角色已有自定义时返回自定义，
    // 会导致「恢复跟随默认」后滑杆仍显示旧自定义值（历史 bug）。
    val defaultCfg = CharacterSchedule.applyTrait(scheduleConfig, card.scheduleTrait())
    // 当前自定义作息（null=跟随默认）；拖动滑杆暂存，点「保存」一并写库
    var customCfg by remember(card.id) { mutableStateOf(card.customSchedule) }
    // 滑杆展示值：自定义优先，否则生效默认
    val shownCfg = customCfg ?: defaultCfg

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
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（显示名，留空用原名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                // 作息设置区（T11.10 深化）：基于基础作息单独调节，未调节跟随默认
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "作息设置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    // 当前模式标签：跟随默认（含特征）或自定义
                    Text(
                        text = if (customCfg == null) "跟随默认·${card.scheduleTrait().label}" else "已自定义",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (customCfg == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Text(
                    text = "拖动下方滑杆即可单独设置该角色的起床/入睡，不设置自动跟随默认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TimeSlider(
                    label = "起床",
                    minute = shownCfg.wakeUpMinute,
                    range = 4 * 60..10 * 60,
                    onValueChange = { v -> customCfg = (customCfg ?: defaultCfg).copy(wakeUpMinute = v) },
                )
                TimeSlider(
                    label = "入睡",
                    minute = shownCfg.sleepMinute,
                    range = 20 * 60..ScheduleConfig.MAX_SLEEP_MINUTE,
                    onValueChange = { v -> customCfg = (customCfg ?: defaultCfg).copy(sleepMinute = v) },
                )
                // 恢复跟随默认（清除自定义作息）
                if (customCfg != null) {
                    TextButton(
                        onClick = { customCfg = null },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("恢复跟随默认")
                    }
                }
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
                    onSave(name.trim(), description.trim(), remark.trim(), customCfg)
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 作息时间滑杆（T11.10 深化，风格对齐设置页 TimeSlider）：
 * 显示 HH:mm，拖动即时回调更新该角色自定义作息。
 * @param minute 当前分钟数（0~1439）
 * @param range 可调范围（分钟）
 */
@Composable
private fun TimeSlider(
    label: String,
    minute: Int,
    range: IntRange, // 分钟范围（起点为整点，如 4*60..10*60）
    onValueChange: (Int) -> Unit,
) {
    // T11.10 优化：从整点起每 5 分钟一格。用「格索引」作为滑块值（离散、无 steps 细分），
    // 拖动顺滑且每次变化都精确落在整点+5分钟倍数的格点上，不再大量 steps 导致卡顿。
    val start = range.first
    val slotMin = 5
    val maxSlot = (range.last - range.first) / slotMin
    // 当前分钟值吸附到最近的 5 分钟格索引（用于滑块定位）
    val currentSlot = ((minute - start).coerceIn(0, range.last - range.first) + slotMin / 2) / slotMin

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label：${formatMinute(minute)}")
        Slider(
            value = currentSlot.toFloat(),
            onValueChange = { onValueChange(start + it.toInt() * slotMin) },
            valueRange = 0f..maxSlot.toFloat(),
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 分钟数 → HH:mm（T11.10 深化：作息滑杆标签显示；>=24 点表示为次日，如 25:00→次日 01:00） */
private fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return if (h >= 24) {
        "次日 %02d:%02d".format(h - 24, m)
    } else {
        "%02d:%02d".format(h, m)
    }
}
