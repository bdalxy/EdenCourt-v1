package com.bdalxy.edencourt.feature.memory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.dao.CharacterCardDao
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 手动/未归属记忆的筛选哨兵（MemoryItem.characterCardId 为 null 那些，见 MemoryScreen） */
private const val MANUAL_CARD_ID = -1L

/**
 * 记忆管理页 ViewModel（T5.5 + T6.13 归属显示）。
 * 数据源：memoryRepository.observeAll()（Room 响应式 Flow → StateFlow）。
 * T6.13 起：每条记忆带角色归属（characterCardId），本页支持按角色筛选并显示归属，
 * 明确「谁的记忆」；null=手动添加/未归属。
 */
class MemoryViewModel(
    private val repository: MemoryRepository,
    private val characterCardDao: CharacterCardDao,
) : ViewModel() {

    /** 全部未删除记忆（最新在前，数据库自动维护） */
    val memories: StateFlow<List<MemoryItem>> =
        repository.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 全部角色卡（用于按角色筛选 + 归属名解析） */
    val characters: StateFlow<List<CharacterCardEntity>> =
        characterCardDao.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 当前筛选的角色卡 id（null=全部，含未归属；T6.13） */
    private val _selectedCardId = MutableStateFlow<Long?>(null)
    val selectedCardId: StateFlow<Long?> = _selectedCardId

    /** 搜索关键字（界面搜索框输入；空串=显示全部） */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** 角色卡 id -> 显示名（备注优先，未备注用角色名；T6.14） */
    fun displayNameFor(cardId: Long?): String? {
        if (cardId == null) return null
        val card = characters.value.firstOrNull { it.id == cardId } ?: return null
        return card.remark.ifBlank { card.name }
    }

    /** 搜索框变更（空串恢复全量列表） */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    /** 切换角色筛选（null=全部；T6.13） */
    fun setSelectedCard(cardId: Long?) {
        _selectedCardId.value = cardId
    }

    /** 当前展示列表：按搜索词 + 角色筛选过滤 */
    val filteredMemories: StateFlow<List<MemoryItem>> =
        combine(_searchQuery, _selectedCardId, memories) { query, cardId, all ->
            all.filter { m ->
                // 角色筛选：null=全部；MANUAL_CARD_ID=只看手动/未归属；否则只看指定角色
                val cardMatches = when (cardId) {
                    null -> true
                    MANUAL_CARD_ID -> m.characterCardId == null
                    else -> m.characterCardId == cardId
                }
                val queryMatches = query.isBlank() ||
                    m.content.contains(query, ignoreCase = true) ||
                    m.keywords.contains(query, ignoreCase = true)
                cardMatches && queryMatches
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** 手动添加记忆（T5.5：用户明确填写，置信度默认 1.0） */
    fun addMemory(type: MemoryType, content: String, keywords: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addManual(type, trimmed, keywords.trim())
        }
    }

    /** 软删除记忆（调用方需已二次确认） */
    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.softDelete(id)
        }
    }

    companion object {
        /** 工厂：从 Application 中取 MemoryRepository 单例 */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                MemoryViewModel(repository = app.memoryRepository, characterCardDao = app.characterCardDao)
            }
        }
    }
}
