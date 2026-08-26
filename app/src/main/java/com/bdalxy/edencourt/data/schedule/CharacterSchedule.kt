package com.bdalxy.edencourt.data.schedule

import com.bdalxy.edencourt.data.local.entity.CharacterCardEntity
import com.bdalxy.edencourt.data.local.entity.Conversation
import com.bdalxy.edencourt.data.setting.ScheduleConfig
import com.bdalxy.edencourt.domain.CharacterCardParser
import java.time.LocalTime

/**
 * 角色生活状态机（方向 B' T11.10 深化：全简化版）。
 * - 生活状态：睡眠中/晨起/活跃中/晚间放松（只依赖起床+入睡两个时刻，其余按固定规则划分）
 * - 按真实时间 + 作息配置（ScheduleConfig）计算「此刻状态」，替换 T6.8 的「在线/睡觉中」二分标签
 * - 纯函数无状态，UI 层直接调用；调用方每分钟刷新即可
 */
enum class LifeState(
    /** 状态中文标签（会话列表/聊天页展示） */
    val label: String,
) {
    /** 睡眠中（入睡 ~ 次日起床） */
    SLEEPING("睡眠中"),
    /** 晨起（起床后一小时） */
    WAKING("晨起"),
    /** 白天活跃（起床后 1 小时 ~ 21:00） */
    ACTIVE("活跃中"),
    /** 晚间放松（21:00 ~ 入睡） */
    EVENING("晚间放松"),
}

/**
 * 角色作息特征（T11.10 深化：基础作息 + 角色个性作息）。
 * 由角色卡的设定文字（性格/背景/标签）经本地关键词规则派生，MVP 支持晨型/夜型两档偏移，
 * 标准 = 跟随全局基础作息。后续可按需求升级为 AI 派生（角色卡创建时生成作息偏好）。
 */
enum class ScheduleTrait(
    /** 特征中文标签（设置页展示） */
    val label: String,
) {
    /** 标准：跟随全局基础作息 */
    NORMAL("标准作息"),
    /** 晨型：整体提前 1 小时（早睡早起） */
    MORNING("晨型"),
    /** 夜型：整体延后 2 小时（晚睡晚起） */
    NIGHT("夜型"),
    ;

    companion object {
        /** 夜型强特征词（晚睡晚起/作息偏夜，命中即判夜型） */
        private val NIGHT_KEYWORDS = listOf(
            "夜猫", "熬夜", "深夜", "夜间", "夜晚", "失眠", "酒吧", "夜店", "夜场",
            "网吧", "网咖", "主播", "直播", "程序员", "程序媛", "加班", "黑客",
            "骇客", "电竞", "修仙", "夜班", "夜神", "赶工", "写代码", "夜市",
        )

        /** 晨型强特征词（早睡早起/作息偏晨） */
        private val MORNING_KEYWORDS = listOf(
            "学生", "上学", "晨跑", "晨练", "早起", "早睡", "图书馆", "健身",
            "晨光", "清晨", "老师", "教师", "医生", "护士", "白班", "公务员",
            "朝九晚五", "书包",
        )

        /**
         * 关键词规则派生（本地、确定、不耗 API）：夜型优先判定。
         * @param text 角色卡设定文字（性格 + 背景 + 标签拼接）
         */
        fun detect(text: String): ScheduleTrait {
            val t = text.lowercase()
            if (NIGHT_KEYWORDS.any { t.contains(it) }) return NIGHT
            if (MORNING_KEYWORDS.any { t.contains(it) }) return MORNING
            return NORMAL
        }
    }
}

/**
 * 角色生活状态机计算逻辑（T11.10 深化：全简化版）。
 * 以「起床时刻」为一天起点归一化到 0~1439 坐标系，天然支持跨天入睡（次日凌晨），
 * 避免「23:59 封顶」导致的夜型入睡表达错误。
 */
object CharacterSchedule {

