package com.bdalxy.edencourt.feature.memory

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.data.local.entity.MemoryItem
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 关系图谱页（阶段三 T11.5）。
 * - 顶部 Canvas：共现网络图——节点=关键词（大小∝提及次数）、连线=共现（粗细∝次数）
 * - 点节点：高亮 + 下方显示「共现词 chips + 命中记忆列表」（复用热词命中项）
 * - 点记忆进详情（复用 MemoryDetailScreen 覆盖）；系统返回逐级回退
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: GraphViewModel = viewModel(
        factory = GraphViewModel.factory(context.applicationContext as Application),
    )
    val graph by viewModel.graph.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val selectedWord by viewModel.selectedWord.collectAsStateWithLifecycle()
    val coWords by viewModel.coWords.collectAsStateWithLifecycle()
    val hits by viewModel.hitMemories.collectAsStateWithLifecycle()
    viewModel.characters.collectAsStateWithLifecycle()

    // 当前正在查看详情（点命中记忆进详情，复用 MemoryDetailScreen 覆盖）
    var detailId by remember { mutableStateOf<Long?>(null) }

    // 系统返回：拦截为「回到记忆列表」；进详情时由详情页自身拦截
    BackHandler(enabled = detailId == null) { onBack() }

    val detailIdValue = detailId
    if (detailIdValue != null) {
        MemoryDetailScreen(memoryId = detailIdValue, onBack = { detailId = null })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("关系图谱") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // 图例说明
                Text(
                    text = "共现网络：节点大小=提及次数，连线=共同出现（越粗越频繁）；点节点看关联记忆",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                // 图谱画布（占剩余空间，供 Canvas 绘制与点击）
                GraphCanvas(
                    layout = layout,
                    edges = graph.edges,
                    selected = selectedWord,
                    onNodeTap = { viewModel.selectWord(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                HorizontalDivider()

                // 下方：共现词 chips + 命中记忆列表（选中节点后显示）
                if (selectedWord == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "点击图中节点，这里会显示与它共现的词和相关记忆",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        // 共现词 chips：点选切换选中词（切换后命中列表联动）
                        if (coWords.isEmpty()) {
                            Text(
                                text = "「$selectedWord」暂无明显共现词",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(coWords, key = { it.first }) { (word, weight) ->
                                    AssistChip(
                                        onClick = { viewModel.selectWord(word) },
                                        label = { Text("$word ×$weight", fontSize = 12.sp) },
                                    )
                                }
                            }
                        }
                        // 命中记忆列表（固定高度区域，点击进详情）
                        if (hits.isEmpty()) {
                            Text(
                                text = "「$selectedWord」暂无相关记忆",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        } else {
                            Text(
                                text = "包含「$selectedWord」的记忆 · ${hits.size} 条",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                            )
                            LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                                items(hits, key = { it.id }) { memory ->
                                    HotwordHitItem(
                                        memory = memory,
                                        ownerName = viewModel.displayNameFor(memory.characterCardId),
                                        subtitle = viewModel.subtitle(memory),
                                        onClick = { detailId = memory.id },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 图谱画布：绘制边（粗细∝共现次数）、节点圆（半径∝提及次数）、节点标签；点击命中最近节点。
 * @param layout 力导向布局后的节点（0~1 归一化坐标）
 * @param edges 共现边
 * @param selected 当前选中词（高亮该节点）
 * @param onNodeTap 命中节点回调
 */
@Composable
private fun GraphCanvas(
    layout: List<GraphLayoutNode>,
    edges: List<GraphEdge>,
    selected: String?,
    onNodeTap: (String) -> Unit,
    modifier: Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    // 画布实际像素尺寸（命中检测与节点半径计算需要）
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 词 → 布局节点索引（绘制连线时查端点坐标）
    val posOf = remember(layout) {
        layout.withIndex().associate { it.value.word to it }
    }
    val maxCount = layout.maxOfOrNull { it.count } ?: 1
    // 颜色与密度在 Composable 内取值（Canvas onDraw 与手势 lambda 非 Composable，不能直接访问主题）
    val edgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val nodeColor = MaterialTheme.colorScheme.primary
    val selColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current.density

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(layout, selected) {
                detectTapGestures { offset ->
                    // 命中检测：找距离点击位置最近的节点（画布坐标换算）
                    val nearest = layout.minByOrNull {
                        hypot(it.x * canvasSize.width - offset.x, it.y * canvasSize.height - offset.y)
                    }
                    if (nearest != null) {
                        val radius = nodeRadiusPx(nearest.count, maxCount, density)
                        if (hypot(nearest.x * canvasSize.width - offset.x, nearest.y * canvasSize.height - offset.y) <= radius + 24f) {
                            onNodeTap(nearest.word)
                        }
                    }
                }
            },
    ) {
        // 画边：共现关系，粗细∝权重（clamp 1~5dp）
        for (edge in edges) {
            val from = posOf[edge.from] ?: continue
            val to = posOf[edge.to] ?: continue
            drawLine(
                color = edgeColor,
                start = Offset(from.value.x * size.width, from.value.y * size.height),
                end = Offset(to.value.x * size.width, to.value.y * size.height),
                strokeWidth = (1f + edge.weight).coerceAtMost(5f).dp.toPx(),
            )
        }
        // 画节点：圆 + 标签
        for (node in layout) {
            val center = Offset(node.x * size.width, node.y * size.height)
            val radius = nodeRadiusPx(node.count, maxCount, density)
            val isSel = node.word == selected
            // 节点圆（选中用强调色并加白描边突出）
            drawCircle(
                color = if (isSel) selColor else nodeColor,
                radius = radius,
                center = center,
            )
            if (isSel) {
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
            // 节点词标签（放节点上方，居中，词过长截断）
            drawLabel(
                measurer = textMeasurer,
                text = node.word,
                center = center,
                above = true,
                maxWidth = size.width * 0.9f,
                labelColor = labelColor,
            )
        }
    }
}

/** 画一个居中的文本标签（above=true 放节点上方；颜色由调用方传入保证非 Composable 可用） */
private fun DrawScope.drawLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    above: Boolean,
    maxWidth: Float,
    labelColor: Color,
) {
    val style = TextStyle(
        color = labelColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    )
    val layoutResult = measurer.measure(
        AnnotatedString(text),
        style = style,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth.toInt()),
        overflow = TextOverflow.Ellipsis,
    )
    val x = (center.x - layoutResult.size.width / 2f).coerceIn(0f, size.width - layoutResult.size.width)
    val y = if (above) (center.y - 8.dp.toPx() - layoutResult.size.height).coerceAtLeast(0f)
    else (center.y + 8.dp.toPx()).coerceAtMost(size.height - layoutResult.size.height)
    drawText(layoutResult, topLeft = Offset(x, y))
}

/** 节点半径：基础 + 按提及次数归一化增长（像素；dp→px 用 density 换算，供手势与绘制共用） */
private fun nodeRadiusPx(count: Int, maxCount: Int, density: Float): Float {
    val base = 10 * density
    val ratio = if (maxCount > 0) sqrt(count.toFloat() / maxCount) else 0f
    return base + ratio * 16 * density
}
