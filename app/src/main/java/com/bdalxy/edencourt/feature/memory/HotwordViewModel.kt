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
import com.bdalxy.edencourt.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一个热词的统计（T11.1）：词 + 出现在多少条记忆里 */
data class WordCount(val word: String, val count: Int)

/**
 * 共现热词 ViewModel（阶段三 T11.1）。
 * - 统计全库记忆关键词（keywords 逗号分隔）的词频排行
 * - 选中某热词 → 筛出「content 或 keywords 含该词」的记忆列表
 * - 角色卡主动收集，用于命中记忆归属名解析
 */
class HotwordViewModel(
    private val repository: MemoryRepository,
    private val characterCardDao: CharacterCardDao,
) : ViewModel() {

    /** 角色卡（主动常驻收集，避免懒订阅致归属名为空） */
    private val _characters = MutableStateFlow<List<CharacterCardEntity>>(emptyList())
    val characters: StateFlow<List<CharacterCardEntity>> = _characters.asStateFlow()

    /** 全库未删除记忆（时间轴同源，随表自动刷新；词频与命中列表都由它派生） */
    private val allItems = repository.observeTimeline()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 热词排行（按出现条数降序） */
    val hotwords: StateFlow<List<WordCount>> = allItems
        .map { items -> computeHotwords(items) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前选中的热词（null=未选中；点排行词时设置） */
    private val _selectedWord = MutableStateFlow<String?>(null)
    val selectedWord: StateFlow<String?> = _selectedWord.asStateFlow()

    /** 命中「选中词」的记忆列表（content 或 keywords 含该词，非空词才有意义） */
    val hitMemories: StateFlow<List<MemoryItem>> = selectedWord
        .map { word -> allItems.value.filter { word != null && it.matches(word) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 常驻收集角色卡（备注优先，与列表/详情一致）
        viewModelScope.launch {
            characterCardDao.observeAll().collect { _characters.value = it }
        }
    }

    /** 选择热词（点排行项调用） */
    fun selectWord(word: String) {
        _selectedWord.value = word
    }

    /** 归属角色显示名（null=手动/未归属） */
    fun displayNameFor(cardId: Long?): String? {
        if (cardId == null) return null
        val card = _characters.value.firstOrNull { it.id == cardId } ?: return null
        return card.remark.ifBlank { card.name }
    }

    /** 命中项副标题（分类 · 时间） */
    fun subtitle(item: MemoryItem): String =
        "${item.typeLabel()} · ${SimpleTimeLabel(item.updatedAt)}"

    /** 词是否命中某记忆：content 或 keywords 均视为命中（容错关键词缺失） */
    private fun MemoryItem.matches(word: String): Boolean =
        content.contains(word, ignoreCase = true) || keywords.contains(word, ignoreCase = true)

    /** 统计词频：keywords 按中英文逗号拆分（T12.x P1 修复：与检索侧口径对齐，避免中文逗号整串算一词），忽略空串，按出现记忆条数降序 */
    private fun computeHotwords(items: List<MemoryItem>): List<WordCount> {
        val map = HashMap<String, Int>()
        for (item in items) {
            for (raw in item.keywords.split(",", "，")) {
                val word = raw.trim()
                if (word.isNotEmpty()) map[word] = (map[word] ?: 0) + 1
            }
        }
        return map.entries
            .map { WordCount(it.key, it.value) }
            .sortedByDescending { it.count }
    }

    private fun MemoryItem.typeLabel(): String = when (type) {
        com.bdalxy.edencourt.data.local.entity.MemoryType.PERSONAL_INFO -> "个人信息"
        com.bdalxy.edencourt.data.local.entity.MemoryType.PREFERENCE -> "偏好"
        com.bdalxy.edencourt.data.local.entity.MemoryType.EVENT -> "事件"
        com.bdalxy.edencourt.data.local.entity.MemoryType.SKILL -> "技能"
        com.bdalxy.edencourt.data.local.entity.MemoryType.SITUATION -> "情景"
        com.bdalxy.edencourt.data.local.entity.MemoryType.RELATION -> "关系"
        com.bdalxy.edencourt.data.local.entity.MemoryType.EMOTION -> "情感"
        com.bdalxy.edencourt.data.local.entity.MemoryType.OTHER -> "其他"
    }

    /** 命中项时间标签：今天内 HH:mm，其余 MM-dd HH:mm */
    private fun SimpleTimeLabel(time: Long): String {
        val fmt = if (java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(time) == java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(System.currentTimeMillis())
        ) "HH:mm" else "MM-dd HH:mm"
        return java.text.SimpleDateFormat(fmt, java.util.Locale.CHINA).format(time)
    }

    companion object {
        /** 工厂：从 Application 取仓储与角色卡 DAO */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                HotwordViewModel(repository = app.memoryRepository, characterCardDao = app.characterCardDao)
            }
        }
    }
}