package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdalxy.edencourt.core.ui.CharacterAvatar
import com.bdalxy.edencourt.core.ui.LifeStateIndicator
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.LifeState
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 聊天页顶部栏（T15.6 拆分：从 ChatScreen 抽离，承载「角色头像+名 / 正在输入 / 此刻状态 / 独有印象 / 开抽屉」）。
 * 把原来内联在 ChatScreen 顶栏里三层状态的 title 收起为独立组件，降低主界面复杂度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    roleAvatar: String?,
    roleName: String,
    isGenerating: Boolean,
    scheduleEnabled: Boolean,
    lifeEnabled: Boolean,
    scheduleConfig: ScheduleConfig,
    card: CharacterCardEntity?,
    conv: Conversation?,
    // 本会话「独有印象」摘要（空白则不显示该行，T13.6）
    traitsBlurb: String,
    onBack: () -> Unit,
    onOpenLife: () -> Unit,
    onEditTraits: () -> Unit,
    onOpenMore: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterAvatar(
                        avatarPath = roleAvatar,
                        name = roleName,
                        modifier = Modifier.size(32.dp),
                        textSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(roleName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // T6.8 + T11.10：生成中显示「正在输入…」，否则显示角色作息状态（或未开启提示）
                if (isGenerating) {
                    Text(
                        text = "正在输入…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 40.dp),
                    )
                } else if (card != null) {
                    // T11.10 深化：全局开关 && 会话开关都开启才显示「此刻状态」；否则提示未开启，点击进入作息面板
                    if (lifeEnabled) {
                        LifeStateIndicator(
                            state = roleLifeState(scheduleConfig, card, conv),
                            modifier = Modifier
                                .padding(start = 40.dp)
                                .clickable { onOpenLife() },
                        )
                    } else {
                        Text(
                            text = if (scheduleEnabled) "作息未开启 · 点此开启" else "角色作息未开启",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 40.dp)
                                .clickable { onOpenLife() },
                        )
                    }
                }
                // T13.6：会话「独有印象」——顶部显示本会话特有的印象摘要（同一张卡不同会话一眼区分）
                if (traitsBlurb.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .padding(start = 40.dp, top = 2.dp)
                            .clickable { onEditTraits() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = traitsBlurb,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "编辑印象",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            // A2（v1.5 上下文注入层 UI）：会话操作收敛为单「⋯」入口——点开「知识库」抽屉
            IconButton(onClick = onOpenMore) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
        },
    )
}

/**
 * 角色「此刻状态」（T11.10 替代 T6.8 的「在线/睡觉中」二分标签）。
 * 按角色生效作息（ScheduleConfig）+ 真实时间计算多态生活状态；每分钟刷新一次。
 * T11.10 深化：经 resolveConfig 解析生效作息（会话自定义 > 角色自定义 > 个性偏移 > 基础）。
 */
@Composable
private fun roleLifeState(config: ScheduleConfig, card: CharacterCardEntity?, conv: Conversation?): LifeState {
    // 解析该角色最终生效作息（会话自定义 > 角色自定义 > 个性偏移 > 基础），保证与会话列表一致
    val resolved = CharacterSchedule.resolveConfig(config, card, conv)
    var state by remember { mutableStateOf(CharacterSchedule.computeState(resolved, LocalTime.now())) }
    LaunchedEffect(config, card, conv) {
        while (true) {
            state = CharacterSchedule.computeState(resolved, LocalTime.now())
            delay(60_000L)
        }
    }
    return state
}