package com.bdalxy.edencourt

import android.app.Application
import com.bdalxy.edencourt.data.local.AppDatabase
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.remote.RemoteApiChatService
import com.bdalxy.edencourt.data.consult.DailyConsultExecutor
import com.bdalxy.edencourt.data.consult.DailyConsultScheduler
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.CrossSessionConsultService
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.setting.DailyConsultConfigStore
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

    /** 记忆条目表数据访问单例（T5.1 起供记忆系统使用） */
    val memoryDao: MemoryDao by lazy {
        AppDatabase.getInstance(this).memoryDao()
    }

    /** API 配置存储单例：DataStore 持久化，阶段 3 起供设置页/网络层使用 */
    val apiConfigStore: ApiConfigStore by lazy {
        ApiConfigStore(this)
    }

    /** AI 对话服务单例：OkHttp 流式实现（T3.5 起供 ChatViewModel 使用） */
    val aiChatService: RemoteApiChatService by lazy {
        RemoteApiChatService(RemoteApiChatService.defaultClient())
    }

    /** 记忆仓库单例（T5.3 起供对话后台提取/记忆管理页使用） */
    val memoryRepository: MemoryRepository by lazy {
        MemoryRepository(
            dao = memoryDao,
            apiConfigStore = apiConfigStore,
            aiChatService = aiChatService,
        )
    }

    /** 跨会话打听·旁路暗访执行器单例（v1.2 套话，T8.2 起供触发打听的对话链路使用） */
    val crossSessionConsultService: CrossSessionConsultService by lazy {
        CrossSessionConsultService(
            chatRepository = chatRepository,
            characterCardDao = characterCardDao,
            memoryRepository = memoryRepository,
            apiConfigStore = apiConfigStore,
            aiChatService = aiChatService,
        )
    }

    /** 每日打听配置存储单例（v1.2 P2，T8.5：启用/窗口/概率） */
    val dailyConsultConfigStore: DailyConsultConfigStore by lazy {
        DailyConsultConfigStore(this)
    }

    /** 每日定时+概率+角色自驱动打听执行器单例（v1.2 P2，T8.5） */
    val dailyConsultExecutor: DailyConsultExecutor by lazy {
        DailyConsultExecutor(
            chatRepository = chatRepository,
            characterCardDao = characterCardDao,
            memoryRepository = memoryRepository,
            crossSessionConsultService = crossSessionConsultService,
            apiConfigStore = apiConfigStore,
            aiChatService = aiChatService,
            configStore = dailyConsultConfigStore,
        )
    }

    /** 每日打听定时调度器单例（v1.2 P2，T8.5：AlarmManager 精确窗口） */
    val dailyConsultScheduler: DailyConsultScheduler by lazy {
        DailyConsultScheduler(dailyConsultConfigStore)
    }
}
