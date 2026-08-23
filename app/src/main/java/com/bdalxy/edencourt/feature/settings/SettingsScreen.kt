package com.bdalxy.edencourt.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
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
        ApiSettingsSection(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
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
            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    viewModel.updateBaseUrl(it)
                },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    viewModel.updateModel(it)
                },
                label = { Text("模型名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
