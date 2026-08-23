package com.bdalxy.edencourt

import android.app.Application
import com.bdalxy.edencourt.data.local.AppDatabase
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.remote.RemoteApiChatService
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.domain.BuiltInCards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EdenCourtApp : Application() {

    /** 应用级协程作用域：执行启动期后台任务（如播种内置角色卡） */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 首次启动播种内置角色卡（幂等，已存在则跳过；T4.4）
        applicationScope.launch {
            BuiltInCards.seed(characterCardDao)
        }
    }

    /** 聊天数据仓库单例：懒加载，首次访问时才建库（T2.6 起供 ViewModel 使用） */
    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            conversationDao = AppDatabase.getInstance(this).conversationDao(),
            messageDao = AppDatabase.getInstance(this).messageDao(),
        )
    }

    /** 角色卡表数据访问单例（T4.2 起供角色卡管理/选卡流程使用） */
    val characterCardDao: CharacterCardDao by lazy {
        AppDatabase.getInstance(this).characterCardDao()
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
