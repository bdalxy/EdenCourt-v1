package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * T7.4 + T11.6 会话记忆面板（会话级私有定稿 + 升级）：
 * - 「共享记忆」区：该角色卡的全部共享记忆（卡片式分区 + 计数角标）
 * - 「本会话私密记忆」区：仅本会话的私有记忆，跨会话隔离
 * - 顶部搜索框本地过滤两区；每条显示类型 chip + 内容 + 时间；底部「前往记忆管理页」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionMemorySheet(
    sharedMemories: List<MemoryItem>,
    privateMemories: List<MemoryItem>,
    cardName: String,
    onOpenMemoryManagement: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 搜索关键词：本地过滤共享/私有两区（按内容或关键词匹配）
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val sharedFiltered =
        if (q.isEmpty()) sharedMemories
        else sharedMemories.filter { it.content.contains(q) || it.keywords.contains(q) }
    val privateFiltered =
        if (q.isEmpty()) privateMemories
        else privateMemories.filter { it.content.contains(q) || it.keywords.contains(q) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "会话记忆 · $cardName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 搜索框
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索记忆…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 共享记忆分区卡片
            MemorySectionCard(
                title = "共享记忆 · $cardName",
                count = sharedFiltered.size,
                accent = false,
            ) {
                if (sharedFiltered.isEmpty()) {
                    EmptyMemoryText(if (q.isEmpty()) "暂无共享记忆" else "无匹配结果")
                } else {
                    sharedFiltered.forEach { MemorySheetItem(it) }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 本会话私密记忆分区卡片
            MemorySectionCard(
                title = "本会话私密记忆",
                count = privateFiltered.size,
                accent = true,
            ) {
                if (privateFiltered.isEmpty()) {
                    EmptyMemoryText(if (q.isEmpty()) "本会话暂无私密记忆" else "无匹配结果")
                } else {
                    privateFiltered.forEach { MemorySheetItem(it) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 前往记忆管理页（搜索/分类/时间轴/热词/图谱全功能）
            TextButton(
                onClick = onOpenMemoryManagement,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往记忆管理页")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** 记忆分区卡片：标题 + 计数角标 + 内容区（T11.6 记忆面板布局升级，卡片式清晰分隔两区） */
@Composable
private fun MemorySectionCard(
    title: String,
    count: Int,
    accent: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                // 计数角标（区分共享/私有用主题色/错误色）
                Surface(
                    shape = CircleShape,
                    color = if (accent) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (accent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

/** 记忆面板空态提示 */
@Composable
private fun EmptyMemoryText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
}

/** 记忆面板单条：类型 chip + 内容 + 时间（T11.6 升级：类型标识 + 时间右置一行） */
@Composable
private fun MemorySheetItem(memory: MemoryItem) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 类型 chip：浅色圆角小标签
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            ) {
                Text(
                    text = memory.type.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatMemoryTime(memory.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(memory.content, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 记忆类型中文标签（与记忆管理页一致，供会话记忆面板类型 chip 用） */
private val MemoryType.label: String
    get() = when (this) {
        MemoryType.PERSONAL_INFO -> "个人信息"
        MemoryType.PREFERENCE -> "偏好"
        MemoryType.EVENT -> "事件"
        MemoryType.SKILL -> "技能"
        MemoryType.SITUATION -> "情景"
        MemoryType.RELATION -> "关系"
        MemoryType.OTHER -> "其他"
    }

/** 记忆时间格式化：MM-dd HH:mm（区别于消息气泡的 formatTime） */
private fun formatMemoryTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))