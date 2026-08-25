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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** 图谱节点：关键词 + 提及条数（T11.5；节点大小∝count） */
data class GraphNode(val word: String, val count: Int)

/** 图谱边：两个关键词在同一记忆条目中共现（T11.5；weight=共现次数） */
data class GraphEdge(val from: String, val to: String, val weight: Int)

/** 关系图谱：节点集 + 边集（T11.5，由全库记忆关键词共现派生） */
data class RelationGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
)

/** 力导向布局后的节点：含归一化坐标 0~1（T11.5；绘制时乘以视口尺寸） */
data class GraphLayoutNode(
    val word: String,
    val count: Int,
    val x: Float,
    val y: Float,
)

/**
 * 关系图谱 ViewModel（阶段三 T11.5）。
 * - 由全库记忆关键词统计「共现图」：节点=关键词（取词频前 TOP_N），边=同一记忆条目中共现
 * - 力导向布局（后台线程）：斥力 + 边引力 + 向心，迭代收敛出节点坐标，供 Canvas 绘制
 * - 选中节点词 → 命中记忆列表（同 T11.1 热词筛选）+ 共现词 chips（点选切换）
 */
class GraphViewModel(
    private val repository: MemoryRepository,
    private val characterCardDao: CharacterCardDao,
) : ViewModel() {

    /** 角色卡（主动常驻收集，归属名解析用，与热词页一致） */
    private val _characters = MutableStateFlow<List<CharacterCardEntity>>(emptyList())
    val characters: StateFlow<List<CharacterCardEntity>> = _characters.asStateFlow()

    /** 全库未删除记忆（共现图与命中列表都由它派生，随表自动刷新） */
    private val allItems = repository.observeTimeline()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 关系图谱（共现图，后台线程计算，避免阻塞 UI） */
    val graph: StateFlow<RelationGraph> = allItems
        .map { items -> withContext(Dispatchers.Default) { computeGraph(items) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RelationGraph(emptyList(), emptyList()))

    /** 力导向布局后的节点坐标（0~1 归一化，后台线程计算，供 Canvas 绘制） */
    val layout: StateFlow<List<GraphLayoutNode>> = graph
        .map { g -> withContext(Dispatchers.Default) { computeLayout(g) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前选中节点词（null=未选中；点节点/共现词 chip 时设置） */
    private val _selectedWord = MutableStateFlow<String?>(null)
    val selectedWord: StateFlow<String?> = _selectedWord.asStateFlow()

    /** 命中「选中词」的记忆列表（content 或 keywords 含该词） */
    val hitMemories: StateFlow<List<MemoryItem>> = selectedWord
        .map { word -> allItems.value.filter { word != null && matches(it, word) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 与「选中词」共现的词（Pair<词, 共现次数>，按共现次数降序），供点选切换 */
    val coWords: StateFlow<List<Pair<String, Int>>> = combine(selectedWord, graph) { word, g ->
        if (word == null) {
            emptyList()
        } else {
            g.edges
                .filter { it.from == word || it.to == word }
                .map { if (it.from == word) it.to to it.weight else it.from to it.weight }
                .sortedByDescending { it.second }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // 常驻收集角色卡（备注优先，与列表/详情一致）
        viewModelScope.launch {
            characterCardDao.observeAll().collect { _characters.value = it }
        }
    }

    /** 选择节点词（点图谱节点或共现词 chip 调用） */
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
    private fun matches(item: MemoryItem, word: String): Boolean =
        item.content.contains(word, ignoreCase = true) || item.keywords.contains(word, ignoreCase = true)

    /**
     * 统计共现图：
     * - 节点：keywords 按逗号拆词，取提及条数前 TOP_N
     * - 边：同一条记忆内任意两词共现一次，共现次数 ≥ MIN_EDGE_WEIGHT 才保留（避免图过密）
     */
    private fun computeGraph(items: List<MemoryItem>): RelationGraph {
        val freq = HashMap<String, Int>()          // 词 → 提及条数
        val co = HashMap<String, Int>()            // "a\u0001b"（a<b 排序保证唯一）→ 共现次数
        for (item in items) {
            val words = item.keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            for (w in words) freq[w] = (freq[w] ?: 0) + 1
            // 同一条记忆内两两配对，累加共现
            for (i in words.indices) for (j in i + 1 until words.size) {
                val a = if (words[i] < words[j]) words[i] else words[j]
                val b = if (words[i] < words[j]) words[j] else words[i]
                val key = "$a\u0001$b"
                co[key] = (co[key] ?: 0) + 1
            }
        }
        // 节点：词频降序取前 TOP_N
        val nodes = freq.entries
            .sortedByDescending { it.value }
            .take(TOP_N)
            .map { GraphNode(it.key, it.value) }
        val nodeSet = nodes.mapTo(HashSet()) { it.word }
        // 边：两端都在节点集内且共现 ≥ 阈值，按共现次数降序
        val edges = co.entries
            .mapNotNull { e ->
                val parts = e.key.split('\u0001')
                val a = parts[0]; val b = parts[1]
                if (a in nodeSet && b in nodeSet && e.value >= MIN_EDGE_WEIGHT) {
                    GraphEdge(a, b, e.value)
                } else {
                    null
                }
            }
            .sortedByDescending { it.weight }
        return RelationGraph(nodes, edges)
    }

    /**
     * 力导向布局（FR 风格简化版）：
     * - 初位置：均匀分布在单位圆上（圆心 0.5,0.5，半径 0.38）
     * - 迭代：节点对斥力（距离平方反比）+ 有边节点引力（∝距离×共现权重）+ 向心引力（防漂移）
     * - 输出归一化坐标 0~1，绘制时乘以画布尺寸
     */
    private fun computeLayout(graph: RelationGraph): List<GraphLayoutNode> {
        val n = graph.nodes.size
        if (n == 0) return emptyList()
        val xs = FloatArray(n); val ys = FloatArray(n)
        for (i in 0 until n) {
            val angle = 2 * PI * i / n
            xs[i] = (0.5 + 0.38 * cos(angle)).toFloat()
            ys[i] = (0.5 + 0.38 * sin(angle)).toFloat()
        }
        val idxOf = HashMap<String, Int>()
        graph.nodes.forEachIndexed { i, node -> idxOf[node.word] = i }
        val fx = FloatArray(n); val fy = FloatArray(n)
        repeat(LAYOUT_ITER) {
            fx.fill(0f); fy.fill(0f)
            // 斥力：所有节点对，距离平方反比，防止重叠
            for (i in 0 until n) for (j in i + 1 until n) {
                var dx = xs[i] - xs[j]; var dy = ys[i] - ys[j]
                var d = hypot(dx, dy)
                if (d < 0.01f) { dx = 0.01f; dy = 0.01f; d = hypot(dx, dy) }
                val rep = (REPULSION / (d * d)).toFloat()
                fx[i] += dx / d * rep; fy[i] += dy / d * rep
                fx[j] -= dx / d * rep; fy[j] -= dy / d * rep
            }
            // 引力：有边相连的节点沿边方向相吸，强度∝距离×共现权重
            for (edge in graph.edges) {
                val i = idxOf[edge.from] ?: continue
                val j = idxOf[edge.to] ?: continue
                val dx = xs[j] - xs[i]; val dy = ys[j] - ys[i]
                val d = max(hypot(dx, dy), 0.01f)
                val attr = (ATTRACTION * d * edge.weight).toFloat()
                fx[i] += dx / d * attr; fy[i] += dy / d * attr
                fx[j] -= dx / d * attr; fy[j] -= dy / d * attr
            }
            for (i in 0 until n) {
                // 向心引力：拉回画布中心，防止整体漂移
                fx[i] += (CENTER_FORCE * (0.5f - xs[i])).toFloat()
                fy[i] += (CENTER_FORCE * (0.5f - ys[i])).toFloat()
                // 阻尼更新 + 边界钳制（留边距给标签）
                xs[i] = (xs[i] + fx[i] * DAMPING).coerceIn(0.08f, 0.92f)
                ys[i] = (ys[i] + fy[i] * DAMPING).coerceIn(0.08f, 0.92f)
            }
        }
        return graph.nodes.mapIndexed { i, node -> GraphLayoutNode(node.word, node.count, xs[i], ys[i]) }
    }

    /** 命中项时间标签：今天内 HH:mm，其余 MM-dd HH:mm */
    private fun SimpleTimeLabel(time: Long): String {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
            .format(System.currentTimeMillis())
        val fmt = if (java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(time) == today
        ) "HH:mm" else "MM-dd HH:mm"
        return java.text.SimpleDateFormat(fmt, java.util.Locale.CHINA).format(time)
    }

    private fun MemoryItem.typeLabel(): String = when (type) {
        com.bdalxy.edencourt.data.local.entity.MemoryType.PERSONAL_INFO -> "个人信息"
        com.bdalxy.edencourt.data.local.entity.MemoryType.PREFERENCE -> "偏好"
        com.bdalxy.edencourt.data.local.entity.MemoryType.EVENT -> "事件"
        com.bdalxy.edencourt.data.local.entity.MemoryType.SKILL -> "技能"
        com.bdalxy.edencourt.data.local.entity.MemoryType.SITUATION -> "情景"
        com.bdalxy.edencourt.data.local.entity.MemoryType.RELATION -> "关系"
        com.bdalxy.edencourt.data.local.entity.MemoryType.OTHER -> "其他"
    }

    companion object {
        /** 节点数上限（词太多画布会过密，取词频前 N） */
        private const val TOP_N = 30
        /** 边共现次数阈值：共现 ≥ 该值才画线（过滤稀疏共现，避免图过密） */
        private const val MIN_EDGE_WEIGHT = 2
        /** 力导向迭代次数 */
        private const val LAYOUT_ITER = 300
        /** 斥力常数（归一化空间，距离平方反比） */
        private const val REPULSION = 0.0008f
        /** 引力常数（有边节点，∝距离×权重） */
        private const val ATTRACTION = 0.02f
        /** 向心引力常数（拉回中心防漂移） */
        private const val CENTER_FORCE = 0.05f
        /** 阻尼系数（力应用的缩放） */
        private const val DAMPING = 0.6f

        /** 工厂：从 Application 取仓储与角色卡 DAO */
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EdenCourtApp
                GraphViewModel(repository = app.memoryRepository, characterCardDao = app.characterCardDao)
            }
        }
    }
}
