# EdenCourt · v1.0 组件规格蓝图

> 用途：统一定义 **v1.0** 阶段四大核心组件（角色卡 / 性格特质 / 记忆系统 / 声音特征）的具体属性、实施指南、集成要求与边界。
> 本文件是**组件规格**，凡与实现不一致处以代码为准并标注；声音特征在 v1.0 为 **预留规格（未实现）**，明确标注。
> 可信度：**已实现**（v1.0 可用）· **预留**（定义契约与字段，未排期实现）· **规划**（v2.x，记录约束）。
> 版本基线：v1.0（versionName=1.0.0，Room schema v8）。

---

## 0. 文档目的与约定

- **目的**：保证四个组件之间「数据契约 + 集成点」一致，避免各功能各自为政，为 v1.1（记忆可见性）与 v2.x（语音/多模态/设备控制）提供可追溯的底座。
- **命名与注释**：字段命名遵循 camelCase / SCREAMING_SNAKE_CASE；**全部代码带中文注释**。
- **本文件不替代 PLAN.md**：执行看板仍是 [PLAN.md](file:///f:/Trae%20AI/EdenCourt-v1/PLAN.md)，本文档用于规格对齐与评审。
- **诚实原则**：声音特征未实现，本文件只给出**规格**，不得在 UI/代码中宣称已有语音能力。

---

## 1. 角色卡 Character Card

### 1.1 属性定义（已实现）

数据契约见 [CharacterCard.kt](file:///f:/Trae%20AI/EdenCourt-v1/app/src/main/java/com/bdalxy/edencourt/domain/CharacterCard.kt)，存储实体见 [CharacterCardEntity.kt](file:///f:/Trae%20AI/EdenCourt-v1/app/src/main/java/com/bdalxy/edencourt/data/local/entity/CharacterCardEntity.kt)。

| 字段 | 类型 | 必填 | 用途 | 状态 |
|---|---|---|---|---|
| `name` | String | ✅ | 角色名；显示优先「备注>名称」 | 已实现 |
| `personality` | String | ✅ | 性格（注入 system prompt 核心） | 已实现 |
| `description`（实体层） | String | — | 列表摘要（=personality 前 40 字种子） | 已实现 |
| `background` | String | 建议 | 角色背景；Tavern `description`→映射 | 已实现 |
| `speakingStyle` | String | 建议 | 说话风格（§2） | 已实现 |
| `exampleDialogue` | list | 可选 | 示例对话（强化模仿） | 已实现 |
| `firstMessage` | String | 可选 | 开场白；Tavern `first_mes`→映射 | 已实现 |
| `constraints` | String | 可选 | 审核硬约束 | 已实现 |
| `tags` | list | 可选 | 分类标签 | 已实现 |
| `avatar`（实体层） | String? | — | 本地头像路径，null=默认首字头像 | 已实现 |
| `remark`（实体层） | String? | — | 备注名，null=用 name | 已实现 |
| `isBuiltIn`（实体层） | Boolean | — | 内置卡不可删 | 已实现 |

### 1.2 实施指南

1. **校验**：`isValid()` → `name` 与 `personality` 均非空，否则拒绝导入/保存。
2. **序列化**：`kotlinx.serialization`，`Json { ignoreUnknownKeys = true }` 保证向前兼容。
3. **持久化**：Room 实体层表 `character_cards`，`isBuiltIn=true` 为内置（启动 `BuiltInCards.seed()` 幂等播种）。
4. **导入映射**：Tavern CCv3 → `description→background / first_mes→firstMessage / mes_example→exampleDialogue`。
5. **头像**：无自定义则用「主题色圆形 + 首字」；有则图片，全局（列表/气泡/选卡弹窗）复用 `CharacterAvatar`。

### 1.3 集成要求

- 新建会话选卡弹窗：复用 `CharacterAvatar` 展示自定义头像（T6.12）。
- PromptBuilder：`name / personality / speakingStyle / exampleDialogue / constraints` → system prompt。
- 显示层：任何显示角色名处以「备注 > name」优先（工程约定）。
- 图标/气泡头像必须与顶部头像一致（T6.6）。

### 1.4 v1.0 范围·优先级·约束

- 范围：内置 3 张 + JSON 导入 + 自建 + 头像 + 备注。
- 优先级：导入可用 > 内置卡 > 自建 > 头像 > 备注。
- 约束：内置卡不可删除；自建/导入卡可删。

---

## 2. 性格特质 Personality Traits

### 2.1 属性（已实现，承载于角色卡）

性格**不是独立表**，而是由角色卡的一组字段共同表达，职责划分：

| 字段 | 表达的性格维度 | 注入方式 |
|---|---|---|
| `personality` | 核心性格（温柔/毒舌/空灵…） | system 首段「你是{name}，性格…」 |
| `speakingStyle` | 口吻/句式（爱用~、反问句、比喻…） | system「说话风格…」 |
| `exampleDialogue` | 行为样例（遇到场景怎么回） | 少量示例注入强化模仿 |
| `constraints` | 边界（毒舌但不攻击、不暴力…） | system「约束…」 |

### 2.2 实施指南

- **约束规则**：`constraints` 永远最后注入（作为不可违反的底线）；`exampleDialogue` 只取最近 1~2 对避免 token 膨胀。
- **风格一致性**：`speakingStyle` 与 `exampleDialogue` 需语义一致（风格与样例冲突时优先 `constraints`）。
- **内置三范例**：小夏（温柔学姐）/ 阿杰（毒舌程序员）/ 羽（空灵天使），已验证「同一句话三角色回答风格差异鲜明」。

### 2.3 集成要求

- system prompt 组装顺序固定为：`角色身份→性格→背景→说话风格→示例→当前时间→记忆→约束`（order 由 PromptBuilder 保证）。
- 记忆注入不得覆盖性格字段（记忆是「用户侧」，性格是「角色侧」，显式分区）。

### 2.4 v1.0 范围·约束

- 范围：仅四种文本维度；**无**语音/情绪/表情等表达载体（见 §4）。
- 约束：`constraints` 为硬边界，不得被示例/对话压低；生成角色需至少提供 `name + personality`。

---

## 3. 记忆系统 Memory System

### 3.1 属性（已实现，契约见 [MemoryItem.kt](file:///f:/Trae%20AI/EdenCourt-v1/app/src/main/java/com/bdalxy/edencourt/data/local/entity/MemoryItem.kt)）

| 字段 | 类型 | 说明 | 状态 |
|---|---|---|---|
| `id` | Long | 主键自增 | 已实现 |
| `type` | enum | PERSONAL_INFO / PREFERENCE / EVENT / OTHER | 已实现 |
| `content` | String | 记忆一句话 | 已实现 |
| `keywords` | String | 逗号分隔，检索用 | 已实现 |
| `confidence` | Float | 0~1，≥阈值才入库 | 已实现 |
| `characterCardId` | Long? | 归属角色（T6.13 隔离） | 已实现 |
| `sourceConversationId/MessageId` | Long? | 来源溯源 | 已实现 |
| `createdAt/updatedAt` | Long | 时间戳 | 已实现 |
| `isDeleted` | Boolean | 软删除（管理页可回溯） | 已实现 |
| `visibility` | enum | SHARED/PRIVATE（**v1.1 新增**，本文档预留字段） | 预留 |

### 3.2 实施指南

1. **短期记忆**：当前会话最近 N=20 条注入（注入位置：system 之后、用户消息之前）。
2. **长期记忆提取**：每轮回复后异步调 API 提取结构化条目；高置信度才入库，不阻塞对话。
3. **检索注入**：生成前按关键词匹配 top-K=3，拼成「[关于用户的长期记忆]…」。
4. **隔离规则（v1.0）**：记忆按 `characterCardId` 隔离——同卡全会话共享、异卡隔离。
5. **更正覆盖**：提取时携带该角色已有记忆清单，命中更正意图则用 `replace_content` 覆盖旧条，避免新旧并存（依赖提取模型，非 100%）。

### 3.3 集成要求

- 检索入参必须同时满足「角色归属」；v1.1 后追加「可见性」维度（§1 与 §4 预留）。
- Room 迁移规范：新增列必须 `ALTER TABLE ADD COLUMN` + `CREATE INDEX` 同步（血泪教训：缺索引导致崩溃）。
- UI：记忆页显示归属角色 + 支持按角色 / 手动未归属筛选；手动添加默认未归属。

### 3.4 v1.0 范围·优先级·约束

- 范围：提取 / 检索注入 / 管理页 / 更正覆盖 / 按角色隔离。
- 优先级：跨会话记住 > 隔离 > 更正 > 管理页 > 可视化（可视化属 2.4）。
- 约束：`content` 非空；`confidence` 默认 1.0；软删除保证回溯；脏数据须手动清理。

---

## 4. 声音特征 Voice Characteristics —— **v1.0 预留规格（未实现）**

> ⚠️ **诚实声明**：截至 v1.0 代码库 **未包含** 任何语音/音频实现（已检索确认无 TTS/录音/播放相关代码）。本节仅定义**未来数据契约与集成点**，供 v2.3（语音输入/TTS 语音回复）实施时引用；**不得**在 v1.0 UI/文案中宣称有语音能力。

### 4.1 预留属性（未来契约）

作为角色卡的可选扩展（建议建模为角色卡附件或独立 `voice_config` 表，二选一待定）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `voiceEngine` | enum | 云端 TTS / 本地 TTS（v2.3 定） |
| `voiceSpeaker` | String? | 音色标识（如嘴型/音源代号） |
| `pitch` / `rate` | Float | 音高 / 语速（每人有自己节奏，呼应 §2 性格） |
| `sampleVoice` | String? | 一句话试听文本 |
| `language` | String? | 主语言（默认跟随 locale） |

### 4.2 预留集成点

- **入口**：角色详情页「声音设置」区（v2.3 再启用）。
- **播放反馈**：用户已反馈需求「下载要有可见反馈（横幅/进度弹窗）」，实现时必须有明显 UI 反馈（呼应 ROADMAP 6.2）。
- **与性格对齐**：pitch/rate 应能跟随 `speakingStyle`（温柔→慢/轻、毒舌→快/硬），保证「人说人话」的一致表达。

### 4.3 v1.0 范围限制

- **范围**：不在 v1.0 实现音频任何功能；仅在本文件锁定字段契约，避免 v2.3 返工。
- **优先级**：低（ROADMAP 2.3 候选）。
- **技术约束**：Android TTS engine / 本地模型容量 / 权限（录音）均待 v2.3 单独评估。

---

## 5. 优先级总表（v1.0）

| 组件 | P0（必达） | P1（应达） | P2（可选） |
|---|---|---|---|
| 角色卡 | name/personality 校验、导入、内置 3 张 | 自建、头像 | 备注、更多 Tavern 字段（scenario/character_book） |
| 性格 | 四种维度注入 system | 示例对话强化 | 与语音节奏联动（v2.3） |
| 记忆 | 提取/检索注入/跨会话记住 | 按角色隔离、更正覆盖、管理页 | 可视化（2.4） |
| 声音 | ——（预留契约） | —— | v2.3 落地 TTS/语音 |

---

## 6. 技术约束总表

| 约束 | 说明 | 影响组件 |
|---|---|---|
| 纯 Kotlin 全栈 | 不引 Python/文件脚本 | 全部 |
| 中文注释 | 关键逻辑必须注释 | 全部 |
| minSdk 29 / compileSdk 36 | 覆盖 Android 10+ | 全部 |
| 语音未实现 | v1.0 不得宣称语音能力 | 声音特征 |
| 记忆隔离维度 | 检索必须带角色归属；v1.1 加可见性 | 记忆 |
| Room 迁移同步索引 | 新列须 ADD COLUMN + CREATE INDEX | 记忆/角色 |
| 状态收口 | 生成/打断/连发集中管理（防卡死回归） | 记忆/聊天 |
| 显示名优先级 | 备注 > 名称 | 角色卡 |

---

## 7. 变更建议（非 v1.0 强制）

- **v1.1（已确认方向）**：`visibility` 可见性字段 + 检索双层过滤——本文件 §3.1 已将字段预留给 v1.1。
- **范围外候选（需用户拍板）**：`scenario` 与 `character_book`（lorebook）映射、语音落地、多角色同场。凡开发者入手前须回到 PLAN.md 做任务点。

> 本文件基于 v1.0 真实代码编制；任何「预留/规划」不得用于宣称已实现。