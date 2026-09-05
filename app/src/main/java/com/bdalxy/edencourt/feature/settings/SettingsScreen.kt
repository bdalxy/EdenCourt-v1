package com.bdalxy.edencourt.feature.settings

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown

import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.data.schedule.ScheduleTrait
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import java.util.Locale

/** 常用 OpenAI 兼容平台地址预设（T3.10，可选其一后仍可手输修改） */
private val PLATFORM_PRESETS = listOf(
    "https://api.deepseek.com/v1",  // DeepSeek 官方
    "https://api.openai.com/v1",    // OpenAI
    "http://localhost:11434/v1",    // 本地 Ollama
)

/** 常用模型预设（T3.10，默认 deepseek-v4-flash，可手输任意模型名） */
private val MODEL_PRESETS = listOf(
    "deepseek-v4-flash",
    "deepseek-chat",
    "deepseek-reasoner",
)

/** 设置页：AI API 配置 + 我的身份 + 角色作息 + 互动中心入口（T3.3 + T8.13 + T11.10 + T11.13，参考企划书 5.3.5） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    // T11.13：互动中心入口（从设置页也能进入）
    onOpenInteraction: () -> Unit,
    // v2.0：关于页入口（底部 Tab 改造后从三点菜单迁到设置页）
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current.applicationContext as Application
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    // 作为底部 Tab 根页面时 onBack 为 null，不显示返回箭头
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 第 1 区：AI API 配置（T3.3）；长分组改为可折叠（B2 设置页单栏折叠），默认收起
            item {
                CollapsibleSection(title = "AI API 配置") {
                    SettingsSectionCard { ApiSettingsSection(viewModel = viewModel) }
                }
            }
            // 第 2 区：我的身份（用户自设角色卡身份，T8.13）；可折叠
            item {
                CollapsibleSection(title = "我的身份") {
                    SettingsSectionCard { IdentitySettingsSection(viewModel = viewModel) }
                }
            }
            // 第 3 区：角色作息（T11.10，可调，即时生效）；可折叠
            item {
                CollapsibleSection(title = "角色作息") {
                    SettingsSectionCard { ScheduleSettingsSection(viewModel = viewModel) }
                }
            }
            // 第 4 区：互动中心入口（T11.13，从设置页也能进入：查看/发起互动、自动驱动、关系故事）
            item { InteractionCenterSection(onOpenInteraction = onOpenInteraction) }
            // 第 5 区：关于（v2.0：底部 Tab 改造后从三点菜单迁到设置页）
            item { AboutSection(onOpenAbout = onOpenAbout) }
            // 开发者工具区：仅 debug 构建可见（性能压测种子数据，M3 验收前提）
            if (isDebugBuild(context)) {
                item { DebugSection(viewModel = viewModel) }
            }
        }
    }
}

/** 是否为 debug 构建（无 BuildConfig 时用 ApplicationInfo 调试标志判断，release 恒为 false） */
private fun isDebugBuild(application: Application): Boolean =
    (application.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

/**
 * B2：设置页可折叠分组（单栏折叠）。
 * 标题行点击展开/收起；收起时页面紧凑不超长，展开后展示内部分区卡片。
 */
@Composable
private fun CollapsibleSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // 展开时箭头旋转 180° 朝上
                modifier = Modifier
                    .size(24.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

/**
 * 设置页分组卡片（P0-3 设置页分组整理）：把每个功能区用一个柔和圆角卡片包起来，
 * 视觉上一目了然地分成「AI API / 我的身份 / 角色作息」等组块，避免平铺显得杂乱。
 * 内部分区各自保留自己的横向内边距（padding 由其子级负责），本卡片只负责外框与边距。
 */
@Composable
private fun SettingsSectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            content()
        }
    }
}

