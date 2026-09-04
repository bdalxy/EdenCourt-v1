package com.bdalxy.edencourt.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bdalxy.edencourt.core.ui.CharacterAvatar

/**
 * B4 关系图谱页（用户规格：`我` 默认且强制开启、置顶独占一行；开启图谱的会话角色两两成行；
 * 点某角色展开其与「我」及与其它开启角色的关系）。
 * - 顶部「我」独占大卡（恒开启）
 * - 「编辑成员」：给每个会话角色开关是否入图谱
 * - 中部：开启的角色两两一行网格（点选看关系）
 * - 底部：选中角色的关系面板（↔我 + ↔其它开启角色）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleRelationGraphScreen(onBack: () -> Unit) {
    val viewModel: RoleRelationGraphViewModel = viewModel(factory = RoleRelationGraphViewModel.factory)
    val meName by viewModel.meName.collectAsStateWithLifecycle()
    val enabledNodes by viewModel.enabledNodes.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedConvId.collectAsStateWithLifecycle()

    // 编辑成员弹层
    var showEdit by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关系图谱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { showEdit = true },
                        label = { Text("编辑成员") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ──「我」置顶独占大卡（恒开启置顶）──
            MyRootCard(
                meName = meName,
                relationCount = enabledNodes.size,
                enabledNames = enabledNodes.take(4).map { it.displayName },
                onClick = { /* 我即根：图谱主体即下方网格，无需额外动作 */ },
            )

            if (enabledNodes.isEmpty()) {
                // 空态：一个也没开 → 引导编辑成员
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有角色加入关系图谱", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "点右上「编辑成员」，把你想观察关系的会话角色开起来",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    AssistChip(onClick = { showEdit = true }, label = { Text("编辑成员") })
                }
            } else {
                Text(
                    "已开启的会话角色（与我相连）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // ── 开启角色 两两一行 网格 ──
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(enabledNodes, key = { it.convId }) { node ->
                        val sel = selectedId == node.convId
                        RoleNodeCard(
                            node = node,
                            selected = sel,
                            onClick = {
                                if (sel) viewModel.select(null) else viewModel.select(node.convId)
                            },
                        )
                    }
                }
            }

            // ── 选中角色的关系面板（随选中刷新）──
            val d = detail
            if (d != null) {
                RelationDetailPanel(meName = meName, detail = d)
            }
        }
    }

    if (showEdit) {
        // 编辑成员弹层：全部会话角色逐个开关
        val allNodes by viewModel.nodes.collectAsStateWithLifecycle()
        ModalBottomSheet(
            onDismissRequest = { showEdit = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    "关系图谱成员",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    "开启后，该会话角色会出现在关系图谱中，并与其它开启角色连线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                if (allNodes.isEmpty()) {
                    Text(
                        "还没有会话角色，先去「角色卡」建一个会话吧。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(20.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(allNodes, key = { it.convId }) { n ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CharacterAvatar(
                                    avatarPath = n.avatar,
                                    name = n.displayName,
                                    modifier = Modifier.size(36.dp),
                                    textSize = 14.sp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(n.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Switch(checked = n.graphEnabled, onCheckedChange = { viewModel.toggleGraph(n.convId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 「我」置顶独占大卡：恒开启根节点 */
@Composable
private fun MyRootCard(meName: String, relationCount: Int, enabledNames: List<String>, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(meName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (relationCount == 0) "我，关系图谱的起点（恒置顶）" else "与 $relationCount 个会话角色相连",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enabledNames.isNotEmpty()) {
                    Text(
                        enabledNames.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 单个会话角色节点卡（两两一行） */
@Composable
private fun RoleNodeCard(node: RoleRelationNode, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterAvatar(
                avatarPath = node.avatar,
                name = node.displayName,
                modifier = Modifier.size(44.dp),
                textSize = 16.sp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(node.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "与我 · ${node.relationWithMe}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        " ${node.affinityWithMe}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (selected) {
                Text("展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 选中角色后的关系详情面板（↔我 + ↔其它开启角色） */
@Composable
private fun RelationDetailPanel(meName: String, detail: RoleRelationDetail) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("关系详情", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // 与我：亲密度 + 档位
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("与「$meName」", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text("${detail.meRelation} · ${detail.meAffinity}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            if (detail.others.isEmpty()) {
                Text(
                    "与其它图谱成员暂未相交（尚未一起互动过）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                Text("与其他图谱成员", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                detail.others.forEach { (name, tier) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(tier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}