package com.bdalxy.edencourt.feature.memory

import android.app.Application
import android.util.Log
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 手动/未归属记忆的筛选哨兵（MemoryItem.characterCardId 为 null 的那些，见 MemoryScreen）。
 * 与 MemoryScreen 共用：-1 表示「只看手动添加/未归属」。
 */
internal const val MANUAL_CARD_ID = -1L

/**
 * 记忆管理页 ViewModel（T5.5 + 阶段一升级 T9.3/9.4/9.5）。
 * - 数据源：不再全量 observeAll 进内存，改由统一检索接口按「搜索词+角色+分类」分页查询（T9.5）
 * - 分类 Tab：7 类 + 全部，带各自数量（T9.4）
 * - 搜索：实时检索（T9.3），300ms 防抖由表 Flow 驱动
 * - 自动刷新：表数据（新增/删除/编辑）变化或筛选条件变化时，重置到第一页重查
 */
class MemoryViewModel(
    private val repository: MemoryRepository,
    private val characterCardDao: CharacterCardDao,
) : ViewModel() {

    /** 全部角色卡（用于按角色筛选 + 归属名解析） */
    val characters: StateFlow<List<CharacterCardEntity>> =
        characterCardDao.observeAll()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** 当前筛选的角色卡 id（null=全部；MANUAL_CARD_ID=只看手动/未归属；T6.13） */
    private val _selectedCardId = MutableStateFlow<Long?>(null)
    val selectedCardId: StateFlow<Long?> = _selectedCardId

    /** 当前筛选的分类（null=全部；T9.4 分类 Tab） */
    private val _selectedType = MutableStateFlow<MemoryType?>(null)
    val selectedType: StateFlow<MemoryType?> = _selectedType

    /** 搜索关键字（T9.4 实时搜索；空串=不过滤） */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** 当前页已加载的记忆（T9.5 分页结果） */
    private val _items = MutableStateFlow<List<MemoryItem>>(emptyList())
    val pagedMemories: StateFlow<List<MemoryItem>> = _items.asStateFlow()

    /** 当前筛选条件下的总条数（T9.5 分页/空态/加载判断） */
    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    /** 各分类数量（T9.4 分类 Tab 角标：null=全部，其余 7 类） */
    private val _tabCounts = MutableStateFlow<Map<MemoryType?, Int>>(emptyMap())
    val tabCounts: StateFlow<Map<MemoryType?, Int>> = _tabCounts.asStateFlow()

    /** 概览统计（T10.6：全库总/置顶/重要/私密，响应式刷新，不受当前筛选影响） */
    private val _overview = MutableStateFlow(OverviewCounts(0, 0, 0, 0))
    val overview: StateFlow<OverviewCounts> = _overview.asStateFlow()

    /** 角色 Tab 数量（T11.2：各角色记忆数 + 手动未归属；随「搜索词+分类筛选」变化，不受当前角色筛选影响） */
    private val _cardTabCounts = MutableStateFlow(CardTabCounts())
    val cardTabCounts: StateFlow<CardTabCounts> = _cardTabCounts.asStateFlow()

    /** 每页条数（T9.5 简单分页） */
    private val pageSize = 20

    /** 已累计加载条数（分页游标，用于 hasMore 与下一页偏移） */
    private var loadedTotal = 0

    /** 是否还有下一页（T9.5 滚动加载；StateFlow 供 UI 可观察，避免普通 getter 快照不触发重组） */
    private val _hasMore = MutableStateFlow(false)
    val hasMoreState: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** 是否还有下一页（内部判断用，读 StateFlow 最新值） */
    val hasMore: Boolean get() = _hasMore.value

    init {
        // 统一驱动：表变化信号（新增/删除改变条数）+ 搜索词 + 角色 + 分类，任一变化 → 重置到第一页重查
        viewModelScope.launch {
            combine(
                repository.observeActiveCount(),
                _searchQuery,
                _selectedCardId,
                _selectedType,
            ) { _, query, cardId, type -> Filter(query, cardId, type) }
                .collect { reloadFirstPage() }
        }
        // 概览统计（T10.6）：随表增删改自动刷新，跨全库不受当前筛选影响
        viewModelScope.launch {
            combine(
                repository.observeActiveCount(),
                repository.observePinnedCount(),
                repository.observeImportantCount(),
                repository.observePrivateCount(),
            ) { total, pinned, important, privateCount ->
                OverviewCounts(total, pinned, important, privateCount)
            }.collect { _overview.value = it }
        }
    }

    /** 一次重查的筛选参数快照 */
    private data class Filter(val query: String, val cardId: Long?, val type: MemoryType?)

    /** 概览统计快照（T10.6：全库 总条数/置顶/重要/私密） */
    data class OverviewCounts(
        val total: Int = 0,
        val pinned: Int = 0,
        val important: Int = 0,
        val privateCount: Int = 0,
    )

    /** 角色 Tab 计数快照（T11.2：total=当前筛选下总数；manual=手动/未归属数；byCard=各角色 id->数量） */
    data class CardTabCounts(
        val total: Int = 0,
        val manual: Int = 0,
        val byCard: Map<Long, Int> = emptyMap(),
    )

    /** 角色卡 id -> 显示名（备注优先，未备注用角色名；T6.14） */
    fun displayNameFor(cardId: Long?): String? {
        if (cardId == null) return null
        val card = characters.value.firstOrNull { it.id == cardId } ?: return null
        return card.remark.ifBlank { card.name }
    }

    /** 搜索框变更（T9.4 实时检索） */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query.trim()
    }

    /** 切换角色筛选（null=全部；MANUAL_CARD_ID=手动/未归属；T6.13） */
    fun setSelectedCard(cardId: Long?) {
        _selectedCardId.value = cardId
    }

    /** 切换分类 Tab（null=全部；T9.4） */
    fun setSelectedType(type: MemoryType?) {
        _selectedType.value = type
    }

    /** 滚动到底追加下一页（T9.5） */
    fun loadMore() {
        if (!hasMore) {
            Log.d("MEM_PAGING", "loadMore SKIP hasMore=false loadedTotal=$loadedTotal totalCount=${_totalCount.value}")
            return
        }
        viewModelScope.launch {
            val page = loadedTotal / pageSize
            val (manual, card) = resolveCardFilter()
            val more = repository.searchPaged(
                query = _searchQuery.value,
                cardId = card,
                manualOnly = manual,
                mapType = _selectedType.value,
                page = page,
                pageSize = pageSize,
            )
            // 追加并去重（防止与当前页因数据变化重叠）
            _items.value = (_items.value + more).distinctBy { it.id }
            loadedTotal += more.size
            _hasMore.value = loadedTotal < _totalCount.value
            Log.d("MEM_PAGING", "loadMore page=$page fetch=${more.size} items=${_items.value.size} loadedTotal=$loadedTotal totalCount=${_totalCount.value} hasMore=${hasMore}")
        }
    }

    /** 手动添加记忆（T5.5：管理页固定作为共享记忆，T7.3；入库后 observeAll 触发自动刷新） */
    fun addMemory(type: MemoryType, content: String, keywords: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addManual(type, trimmed, keywords.trim())
        }
    }

    /** 软删除记忆（T5.5，调用方需已二次确认；软删后 observeAll 触发自动刷新） */
    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.softDelete(id)
        }
    }

    /** 重置到第一页重查：加载首页 + 总数 + 各分类计数 */
    private suspend fun reloadFirstPage() {
        val (manual, card) = resolveCardFilter()
        val first = repository.searchPaged(
            query = _searchQuery.value,
            cardId = card,
            manualOnly = manual,
            mapType = _selectedType.value,
            page = 0,
            pageSize = pageSize,
        )
        _items.value = first
        loadedTotal = first.size
        _totalCount.value = repository.countFiltered(_searchQuery.value, card, manual, _selectedType.value)
        _hasMore.value = loadedTotal < _totalCount.value
        Log.d("MEM_PAGING", "reloadFirstPage query='${_searchQuery.value}' fetch=${first.size} loadedTotal=$loadedTotal totalCount=${_totalCount.value}")
        loadTabCounts()
        loadCardTabCounts()
    }

    /** 计算各分类选项卡数量（T9.4：Tab 角标） */
    private suspend fun loadTabCounts() {
        val (manual, card) = resolveCardFilter()
        val query = _searchQuery.value
        val map = mutableMapOf<MemoryType?, Int>()
        map[null] = repository.countFiltered(query, card, manual, null)
        MemoryType.entries.forEach { t ->
            map[t] = repository.countFiltered(query, card, manual, t)
        }
        _tabCounts.value = map
    }

    /**
     * 计算角色 Tab 数量（T11.2：全部/手动未归属/各角色）。
     * 维度跟随「搜索词+分类筛选」变化，**不受当前角色筛选影响**（否则选中某角色后其余角色数量归零、Tab 失去意义）。
     * 角色卡数量很少（个位数），逐卡 countFiltered 开销可接受。
     */
    private suspend fun loadCardTabCounts() {
        val query = _searchQuery.value
        val type = _selectedType.value
        val total = repository.countFiltered(query, null, false, type)
        val manual = repository.countFiltered(query, null, true, type)
        val byCard = mutableMapOf<Long, Int>()
        characters.value.forEach { card ->
            byCard[card.id] = repository.countFiltered(query, card.id, false, type)
        }
        _cardTabCounts.value = CardTabCounts(total = total, manual = manual, byCard = byCard)
    }

    /** 解析角色筛选：MANUAL_CARD_ID→manualOnly=true；否则按卡 id */
    private fun resolveCardFilter(): Pair<Boolean, Long?> = when (_selectedCardId.value) {
        MANUAL_CARD_ID -> true to null
        else -> false to _selectedCardId.value
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