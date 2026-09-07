package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bdalxy.edencourt.data.local.entity.InteractionStatus

/**
 * 互动面板（v1.2b T8.11，聊天页顶部「互动」图标弹出）。
 * - 「开放互动」开关：控制本会话能否成为互动成员（persist 到 conversations.interactionEnabled）
 * - 「本会话互动记录」：对方名 + 该会话视角摘要
 * - 「前往互动中心」：看全部互动、发起新互动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InteractionSheet(
    roleName: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    records: List<InteractionRecordItem>,
    relationships: List<RelationshipItem>,
    onOpenCenter: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            // 开放互动开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开放角色互动", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "开启后会被其他会话选中互动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 本会话互动记录
            Text(
                text = "本会话互动记录 · $roleName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (records.isEmpty()) {
                Text(
                    text = "暂无互动记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                records.forEach { r ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "与 ${r.peerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = interactionStatusText(r.status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        if (r.summary.isNotBlank()) {
                            Text(
                                text = r.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 我的关系（好感度）：本会话角色与各会话角色的好感度数值 + 档位（T11.6 好感度细分到会话角色）
            Text(
                text = "我的关系（好感度） · $roleName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (relationships.isEmpty()) {
                Text(
                    text = "暂无好感度数据，去互动中心发起互动吧",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                relationships.forEach { r ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 关系为内部状态，浓度数值不外显（2026-09-07 R5 拍板），仅保留角色名
                            Text(
                                text = "与 ${r.peerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            text = "已互动 ${r.interactionCount} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 前往互动中心
            TextButton(
                onClick = onOpenCenter,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("前往互动中心")
            }
        }
    }
}

/** 互动状态中文标签（互动面板记录用） */
private fun interactionStatusText(status: String): String = when (status) {
    InteractionStatus.COMPLETED.name -> "已完成"
    InteractionStatus.RUNNING.name -> "进行中"
    InteractionStatus.SKIPPED.name -> "已跳过"
    InteractionStatus.FAILED.name -> "未完成"
    else -> "待发起"
}