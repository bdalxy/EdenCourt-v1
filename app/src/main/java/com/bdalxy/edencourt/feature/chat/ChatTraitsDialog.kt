package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 印象摘要编辑对话框（T13.6 可视化区分）：用户可修改本会话角色的印象摘要 + 标签。
 * 保存后 generatedBy='user'，后续 LLM 生成不会覆盖用户的定义（T13.4 生成器只写非 user 来源）。
 */
@Composable
internal fun TraitsEditDialog(
    initialBlurb: String,
    initialTags: String,
    onConfirm: (blurb: String, tags: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var blurb by remember { mutableStateOf(initialBlurb) }
    var tags by remember { mutableStateOf(initialTags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑角色印象") },
        text = {
            Column {
                OutlinedTextField(
                    value = blurb,
                    onValueChange = { blurb = it },
                    label = { Text("印象摘要（别人眼中的 TA）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签（用顿号或逗号分隔，最多 3 个）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(blurb, tags) },
                enabled = blurb.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}