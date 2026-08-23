package com.bdalxy.edencourt.feature.memory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bdalxy.edencourt.EdenCourtApp
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import com.bdalxy.edencourt.data.local.entity.MemoryType
import com.bdalxy.edencourt.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 记忆管理页 ViewModel（T5.5）。
 * 数据源：memoryRepository.observeAll()（Room 响应式 Flow → StateFlow）。
 * 提供：手动添加、软删除（二次确认后调用）、搜索框过滤。
 */
class MemoryViewModel(
    private val repository: MemoryRepository,
) : ViewModel() {

    /** 全部未删除记忆（最新在前，数据库自动维护） */
    val memories: StateFlow<List<MemoryItem>> =
        repository.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 搜索关键字（界面搜索框输入；空串=显示全部） */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** 搜索框变更（空串恢复全量列表） */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    /** 当前展示列表：搜索关键字非空时按关键词过滤，否则全量 */
    val filteredMemories: StateFlow<List<MemoryItem>> =
        _searchQuery.combine(memories) { query, all ->
            if (query.isBlank()) all
            else all.filter { m ->
                m.content.contains(query, ignoreCase = true) ||
                    m.keywords.contains(query, ignoreCase = true)
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
                MemoryViewModel(repository = app.memoryRepository)
            }
        }
    }
}
