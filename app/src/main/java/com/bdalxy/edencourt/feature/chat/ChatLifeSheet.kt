package com.bdalxy.edencourt.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.schedule.CharacterSchedule
import com.bdalxy.edencourt.data.schedule.ScheduleTrait
import com.bdalxy.edencourt.data.schedule.scheduleTrait
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import java.util.Locale

/**
 * 聊天页顶部作息调节面板（T11.10 深化：每个会话角色单独调整）。
 * - 会话级开关：控制本会话是否显示角色「此刻状态」（第二级开关，需全局开关开启）
 * - 起床/入睡两个滑杆：单独调节本会话角色的作息，保存后覆盖角色卡/基础作息
 * - 「恢复默认」：清除会话级自定义，回到跟随默认（基础作息 + 个性偏移）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LifeScheduleSheet(
    roleName: String,
    globalEnabled: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    scheduleConfig: ScheduleConfig,
    card: CharacterCardEntity?,
    conv: Conversation?,
    onScheduleChange: (ScheduleConfig?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 跟随默认时的展示作息 = 基础作息 + 角色个性偏移（用于滑杆初始值 / 恢复默认）
    val baseCfg = CharacterSchedule.applyTrait(scheduleConfig, card?.scheduleTrait() ?: ScheduleTrait.NORMAL)
    // 会话级自定义作息（null=未单独设置）
    val convCfg = conv?.customSchedule
    var wakeUp by remember { mutableIntStateOf(convCfg?.wakeUpMinute ?: baseCfg.wakeUpMinute) }
    var sleep by remember { mutableIntStateOf(convCfg?.sleepMinute ?: baseCfg.sleepMinute) }
    val isCustom = convCfg != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "${roleName}的作息设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 会话级开关（第二级开关）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("开启本会话作息", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (globalEnabled) "开启后顶部显示「此刻状态」" else "需先在「设置 → 角色作息」开启全局开关",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (globalEnabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = globalEnabled,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            // 起床/入睡 2 滑杆（入睡支持跨天，最晚次日 01:59）
            TimeSlider(
                label = "起床",
                minute = wakeUp,
                range = 4 * 60..10 * 60,
                onValueChange = { wakeUp = it },
            )
            TimeSlider(
                label = "入睡",
                minute = sleep,
                range = 20 * 60..ScheduleConfig.MAX_SLEEP_MINUTE,
                onValueChange = { sleep = it },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 已单独设置过才显示「恢复默认」（清除自定义，跟随角色卡/默认）
                if (isCustom) {
                    TextButton(onClick = {
                        wakeUp = baseCfg.wakeUpMinute
                        sleep = baseCfg.sleepMinute
                    }) {
                        Text("恢复默认")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = {
                        // 与跟随默认一致则视为未自定义（清除会话级作息）
                        val newCfg = if (wakeUp == baseCfg.wakeUpMinute && sleep == baseCfg.sleepMinute) {
                            null
                        } else {
                            ScheduleConfig(wakeUp, sleep)
                        }
                        onScheduleChange(newCfg)
                        onDismiss()
                    },
                ) {
                    Text("保存")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * 作息时间滑杆（T11.10 深化，风格对齐设置页/角色卡 TimeSlider）：
 * 显示 HH:mm，拖动即时回调更新作息分钟数，5 分钟一档。
 */
@Composable
private fun TimeSlider(
    label: String,
    minute: Int,
    range: IntRange, // 分钟范围（起点为整点，如 4*60..10*60）
    onValueChange: (Int) -> Unit,
) {
    // T11.10 优化：从整点起每 5 分钟一格。用「格索引」作为滑块值（离散、无 steps 细分），
    // 拖动顺滑且每次变化都精确落在整点+5分钟倍数的格点上，不再大量 steps 导致卡顿。
    val start = range.first
    val slotMin = 5
    val maxSlot = (range.last - range.first) / slotMin
    // 当前分钟值吸附到最近的 5 分钟格索引（用于滑块定位）
    val currentSlot = ((minute - start).coerceIn(0, range.last - range.first) + slotMin / 2) / slotMin

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label：${formatMinute(minute)}")
        Slider(
            value = currentSlot.toFloat(),
            onValueChange = { onValueChange(start + it.toInt() * slotMin) },
            valueRange = 0f..maxSlot.toFloat(),
            steps = 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 分钟数 → HH:mm（T11.10 作息滑杆标签显示；>=24 点表示为次日，如 25:00→次日 01:00） */
private fun formatMinute(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    return if (h >= 24) {
        "次日 %02d:%02d".format(h - 24, m)
    } else {
        String.format(Locale.US, "%02d:%02d", h, m)
    }
}