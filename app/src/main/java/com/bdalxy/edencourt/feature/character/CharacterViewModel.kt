package com.bdalxy.edencourt.feature.character

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.domain.CharacterCardParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 角色卡管理页 ViewModel（T4.3）。
 * 数据源：characterCardDao.observeAll()（Room 响应式 Flow → StateFlow）。
 * 提供：导入 JSON、编辑名称/描述、删除（内置卡不可删）。
 */
class CharacterViewModel(
    private val dao: CharacterCardDao,
) : ViewModel() {

    /** 角色卡列表（内置卡优先，再按创建时间降序，数据库自动维护） */
    val cards: StateFlow<List<CharacterCardEntity>> =
        dao.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 导入中标志（控制 FAB 加载态，防止重复导入） */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

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
     * 编辑角色卡名称/简介。
     * 名称会同步写回 jsonContent（保证下次解析出的一致）；简介仅列表展示用。
     */
    fun updateCardInfo(card: CharacterCardEntity, name: String, description: String) {
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

    companion object {
        /** 工厂：从 Application 中取 CharacterCardDao 单例 */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                CharacterViewModel(dao = app.characterCardDao)
            }
        }
    }
}
