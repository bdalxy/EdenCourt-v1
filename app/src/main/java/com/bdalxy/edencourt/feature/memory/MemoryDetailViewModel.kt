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
import com.bdalxy.edencourt.data.local.entity.MemoryImportance
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryVisibility
import com.bdalxy.edencourt.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 记忆详情页 ViewModel（阶段二 T10.1）。
 * - 按 id 加载单条全文，供详情页展示所有字段
 * - 承载通用卡片操作：置顶 / 重要 / 可见性(共享↔私密) / 锁定 / 编辑(覆盖更正) / 软删除
 * - 每次操作原地 copy 后经 MemoryRepository.updateMemory 落库并刷新（updatedAt 自动更新）
 */
class MemoryDetailViewModel(
    private val repository: MemoryRepository,
    private val characterCardDao: CharacterCardDao,
    private val id: Long,
) : ViewModel() {

    /** 角色卡（主动常驻收集，避免 stateIn 懒订阅导致归属名解析为空） */
    private val _characters = MutableStateFlow<List<CharacterCardEntity>>(emptyList())
    val characters: StateFlow<List<CharacterCardEntity>> = _characters.asStateFlow()

    /** 当前详情记忆（null=加载中/已被软删除） */
    private val _item = MutableStateFlow<MemoryItem?>(null)
    val item: StateFlow<MemoryItem?> = _item.asStateFlow()

    init {
        // 常驻收集角色卡（同卡 dataCommon：备注优先）
        viewModelScope.launch {
            characterCardDao.observeAll().collect { _characters.value = it }
        }
        refresh()
    }

    /** 按 id 重新加载（进入详情/每次操作后保持最新） */
    private fun refresh() {
        viewModelScope.launch {
            _item.value = repository.getMemoryById(id)
        }
    }

    /** 归属角色显示名（null=手动/未归属） */
    fun displayNameFor(cardId: Long?): String? {
        if (cardId == null) return null
        val card = characters.value.firstOrNull { it.id == cardId } ?: return null
        return card.remark.ifBlank { card.name }
    }

    // ---------- 通用卡片操作 ----------

    /** 置顶开关注销（开关切换后落库） */
    fun togglePinned() = update { it.copy(isPinned = !it.isPinned) }

    /** 重要程度切换（普通 ↔ 重要） */
    fun toggleImportant() = update {
        it.copy(
            importance = if (it.importance == MemoryImportance.IMPORTANT) MemoryImportance.NORMAL else MemoryImportance.IMPORTANT,
        )
    }

    /** 锁定切换（锁定后防误删，但本 MVP 仍允许用户长按删除） */
    fun toggleLocked() = update { it.copy(locked = !it.locked) }

    /** 可见性切换（共享 ↔ 私密） */
    fun toggleVisibility() = update {
        it.copy(visibility = if (it.visibility == MemoryVisibility.SHARED) MemoryVisibility.PRIVATE else MemoryVisibility.SHARED)
    }

    /** 编辑内容/关键词（编辑即更正，沿用「覆盖」语义，T10.3） */
    fun editContent(content: String, keywords: String) {
        update { it.copy(content = content.trim(), keywords = keywords.trim()) }
    }

    /** 软删除后置空（调用方据此退出详情页并触发列表刷新） */
    fun delete() {
        viewModelScope.launch {
            repository.softDelete(id)
            _item.value = null
        }
    }

    private fun update(transform: (MemoryItem) -> MemoryItem) {
        val current = _item.value ?: return
        viewModelScope.launch {
            repository.updateMemory(transform(current))
            refresh()
        }
    }

    companion object {
        /** 工厂：从 Application 取 MemoryRepository 单例，并绑定目标记忆 id */
        fun factory(application: Application, id: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                MemoryDetailViewModel(repository = app.memoryRepository, characterCardDao = app.characterCardDao, id = id)
            }
        }
    }
}