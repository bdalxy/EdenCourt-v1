package com.bdalxy.edencourt

import android.app.Application
import com.bdalxy.edencourt.data.local.AppDatabase
import com.bdalxy.edencourt.data.remote.RemoteApiChatService
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.setting.ApiConfigStore

class EdenCourtApp : Application() {

    /** 聊天数据仓库单例：懒加载，首次访问时才建库（T2.6 起供 ViewModel 使用） */
    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            conversationDao = AppDatabase.getInstance(this).conversationDao(),
            messageDao = AppDatabase.getInstance(this).messageDao(),
        )
    }

    /** API 配置存储单例：DataStore 持久化，阶段 3 起供设置页/网络层使用 */
    val apiConfigStore: ApiConfigStore by lazy {
        ApiConfigStore(this)
    }

    /** AI 对话服务单例：OkHttp 流式实现（T3.5 起供 ChatViewModel 使用） */
    val aiChatService: RemoteApiChatService by lazy {
        RemoteApiChatService(RemoteApiChatService.defaultClient())
    }
}
