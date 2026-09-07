package com.bdalxy.edencourt.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.domain.ContextBudget

/**
 * 聊天页「知识库」右抽屉（v1.5 上下文注入层 UI）。
 *
 * 从右侧滑出的全高抽屉：把本轮注入的上下文按「优先级链」分组可视化（对齐 ContextBudget）：
 *   ② 恒久身份（角色卡）
 *   ③ 恒久世界规则 / ⑤ 世界书命中（世界书行）
 *   ⑤ 记忆（共享+私有）
 *   ④ 关系与状态（亲密度/情绪/生活状态）
 * 底部一条总的预算占用进度条，让用户直观看到「这个角色此刻脑子里装了什么、占多少 token」。
 *
 * 入口：聊天页顶栏右上角「⋯」。抽屉是纯展示组件，token 数值由上层（ChatScreen）传入，
 * 保证显示值与实际注入一致（共用 TokenEstimator.count，见 ContextBudget/TokenEstimator）。
 *
 * @param visible        是否显示（true 时从右侧滑入）
 * @param roleName       会话角色名
 * @param avatarPath     会话角色头像路径（CharacterAvatar 用）
 * @param characterTokens 角色卡（恒久身份）估值 token
 * @param worldBookLabel 世界书行文案（如「未挂载」/「已挂 1 本 · 命中 3 条」）
 * @param worldTokens    世界书浮动 token（~值，命中条数变化）
 * @param worldHitCount  世界书命中条数（>0 时徽章高亮，指示「正在生效」）
 * @param memoryLabel    记忆行文案（如「共享 12 · 私有 5」）
 * @param memoryTokens   记忆浮动 token（~值）
 * @param affinityText   亲密度文案（如「63 / 100」）
 * @param lifeStateText  生活状态文案（如「午后散步」）
 * @param moodText       当前情绪文案（如「开心 · 期待」）
 * @param unreadInteractions 互动未读数（>0 时互动行显示红点）
 * @param budgetUsed     已用 token（预算条）
 * @param onOpenMemory   点击「记忆」行（触发原 SessionMemorySheet）
 * @param onOpenInteraction 点击「互动」行（触发原 InteractionSheet）
 * @param onOpenCharacterDetail 点击「角色卡」行（跳转角色卡详情页）
 * @param onMountWorldBook 点击「世界书」行（弹出选书挂载）
 * @param onManageWorldBook 点击世界书行尾「管理」（跳设置页/管理页）
 * @param onDismiss      点击遮罩/关闭
 */
