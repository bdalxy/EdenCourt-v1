package com.bdalxy.edencourt.feature.memory

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.bdalxy.edencourt.data.local.entity.MemoryItem

/**
 * 记忆时间轴（阶段二可视化 T10.7）。
 * - 全库记忆按更新时间降序，按天分组（今天 / 昨天 / 具体日期）
 * - 每项显示类型·时间副标题 + 内容 + 归属角色
 * - 点击项进入记忆详情页（复用 MemoryDetailScreen 覆盖）
 * - 空状态提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: TimelineViewModel = viewModel(
        factory = TimelineViewModel.factory(context.applicationContext as Application),
    )
    val days by viewModel.days.collectAsStateWithLifecycle()

    // 订阅角色卡流：确保归属名解析有数据
    viewModel.characters.collectAsStateWithLifecycle()

    // 当前正在查看详情的记忆 id（null=时间轴；点击项进详情，T10.7 复用详情页）
    var detailId by remember { mutableStateOf<Long?>(null) }

    val detailIdValue = detailId
    if (detailIdValue != null) {
        MemoryDetailScreen(memoryId = detailIdValue, onBack = { detailId = null })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("记忆时间轴") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { innerPadding ->
            if (days.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无记忆，先跟角色聊聊吧",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    days.forEach { day ->
                        // 天分组标题（今天 / 昨天 / 日期）
                        item(key = "day-${day.label}") {
                            Text(
                                text = day.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                        items(day.items, key = { it.id }) { memory ->
                            TimelineItem(
                                memory = memory,
                                ownerName = viewModel.displayNameFor(memory.characterCardId),
                                subtitle = viewModel.subtitle(memory),
                                onClick = { detailId = memory.id },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 时间轴中的一条记忆：类型·时间副标题 + 内容 + 归属 */
@Composable
private fun TimelineItem(
    memory: MemoryItem,
    ownerName: String?,
    subtitle: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (ownerName != null) {
                Text(
                    text = ownerName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Text(
            text = memory.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp), thickness = 0.5.dp)
    }
}