/** AI API 配置输入区：即时保存到 DataStore（外层已可滚动，本区用 Column 避免嵌套滚动） */
@Composable
private fun ApiSettingsSection(viewModel: SettingsViewModel) {
    val config by viewModel.apiConfig.collectAsStateWithLifecycle()
    val testState by viewModel.testState.collectAsStateWithLifecycle()

    // 文本输入用本地状态：输入即时响应，DataStore 异步写回不干扰光标
    var baseUrl by rememberSaveable { mutableStateOf(config.baseUrl) }
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey) }
    var model by rememberSaveable { mutableStateOf(config.model) }
    var maxTokens by rememberSaveable { mutableStateOf(config.maxTokens.toString()) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    // 首次进入从 DataStore 加载一次（避免覆盖用户正在输入的值）
    LaunchedEffect(Unit) {
        if (!initialized) {
            baseUrl = config.baseUrl
            apiKey = config.apiKey
            model = config.model
            maxTokens = config.maxTokens.toString()
            initialized = true
        }
    }

    // API Key 明文/密文切换
    var showKey by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "AI API",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        // Base URL：平台预设下拉 + 可手输（T3.10）
        PresetOutlinedTextField(
            value = baseUrl,
            onValueChange = {
                baseUrl = it
                viewModel.updateBaseUrl(it)
            },
            label = "Base URL",
            presets = PLATFORM_PRESETS,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                viewModel.updateApiKey(it)
            },
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(if (showKey) "隐藏" else "显示")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // 模型名：模型预设下拉 + 可手输（T3.10）
        PresetOutlinedTextField(
            value = model,
            onValueChange = {
                model = it
                viewModel.updateModel(it)
            },
            label = "模型名",
            presets = MODEL_PRESETS,
        )
        Column {
            Text("温度：${String.format(Locale.US, "%.1f", config.temperature)}")
            Slider(
                value = config.temperature.toFloat(),
                onValueChange = { viewModel.updateTemperature(it.toDouble()) },
                valueRange = 0f..2f,
                steps = 19, // 0.1 步进
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = maxTokens,
            onValueChange = { newValue ->
                // 仅允许数字输入
                if (newValue.all { it.isDigit() }) {
                    maxTokens = newValue
                    viewModel.updateMaxTokens(newValue)
                }
            },
            label = { Text("max_tokens") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        // 测试连接按钮 + 结果提示（T3.9）
        Column {
            Button(
                onClick = { viewModel.testConnection() },
                enabled = testState != TestState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (testState == TestState.Loading) "测试中…" else "测试连接")
            }
            when (val state = testState) {
                is TestState.Success -> Text(
                    text = "连接成功",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                is TestState.Error -> Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> Unit
            }
        }
        Text(
            text = "Key 仅保存在本机 DataStore，不会上传。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** 第 2 区：我的身份（用户自设角色卡身份，T8.13）——唯一绑定，所有角色默认知道并统一称呼 */
@Composable
private fun IdentitySettingsSection(viewModel: SettingsViewModel) {
    val identity by viewModel.userIdentity.collectAsStateWithLifecycle()

    // 编辑态本地状态：初始用当前身份值，点「保存身份」才统一写库（避免逐字写回产生半成品）
    var name by rememberSaveable { mutableStateOf(identity?.name ?: "我") }
    var calling by rememberSaveable { mutableStateOf(identity?.calling ?: "你") }
    var description by rememberSaveable { mutableStateOf(identity?.description ?: "") }
    var personality by rememberSaveable { mutableStateOf(identity?.personality ?: "") }
    var background by rememberSaveable { mutableStateOf(identity?.background ?: "") }
    var saved by rememberSaveable { mutableStateOf(false) }

    // 身份在「加载完成」或「保存成功」后同步到编辑态；不会覆盖用户正在输入的内容
    LaunchedEffect(identity) {
        name = identity?.name ?: "我"
        calling = identity?.calling ?: "你"
        description = identity?.description ?: ""
        personality = identity?.personality ?: ""
        background = identity?.background ?: ""
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "我的身份",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "给自己安排一个唯一身份，所有角色默认知道，并统一用「称呼」叫你。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; saved = false },
            label = { Text("名字/昵称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = calling,
            onValueChange = { calling = it; saved = false },
            label = { Text("别人对你的称呼") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it; saved = false },
            label = { Text("身份自述（我是谁，一句话）") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = personality,
            onValueChange = { personality = it; saved = false },
            label = { Text("性格（可选）") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = background,
            onValueChange = { background = it; saved = false },
            label = { Text("背景（可选）") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                viewModel.saveIdentity(name, calling, description, personality, background)
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存身份")
        }
        if (saved) {
            Text(
                text = "已保存：所有角色将统一以「$calling」称呼你",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * 第 3 区：角色作息（T11.10 深化：全简化版）。
 * - 全局总开关：默认关闭，开启后角色才随真实时间显示「此刻状态」
 * - 只保留「起床 + 入睡」两个滑杆（入睡支持次日凌晨），其余时段按固定规则划分
 * - 各角色可在此基础上的角色卡详情/会话角色单独调节
 */
@Composable
private fun ScheduleSettingsSection(viewModel: SettingsViewModel) {
    val config by viewModel.scheduleConfig.collectAsStateWithLifecycle()
    val enabled by viewModel.scheduleEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "角色作息",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        // 全局总开关（T11.10 深化两级开关第一级）
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) "已开启" else "已关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "开启后，再到对应会话角色顶部开启，才会显示「此刻状态」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { viewModel.setScheduleEnabled(it) },
            )
        }
        Text(
            text = "角色们按照起床/入睡作息「生活」，其余时段按固定规则（晨起→活跃→晚间放松）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 起床 / 入睡：定义醒着的时间窗（入睡支持跨天，最晚次日 01:59）
        TimeSlider(
            label = "起床",
            minute = config.wakeUpMinute,
            range = 4 * 60..10 * 60,
            onValueChange = { value -> viewModel.updateScheduleConfig { it.copy(wakeUpMinute = value) } },
        )
        TimeSlider(
            label = "入睡",
            minute = config.sleepMinute,
            range = 20 * 60..ScheduleConfig.MAX_SLEEP_MINUTE,
            onValueChange = { value -> viewModel.updateScheduleConfig { it.copy(sleepMinute = value) } },
        )
        // 恢复默认作息
        TextButton(
            onClick = { viewModel.resetScheduleConfig() },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("恢复默认作息")
        }
        // 各角色个性作息（T11.10 深化）：基础作息 + 角色个性派生，只读概览
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "各角色作息",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "根据角色设定（性格/背景/职业）自动生成；也可在「角色卡管理 → 角色详情」或各会话顶部单独调节：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val previews by viewModel.characterSchedulePreviews.collectAsStateWithLifecycle()
        if (previews.isEmpty()) {
            Text(
                text = "暂无角色卡，先去「角色卡管理」创建角色吧",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            previews.forEach { p ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CharacterAvatar(
                        avatarPath = p.avatar,
                        name = p.name,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = p.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = p.trait.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = traitColor(p.trait),
                            )
                            // T11.10 深化：手动设置过自定义作息的角色打「自定义」标记
                            if (p.isCustom) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "·自定义",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "起床 ${p.wakeUp} · 入睡 ${p.sleep}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 作息特征配色（T11.10 深化：设置页「各角色作息」标签颜色） */
private fun traitColor(trait: ScheduleTrait): Color = when (trait) {
    ScheduleTrait.NORMAL -> Color(0xFF64B5F6)   // 蓝：标准作息
    ScheduleTrait.MORNING -> Color(0xFFFFB74D)  // 橙：晨光早型
    ScheduleTrait.NIGHT -> Color(0xFF9575CD)    // 紫：夜晚夜型
}

/**
 * 作息时间滑杆（T11.10）：显示 HH:mm，拖动即时回调更新配置。
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

/** 分钟数 → HH:mm（T11.10 作息滑杆标签显示；>=24 点表示为次日，如 25:00→次日 01:00） */
private fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return if (h >= 24) {
        "次日 %02d:%02d".format(h - 24, m)
    } else {
        String.format(Locale.US, "%02d:%02d", h, m)
    }
}

/**
 * 带预设下拉的可编辑输入框（T3.10）：点击右侧箭头展开预设菜单，选中即填入；也可自由手输任意值。
 * 用于 Base URL（平台预设）与模型名（模型预设）。
 */
@Composable
private fun PresetOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    presets: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择预设")
                }
            },
        )
        // 下拉菜单：以输入框区域为锚点弹出
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset) },
                    onClick = {
                        onValueChange(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * 第 4 区：互动中心入口（T11.13，从设置页也能进入）。
 * 紧凑导航卡：点击跳转互动中心（查看/发起角色互动、自动驱动设置、关系故事时间线）。
 * 做成单行入口而非展开大区块，保持设置页清爽不杂乱。
 */
@Composable
private fun InteractionCenterSection(onOpenInteraction: () -> Unit) {
    Card(
        onClick = onOpenInteraction,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "互动中心",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "查看与发起角色互动、自动驱动、关系故事（共同经历）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 关于页入口（v2.0：底部 Tab 改造后，从原会话列表三点菜单迁到设置页，保证 About 页可达）。
 */
@Composable
private fun AboutSection(onOpenAbout: () -> Unit) {
    Card(
        onClick = onOpenAbout,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "版本信息与应用说明",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 开发者工具区（仅 debug 构建显示，release 经 isDebugBuild 恒隐藏）。
 * - 「生成千条压测数据」：调用 SeedDataTool 新建一次性测试会话并灌入千条消息/记忆，
 *   用于真机实测 聊天页滚动 与 记忆页分页 的流畅度（M3「千条数据 60fps」验收前提）。
 * - 生成只放独立【压测】会话/记忆，不触碰正式会话；运行结果用文本就地反馈。
 */
@Composable
private fun DebugSection(viewModel: SettingsViewModel) {
    val seedState by viewModel.seedState.collectAsStateWithLifecycle()
    val statusText = when (val s = seedState) {
        is SeedState.Idle -> "生成后可在对话列表看到【压测·1】会话，进入验证千条消息滚动流畅度。"
        is SeedState.Running -> "正在生成千条数据，请稍候…"
        is SeedState.Done -> "已生成，测试会话 id=${s.conversationId}（对话列表可见【压测·1】）。"
        is SeedState.Error -> s.message
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsSectionTitle("开发者工具（仅调试构建）")
        Button(
            onClick = { viewModel.generateSeedData(tag = 1) },
            enabled = seedState !is SeedState.Running,
        ) {
            Text(if (seedState is SeedState.Running) "生成中…" else "生成千条压测数据")
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 设置页分区小标题（复用现有标题样式，供 DebugSection 等分区使用） */
@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}
