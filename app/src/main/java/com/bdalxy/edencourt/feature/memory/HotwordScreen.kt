package com.bdalxy.edencourt.feature.memory

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.data.local.entity.MemoryItem

/**
 * 共现热词页（阶段三 T11.1）。
 * - 顶部：热词排行（词 + 出现条数），点击选中
 * - 下方：含选中词的记忆列表（content/keywords 命中），点击进详情
 * - 元信息复用：分类 · 时间副标题 + 归属角色；空状态提示
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotwordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: HotwordViewModel = viewModel(
        factory = HotwordViewModel.factory(context.applicationContext as Application),
    )
    val hotwords by viewModel.hotwords.collectAsStateWithLifecycle()
    val selectedWord by viewModel.selectedWord.collectAsStateWithLifecycle()
    val hits by viewModel.hitMemories.collectAsStateWithLifecycle()
    viewModel.characters.collectAsStateWithLifecycle()

    // 当前正在查看详情（点命中记忆进详情，复用 MemoryDetailScreen 覆盖）
    var detailId by remember { mutableStateOf<Long?>(null) }

    // 系统返回：拦截为「回到记忆列表」，避免直接退出到主界面（bugfix）；进详情时由详情页拦截
    BackHandler(enabled = detailId == null) { onBack() }

    val detailIdValue = detailId
    if (detailIdValue != null) {
        MemoryDetailScreen(memoryId = detailIdValue, onBack = { detailId = null })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("共现热词") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // 热词排行区
                Text(
                    text = "热词排行（点选查看对应记忆）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                if (hotwords.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "暂无关键词，先跟角色聊聊提取些记忆吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(hotwords) { w ->
                            AssistChip(
                                onClick = { viewModel.selectWord(w.word) },
                                label = {
                                    Text("${w.word} ${w.count}", fontSize = 13.sp)
                                },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // 命中「选中词」的记忆列表
                if (selectedWord == null) {
                    // 未选中：给引导
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "点上方任意热词，这里会列出相关记忆",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (hits.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "「$selectedWord」暂无相关记忆",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        // 顶部小标题：包含「词」的记忆 N 条
                        item(key = "hit-title") {
                            Text(
                                text = "包含「$selectedWord」的记忆 · ${hits.size} 条",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(hits, key = { it.id }) { memory ->
                            HotwordHitItem(
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

/** 命中「热词」的一条记忆：副标题(分类·时间) + 内容 + 归属（供 T11.1 热词页 / T11.5 图谱页复用） */
@Composable
internal fun HotwordHitItem(
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