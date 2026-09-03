# EdenCourt「AI 对话效果」差距分析

> 状态：**草案（待用户审阅）**
> 背景：用户澄清「要的效果 = 别人视频里 AI 对话的效果（记忆深度、人格一致、情感表达、角色成长）」，而非 UI 视觉。
> 本文件基于对 [PromptBuilder.kt](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt)、[ChatPromptAssembler.kt](../../app/src/main/java/com/bdalxy/edencourt/feature/chat/ChatPromptAssembler.kt)、[MemoryRepository.kt](../../app/src/main/java/com/bdalxy/edencourt/data/repository/MemoryRepository.kt) 的通读，逐项对比成熟产品（Tavo / 星野 / SillyTavern 生态）。

---

## 一、先说结论

当前项目的「AI 对话能力」**骨架已经很全**，但**缺了几块决定"对话有没有灵魂"的关键能力**。别人视频里那种「角色记着你、会生气会撒娇、越聊越像真人」的效果，主要卡在下面 4 个差距上。这不是推倒重来，而是**在现有骨架上补能力**。

---

## 二、已具备的能力（有代码支撑，无需重做）

| 能力 | 实现位置 | 说明 |
|---|---|---|
| 短期记忆 | [PromptBuilder.kt](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt#L188) | 每轮注入最近 20 条历史 |
| 长期记忆注入 | [Builder L174](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt#L174) / [Assembler L170](../../app/src/main/java/com/bdalxy/edencourt/feature/chat/ChatPromptAssembler.kt#L170) | 按角色卡隔离：共享+私有，top-K 相关排序，还带「主动提起」引导 |
| 用户身份 | [Builder L118](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt#L118) | 名字/称呼/自述/性格/背景，全角色统一称呼用户 |
| 角色卡人设 | [Builder L37](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt#L37) | 性格/背景/说话风格/示例对话/约束 |
| 独有人格 | [Builder L73](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt#L73) | 6 维 + 反差对（依恋度阈值触发）+ 印象摘要 + 自补充引导 |
| 生活状态 | [Assembler L148](../../app/src/main/java/com/bdalxy/edencourt/feature/chat/ChatPromptAssembler.kt#L148) | 睡眠/晨起/活跃/晚间，影响语气 |
| 共同经历 | [Assembler L116](../../app/src/main/java/com/bdalxy/edencourt/feature/chat/ChatPromptAssembler.kt#L116) | 关系事件注入（最多 3 条） |
| 时间感知 | [Builder L156](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt#L156) | 注入当前现实时间 |

---

## 三、核心差距（按"影响对话灵魂的程度"排序）

### 差距 1：好感度/关系「不反哺对话」—— 最该补
**现状**：项目有 `affinityScore`（好感度）和关系标签（陌生/相识/友好/亲密），但**只用在「互动中心」分档展示**，**没有注入到聊天 prompt**。也就是说，你和角色聊了 100 轮、好感度 90 的「亲密」关系，和好感度 0 的「陌生」关系，AI 说话的口吻**几乎一样**（只有反差对按依恋度 65 这个静态值切换）。

**成熟产品怎么做的**：关系深浅直接决定 AI 的语气、亲昵度、是否会主动撒娇/依赖/吃醋。这是「越聊越近」的核心。

**补法（倾向）**：把 `relationStatus / affinityScore` 注入 prompt，按档位叠加「你对用户的亲密程度是 X，所以你应该……」。

### 差距 2：没有「实时情绪系统」
**现状**：只有「生活状态」（作息：睡/醒/活跃/晚间），这是**时段状态**，不是**情绪状态**。角色没有「此刻的心情」——不会因为用户说了什么而开心/生气/吃醋/难过，情绪缺乏起伏。

**成熟产品怎么做的**：角色有运行时的情绪状态机，情绪会**影响回复语气**，且情绪会**随对话累积、衰减、被特定事件触发**（吃醋、撒娇、别扭）。

**补法（倾向）**：引入轻量情绪维度（如当下心情 + 对用户的情绪）+ 从对话中提取情绪事件，注入 prompt 影响口吻。

### 差距 3：没有「文风/预设」控制
**现状**：说话风格是角色卡上的**静态属性**，用户**无法实时调**「更热情一点 / 更冷淡一点 / 少抢话 / 用第三人称」等。

**成熟产品怎么做的**：有「预设系统(Preset)」精细化控制 POV、文风、防抢话、破折号习惯等。这是"对话质感"的微调控件。

**补法（倾向）**：先做「角色卡级文风强度」或「会话级文风开关」，控制对话质感，不追求 Tavo 那样完整 Preset。

### 差距 4：没有「世界书 / 动态世界观」
**现状**：角色背景 `background` 是**一段静态文本**塞进 prompt。较长的世界观背景会一直占用 token，且**无法按"当前话题"精准注入**相关设定。

**成熟产品怎么做的**：Lorebook/世界书：把世界观拆成**带关键词的条目**，聊到相关话题才动态注入对应条目，既省 token 又精准。

**补法（倾向）**：把角色「背景/世界观」做成「关键词 → 条目」结构，按当前消息动态注入。这是提升"沉浸感"和"角色深度的关键一步"。

---

## 四、其他次要差距（可后议）

| 差距 | 现状 | 说明 |
|---|---|---|
| 开场白 `firstMessage` 未注入 | [CharacterCard.kt L25](../../app/src/main/java/com/bdalxy/edencourt/domain/CharacterCard.kt#L25) 有字段但 [PromptBuilder](../../app/src/main/java/com/bdalxy/edencourt/domain/PromptBuilder.kt) 未引用 | 需确认新会话是否用它主动问候（疑似缺口，待查） |
| 情感记忆提取 | 记忆提取偏「事实」，无「情感事件」专属类型 | 难以还原"角色因你而难过"这类情绪记忆 |
| 群聊 | 有互动系统，但非真群聊 | 后议 |

---

## 五、建议优先级（倾向，待你确认）

| 优先级 | 补的能力 | 理由 |
|---|---|---|
| P0 | 差距 1（好感度反哺对话） | 数据已有，只差"注入 prompt"一步，**投入小、见效大**，直接让「关系递进」可见 |
| P0 | 差距 2（实时情绪系统） | 决定"角色活不活"，是别人视频效果的核心差异 |
| P1 | 差距 3（文风控制） | 提升对话质感，投入中等 |
| P1 | 差距 4（世界书/动态世界观） | 提升沉浸感与角色深度，需构建条目结构 |

> 以上均为**倾向**。你拍板「先补哪几个、什么顺序」，我再拆任务点进 PLAN。

---

## 六、附：你已拍板的决策（同步固化到方案文档）

- 设置页 = 单栏折叠
- 我的 Tab 暂按原型
- 角色卡页参考 Tavo（见下）
- 关系图谱规格（见方案文档新增 §6.4）