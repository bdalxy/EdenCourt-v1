package com.bdalxy.edencourt.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** 底部 Tab 枚举（V5-1：底部导航栏） */
enum class BottomTab(
    val label: String,
    val icon: ImageVector,
    val route: String, // 对应路由根
) {
    /** 会话列表 */
    CHATS("会话", Icons.Filled.Chat, NavRoutes.CONVERSATION_LIST),

    /** 角色卡管理 */
    CHARACTERS("角色卡", Icons.Filled.Person, NavRoutes.CHARACTER_CARDS),

    /** 记忆管理 */
    MEMORY("记忆", Icons.Filled.Star, NavRoutes.MEMORY_LIST),

    /** 设置 */
    SETTINGS("设置", Icons.Filled.Settings, NavRoutes.SETTINGS),
}

/** 底部导航栏组件（V5-1） */
@Composable
fun BottomNavBar(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        BottomTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}