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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 时间轴中的一个「天」分组（T10.7）：标题（今天/昨天/日期）+ 该天的记忆 */
data class TimelineDay(val label: String, val items: List<MemoryItem>)

/**
 * 时间轴 ViewModel（阶段二可视化 T10.7）。
 * - 全库未删除记忆按更新时间降序（记忆「演化」），按天分组：今天 / 昨天 / 具体日期
 * - 角色卡主动收集，用于列表项归属名解析（备注优先）
 */
class TimelineViewModel(
    private val repository: MemoryRepository,
    private val characterCardDao: CharacterCardDao,
) : ViewModel() {

    /** 角色卡（主动常驻收集，避免懒订阅致归属名为空） */
    private val _characters = MutableStateFlow<List<CharacterCardEntity>>(emptyList())
    val characters: StateFlow<List<CharacterCardEntity>> = _characters.asStateFlow()

    /** 按天分组的记忆时间轴（随表增删改自动刷新） */
    val days: StateFlow<List<TimelineDay>> = repository.observeTimeline()
        .map { groupByDay(it) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    init {
        // 常驻收集角色卡（备注优先，与详情/列表一致）
        viewModelScope.launch {
            characterCardDao.observeAll().collect { _characters.value = it }
        }
    }

    /** 归属角色显示名（依赖外部先 subscribe characters 保证有数据；此处供 UI 直接调） */
    fun displayNameFor(cardId: Long?): String? {
        if (cardId == null) return null
        val card = _characters.value.firstOrNull { it.id == cardId } ?: return null
        return card.remark.ifBlank { card.name }
    }

    /** 时间轴项副标题（类型 + 更新时间） */
    fun subtitle(item: MemoryItem): String {
        val label = TimeLabel(item.updatedAt)
        return "${item.typeLabel()} · $label"
    }

    private fun MemoryItem.typeLabel(): String = when (type) {
        MemoryType.PERSONAL_INFO -> "个人信息"
        MemoryType.PREFERENCE -> "偏好"
        MemoryType.EVENT -> "事件"
        MemoryType.SKILL -> "技能"
        MemoryType.SITUATION -> "情景"
        MemoryType.RELATION -> "关系"
        MemoryType.EMOTION -> "情感"
        MemoryType.OTHER -> "其他"
    }

    /** 把扁平记忆列表按「更新时间所属天」分组，保持最近在前的顺序 */
    private fun groupByDay(items: List<MemoryItem>): List<TimelineDay> {
        if (items.isEmpty()) return emptyList()
        // 按天边界切段：同一天累积到 pending，跨天时把上一段落成一个「天」分组
        val result = ArrayList<TimelineDay>()
        val pending = ArrayList<MemoryItem>()
        var lastKey: String? = null
        for (item in items) {
            val key = dayKeyOf(item.updatedAt)
            if (key != lastKey) {
                if (pending.isNotEmpty()) {
                    result.add(TimelineDay(labelOfDay(pending.first().updatedAt), pending.toList()))
                }
                pending.clear()
                lastKey = key
            }
            pending.add(item)
        }
        if (pending.isNotEmpty()) {
            result.add(TimelineDay(labelOfDay(pending.first().updatedAt), pending.toList()))
        }
        return result
    }

    private fun dayKeyOf(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(time)

    /** 天标题：今天 / 昨天 / 具体日期 */
    private fun labelOfDay(time: Long): String {
        val cal = Calendar.getInstance()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.timeInMillis)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.timeInMillis)
        val key = dayKeyOf(time)
        return when (key) {
            today -> "今天"
            yesterday -> "昨天"
            else -> key
        }
    }

    /** 时间标签：今天内显示 HH:mm，其余显示 MM-dd HH:mm */
    private fun TimeLabel(time: Long): String {
        val cal = Calendar.getInstance()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.timeInMillis)
        val key = dayKeyOf(time)
        val fmt = if (key == today) "HH:mm" else "MM-dd HH:mm"
        return SimpleDateFormat(fmt, Locale.CHINA).format(time)
    }

    companion object {
        /** 工厂：从 Application 取仓储与角色卡 DAO */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                TimelineViewModel(repository = app.memoryRepository, characterCardDao = app.characterCardDao)
            }
        }
    }
}