    /**
     * 按真实时间与作息配置计算「此刻状态」。
     * 固定规则划分：入睡点~起床=睡眠 → 起床后 1 小时=晨起 → 21:00 前=活跃中 → 入睡前=晚间放松。
     * @param cfg 作息配置（起床+入睡）
     * @param now 当前时间
     */
    fun computeState(cfg: ScheduleConfig, now: LocalTime): LifeState {
        val minute = now.hour * 60 + now.minute
        val dayStart = cfg.wakeUpMinute
        // 归一化：以起床为 0 点，映射到 0~1439（跨天时刻也正确归位）
        fun rel(m: Int) = (m - dayStart + ScheduleConfig.MINUTE_DAY) % ScheduleConfig.MINUTE_DAY
        val nowRel = rel(minute)
        val sleepRel = rel(cfg.sleepMinute)
        // 固定分界：21:00 在「以起床为 0 点」坐标系中的位置（活跃→晚间）
        val eveningRel = rel(21 * 60)
        return when {
            // 到达入睡点 ~ 次日起床 → 睡眠中（跨天窗口）
            nowRel >= sleepRel -> LifeState.SLEEPING
            // 起床后 1 小时内 → 晨起
            nowRel < 60 -> LifeState.WAKING
            // 起床后 1 小时 ~ 21:00 → 白天活跃
            nowRel < eveningRel -> LifeState.ACTIVE
            // 21:00 ~ 入睡前 → 晚间放松
            else -> LifeState.EVENING
        }
    }

    /**
     * 对基础作息应用角色作息特征（T11.10 深化：个性作息派生）。
     * 晨型整体提前 1 小时，夜型整体延后 2 小时；标准返回原配置。
     */
    fun applyTrait(config: ScheduleConfig, trait: ScheduleTrait): ScheduleConfig = when (trait) {
        ScheduleTrait.NORMAL -> config
        ScheduleTrait.MORNING -> config.offsetBy(-60)
        ScheduleTrait.NIGHT -> config.offsetBy(120)
    }

    /**
     * 解析角色最终生效的作息（T11.10 深化：两级自定义）。
     * 优先级：会话自定义 > 角色卡自定义 > 个性派生（晨型/夜型） > 全局基础作息。
     * 所有 UI（会话列表/聊天页/设置概览）统一走此函数，保证各入口一致。
     * @param base 全局基础作息（设置页可调）
     * @param card 角色卡；null 表示未绑定角色（默认助手），不参与自定义/派生
     * @param conv 会话（可为 null）；会话级自定义作息优先级最高，覆盖角色卡级
     */
    fun resolveConfig(base: ScheduleConfig, card: CharacterCardEntity?, conv: Conversation? = null): ScheduleConfig {
        var cfg = base
        // 角色卡级：自定义优先，否则个性偏移
        card?.let { role ->
            val custom = role.customSchedule
            cfg = if (custom != null) custom else applyTrait(base, role.scheduleTrait())
        }
        // 会话级：会话自定义作息覆盖角色卡级（同一角色卡多会话个体各自独立）
        conv?.let {
            val convCustom = it.customSchedule
            if (convCustom != null) cfg = convCustom
        }
        return cfg
    }
}

/**
 * 解析角色卡的作息特征（T11.10 深化：个性作息派生）。
 * 复用角色卡解析（jsonContent → CharacterCard），取性格/背景/标签文字走本地关键词规则。
 * 解析失败（卡内容异常）时按标准作息兜底，不影响状态展示。
 */
fun CharacterCardEntity.scheduleTrait(): ScheduleTrait {
    val card = runCatching { CharacterCardParser.parse(jsonContent) }.getOrNull()
    val text = buildString {
        card?.let {
            append(it.personality).append(' ')
            append(it.background).append(' ')
            append(it.tags.joinToString(" "))
        }
    }
    return ScheduleTrait.detect(text)
}

