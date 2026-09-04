package com.bdalxy.edencourt.feature.character

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.dao.WorldInfoDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import com.bdalxy.edencourt.data.setting.ScheduleConfigStore
import com.bdalxy.edencourt.domain.CharacterCard
import com.bdalxy.edencourt.domain.CharacterCardParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 角色卡管理页 ViewModel（T4.3）。
 * 数据源：characterCardDao.observeAll()（Room 响应式 Flow → StateFlow）。
 * 提供：导入 JSON、编辑名称/描述、删除（内置卡不可删）。
 */
class CharacterViewModel(
    private val dao: CharacterCardDao,
    private val scheduleConfigStore: ScheduleConfigStore,
    private val worldInfoDao: WorldInfoDao,
) : ViewModel() {

    /** 角色卡列表（内置卡优先，再按创建时间降序，数据库自动维护） */
    val cards: StateFlow<List<CharacterCardEntity>> =
        dao.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 世界书数量（B3：角色卡页顶部「世界书」入口卡副标题展示，随库实时刷新） */
    val worldBookCount: StateFlow<Int> =
        worldInfoDao.observeBooks()
            .map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0,
            )

    /** 全局基础作息（T11.10 深化：角色详情页「基于基础作息单独调节」的起点） */
    val scheduleConfig: StateFlow<ScheduleConfig> = scheduleConfigStore.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ScheduleConfig(),
        )

    /** 导入中标志（控制 FAB 加载态，防止重复导入） */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    /** 置顶/取消置顶（B3：置顶卡独占顶部横滑大卡区；写库后 observeAll 自动重发刷新列表） */
    fun togglePin(card: CharacterCardEntity) {
        viewModelScope.launch { dao.setPinned(card.id, !card.isPinned) }
    }

    /** 设置/清除立绘（B3：path 为保存后的立绘大图路径，null 表示清除；置顶大卡背景用） */
    fun setArtwork(card: CharacterCardEntity, path: String?) {
        viewModelScope.launch { dao.setArtwork(card.id, path) }
    }

    /**
     * 导入角色卡 JSON（T4.5：标准格式 + Tavern CCv3 兼容映射）。
     * @param jsonText 文件读取到的完整 JSON 文本
     * @param onResult 回调结果：null=成功；非 null=失败原因文案
     */
    fun importCard(jsonText: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (_importing.value) return@launch
            _importing.value = true
            try {
                // 解析 + 必填校验（失败统一提示「角色卡 JSON 格式错误」，企划书 5.5.3）
                val card = CharacterCardParser.parse(jsonText)
                // 入库：description 取性格前 40 字作列表简介
                dao.insert(
                    CharacterCardEntity(
                        name = card.name,
                        description = card.personality.take(40),
                        jsonContent = jsonText,
                        isBuiltIn = false,
                    ),
                )
                onResult(null)
            } catch (e: IllegalArgumentException) {
                onResult("角色卡 JSON 格式错误：${e.message}")
            } finally {
                _importing.value = false
            }
        }
    }

    /**
     * 在角色管理页「新建」一张非内置角色卡（T6.12）。
     * 用户在表单中填名称/性格/简介并可设头像，落地为标准 jsonContent 入库。
     * @param name 角色名（必填）
     * @param personality 性格设定（必填，转 system prompt）
     * @param background 简介/背景（可选，作列表简介）
     * @param avatarPath 头像本地路径；null 表示用默认首字头像
     * @param onResult 回调结果：null=成功；非 null=失败原因
     */
    fun createCard(
        name: String,
        personality: String,
        background: String,
        avatarPath: String?,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val n = name.trim()
            val p = personality.trim()
            // 必填校验（企划书 3.4.1：name/personality 均非空）
            if (n.isBlank() || p.isBlank()) {
                onResult("名称与性格不能为空")
                return@launch
            }
            val bg = background.trim()
            val card = CharacterCard(name = n, personality = p, background = bg)
            // 列表简介：优先用用户填的简介，否则取性格前 40 字（与 importCard 一致口径）
            dao.insert(
                CharacterCardEntity(
                    name = n,
                    description = bg.ifBlank { p.take(40) },
                    jsonContent = CharacterCardParser.toJson(card),
                    avatar = avatarPath,
                    isBuiltIn = false,
                ),
            )
            onResult(null)
        }
    }

    /**
     * 编辑角色卡名称/简介/备注（T6.14 加备注）。
     * 名称会同步写回 jsonContent（保证下次解析出的一致）；简介仅列表展示用；
     * 备注为本地别名（类微信备注），不写回卡 JSON。
     */
    fun updateCardInfo(card: CharacterCardEntity, name: String, description: String, remark: String) {
        viewModelScope.launch {
            // 解析原 JSON 并回写 name（解析失败则保留原 JSON 不动）
            val updatedJson = runCatching {
                val parsed = CharacterCardParser.parse(card.jsonContent)
                CharacterCardParser.toJson(parsed.copy(name = name))
            }.getOrDefault(card.jsonContent)

            dao.update(
                card.copy(
                    name = name,
                    description = description,
                    remark = remark.trim(),
                    jsonContent = updatedJson,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 删除角色卡（内置卡不可删，调用方需已二次确认） */
    fun deleteCard(card: CharacterCardEntity) {
        if (card.isBuiltIn) return // 内置卡不可删（企划书 3.4.3）
        viewModelScope.launch {
            dao.delete(card)
        }
    }

    /**
     * 更新角色卡自定义头像（T6.2B）。
     * @param card 当前角色卡
     * @param avatarPath 新头像本地路径；传 null 表示清除自定义头像恢复默认
     */
    fun updateAvatar(card: CharacterCardEntity, avatarPath: String?) {
        viewModelScope.launch {
            dao.update(
                card.copy(
                    avatar = avatarPath,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * 设置/清除该角色自定义作息（T11.10 深化：基于基础作息单独调节，全简化版仅起床+入睡）。
     * @param card 当前角色卡
     * @param cfg 自定义作息；传 null 表示清除自定义，恢复「跟随默认」（基础作息+个性偏移）
     */
    fun updateSchedule(card: CharacterCardEntity, cfg: ScheduleConfig?) {
        viewModelScope.launch {
            dao.update(
                card.copy(
                    scheduleWakeUpMinute = cfg?.wakeUpMinute,
                    scheduleSleepMinute = cfg?.sleepMinute,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    companion object {
        /** 工厂：从 Application 中取 CharacterCardDao 单例 */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                CharacterViewModel(
                    dao = app.characterCardDao,
                    scheduleConfigStore = app.scheduleConfigStore,
                    worldInfoDao = app.worldInfoDao,
                )
            }
        }
    }
}
