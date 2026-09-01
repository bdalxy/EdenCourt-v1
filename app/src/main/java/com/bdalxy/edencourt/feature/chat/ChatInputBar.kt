package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 底部输入栏：多行输入框（1~5 行自动扩展）+ 发送按钮；生成中额外显示停止按钮（T5.10） */
@Composable
internal fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 多行输入框：内容变多自动扩展，最多 5 行
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 生成中额外显示「停止」按钮（仅停止，不发送）；发送按钮始终存在，
            // 生成中点发送 = 打断当前回复并发送新消息（T5.10 双向可打断）
            if (isGenerating) {
                FilledIconButton(onClick = onStop) {
                    Icon(Icons.Filled.Close, contentDescription = "停止生成")
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}