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
import com.bdalxy.edencourt.feature.memory.MemoryScreen
import com.bdalxy.edencourt.feature.settings.SettingsScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = NavRoutes.CONVERSATION_LIST) {
        composable(NavRoutes.CONVERSATION_LIST) {
            ConversationListScreen(
                onNavigateToChat = { id -> navController.navigate(NavRoutes.chat(id)) },
                onOpenCharacter = { navController.navigate(NavRoutes.CHARACTER_CARDS) },
                onOpenMemory = { navController.navigate(NavRoutes.MEMORY_LIST) },
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
            )
        }
        composable(NavRoutes.CHARACTER_CARDS) {
            CharacterScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.MEMORY_LIST) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}