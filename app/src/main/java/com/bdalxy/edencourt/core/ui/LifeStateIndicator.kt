package com.bdalxy.edencourt.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bdalxy.edencourt.data.schedule.LifeState

/** 生活状态 → 配色映射（T11.10 深化：简化 4 态，会话列表/聊天页状态小圆点统一配色） */
fun LifeState.color(): Color = when (this) {
    LifeState.SLEEPING -> Color(0xFF90A4AE) // 蓝灰：睡眠中
    LifeState.WAKING -> Color(0xFFFFB74D)   // 橙：晨起
    LifeState.ACTIVE -> Color(0xFF64B5F6)   // 蓝：活跃中
    LifeState.EVENING -> Color(0xFFBA68C8)  // 紫：晚间放松
}

/** 状态小圆点 + 状态文案（T11.10：「此刻状态」会话列表/聊天页通用展示） */
@Composable
fun LifeStateIndicator(state: LifeState, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(state.color()),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = state.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
