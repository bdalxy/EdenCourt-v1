package com.bdalxy.edencourt.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
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

/** 主入口：底部 Tab 导航 + 各 Tab 独立导航图（V5-2） */
@Composable
fun AppRoot() {
    var selectedTab by remember { mutableStateOf(BottomTab.CHATS) }

    // 每个 Tab 独立的 NavController，避免选项卡之间导航栈混乱
    val chatsNavController = rememberNavController()
    val charactersNavController = rememberNavController()
    val memoryNavController = rememberNavController()
    val settingsNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    // 重复点击当前 Tab 时回到根
                    if (selectedTab == tab) {
                        when (tab) {
                            BottomTab.CHATS -> chatsNavController.navigate(NavRoutes.CONVERSATION_LIST) {
                                popUpTo(NavRoutes.CONVERSATION_LIST) { inclusive = true }
                            }
                            BottomTab.CHARACTERS -> charactersNavController.navigate(NavRoutes.CHARACTER_CARDS) {
                                popUpTo(NavRoutes.CHARACTER_CARDS) { inclusive = true }
                            }
                            BottomTab.MEMORY -> memoryNavController.navigate(NavRoutes.MEMORY_LIST) {
                                popUpTo(NavRoutes.MEMORY_LIST) { inclusive = true }
                            }
                            BottomTab.SETTINGS -> settingsNavController.navigate(NavRoutes.SETTINGS) {
                                popUpTo(NavRoutes.SETTINGS) { inclusive = true }
                            }
                        }
                    }
                    selectedTab = tab
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                BottomTab.CHATS -> ChatsTab(
                    navController = chatsNavController,
                    onSwitchToSettings = { selectedTab = BottomTab.SETTINGS },
                )
                BottomTab.CHARACTERS -> CharactersTab(
                    navController = charactersNavController,
                )
                BottomTab.MEMORY -> MemoryTab(navController = memoryNavController)
                BottomTab.SETTINGS -> SettingsTab(navController = settingsNavController)
            }
        }
    }
}

// ── 会话 Tab（V5-2）─────────────────────────────────────────────

/** 会话 Tab 导航图：会话列表 → 聊天 → 互动中心 → 世界书 */
@Composable
private fun ChatsTab(
    navController: NavHostController,
    onSwitchToSettings: () -> Unit,
) {
    NavHost(navController = navController, startDestination = NavRoutes.CONVERSATION_LIST) {
        composable(NavRoutes.CONVERSATION_LIST) {
            ConversationListScreen(
                onNavigateToChat = { id -> navController.navigate(NavRoutes.chat(id)) },
                onOpenCharacter = {}, // 由底部 Tab 导航
                onOpenMemory = {},    // 由底部 Tab 导航
                onOpenInteraction = { navController.navigate(NavRoutes.INTERACTION_CENTER) },
                onOpenSettings = {},  // 由底部 Tab 导航
                onOpenAbout = {},
            )
        }
        composable(
            route = NavRoutes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.LongType }),
        ) { entry ->
            ChatScreen(
                conversationId = entry.arguments?.getLong("conversationId") ?: 0L,
                onBack = { navController.popBackStack() },
                onOpenSettings = onSwitchToSettings,
                onOpenInteraction = { navController.navigate(NavRoutes.INTERACTION_CENTER) },
                onOpenMemoryManagement = { navController.navigate(NavRoutes.MEMORY_LIST) },
                onOpenWorldBook = { navController.navigate(NavRoutes.WORLD_BOOK) },
                onOpenCharacterDetail = { navController.navigate(NavRoutes.CHARACTER_CARDS) },
            )
        }
        composable(NavRoutes.INTERACTION_CENTER) {
            InteractionCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.CHARACTER_CARDS) {
            CharacterScreen(
                onBack = { navController.popBackStack() },
                onOpenWorldBook = { navController.navigate(NavRoutes.WORLD_BOOK) },
            )
        }
        composable(NavRoutes.MEMORY_LIST) {
            MemoryScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.WORLD_BOOK) {
            WorldBookScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { id -> navController.navigate(NavRoutes.worldBookEdit(id)) },
            )
        }
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
    }
}

// ── 角色卡 Tab（V5-2）───────────────────────────────────────────

/** 角色卡 Tab 导航图：角色卡列表 → 角色卡详情 → 世界书 */
@Composable
private fun CharactersTab(navController: NavHostController) {
    NavHost(navController = navController, startDestination = NavRoutes.CHARACTER_CARDS) {
        composable(NavRoutes.CHARACTER_CARDS) {
            CharacterScreen(
                onBack = null, // 角色卡 Tab 根页面，无返回箭头
                onOpenWorldBook = { navController.navigate(NavRoutes.WORLD_BOOK) },
            )
        }
        composable(NavRoutes.WORLD_BOOK) {
            WorldBookScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { id -> navController.navigate(NavRoutes.worldBookEdit(id)) },
            )
        }
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
    }
}

// ── 记忆 Tab（V5-2）─────────────────────────────────────────────

/** 记忆 Tab 导航图：记忆列表 */
@Composable
private fun MemoryTab(navController: NavHostController) {
    NavHost(navController = navController, startDestination = NavRoutes.MEMORY_LIST) {
        composable(NavRoutes.MEMORY_LIST) {
            MemoryScreen(onBack = null) // 记忆 Tab 根页面，无返回箭头
        }
    }
}

// ── 设置 Tab（V5-2）─────────────────────────────────────────────

/** 设置 Tab 导航图：设置 → 关于 → 互动中心 → 世界书 */
@Composable
private fun SettingsTab(navController: NavHostController) {
    NavHost(navController = navController, startDestination = NavRoutes.SETTINGS) {
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onBack = null, // 设置 Tab 根页面，无返回箭头
                onOpenInteraction = { navController.navigate(NavRoutes.INTERACTION_CENTER) },
                onOpenAbout = { navController.navigate(NavRoutes.ABOUT) },
            )
        }
        composable(NavRoutes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.INTERACTION_CENTER) {
            InteractionCenterScreen(onBack = { navController.popBackStack() })
        }
        composable(NavRoutes.WORLD_BOOK) {
            WorldBookScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { id -> navController.navigate(NavRoutes.worldBookEdit(id)) },
            )
        }
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
    }
}