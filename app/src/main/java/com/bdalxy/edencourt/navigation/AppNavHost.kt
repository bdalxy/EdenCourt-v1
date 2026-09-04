package com.bdalxy.edencourt.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bdalxy.edencourt.feature.about.AboutScreen
import com.bdalxy.edencourt.feature.character.CharacterScreen
import com.bdalxy.edencourt.feature.chat.ChatScreen
import com.bdalxy.edencourt.feature.conversationlist.ConversationListScreen
import com.bdalxy.edencourt.feature.interaction.InteractionCenterScreen
import com.bdalxy.edencourt.feature.memory.MemoryScreen
import com.bdalxy.edencourt.feature.settings.SettingsScreen
import com.bdalxy.edencourt.feature.worldbook.WorldBookEditScreen
import com.bdalxy.edencourt.feature.worldbook.WorldBookScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NavRoutes.CONVERSATION_LIST) {
        composable(NavRoutes.CONVERSATION_LIST) {
            ConversationListScreen(
                onNavigateToChat = { id -> navController.navigate(NavRoutes.chat(id)) },
                onOpenCharacter = { navController.navigate(NavRoutes.CHARACTER_CARDS) },
                onOpenMemory = { navController.navigate(NavRoutes.MEMORY_LIST) },
                // T11.15：会话列表顶栏「互动中心」直达
                onOpenInteraction = { navController.navigate(NavRoutes.INTERACTION_CENTER) },
                onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onOpenAbout = { navController.navigate(NavRoutes.ABOUT) },
            )
        }
        composable(
            route = NavRoutes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType }),
        ) { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getLong("conversationId") ?: 0L,
                onBack = { navController.popBackStack() },
                // T6.1：聊天页错误提示「Key 无效」可跳转设置页
                onOpenSettings = { navController.navigate(NavRoutes.SETTINGS) },
                // v1.2b T8.11：聊天页顶部「互动」图标 → 互动中心
                onOpenInteraction = { navController.navigate(NavRoutes.INTERACTION_CENTER) },
                // T11.6：会话记忆面板「前往记忆管理页」入口
                onOpenMemoryManagement = { navController.navigate(NavRoutes.MEMORY_LIST) },
                // C：聊天页内「世界书」管理入口（知识库抽屉「管理」/ 挂载弹层引导）
                onOpenWorldBook = { navController.navigate(NavRoutes.WORLD_BOOK) },
            )
        }
        // v1.2b T8.11：互动中心（列表 + 全局开关 + 发起互动）
        composable(NavRoutes.INTERACTION_CENTER) {
            InteractionCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.CHARACTER_CARDS) {
            CharacterScreen(
                onBack = { navController.popBackStack() },
                // B3：角色卡页顶部「世界书」入口卡 → 世界书管理页
                onOpenWorldBook = { navController.navigate(NavRoutes.WORLD_BOOK) },
            )
        }
        composable(NavRoutes.MEMORY_LIST) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                // T11.13：设置页也能进入互动中心
                onOpenInteraction = { navController.navigate(NavRoutes.INTERACTION_CENTER) },
                // C：设置页「世界书」入口
                onOpenWorldBook = { navController.navigate(NavRoutes.WORLD_BOOK) },
            )
        }
        // C 阶段：世界书管理（书列表）
        composable(NavRoutes.WORLD_BOOK) {
            WorldBookScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { id -> navController.navigate(NavRoutes.worldBookEdit(id)) },
            )
        }
        // C 阶段：世界书编辑（bookId<=0 新建）
        composable(
            route = NavRoutes.WORLD_BOOK_EDIT,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { entry ->
            WorldBookEditScreen(
                bookId = entry.arguments?.getLong("bookId") ?: 0L,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(NavRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}