@Composable
fun KnowledgeDrawer(
    visible: Boolean,
    roleName: String,
    avatarPath: String?,
    characterTokens: Int,
    worldBookLabel: String,
    worldTokens: Int,
    worldHitCount: Int,
    memoryLabel: String,
    memoryTokens: Int,
    affinityText: String,
    /** 关系方向文案（如「朋友」/「暧昧」/「尚未明确」） */
    directionText: String,
    lifeStateText: String,
    moodText: String,
    unreadInteractions: Int,
    budgetUsed: Int,
    onOpenMemory: () -> Unit,
    onOpenInteraction: () -> Unit,
    onOpenCharacterDetail: () -> Unit,
    onMountWorldBook: () -> Unit,
    onManageWorldBook: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 遮罩 + 抽屉容器（抽屉占屏宽 82%，右侧滑入/滑出）
    Box(modifier = Modifier.fillMaxSize()) {
        // 半透明遮罩：点遮罩关闭，避免事件穿透
        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 头部：角色头像 + 角色名 + 关闭
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CharacterAvatar(
                            avatarPath = avatarPath,
                            name = roleName,
                            modifier = Modifier.size(36.dp),
                            textSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "知识库",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = roleName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(onClick = onDismiss),
                        )
                    }
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp),
                    ) {
                        // —— ② 恒久身份：角色卡 ——
                        SectionLabel("恒久身份")
                        DrawerItem(
                            icon = { Icon(Icons.Filled.Person, contentDescription = "角色卡", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            label = "角色卡",
                            sub = "名字 · 性格 · 背景 · 说话风格",
                            token = characterTokens,
                            onClick = onOpenCharacterDetail, // 跳转角色卡详情页
                        )
                        // —— ③⑤ 世界书：挂载 + 命中 ——
                        SectionLabel("世界书")
                        DrawerWorldItem(
                            label = worldBookLabel,
                            token = worldTokens,
                            hitCount = worldHitCount,
                            onClick = onMountWorldBook,
                            onManage = onManageWorldBook,
                        )
                        // —— ⑤ 记忆（共享 + 私有）——
                        SectionLabel("记忆")
                        DrawerItem(
                            icon = { Icon(Icons.Filled.Memory, contentDescription = "记忆", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                            label = "记忆",
                            sub = memoryLabel,
                            token = memoryTokens,
                            onClick = onOpenMemory,
                        )
                        // —— ④ 关系与状态（只读）——
                        SectionLabel("关系与状态")
                        DrawerStateRow(
                            icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = "情绪", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)) },
                            label = "当前情绪",
                            value = moodText,
                        )
                        DrawerStateRow(
                            icon = { Icon(Icons.Filled.Schedule, contentDescription = "状态", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp)) },
                            label = "生活状态",
                            value = lifeStateText,
                        )
                        // 注意：关系为内部状态，亲密度/关系方向数值不外显（2026-09-07 R5 拍板）
                        // —— 互动行：进入互动面板 ——
                        SectionLabel("互动")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenInteraction)
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BadgedBox(badge = {
                                if (unreadInteractions > 0) Badge { Text("$unreadInteractions") }
                            }) {
                                Icon(Icons.Filled.Email, contentDescription = "互动", tint = MaterialTheme.colorScheme.tertiary)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("互动", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("查看记录 · 开放互动 ›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        // —— 预算占用条 ——
                        BudgetBar(used = budgetUsed, total = ContextBudget.DEFAULT_TOTAL_BUDGET)
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

/** 分区小标题（对齐优先级链分组） */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .padding(start = 10.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** 普通横排条目：图标 + 主标题 + 副标题 + token 徽章（实心 = 固定值） */
@Composable
private fun DrawerItem(
    icon: @Composable () -> Unit,
    label: String,
    sub: String,
    token: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        TokenBadge(token, floating = false)
    }
}

/** 世界书行：挂载文案 + 命中徽章 + 行尾「管理」；点行 = 挂载，点管理 = 去管理页 */
@Composable
private fun DrawerWorldItem(
    label: String,
    token: Int,
    hitCount: Int,
    onClick: () -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Text("📖", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // 命中徽章：激活中绿点 / 未命中灰点（指示「本条是否真的被注入」）
        val hitColor = if (hitCount > 0) Color(0xFF3FA36B) else Color(0xFFC6C1CC)
        Spacer(modifier = Modifier.width(6.dp))
        TokenBadge(token, floating = true)
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .clickable(onClick = onManage)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("管理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        // 命中指示点
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(hitColor, RoundedCornerShape(4.dp)),
        )
    }
}

/** 只读状态行（情绪/生活状态/亲密度）：图标 + 标签 + 值 */
@Composable
private fun DrawerStateRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** token 徽章：固定值实心灰紫 / 浮动值虚线描边 + 「~」前缀（对齐原型固定/浮动区分） */
@Composable
private fun TokenBadge(text: Int, floating: Boolean) {
    val shape = RoundedCornerShape(9.dp)
    if (floating) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                text = "~$text",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = "$text",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

/** 底部预算占用条：已用/总 token + LinearProgressIndicator（接近上限时变警示色） */
@Composable
private fun BudgetBar(used: Int, total: Int) {
    val ratio = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("本次注入预算", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$used / $total tok", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}