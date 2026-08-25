package com.bdalxy.edencourt.navigation

/** 路由定义（企划书 5.2.2）。集中管理避免字符串散落。 */
object NavRoutes {
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}"
    const val CHARACTER_CARDS = "character_cards"
    const val MEMORY_LIST = "memory_list"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    /** 互动中心（v1.2b 角色间互动，T8.11） */
    const val INTERACTION_CENTER = "interaction_center"

    fun chat(conversationId: Long): String = "chat/$conversationId"
}