package com.bdalxy.edencourt.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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

/** 设置页：第 1 区 AI API 配置（T3.3，参考企划书 5.3.5） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext as Application
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // 第 1 区：AI API 配置（T3.3，可滚动）
            ApiSettingsSection(
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
            // 第 2 区：我的身份（用户自设角色卡身份，T8.13）
            IdentitySettingsSection(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** AI API 配置输入区：即时保存到 DataStore */
@Composable
private fun ApiSettingsSection(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
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

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "AI API",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item {
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
        }
        item {
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
        }
        item {
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
        }
        item {
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
        }
        item {
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
        }
        item {
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
        }
        item {
            Text(
                text = "Key 仅保存在本机 DataStore，不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** 第 2 区：我的身份（用户自设角色卡身份，T8.13）——唯一绑定，所有角色默认知道并统一称呼 */
@Composable
private fun IdentitySettingsSection(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
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
        modifier = modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
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
