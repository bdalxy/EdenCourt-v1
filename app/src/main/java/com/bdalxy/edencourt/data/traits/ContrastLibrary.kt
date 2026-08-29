package com.bdalxy.edencourt.data.traits

import java.util.Random

/**
 * 反差对生成库（T13.3 角色差异化/自补充方案，§3.2 / §9-A 定案）。
 * 机制：卡派生「表面特质」+ 从「受限反差库」选 1 个「隐藏特质」（与卡设定做关键词冲突检测，冲突则重选，保证不自相矛盾）。
 * 生成策略刻意「不用全随机、不依赖 LLM 自由生成」（§9-A 结论：可控性最好），隐藏特质从内置反差库候选里挑。
 */
object ContrastLibrary {

    /** 反差对：表面特质 + 隐藏特质 + 触发情境描述（随 conversation_traits 存储） */
    data class ContrastPair(
        /** 表面特质（平时给人的感觉） */
        val surface: String,
        /** 隐藏特质（特定情境下流露的另一面） */
        val inner: String,
        /** 触发情境描述（如「被误解或低估时」「熟络之后」） */
        val trigger: String,
    )

    /** 隐藏特质候选：inner(隐藏)+trigger(触发情境)+conflictKeywords(冲突关键词，命中则此候选作废) */
    data class InnerCandidate(
        val inner: String,
        val trigger: String,
        val conflictKeywords: List<String>,
    )

    /** 内置反差库：隐藏特质候选池（冲突检测时逐条比对） */
    private val INNER_POOL: List<InnerCandidate> = listOf(
        InnerCandidate("其实很要强，不容置疑", "被质疑或被轻视的时候", listOf("随和", "软弱", "好说话")),
        InnerCandidate("有倔强的一面，认准的事不回头", "被推着改变主意的时候", listOf("随波逐流")),
        InnerCandidate("偶尔会流露出依赖感", "深夜独处或感到疲惫的时候", listOf("独立", "独行侠")),
        InnerCandidate("带点腹黑的小心思", "占了上风而对方浑然不觉的时候", listOf("憨厚", "纯真")) ,
        InnerCandidate("占有欲很强，对在乎的人会有一点小心眼", "自己重视的人被抢走关注的时候", listOf("洒脱", "豁达")),
        InnerCandidate("骨子里有点傲娇，嘴上不服软", "内心其实很高兴却不肯承认的时候", listOf("温和")),
        InnerCandidate("比看起来更粘人，会悄悄想念", "分别之后或感到不安全的时候", listOf("疏离", "冷淡")),
        InnerCandidate("其实很容易着急上火", "计划被打乱或事不遂愿的时候", listOf("佛系", "随性", "淡然")),
        InnerCandidate("有追求完美、不肯将就的执念", "结果达不到自己标准的时候", listOf("随性", "将就")),
        InnerCandidate("背地里比谁都爱较真、爱钻牛角尖", "争论一旦开始收不住的时候", listOf("圆滑", "好说话")),
    )

    /** 表面特质候选人（卡 personality/description 关键词命中 → 表面特质）；无命中用兜底 */
    private val SURFACE_KEYWORD_MAP: List<Pair<List<String>, String>> = listOf(
        listOf("温柔", "体贴", "耐心") to "温柔体贴",
        listOf("高冷", "冷淡", "冷漠", "面无表情") to "疏离冷淡",
        listOf("活泼", "开朗", "元气", "爱笑") to "活泼开朗",
        listOf("毒舌", "吐槽", "嘴毒") to "毒舌犀利",
        listOf("安静", "内向", "寡言", "沉默") to "安静内敛",
        listOf("温柔", "学姐") to "温柔学姐",
        listOf("认真", "严谨", "自律") to "认真严谨",
        listOf("腹黑", "狡黠", "狡诈") to "腹黑狡黠",
    )

    /** 无表面关键词命中时的兜底表面特质 */
    private const val DEFAULT_SURFACE = "沉稳内敛"

    /**
     * 生成一组反差对。
     * @param personality 卡性格文本（派生表面特质 + 冲突检测依据）
     * @param background  卡背景文本（一并参与冲突检测）
     * @param random      随机源（可注入，便于测试可复现；生产用 SecureRandom）
     * @return 反差对（含表面/隐藏/触发情境）
     */
    fun generate(
        personality: String,
        background: String,
        random: Random = Random(),
    ): ContrastPair {
        val surface = deriveSurface(personality)
        // 拼接冲突检测文本：卡里可用的全部设定
        val conflictText = "$personality $background"
        val candidate = pickInner(conflictText, random)
        return ContrastPair(surface = surface, inner = candidate.inner, trigger = candidate.trigger)
    }

    /** 从卡 personality 派生表面特质（首个命中关键词；无则兜底） */
    fun deriveSurface(personality: String): String =
        SURFACE_KEYWORD_MAP.firstOrNull { (keywords, _) -> keywords.any { personality.contains(it) } }?.second
            ?: DEFAULT_SURFACE

    /** 受限反差库选隐藏特质：随机排序后取「第一个与卡设定无冲突」的候选；全冲突则兜底取首个 */
    private fun pickInner(conflictText: String, random: Random): InnerCandidate {
        // java.util.Random 无 shuffled，用 sortedBy(nextDouble) 近似随机打乱
        val shuffled = INNER_POOL.sortedBy { random.nextDouble() }
        return shuffled.firstOrNull { c -> c.conflictKeywords.none { conflictText.contains(it) } }
            ?: INNER_POOL.first()
    }
}