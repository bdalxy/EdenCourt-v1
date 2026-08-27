package com.bdalxy.edencourt

import android.app.Application
import com.bdalxy.edencourt.data.local.AppDatabase
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.ConversationDao
import com.bdalxy.edencourt.data.local.dao.InteractionDao
import com.bdalxy.edencourt.data.local.dao.MemoryDao
import com.bdalxy.edencourt.data.local.dao.MemorySearchDao
import com.bdalxy.edencourt.data.local.dao.RelationshipDao
import com.bdalxy.edencourt.data.local.dao.UserIdentityDao
import com.bdalxy.edencourt.data.remote.RemoteApiChatService
import com.bdalxy.edencourt.data.search.MemorySearchService
import com.bdalxy.edencourt.data.consult.DailyConsultExecutor
import com.bdalxy.edencourt.data.consult.DailyConsultScheduler
import com.bdalxy.edencourt.data.interaction.InteractionAutoExecutor
import com.bdalxy.edencourt.data.interaction.InteractionAutoScheduler
import com.bdalxy.edencourt.data.interaction.InteractionExecutor
import com.bdalxy.edencourt.data.repository.ChatRepository
import com.bdalxy.edencourt.data.repository.CrossSessionConsultService
import com.bdalxy.edencourt.data.repository.MemoryRepository
import com.bdalxy.edencourt.data.repository.RelationshipRepository
import com.bdalxy.edencourt.data.setting.ApiConfigStore
import com.bdalxy.edencourt.data.setting.DailyConsultConfigStore
import com.bdalxy.edencourt.data.setting.InteractionConfigStore
import com.bdalxy.edencourt.data.setting.OnboardingConfigStore
import com.bdalxy.edencourt.data.setting.ReplyCursorStore
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
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
            // T12.11 性别字段回填补丁：升级前已播种的内置卡补 gender（幂等）
            BuiltInCards.seedGenderIfMissing(characterCardDao)
        }
        // T11.7 互动自动驱动：App 启动即安排当日自动窗口闹钟（总开关/自动开关任一关闭时自动取消）
        applicationScope.launch {
            interactionAutoScheduler.schedule(this@EdenCourtApp)
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

    /** 会话表数据访问单例（T8.11 互动中心选会话用） */
    val conversationDao: ConversationDao by lazy {
        AppDatabase.getInstance(this).conversationDao()
    }

    /** 记忆条目表数据访问单例（T5.1 起供记忆系统使用） */
    val memoryDao: MemoryDao by lazy {
        AppDatabase.getInstance(this).memoryDao()
    }

    /** FTS4 全文检索数据访问单例（T9.2 起供记忆检索服务使用） */
    val memorySearchDao: MemorySearchDao by lazy {
        AppDatabase.getInstance(this).memorySearchDao()
    }

    /** API 配置存储单例：DataStore 持久化，阶段 3 起供设置页/网络层使用 */
    val apiConfigStore: ApiConfigStore by lazy {
        ApiConfigStore(this)
    }

    /** AI 对话服务单例：OkHttp 流式实现（T3.5 起供 ChatViewModel 使用） */
    val aiChatService: RemoteApiChatService by lazy {
        RemoteApiChatService(RemoteApiChatService.defaultClient())
    }

    /** 统一记忆检索服务单例（T9.3 起供管理页搜索 + 注入检索排序使用，覆盖 FTS + LIKE 兜底） */
    val memorySearchService: MemorySearchService by lazy {
        MemorySearchService(
            memoryDao = memoryDao,
            searchDao = memorySearchDao,
        )
    }

    /** 记忆仓库单例（T5.3 起供对话后台提取/记忆管理页使用） */
    val memoryRepository: MemoryRepository by lazy {
        MemoryRepository(
            dao = memoryDao,
            searchService = memorySearchService,
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

    /** 角色间互动全局配置存储单例（v1.2b，T8.7：全局总开关） */
    val interactionConfigStore: InteractionConfigStore by lazy {
        InteractionConfigStore(this)
    }

    /** 互动表数据访问单例（v1.2b 互动系统，T8.10） */
    val interactionDao: InteractionDao by lazy {
        AppDatabase.getInstance(this).interactionDao()
    }

    /** 会话间关系表数据访问单例（v1.2b 互动系统，T8.8） */
    val relationshipDao: RelationshipDao by lazy {
        AppDatabase.getInstance(this).relationshipDao()
    }

    /** 会话间关系仓储单例（T12.10：关系对规范化约定下沉，业务层不再直接裸 upsert） */
    val relationshipRepository: RelationshipRepository by lazy {
        RelationshipRepository(relationshipDao)
    }

    /** 用户自设身份表数据访问单例（v1.2b，T8.13：我的身份，唯一一条） */
    val userIdentityDao: UserIdentityDao by lazy {
        AppDatabase.getInstance(this).userIdentityDao()
    }

    /** 角色间互动执行器单例（v1.2b，T8.9：双向多轮状态机，供手动触发入口调用） */
    val interactionExecutor: InteractionExecutor by lazy {
        InteractionExecutor(
            chatRepository = chatRepository,
            characterCardDao = characterCardDao,
            memoryRepository = memoryRepository,
            interactionDao = interactionDao,
            relationshipRepository = relationshipRepository,
            interactionConfigStore = interactionConfigStore,
            apiConfigStore = apiConfigStore,
            aiChatService = aiChatService,
            userIdentityDao = userIdentityDao,
        )
    }

    /** 互动自动驱动执行器单例（v1.2b，T11.7：窗口内选对+概率→复用 interactionExecutor 跑互动） */
    val interactionAutoExecutor: InteractionAutoExecutor by lazy {
        InteractionAutoExecutor(
            conversationDao = conversationDao,
            interactionDao = interactionDao,
            relationshipRepository = relationshipRepository,
            interactionConfigStore = interactionConfigStore,
            interactionExecutor = interactionExecutor,
            // T11.11：注入作息存储 + 角色卡 DAO，用于「睡眠中不串门」判断
            scheduleConfigStore = scheduleConfigStore,
            characterCardDao = characterCardDao,
            // T11.12：注入记忆仓库，用于挑选「因由」事件候选
            memoryRepository = memoryRepository,
        )
    }

    /** 互动自动驱动定时调度器单例（v1.2b，T11.7：AlarmManager 每日自动窗口） */
    val interactionAutoScheduler: InteractionAutoScheduler by lazy {
        InteractionAutoScheduler(interactionConfigStore)
    }

    /** 角色生活作息配置存储单例（v1.2b，T11.10：起床/入睡/工作/午休时刻，驱动「此刻状态」） */
    val scheduleConfigStore: ScheduleConfigStore by lazy {
        ScheduleConfigStore(this)
    }

    /** 首启引导状态存储单例（T11.14：是否已看过新手指引，跳过或看完都记「已看过」） */
    val onboardingConfigStore: OnboardingConfigStore by lazy {
        OnboardingConfigStore(this)
    }

    /** 回复泵已处理游标存储单例（T12.4：进程重启后泵恢复游标，避免把历史重复洗澡成新来信） */
    val replyCursorStore: ReplyCursorStore by lazy {
        ReplyCursorStore(this)
    }
}
