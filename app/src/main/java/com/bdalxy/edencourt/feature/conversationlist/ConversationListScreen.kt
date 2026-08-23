package com.bdalxy.edencourt.feature.conversationlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onNavigateToChat: (Long) -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("角色卡管理") },
                            onClick = { menuExpanded = false; onOpenCharacter() },
                        )
                        DropdownMenuItem(
                            text = { Text("记忆管理") },
                            onClick = { menuExpanded = false; onOpenMemory() },
                        )
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = { menuExpanded = false; onOpenSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("关于") },
                            onClick = { menuExpanded = false; onOpenAbout() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToChat(1L) }) {
                Icon(Icons.Filled.Add, contentDescription = "新建对话")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("还没有对话，点击右下角开始")
        }
    }
}