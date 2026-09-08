## 9.8 阶段 7（v1.1）：记忆可见性底座（M7，用户 2026-08-24 确认方向，2026 推进）

> 目标：把长期记忆从「按角色隔离 + 一律共享」升级为「按角色隔离 + 共享/私有分层」，作为 v1.2 角色互动、v1.3 群聊的地基（ROADMAP.md M7）。v1.0 单角色对话体验不改变。
> 语义：`SHARED`=一般事实，将来可在角色间互动中被其他角色获知；`PRIVATE`=该角色专属（如用户私密心事），仅该角色自己可见。本阶段只做「数据层表达 + 提取标注 + 记忆页可视化」，跨角色信息不对等（角色互动产生）逻辑留 v1.2。

### T7.1 memory_items 加 visibility 列 + Room 迁移 v8→v9
- 依赖：无
- 操作：MemoryItem 增 `visibility`（枚举 SHARED/PRIVATE，默认 SHARED）；AppDatabase version→9，MIGRATION_8_9 加列 `visibility TEXT NOT NULL DEFAULT 'SHARED'`（区分度低，不建索引）
- 文件：`data/local/entity/MemoryItem.kt` · `data/local/AppDatabase.kt`
- 验收：[x] 既有记忆迁移后全部为 SHARED · [x] 升级 0 崩溃（K60 真机旧库覆盖升级 v8→v9，无 FATAL） · [x] 编译+安装+启动通过

### T7.2 提取时标注可见性 + 冲突写入可见性
- 依赖：T7.1
- 参考：企划书 3.5.2 提取（在 T5.3/T6.13.2 提取链路上扩展字段）
- 操作：①提取 prompt 增加可见性判定规则（私密=用户明确要求保密/仅对当前角色说的私密心事→PRIVATE；一般事实→SHARED），输出项加 `visibility` 字段；②`ExtractedMemory` DTO 加字段（默认 SHARED）；③`upsertWithConflictCheck` 新增/更新时写入 visibility
- 文件：`data/repository/MemoryRepository.kt` · `data/local/entity/MemoryItem.kt`
- 验收：[x] 给角色 A 说「这是秘密，别告诉别人」→ 该记忆标记为【私密】 · [x] 普通告知（如「喜欢奶茶」）→【共享】 · [x] 编译+安装+启动通过

### T7.3 记忆管理页收敛为「共享记忆池」（会话级私有改造，2026-08-24 定稿）
- 依赖：T7.2
- 参考：企划书 5.3.4 记忆页
- 操作：记忆管理页**只显示共享记忆**（`visibility=SHARED`），隐藏私有；保留角色筛选（全部/各角色/手动未归属）；移除「私有」可见性筛选维度（私有改从会话内进去看）
- 文件：`feature/memory/MemoryScreen.kt` · `MemoryViewModel.kt`
- 验收：[x] 记忆页只出现共享记忆（K60 实测全部条目标「共享」，无私有） · [x] 无「私有」筛选维度（筛选下拉仅「全部/光羽/阿杰/小夏/手动未归属」） · [x] 编译+安装+启动通过

### T7.4 会话内记忆面板（共享 + 私有）
- 依赖：T7.3
- 参考：企划书 5.3.4 记忆页延伸；产品哲学「一个会话≈一个独立人格实例」
- 操作：会话聊天页右上角加「记忆」图标 → 半屏面板分两区：**共享**（该角色 `characterCardId=本卡 AND visibility=SHARED`）、**私有**（本会话 `sourceConversationId=当前会话 AND visibility=PRIVATE`）；空态显示「暂无记忆」
- 文件：`feature/chat/ChatScreen.kt` · 新增面板 Composable + ViewModel 数据源
- 验收：[x] 会话内面板「共享」展示该角色共享记忆（K60 实测「共享记忆 · 小夏」2 条）· [x] 「私有」展示本会话私密记忆（不含他会话；空态「本会话暂无私密记忆」正常，代码层确认隔离）· [x] 图标入口明显（右上角「记忆」）· [x] 编译+安装+启动通过

### T7.5 检索注入对齐会话级私有
- 依赖：T7.4
- 操作：注入到 system prompt 的记忆改为「该角色共享 + 本会话私有」（原 ChatViewModel.kt:469 `getActiveByCard` 取同卡全部含私有，需要按会话过滤私有；共享仍按角色全取）
- 文件：`data/local/dao/MemoryDao.kt` · `data/repository/MemoryRepository.kt` · `feature/chat/ChatViewModel.kt`
- 验收：[x] 会话A可见该角色共享 + 会话A私有；会话B的私有不注入会话A（MemoryDao.getPrivateByConversation 以 sourceConversationId 会话级隔离，代码层确认）· [x] 编译+安装+启动通过

> **阶段 7（v1.1）人工审核门槛**：五项编译通过 + K60 真机逐项验证（升级不崩 / 记忆页只显共享 / 会话内共享+私有两区 / 私有跨会话隔离含检索注入 / 手动添加默认共享），交用户确认后再推送 GitHub。

---

## 9.9 阶段 8（v1.2）：跨会话打听（套话，M8，用户 2026-08-24 定稿方向）

> 目标：把跨会话信息获取从「静态共享」升级为「主动社交打听」——一个会话（≈一个真人）主动向另一个具体会话打探信息。
> 定稿后语义（用户 2026-08-24 批注修正）：**即使同角色卡，不同会话经历独立**，不以角色卡划分，精确到具体会话个体。
> 详细设计见 `docs/plan/PLAN-v1.2-跨会话打听.md`。本阶段含 P1（用户话语触发闭环）+ P2（每日定时概率自驱动）。

### T8.0 会话唯一身份底座（Room 加列存随机短码，迁移 v9→v10）
- 依赖：无
- 参考：`docs/plan/PLAN-v1.2-跨会话打听.md` §1「会话唯一身份」
- 操作：角色卡表加 `code`（随机短码，建档时生成）；会话表加 `code`（随机短码，建会话时生成）；Room 迁移 v9→v10 `ALTER TABLE ADD COLUMN`；既有记录补发短码；会话唯一身份 = `card.code + conv.code` 叠加（已知是所有旧实体标识，改造后标志 sourceConversationId/card 维度可精确到个体）
- 文件：`data/local/entity/CharacterCardEntity.kt` · `Conversation.kt` · `AppDatabase.kt`
- 验收：[ ] 角色卡/会话各带唯一短码code · [ ] 已有数据迁移后补发code（不丢） · [ ] 唯一身份可叠加计算 · [ ] 编译+v9→v10升级无崩溃
- 状态：[ ] 未开始

### T8.1 会话「对外记忆概况」索引 + 目标会话B定位检索
- 依赖：T8.0
- 参考：`PLAN-v1.2` §4（先按此实现，后续优化）
- 操作：为活跃会话聚合"对外记忆概况（对外/非绝密记忆关键词+近期主题）"，键=会话唯一身份；按打听主题/关键词检索可命中会话B候选并排序（有对外记忆命中>信息量大>近期活跃）；无候选→返回"没人知道"
- 文件：`data/repository/MemoryRepository.kt` · `data/local/dao/MemoryDao.kt` · 新增概况聚合逻辑
- 验收：[ ] 能按主题检索出可能知道的具体会话B · [ ] 排序偏好正确 · [ ] 无命中返回空候选 · [ ] 编译通过
- 状态：[ ] 未开始

### T8.2 旁路暗访执行器（B侧代问+收回答，私密由B自决）
- 依赖：T8.1、T8.0
- 参考：`PLAN-v1.2` §3 旁路暗访
- 操作：在目标会话B的上下文中以"来客代问"注入一次提问（不进B正文消息流，作为旁路任务），调LLM取得B作答；B在作答时按自决决定是否透露其私密；绝密内容硬性不透（§2兜底）；超时/失败按"没问到"处理
- 文件：`data/repository/` 新增跨会话执行器 / `feature/chat/ChatViewModel.kt` 复用AiChatService
- 验收：[ ] B作答经旁路取得且不进B正文 · [ ] B可自决透露私密 · [ ] 绝密内容永不外泄（断言） · [ ] 失败返回"没问到" · [ ] 编译通过
- 状态：[ ] 未开始

### T8.3 权限过滤 + 结果回传A + 旁路小字标记
- 依赖：T8.2
- 参考：`PLAN-v1.2` §6
- 操作：回传前权限过滤（A无权内容不进入A上下文）；把B的回应作为「情报/听闻」注入A的下一条回复参考；A会话内渲染一条小字「↑ 打听到：来自X」
- 文件：`feature/chat/ChatScreen.kt` · `ChatViewModel.kt`
- 验收：[ ] A上下文只含 A 有权内容 · [ ] A可在回复中自然引用情报 · [ ] 小字来源标记显示 · [ ] 编译通过
- 状态：[ ] 未开始

### T8.4 用户话语触发（意图识别+装配）
- 依赖：T8.3
- 参考：`PLAN-v1.2` §5 第一层
- 操作：A发送链路识别"去问/打听/问问"意图（关键词+LLM双保险）→ 解析打听主题与目标 → 装配T8.1~T8.3执行 → 结果回传A下一条回复；非触发话语走正常对话
- 文件：`feature/chat/ChatScreen.kt` · `ChatViewModel.kt` · `domain/` 意图识别
- 验收：[ ] "让AI去问某事"触发打听 · [ ] 普通对话不误触发 · [ ] 未命中目标如实说"没人知道" · [ ] 编译通过
- 状态：[ ] 未开始

> **P1 人工审核门槛（M8.1）**：编译通过 + AI 自动化测试（数据由 AI 生成，详见 `docs/plan/PLAN-v1.2-跨会话打听.md` §11）：A 让 AI 去问某会话 → 带回该会话对外记忆真实信息并自然引用；同卡/异卡均生效；B 决定不透的私密 + 绝密不外泄；没问到如实说"没人知道"；旁路不污染 B 正文。交用户确认后再推送。

### T8.5 每日定时窗口 + 概率触发 + 角色自驱动主题
- 依赖：T8.3、T8.4
- 参考：`PLAN-v1.2` §5 第二层
- 操作：每天可配置窗口（默认20:00–22:00）给当天活跃会话一次"可选择去打听"机会；是否发起由概率（默认~30%）决定；"好奇心主题"由该会话LLM基于上下文自主生成；执行旁路打听，结果沉淀为该会话当天"见闻"；角色自决是否告知用户；无Push不打断
- 文件：`data/` 调度器 + 配置 · `feature/chat/`
- 验收：[ ] 到达时段活跃会话有一定概率发起打听 · [ ] 主题由角色自驱动生成 · [ ] 结果沉淀可见 · [ ] 角色可自决是否告知用户 · [ ] 不打扰（无push/不注入正文） · [ ] 编译通过
- 状态：[ ] 未开始

### T8.6 打听结果沉淀为A新记忆（来源标记+可见性）+ 角色自决告知
- 依赖：T8.5
- 参考：`PLAN-v1.2` §6 沉淀
- 操作：把B打听到的稳定事实写为A会话一条新记忆，标记"来源=打听到，非亲历"，进入v1.1可见性体系（默认SHARED或按内容/角色自决）；与绝密兜底联动（打听到的内容即便沉淀也不含绝密）
- 文件：`data/repository/MemoryRepository.kt` · `MemoryDao.kt`
- 验收：[ ] 打听结果可沉淀为A新记忆且带来源标记 · [ ] 沉淀不引入 B 绝密内容 · [ ] 编译通过
- 状态：[ ] 未开始

> **P2 人工审核门槛（M8.2）**：约定时段活跃会话概率自发打听；结果可被看到；不打扰；角色可自决告知；无重复骚扰错乱。

---

## 9.10 阶段 8.5（v1.2b）：角色间互动 +「互动中心」（2026-08-25 定稿，并入 DeepSeek 专家评审 + 互动资格开关）

> 目标：把跨会话行为从「帮用户打听」**校正并升级**为「角色自发相互交往」——一个会话（≈独立真人）主动与另一会话进行**双向真实对话**；由用户授权哪些会话参与（**新建默认不参与**），互动记录在会话聊天页「互动」入口与「互动中心」回看。
> 详细设计见 `docs/plan/PLAN-v1.2-角色互动.md`。MVP=**手动触发**（先验证"角色聊天有没有看头"），定时自动并入 v1 后续候选。

### T8.7 互动资格开关（库）
- 依赖：阶段 7（会话/消息表）
- 参考：`docs/plan/PLAN-v1.2-角色互动.md` §2
- 操作：`conversation` 加 `interactionEnabled: Boolean` 默认 `false`（新建默认不参与）；Room 迁移（**与记忆系统升级错开版本号**）；全局总开关用 DataStore
- 文件：`data/local/entity/Conversation.kt` · 对应 DAO · `AppDatabase.kt`
- 验收：[ ] 新建会话默认 false · [ ] 未授权会话不进入互动候选 · [ ] 真机迁移无崩、旧数据无损 · [ ] 编译通过

### T8.8 关系底座 conv_relationship（标签 + 排序，不做数值）
- 依赖：T8.7
- 参考：`docs/plan/PLAN-v1.2-角色互动.md` §5
- 操作：关系表 `conv_relationship(a_id,b_id,relation_status[? stranger/acquaintance/friendly/close/rift], affinity_score 默认0不参与, interaction_count, last_interaction_at, note_summary?)`；同卡解释为「共同话题更容易破冰」标记 `same_card_bonus`；选 B 排序：同卡 > 最近互动 > 互动次数
- 文件：`data/local/entity/ConvRelationship.kt` · DAO · 迁移
- 验收：[ ] 表结构 · [ ] 初始 stranger · [ ] 选择优先级生效 · [ ] 编译通过

### T8.9 多轮互动执行器 InteractionExecutor
- 依赖：T8.8 + 阶段 8（复用 AiChatService / Memory / PromptBuilder）
- 参考：`docs/plan/PLAN-v1.2-角色互动.md` §4
- 操作：状态机 `INITIATING→A→B→A→B→CLOSING`；每轮仅注入当前说话者私密记忆(≤5条)+共享记忆+对对方认知+话题种子+最近 6 条历史；只输出当前一句、不替对方说话、不泄露秘密、60 字上限、无话可说可自然结束；**硬性 2~3 轮上限强制收敛**；temp≈0.7
- 修正（2026-08-25）：互动氛围**不由"第 N 次"机械判断**，改为**关系标签 relation_status + 角色性格**驱动；同卡额外告知"来自同一张角色卡"，首次见面可自然惊讶、后续按关系标签自然递进（陌生人→认识→熟络→亲密）；互动成功后关系标签按次数递进（数值好感度 affinity_score 留远期）
- 文件：`data/repository/InteractionExecutor.kt`
- 验收：[ ] 手动执行 A↔B 多轮真实对话 · [ ] 双方人设/记忆一致 · [ ] 隐私不外泄 · [ ] 上限收敛无死循环 · [ ] 氛围随关系标签变化、同卡后续不镜像 · [ ] 编译通过

### T8.10 互动数据模型与落位（三层表 + digest 摘要）
- 依赖：T8.9
- 参考：`docs/plan/PLAN-v1.2-角色互动.md` §6
- 操作：`interaction` / `interaction_participant` / `interaction_turn` 三表；`message` 插 `type=interaction_digest`（metadata 存 interaction_id/participant_id）到双方会话（A 视角"我去找 B 聊了…"/B 视角"A 来找我聊了…"），点击可跳详情；`read_at IS NULL`=未读→红点
- 文件：`data/local/entity/` 三实体 · DAO · 迁移
- 验收：[ ] 三表可用 · [ ] 双方会话各一条 digest · [ ] metadata/跳转 · [ ] 编译通过

### T8.11 互动中心 UI（聊天页入口）
- 依赖：T8.10
- 参考：`docs/plan/PLAN-v1.2-角色互动.md` §6
- 操作：ChatScreen top bar 新增「互动」图标（与「记忆」并列）→ 互动面板（本会话互动记录 + 开放互动开关 + 手动触发）→ 互动中心（预览列表[双方名/时间/一句话摘要] · 详情[气泡多轮] · 全局总开关）；红点
- 文件：`feature/chat/ChatScreen.kt` · `feature/interaction/`
- 验收：[ ] 入口/红点 · [ ] 面板开关生效 · [ ] 预览+详情可看 · [ ] 空态无红点 · [ ] 编译通过

### T8.12 手动触发入口
- 依赖：T8.9 + T8.11
- 参考：`docs/plan/PLAN-v1.2-角色互动.md` §3
- 操作：授权会话内「去找某会话聊聊」，选 B（须已授权）→ 执行互动；未授权被拦截
- 文件：`feature/interaction/` · `feature/chat/ChatScreen.kt`
- 验收：[ ] 选 A/B 触发 · [ ] 未授权被拦 · [ ] 编译通过

### T8.13 用户自设角色卡身份（唯一绑定，所有角色默认知道）（2026-08-25 新增需求，用户拍板插入）
- 依赖：阶段 7（角色卡/记忆）+ T8.9（互动注入）
- 参考：用户"给用户添加一个自设，让用户给自己安排一个角色卡身份，并且是唯一绑定的，所有角色都默认知道"
- 操作：新增单例「我的身份」（昵称/称呼/设定描述/性格/背景，**唯一一条**）；角色卡 persona 构建时**注入用户身份**（所有角色默认知道用户是谁、统一称呼）；互动 prompt 同步注入（角色间提到用户称呼一致）；设置页新增「我的身份」编辑入口
- 文件：`data/local/entity/UserIdentity.kt` · DAO · 迁移 · `domain/PromptBuilder.kt` · `feature/settings/` · `data/interaction/InteractionExecutor.kt`
- 验收：[x] 身份唯一一条 · [x] 角色 system 注入用户身份（称呼一致） · [x] 互动注入 · [x] 设置页可编辑 · [x] 真机迁移无崩 · [x] 编译通过（2026-08-25 真机端到端验证 PASS：设置页存「小赵/测友」，光羽会话实测按「测友」称呼；身份注入互动正常；Room v14→v15 真机升级无崩）

> **阶段 8.5 人工审核门槛（MVP）**：新建会话默认不参与互动；手动触发 A 找 B 出真实多轮对话且人设/隐私到位；互动中心红点→预览→详情闭环；双方会话各一条摘要可点击跳转；互动氛围随关系标签递进、同卡后续不镜像；用户身份全角色一致；真机无崩、Room 迁移无损。

---

## 9.11 阶段 9（v1.3 记忆管理页升级·阶段一地基）（2026-08-25 用户拍板「按1继续」）

> 依赖：阶段 7/8（记忆底座）。参考 `docs/plan/PLAN-记忆系统升级.md` 定稿。
> 目标：**先解决「大数据下管理页搜索慢 + 生成注入检索慢」**，并把多类型管理字段一步到位（避免二次迁移）。
> 范围：字段/类型/索引 + FTS 底座 + 统一检索服务 + 分类Tab + 简单分页。**关系图谱/时间轴/详情放阶段二、三**（本期不做 UI）。

### T9.1 字段补齐 + 类型扩 7 类 + Room v16 + 组合索引（阶段一地基）
- `MemoryItem` 增字段：`timeScope`(SHORT_TERM/LONG_TERM) · `expiresAt`(可空) · `isPinned` · `importance`(NORMAL/IMPORTANT) · `locked` · `sourceType`(CHAT/USER_EDIT/SYSTEM)
- `MemoryType` 扩为 7 类：个人信息/偏好/事件/技能/情景/关系/其他（新增 SKILL/SITUATION/RELATION）
- `MIGRATION_15_16`：`ALTER TABLE memory_items ADD COLUMN` 全部默认值（一次到位）+ 组合索引 `type+timeScope+characterCardId+visibility`
- 文件：`entity/MemoryItem.kt` · `AppDatabase.kt`
- 验收：编译通过 · 真机迁移 v15→v16 无损 · 旧 memory_items 行新字段为默认值

### T9.2 FTS4 虚拟表 `memory_items_fts`（contentEntity 自动同步 + 中文bigram）
- `@Fts4(contentEntity=MemoryItem::class)`：新增/改/删自动同步，杜绝残留旧索引
- 中文子串检索预处理：写入前 bigram；2 字短词/模糊词退 LIKE（先按类型/角色缩小候选）
- 文件：`dao/MemorySearchDao.kt` · 迁移里手工建 FTS 索引
- 验收：编译 · 管理页/注入检索能命中中文子串（如搜「青蓝」命中「调青蓝釉用钴料」）
- 进度（2026-08-25）：✅ 已落地。FTS4 虚拟表 + 触发器(AFTER INSERT/UPDATE/DELETE 同步)在 MIGRATION_16_17 建立；`MemorySearchDao` 因虚拟表非 Room 实体改为 `@RawQuery` 动态 SQL（宽容映射回 MemoryItem）。中文子串由 `MemorySearchService.searchPaged` 走 LIKE 兜底（先按角色/分类缩小候选）。编译通过 + 真机迁移（v15→v17）无崩。

### T9.3 MemorySearchService 统一检索服务
- 主入口：FTS MATCH 检索（按 type/characterCardId/visibility 缩候选），LIKE 兜底短词
- 注入路径 top-K 截断（默认 20），按 importance/pinned/updatedAt 排序——同时覆盖「管理页搜索」与「生成注入」
- 改造：`ChatViewModel` 注入（getSharedByCard 等）与 `MemoryRepository` 检索改走该服务
- 文件：`data/search/MemorySearchService.kt` · 改动注入调用点
- 验收：注入检索不拖慢生成 · top-K 生效
- 进度（2026-08-25）：✅ 已落地。新增 `MemorySearchService`（统一入口：`searchPaged`/`countFiltered` 走 LIKE 兜底、`searchFts` 走 FTS 精确、`rankByRelevance` 注入排序 top-K）。`MemoryRepository.searchPaged/countFiltered` 委托该服务，`getInjectableMemories` 注入改走 `rankByRelevance`（置顶>重要>更新时间）。编译通过。

### T9.4 记忆管理页：分类 Tab（7 类+数量）+ 新字段展示 + 搜索走 FTS
- `MemoryScreen` 顶部「分类 Tab」带数量；列表项信息密度升级（分类/可见性/置顶/重要/锁定/归属/相对时间）
- `MemoryViewModel` 改用 `MemorySearchService` 做实时搜索
- 文件：`feature/memory/MemoryScreen.kt` · `MemoryViewModel.kt`
- 验收：分类筛选准确 · 搜索即输即查 · 新字段可见

### T9.5 列表简单分页（LIMIT/OFFSET 滚动加载，不引入 Paging3 依赖）
- DAO 分页查询 + ViewModel load-more；首屏限载，滚动按需取
- 验收：大数据下管理页不卡

### T9.6 阶段一收尾
- 编译 + 真机验证：分类 Tab/搜索/注入检索正常；Room v15→v16 迁移无损；（可选）5000 条压测
- 人工审核门槛：记忆管理页分类筛选准确、搜索即输即查、注入走新服务不拖慢生成、真机无崩、迁移无损
- 进度（2026-08-25）：✅ 数据层真机冒烟通过：`assembleDebug` 编译成功；真机(K60) `-r` 安装保留数据，Room v15→v16→v17 链式迁移无崩溃、启动正常、进程存活。FTS 表/触发器随 MIGRATION_16_17 建成（无 SQL 异常）。✅ UI ADB 自测通过：记忆管理页渲染正常（分类 Tab 带数量：全部/个人信息/偏好/事件/技能…、列表项含类型徽章+可见性+内容+归属·时间）；分类筛选准确（点「个人信息」Tab 只剩对应条）；搜索即输即查（ADBKeyboard B64 注入「陶艺」，Tab 数量联动、列表命中「…学陶艺…青蓝釉」——中文子串 LIKE 兜底验证 PASS）。【待人工：滚动加载（当前仅 3 条数据量小，分页每页 20 无法触发）、删/增即时刷新；可选 5000 条压测】

---

## 9.12 阶段 9.12（记忆管理页升级·阶段二：记忆详情与编辑）（2026-08-25）

> 依赖：阶段 9.11（T9.1~T9.6 阶段一地基，已推 main）；参考 `docs/plan/PLAN-记忆系统升级.md` §③。
> 目标：**记忆详情页**，实现「点条目进详情，看全字段 + 通用卡片操作」，让记忆可查看、可更正、可管理。
> 范围：仅做详情页（全字段展示 + 置顶/重要/可见性/锁定/编辑/软删除）。关系图谱/时间轴仍留阶段三。

### T10.1 详情页数据层
- `MemoryRepository` 补代理：`getMemoryById(id)` 单条查询 · `updateMemory(item)`（自动刷新 updatedAt）· 沿用已有 `softDelete`
- 新增 `MemoryDetailViewModel`：按 id 加载单条全文 + 承载通用操作（置顶/重要/可见性/锁定/编辑/软删除）；主动收集角色卡流解析归属名
- 文件：`data/repository/MemoryRepository.kt` · `feature/memory/MemoryDetailViewModel.kt`
- 验收：编译通过 · 详情能按 id 取到全文 · 操作落库后刷新

### T10.2 MemoryDetailScreen UI（全字段展示 + 通用操作）
- 全字段：内容/关键词/分类/时间范围/可见性/归属/来源(会话#id·消息id)/置信度/创建/更新
- 操作行：置顶 / 重要(常规↔⭐) / 可见性(共享↔私密) / 锁定；状态在按钮上实时反映
- 文件：`feature/memory/MemoryDetailScreen.kt`
- 验收：字段齐全 · 按钮状态随操作即时切换

### T10.3 编辑 + 删除二次确认
- 编辑对话框：内容/关键词可改（编辑即更正，覆盖落库）；删除走软删除 + 二次确认对话框
- 文件：`feature/memory/MemoryDetailScreen.kt` · `MemoryDetailViewModel.kt`
- 验收：编辑回填现值并保存生效 · 删除有确认弹窗

### T10.4 列表项点击 → 详情入口 + 返回 + 列表刷新联动
- `MemoryScreen` 列表项点击 → 全屏覆盖详情；返回回到列表；操作/删除后列表经 observeActiveCount 联动刷新
- 文件：`feature/memory/MemoryScreen.kt`
- 验收：点击进详情 · 返回回列表 · 删除后列表少一条

### T10.5 阶段二收尾
- 编译 + 真机自测 + 推 GitHub
- 进度（2026-08-25）：✅ 编译通过（`compileDebugKotlin` exit 0）+ 真机(K60) installDebug 安装运行。ADB 自测：①点第一条「陶艺」进详情页，全字段渲染齐全（归属=小夏、来源=会话#9、置信度0.95、创建/更新、分类=偏好、时间范围=长期、可见性=共享）✅；②重要切换「常规→⭐重要→常规」往返 + 落库持久化 ✅；③可见性「共享→私密→共享」往返 ✅；④编辑对话框正确回填内容/关键词、保存/取消按钮齐全 ✅；⑤删除二次确认弹窗出现、点取消未误删、数据完好 ✅；⑥返回列表正常。已恢复测试数据原状。【待人工验收：编辑保存覆盖闭环、正式删除生效、滚动加载（当前 3 条数据量小无法触发）、删/增即时刷新、5000 条压测】

### T10.6 概览统计条（阶段二可视化·第 1 步，2026-08-25 立项）
- 目标：记忆管理页顶部「一眼看懂」——全库统计条：`共N · 📍置顶X · ⭐重要Y · 🔒私密Z`
- 数据：跨全库（无视当前列表筛选），响应式（随表增删改自动刷新）
- 文件：`dao/MemoryDao.kt`(observePinned/Important/PrivateCount) · `MemoryRepository.kt` · `MemoryViewModel.kt` · `MemoryScreen.kt`
- 验收：统计条数字随增/删/改即时联动；编译通过
- 进度（2026-08-25）：✅ 编译通过 + 真机(K60)「共3 置顶1 重要0 私密0」渲染与库一致；响应式联动「重要 0→1→0」往返实时刷新；测试数据已复原。

### T10.7 时间轴视图（阶段二可视化·第 2 步，2026-08-25 立项 & 完成）
- 目标：记忆按时间线演化的浏览视图（`时间分组 → 列表`）
- 文件：`feature/memory/TimelineScreen.kt` · `TimelineViewModel.kt` · `dao/MemoryDao.kt`(observeAllByUpdatedDesc) · `MemoryScreen.kt`(顶栏入口)
- 验收：按天分组展示 · 分组「今天/昨天/日期」· 点击进详情
- 进度（2026-08-25）：✅ 编译通过 + 真机(K60)「记忆时间轴」今天/昨天分组正确、类型·时间副标题+内容+归属渲染正常、点击陶艺项进详情、返回回时间轴。

## 9.13 阶段 9.13（记忆关联分析·共现热词，阶段三，2026-08-25 立项）

> 依赖：阶段二；参考设计文档 `PLAN-记忆系统升级.md` §⑤ 决策（关系图谱降级为共现热词列表）。

### T11.1 共现热词列表（2026-08-25 立项 & 完成）
- 目标：关键词共现频率排行（哪些词常一起出现）→ 点击热词进对应记忆列表
- 文件：`feature/memory/HotwordScreen.kt` · `HotwordViewModel.kt` · `MemoryScreen.kt`(顶栏入口 Star)
- 验收：热词排行榜 · 点击筛选出相关记忆；关系图谱留远期
- 进度（2026-08-25）：✅ 编译通过 + 真机(K60) 热词排行正确（风神翼龙/Nana/疑问/陶艺/青蓝色釉彩 各1）；点「陶艺」筛出「包含『陶艺』的记忆」（偏好·陶艺，含青蓝色釉彩）；命中项进详情、返回热词页且选中词保持。<br/>**阶段二(概览统计+时间轴)+阶段三(共现热词) 全三个可视化完成，阶段二/三收尾。**

---

## 9.14 阶段 9.14（记忆按角色卡直观区分，新需求 2026-08-25 用户验收提出）

> 背景：记忆可视化验收时用户反馈「没进行角色卡区分」——代码已有下拉角色筛选+归属名，但①入口是下拉难发现、②列表项无角色头像、③压测数据全为「未归属」导致看不到区分效果。用户确认新需求：**顶部角色 Tab + 列表项角色头像**（2026-08-25 征询同意，插入看板）。

### T11.2 记忆页角色卡区分：顶部角色 Tab（带数量）+ 列表项角色头像
- 目标：让「记忆是谁的」一屏可见、点击即筛，弱化「手动/未归属」主导的观感
- 操作：
  ① 角色筛选从「下拉」改为**横向角色 Tab 行**（全部 / 各角色 / 手动未归属），每个 chip 带**记忆数量角标**；数量随「搜索词+分类筛选」实时变化（不受当前角色筛选影响，避免选中后其余角色全归零）
  ② 列表项每条记忆前加**角色头像**（复用 `CharacterAvatar`：有头像显示图片，无则主题色块+首字）；「手动/未归属」用灰色块+「手」占位（或省略头像）
  ③ `MemoryViewModel` 新增 `cardTabCounts`（total/manual/byCard 三维计数），随 `reloadFirstPage` 刷新
- 文件：`feature/memory/MemoryScreen.kt` · `MemoryViewModel.kt`（复用 `data/search/MemorySearchService.countFiltered` 逐卡计数，角色卡量少可接受）
- 验收：[x] 顶部角色 Tab 显示各角色数量，点击即筛出对应角色记忆 · [x] 列表项显示角色头像/色块，一眼看出归属 · [x] 手动/未归属仍可筛出 · [x] 压测数据分配角色后区分效果明显 · [x] 编译+真机通过
- 进度（2026-08-25）：✅ 已实现并真机(K60)自测通过，已推 GitHub（commit `3396520`）。压测 5000 条按 40/30/20/10 分配角色（小夏1997/阿杰1529/光羽991/未归属484），真机验证：角色 Tab 数量正确、点「小夏」筛出全小夏且分类 Tab 联动数量（1997/277/297/286）、点「手动/未归属」筛出 484 条且列表无头像、概览条保持全库统计不受筛选影响；列表项小夏/光羽显示头像图、阿杰显示色块+「阿」字。

---

## 9.15 阶段 9.15（下一步三方向规划，用户 2026-08-25 选定 1+2+4，「你看着来」排优先级）

> 用户强调用词：角色间信息不对等是**互动**（角色自交往）自然产生，**不是"套话"**。以下「互动服务」均按此语义。执行顺序：A（记忆深化）→ B（互动+好感度）→ D（互动服务），一次只做一个任务点，完成后人工审核再推送。

### 方向 A：记忆系统深化（趁热打铁，复用 T9/T10/T11 架构）
- **T11.3 批量管理**（2026-08-26 立项 & 完成）：
  - 目标：列表长按进入多选模式 → 批量删除（二次确认）/ 批量设重要 / 批量置顶
  - 操作：
    ① `MemoryDao` 新增批量软删 `softDeleteByIds`、批量重要 `updateImportanceByIds`、批量置顶 `updatePinnedByIds` 三条批量 SQL
    ② `MemoryRepository` 包装三个批量方法；`MemoryViewModel` 新增 `batchDelete/batchSetImportance/batchSetPinned`，操作后 `reloadFirstPage()` 强制刷新（批量操作会改 updatedAt 排序维度）
    ③ `MemoryScreen` 多选 UI：长按进多选 + 勾选该项；点其他项追加/取消勾选；顶部操作栏（已选数量/全选/删除/更多）；「删除」二次确认 AlertDialog；「更多」下拉菜单（设重要/取消重要/置顶/取消置顶）；系统返回键退出多选；多选模式隐藏 FAB
  - 文件：`data/local/dao/MemoryDao.kt` · `data/repository/MemoryRepository.kt` · `feature/memory/MemoryViewModel.kt` · `feature/memory/MemoryScreen.kt`
  - 验收：[x] 长按进入多选 · [x] 点选追加/取消勾选 · [x] 全选/全不选 · [x] 批量设重要/置顶数据正确 · [x] 批量删除二次确认 · [x] 操作后退出多选 · [x] 返回键退出多选 · [x] 编译+真机通过
  - 进度（2026-08-26）：✅ 已实现并真机(K60)自测通过。验证过程：ADB `input swipe` 原地长按 900ms 可稳定触发 `onLongClick`（此前 motionevent/drag 均失败）；多选勾选/取消/全选正常；批量「设重要」概览重要 40→42、批量「取消置顶」置顶 5→3、批量删除 5001→5000 数据均正确；**期间发现并修复 UX 问题**：批量删除已退出多选但「更多操作」4 项未退出 → 4 处 DropdownMenuItem 补齐退出逻辑。
- **T11.4 记忆导入导出**（2026-08-26 立项 & 完成）：
  - 目标：记忆一键备份（导出 JSON）与恢复（从 JSON 导入），跨设备迁移不丢类型/角色归属/可见性/置顶/重要等字段
  - 操作：
    ① `MemoryRepository` 定义备份 DTO（`BackupMemory`/`MemoryBackup`，version 字段供格式演进）；`buildBackupJson` 序列化全部未删记忆（角色归属记录为「显示名 remark>name」，跨设备导入按名匹配现有角色卡）；`importBackupJson` 反序列化并逐条入库（校验 kind=memory_backup、跳过空内容条、字段值解析失败回落默认）
    ② `MemoryViewModel` 新增 `exportBackup`（构建 JSON 回传）与 `importBackup`（解析入库后刷新列表，回传成功条数）
    ③ `MemoryScreen` 顶栏「备份/恢复」菜单（导出走 SAF CreateDocument 写 JSON 到 Download；导入走 SAF OpenDocument 读文件，成功后 Toast 提示条数）
  - 文件：`data/repository/MemoryRepository.kt` · `feature/memory/MemoryViewModel.kt` · `feature/memory/MemoryScreen.kt`
  - 验收：[x] 导出生成 JSON 备份文件 · [x] 导入 3 条测试记忆入库且类型/角色归属/可见性/置顶/重要/私密/短期/过期时间全保留 · [x] 列表展示正确（角色 Tab/分类 Tab 联动）· [x] 非法/空文件有提示 · [x] 编译+真机通过
  - 进度（2026-08-26）：✅ 已实现并真机(K60)自测通过。导出经 SAF 写入 Download（`EdenCourt-记忆备份-*.json`）；测试导入 `import_test.json`（含小夏/阿杰/无归属 3 条，覆盖置顶+重要、私密+绝密+短期、无归属三类）→ 概览 4920→4923、置顶/重要/私密 0→1、小夏/阿杰 +1，列表项「📌置顶·⭐重要」「⏰短期」「手动/未归属」展示全部正确；**测试过程发现**：SAF 文件选择器搜不到 adb push 的文件 → 需先 `content call --method scan_file/scan_volume` 触发媒体扫描；搜索框注入中文用 ADBKeyboard B64、ASCII 下划线 `_` 会被 `input text` 转成全角破折号（改用 B64）；测试后已批量删除 3 条测试数据恢复 4920。
- **T11.5 关系图谱**（2026-08-26 立项 & 完成）：按关键词共现/关联构建节点-边图（记忆→图谱可视化），复用 T11.1 共现词。
  - 目标：全库记忆关键词构建「共现网络」——节点=关键词（大小∝提及条数）、边=同条记忆共现（粗细∝共现次数）；点节点看「共现词 chips + 命中记忆列表」，点记忆进详情；系统返回逐级回退
  - 操作：
    ① `GraphViewModel`：全库未删记忆统计共现图（词频取 TOP-30 作节点、同条记忆两两配对计数作边、共现 ≥2 才画线防过密）+ 力导向布局（FR 简化：斥力+边引力+向心，迭代 300 次收敛 0~1 归一化坐标，后台线程算）
    ② `GraphScreen`：`Canvas` 画边（粗细 1~5dp∝权重）/节点圆（半径∝提及次数）/词标签；`detectTapGestures` 最近节点命中（半径+24px）；选中高亮+白描边；下方共现词 `AssistChip`（点选切换联动命中列表）+ 命中记忆列表（复用 `HotwordHitItem`，固定 260dp 区）
    ③ `MemoryScreen` 概览条下新增「关系图谱」文字 chip 入口（与时间轴/共现热词并列），`showGraph` 覆盖页分支
  - 文件：`feature/memory/GraphViewModel.kt`（新增）· `feature/memory/GraphScreen.kt`（新增）· `feature/memory/MemoryScreen.kt` · `feature/memory/HotwordScreen.kt`（HotwordHitItem 改 internal 复用）
  - 验收：[x] 图谱渲染节点+边 · [x] 点节点选中高亮+共现词 chips+命中列表 · [x] 点共现词 chip 切换联动 · [x] 点命中记忆进详情 · [x] 系统返回逐级回退 · [x] 空态提示 · [x] 编译+真机通过
  - 进度（2026-08-26）：✅ 已实现并真机(K60)自测通过。验证过程：入口 chip→图谱页渲染 13 节点正常；点「滚动加载」→命中列表 4919 条+归属名正确；**压测数据每条仅 1 关键词导致无边** → 注入 30 条「樱花,春(,赏花)」共现记忆后边像素 234→1608，点「樱花」节点→共现词 chips「春 ×30」「赏花 ×10」+命中列表 30 条正确、点「春 ×830」chip 切换联动；注入 800 条「樱花,春」使边最粗（5dp）；点命中记忆进详情、返回逐级回退（详情→图谱→记忆列表）全通过；**期间修复注入事故**：sourceType 注入成不存在的 `MANUAL` 枚举导致 Room 读库崩溃 `Can't convert value to enum` → 修正为 `USER_EDIT` 后正常；**注：真机库注入 830 条测试记忆（内容含「共现图谱」前缀），如需恢复干净库需人工确认后删除**。

### 方向 B：互动 + 好感度（角色自交往）
- **T11.6 好感度数值体系**（2026-08-26 立项 & 完成）：互动行为（对话/互动次数/氛围）改变好感度；好感度分档驱动互动氛围与对话内容（取代 T8.9b 仅靠次数的近似）。
  - 目标：把 T8.8 预留的 `affinity_score` 从"占位 0"升级为生效的数值体系——每场互动按投入度累计好感度；好感度分档（陌生/相识/友好/亲密）决定关系标签，进而驱动互动氛围与对话内容；互动中心可视化展示
  - 操作：
    ① `ConversationRelationship` 伴生对象集中定义分档阈值（20/40/70）与规则：单场加成 `affinityGain = 10 + 2×min(回合,5)`（聊得越投入加越多）、`relationStatusForAffinity(score)`（取代按次数推导）、`affinityTierLabel(score)` 中文档位名
    ② `RelationshipDao`：`touchInteraction` 增加 `affinityScore = :affinity` 更新列；新增 `observeAll()` 供关系一览实时刷新
    ③ `InteractionExecutor`：互动前按「当前好感度」定氛围（RIFT 人工负向态保留）；`relationTone` 增强——注入「好感度约 X · 已到「档位」」让对话内容随数值更细腻；互动成功后 `affinityScore += gain` 并按新值递进标签
    ④ `InteractionCenterViewModel` + `InteractionCenterScreen`：互动列表顶部新增「关系一览（好感度）」区——每对会话显示「档位 · X 分 · N 次」，随互动实时刷新
  - 文件：`data/local/entity/ConversationRelationship.kt` · `data/local/dao/RelationshipDao.kt` · `data/interaction/InteractionExecutor.kt` · `feature/interaction/InteractionCenterViewModel.kt` · `feature/interaction/InteractionCenterScreen.kt`
  - 验收：[x] 单场互动好感度按公式累计 · [x] 分档随数值递进（20=相识/40=友好/70=亲密） · [x] 好感度跨重启持久（WAL 回放） · [x] 互动中心「关系一览」实时显示档位/分/次数 · [x] 编译+真机通过
  - 进度（2026-08-26）：✅ 已实现并真机(K60)自测通过。验证过程：无存量关系 → 注入第二个可互动会话「光羽」(card 3, 与学姐异卡)；互动中心发起「学姐→光羽」两场 → 关系一览 20 分(相识)→40 分(友好)、2 次实时刷新；**注意**：App 运行中写入在 WAL（主库文件不即时增长属正常），force-stop 后 pull 主库仍为空是 WAL 未合并——重启 App 后 UI 数据完整（WAL 回放正常）即持久化达标；真机库新增「光羽」会话与 2 场真实互动记录（非测试垃圾数据，保留）。
  - 追加（2026-08-26 会话记忆面板升级 + 好感度细分到会话角色）：① `ChatScreen.SessionMemorySheet` 重构——顶部搜索框（本地过滤共享/私有两区）+ 卡片式分区「共享记忆/本会话私密记忆」带计数角标 + 每条类型 chip·内容·时间 + 底部「前往记忆管理页」入口；② `ChatViewModel` 新增 `relationshipDao` 依赖与 `myRelationships` 流（按本会话聚合其参与的关系，解析对方名）；③ `ChatScreen.InteractionSheet` 新增「我的关系（好感度）· 角色名」区——逐条显示「与 X · 分值 · 档位色标 · 已互动 N 次」，好感度细分到会话角色；④ `AppNavHost` 接通 `onOpenMemoryManagement` → 记忆管理页。
- **T11.7 互动自动驱动**（2026-08-26 立项 & 完成）：已授权互动的角色按概率/时间自发发起互动（参考 T8.6 定时窗口），取代纯手动触发。
  - 目标：每天「自动窗口」内，已开放互动的角色两两配对，按概率自发互相串门跑一场完整双向互动（复用 T8.9 执行器），自动互动同样累计好感度（T11.6），并做冷却期去重避免同一对频繁被撮合。
  - 操作：
    ① `InteractionConfigStore` 扩展 `InteractionConfig`：`autoEnabled`（默认开，跟随全局总开关）/ `autoWindowStartMinute`/`autoWindowEndMinute`（默认 20:00–22:00）/ `autoProbability`（默认 0.3）
    ② `Interaction` 实体 + `InteractionDao`：新增 `source` 字段（MANUAL/AUTO）+ Room v17→v18 迁移 + `lastAutoTimeBetween` 冷却期查询
    ③ `InteractionAutoExecutor`：候选=已开放互动会话 → 两两配对过滤冷却期（3 天内已自动的跳过）→ 优先选「距上次互动最久/从未互动」的一对 → 按概率判定 → 复用 `InteractionExecutor.start(source=AUTO)`
    ④ `InteractionAutoScheduler` + `InteractionAutoReceiver`：AlarmManager 在「下次窗口起点」精确闹钟（无 SCHEDULE_EXACT_ALARM 时退回非精确）；接收器按全局+自动开关 + 窗口内判断执行一次 runOnce() 后无论结果重排次日；`force=true` 调试通道（adb 显示广播）跳过窗口/概率用于真机验收；Manifest 注册 receiver（exported 仅供 adb 调试）
    ⑤ `EdenCourtApp` 装配并启动时 schedule
    ⑥ `InteractionCenterViewModel` + `InteractionCenterScreen`：新增「自动驱动」设置区（开关/窗口起始结束 chip 弹小时选择/概率 Slider）+ 互动列表 AUTO 标记（「自动」小标）；配置修改即重排当日闹钟
  - 文件：`data/setting/InteractionConfigStore.kt` · `data/local/entity/Interaction.kt` · `data/local/dao/InteractionDao.kt` · `data/local/AppDatabase.kt` · `data/interaction/InteractionAutoExecutor.kt`（新增）· `InteractionAutoScheduler.kt`（新增）· `InteractionAutoReceiver.kt`（新增）· `EdenCourtApp.kt` · `AndroidManifest.xml` · `feature/interaction/InteractionCenterViewModel.kt` · `InteractionCenterScreen.kt`
  - 验收：[x] 编译通过 · [x] force 调试通道触发自动互动 · [x] 互动记录 source=AUTO 且 COMPLETED · [x] 自动互动摘要回写双方会话 · [x] 冷却期内重复触发被拦截 · [x] 闹钟重排（精确不可用退回非精确不崩溃）
  - 进度（2026-08-26）：✅ 已实现并真机(K60)自测通过。验证过程：App 启动即重排当日闹钟（logcat 确认精确闹钟无权限自动退回非精确，不崩溃）；`adb am broadcast -n .../.data.interaction.InteractionAutoReceiver --ez extra_force true` 触发 force 通道 → 执行器 runOnce 命中冷却期去重（返回「所有角色对都在冷却期内」）；pull 数据库核对 interaction 表存在 **id=8 source=AUTO status=COMPLETED**（此前 force 测试产生的自动互动，小夏→光羽），小夏会话内已回写「（互动）今天我去找了光羽聊…」摘要（03:59 手动 / 06:21 自动两条并存）；手动 MANUAL 与自动 AUTO 来源区分正确；冷却期去重逻辑在 force 重复触发下验证通过。**注**：真机库已确认干净（记忆仅剩 1 条真实数据「用户的昵称是Nana」），此前压测数据已不在库中，无需清理。

### 方向 B'：角色活力（2026-08-26 用户拍板新增，对应 IMPROVEMENT-PLAN 方案 A/B/C/D）
> 来源：系统性改进优化方案（docs/IMPROVEMENT-PLAN.md）4 条新增需求 N1~N4（2026-08-26 用户批准）+ N5「一起看」（同日用户新增，台阶1）。目标：把「能聊能记住」补上「角色有自己的生活节奏」——让角色像真人一样有作息/有此刻状态、会因事主动、关系有共同经历、能陪用户一起看。**执行顺序（2026-08-26 审阅后调整，详见方案附录 C）**：T11.10（状态机+展示，最快立真人感）→ T11.14（轻量首启引导+空态文案）→ T11.11（状态驱动对话与调度）→ T11.13（关系事件）→ T11.16（一起看，复用关系事件沉淀）→ T11.15（完整引导+入口提级，A/C 落地后引导才有内容）→ T11.12（自发动机，含 A' 软感知陪伴时机）→ 接方向 D（T11.8）。一次只做一个任务点，完成后人工审核再推送。

- **T11.10 角色生活状态机（N1 核心）**：
  - 依赖：T11.10（状态机+展示，最快立真人感）→ T11.14（轻量首启引导+空态文案）→ T11.11（状态驱动对话与调度）→ T11.13（关系事件）→ T11.16（一起看，复用关系事件沉淀）→ T11.15（完整引导+入口提级，A/C 落地后引导才有内容）→ T11.12（自发动机，含 A' 软感知陪伴时机）→ 接方向 D（T11.8）。一次只做一个任务点，完成后人工审核再推送。
  - 操作：
    ① 新增 `data/schedule/CharacterSchedule.kt`（状态机+按真实时间计算）与 `data/setting/ScheduleConfigStore.kt`（作息配置，DataStore）
    ② 会话列表角色卡片 + 聊天页顶部显示多态「此刻状态」（含状态小图标/文案）
    ③ 设置页新增「角色作息」配置区：起床/入睡/工作开始/工作结束/午休开始/午休结束 6 个滑杆 + 「恢复默认作息」，拖动即时生效并联动会话列表/聊天页状态
  - 文件：`data/schedule/CharacterSchedule.kt`（新增）· `data/setting/ScheduleConfigStore.kt`（新增）· `core/ui/LifeStateIndicator.kt`（新增）· `feature/conversationlist/ConversationListScreen.kt` · `feature/chat/ChatScreen.kt` · `feature/settings/SettingsScreen.kt` · `SettingsViewModel.kt` · 相关 ViewModel
  - 验收：[x] 角色卡片/聊天页按时段显示正确状态 · [x] 作息配置可调且即时生效 · [x] 编译+真机通过
  - **深化（2026-08-26，用户：「时间安排不应该每个都不太一样吗」→「角色卡处基于一个基础单独调节，不然默认」）**：
    - ① **个性作息派生**：`CharacterSchedule` 新增 `ScheduleTrait`（标准/晨型/夜型）关键词规则派生 + `applyTrait` 整体偏移（晨型-1h/夜型+2h），会话列表/聊天页按各自作息算「此刻状态」，设置页「各角色作息」区只读概览
    - ② **每角色手动覆盖**（本次）：`character_cards` 加 6 个可空自定义作息列（Room v19 迁移）；`CharacterSchedule.resolveConfig` 统一解析「自定义 > 个性派生 > 基础」；角色卡详情对话框新增「作息设置」区（6 滑杆 + 恢复跟随默认），未设置自动跟随默认
  - 文件（深化）：`data/schedule/CharacterSchedule.kt`（ScheduleTrait/resolveConfig）· `data/local/entity/CharacterCardEntity.kt`（自定义作息字段）· `AppDatabase.kt`（v18→v19）· `feature/character/CharacterScreen.kt`·`CharacterViewModel.kt` · `feature/settings/SettingsScreen.kt`·`SettingsViewModel.kt` · `feature/conversationlist/ConversationListScreen.kt` · `feature/chat/ChatScreen.kt`
  - 验收（深化）：[x] 三角色作息不同（晨型/夜型/标准） · [x] 角色详情可单独调节并生效 · [x] 未调节跟随默认 · [x] 编译+真机通过
  - **深化②全简化版（2026-08-26，用户：「改成只有起床和睡觉时间吧」「全部角色卡支持自定义，每个会话角色都支持单独调整」+ 修复「恢复默认还是10」与「最晚才12点睡」）**：
    - ① **作息简化 2 时刻**：`ScheduleConfig` 只保留起床+入睡（入睡支持跨天，最晚次日 01:59，`MAX_SLEEP_MINUTE`）；状态机简化为 4 态（睡眠/晨起/活跃/晚间放松），固定规则划分（入睡~起床=睡眠 → 起床后 1h=晨起 → 21:00 前=活跃 → 入睡前=晚间放松），以起床为 0 点归一化坐标系支持跨天入睡
    - ② **两级自定义**：`character_cards` 自定义作息改 2 列（起床/入睡）；`conversations` 新增会话级作息（lifeEnabled + 起床/入睡，Room v20 迁移）；`resolveConfig` 优先级=会话自定义 > 角色自定义 > 个性派生 > 基础
    - ③ **两级开关**：设置页全局开关（DataStore `schedule_enabled`）默认关 + 聊天页顶部作息面板会话级开关（`lifeEnabled`）默认关；都开启才显示「此刻状态」
    - ④ **修复「恢复默认还是10」**：角色卡详情默认值改走 `applyTrait`（基础+个性偏移）而非 `resolveConfig`（含自定义），恢复默认后滑杆正确显示派生值；**修复「最晚才12点睡」**：入睡支持跨天次日凌晨
    - ⑤ **聊天页顶部入口**：顶部状态标签/提示可点击 → `LifeScheduleSheet`（会话开关 + 起床/入睡 2 滑杆 + 恢复默认/取消/保存）
  - 文件（简化）：`data/setting/ScheduleConfigStore.kt`（2 时刻+全局开关）· `data/schedule/CharacterSchedule.kt`（4 态+跨天+resolveConfig 会话级）· `data/local/entity/CharacterCardEntity.kt`·`Conversation.kt`（会话级字段）· `AppDatabase.kt`（v19→v20）· `feature/chat/ChatScreen.kt`（LifeScheduleSheet+TimeSlider）·`ChatViewModel.kt` · `feature/conversationlist/ConversationListScreen.kt`·`ConversationListViewModel.kt`（两级开关+会话级解析）· `feature/settings/SettingsScreen.kt`（全局开关+2 滑杆）· `feature/character/CharacterScreen.kt`（恢复默认修复+2 滑杆）· `core/ui/LifeStateIndicator.kt`（4 态配色）
  - 验收（简化）：[x] 恢复默认显示派生值（阿杰夜型 10:00→09:00、入睡次日 01:00） · [x] 入睡支持跨天次日 · [x] 两级开关控制显示（全局开+会话开才显示） · [x] 聊天页顶部作息面板滑杆保存生效 · [x] 会话列表/聊天页状态随会话作息实时计算（21:34 晚间放松） · [x] 编译+真机通过
  - **滑杆体验优化（2026-08-26，用户：「滑动卡卡的、不支持准确到分钟，从整点开始每五分钟一格也好」）**：三处 TimeSlider（设置页/角色卡详情/聊天页作息面板）由「分钟连续值 + 70+ steps 细分」改为「整点起每 5 分钟一格的格索引」离散实现——Slider value 为格索引（steps=0，无大量细分节点 → 拖动顺滑），`onValueChange = 起点 + 格索引*5` 保证每次变化精确落在整点+5分钟倍数。真机拖动起床滑杆 07:00→08:25（5 分钟格吸附精确）。验收：[x] 拖动顺滑 · [x] 吸附 5 分钟格 · [x] 编译+真机通过
- **T11.11 状态驱动对话与互动调度（N1 深化）**：
  - 依赖：T11.10、T11.7
  - 目标：生活状态真实影响角色行为——深夜被叫醒会困倦、工作时段回复更简短；睡眠窗口不发起自动互动
  - 操作：
    ① `PromptBuilder` 注入「当前角色状态」影响语气与内容
    ② `InteractionAutoExecutor` 联动：角色处于睡眠状态时不参与自动串门（含会话内手动触发提示）
  - 文件：`domain/PromptBuilder.kt` · `feature/chat/ChatViewModel.kt` · `data/interaction/InteractionAutoExecutor.kt` · `InteractionAutoScheduler.kt`
  - 验收：[x] 深夜唤醒回复语气变化（人工复核：状态注入 system「【当前角色状态】」，两级开关开启时生效） · [x] 睡眠窗口不自动串门（真机 force 触发：学姐可互动+光羽睡眠→返回「不足2个」；睡眠过滤独立生效） · [x] 编译+真机通过
  - 实现（2026-08-26）：
    - ① `PromptBuilder.build()` 新增 `lifeStateText` 参数，注入「【当前角色状态】」片段（睡眠→带睡意/起床气、晨起→清爽活力、活跃→干脆、晚间→放松惬意）；`ChatViewModel.currentLifeStateText()` 两级开关都开启时按 `resolveConfig`+`computeState` 生成
    - ② `InteractionAutoExecutor` 构造注入 `scheduleConfigStore`+`characterCardDao`，`runOnce()` 候选过滤 `isSleeping()`（两级开关都开启才按睡眠判断）；`EdenCourtApp` 注入
- **T11.12 事件驱动自发动机（N2）**：
  - 依赖：T11.11、T11.6
  - 目标：主动互动从「定时+概率」升级为「事件+动机」——用角色记忆中的「事」（用户提过的主题/约定/关系事件）生成候选，满足触发条件时「因某件事而起意」发起互动，开场白注入「因由」；定时窗口降级为兜底
  - 操作：
    ① `InteractionAutoExecutor` 新增事件候选生成器（关联记忆主题/关系事件，含冷却/去重升级）
    ② 互动开场白注入「因由」片段（如「因为你说过喜欢奶茶，我刚刚路过…」）
  - 文件：`data/interaction/InteractionAutoExecutor.kt` · `InteractionExecutor.kt` · `data/local/dao/InteractionDao.kt` · `data/local/entity/Interaction.kt` · `AppDatabase.kt`（v21）
  - 验收：[x] 主动互动开场白可解释「因为…」 · [x] 因果占比≥60%（抽样：真机 3 场自动互动中 2 场带因由=66.7%） · [x] 定时兜底仍生效 · [x] 编译+真机通过
  - 实现（2026-08-26）：
    - ① `InteractionAutoExecutor` 新增 `pickMotive()` 事件候选生成器：取双方「可注入记忆」（该角色共享+本会话私有，排除绝密），按「置顶 > 重要 > 更新时间」排序，返回去重窗口内未用过的记忆内容（截断 40 字）作因由；候选排序「有因由的事件候选优先于无因由的定时兜底」
    - ② `InteractionExecutor.start()` 新增 `motive` 参数并写入互动主表；开场白系统提示注入「你这次来找对方，是因为「{因由}」这件事——自然地从这个由头聊起」，实现可解释的「因为…所以来找你」
    - ③ 因由去重：`InteractionDao.countMotiveUsedBetween()` 按「AUTO+同因由+窗口内+该对」精确匹配；**去重窗口独立为 7 天（motiveCooldownDays），必须大于配对冷却期 3 天**——否则能成为候选（=冷却期内无 AUTO 记录）时去重查询恒为 0，去重永不生效（真机验证时发现并修复）
    - ④ 数据库 v20→v21：`interaction` 表新增 `motive` 列
  - 真机验证（K60，force 通道）：#9 因由「用户的昵称是Nana」注入开场（话题=「你认识一个叫Nana的人吗」）✓；同一因由 7 天窗口内已用过（#9/#10）时 #11 motive=null 回落定时兜底（话题=「最近天气转凉了，你有没有好好加衣服」）✓；配对冷却期照常生效 ✓；motive 落库 ✓
- **T11.13 关系事件叙事（N3，数值保留）**：
  - 依赖：T11.6（好感度数值）、T8.10（互动三层表）
  - 目标：为关系引入「共同经历事件」——每次互动沉淀事件（场景/主题/氛围/要点），互动中心新增「关系故事」区展示里程碑叙事；**保留好感度数值体系**（用户明确：数值也要保留），事件作为叙事层叠加、不替代数值
  - 操作：
    ① 新增关系事件实体（`relationship_event`：关系对+场景/主题/氛围/要点/时间）与迁移；`InteractionExecutor` 互动成功后沉淀事件
    ② `InteractionCenterViewModel/Screen` 新增「关系故事」区（时间线里程碑）
    ③ `PromptBuilder` 事件注入（让对话有「我们经历过」的连续性）
  - 文件：`data/local/entity/RelationshipEvent.kt`（新增）· `data/local/dao/RelationshipDao.kt` · `AppDatabase.kt`（迁移）· `data/interaction/InteractionExecutor.kt` · `feature/interaction/InteractionCenterViewModel.kt` · `InteractionCenterScreen.kt` · `domain/PromptBuilder.kt` · `feature/settings/SettingsScreen.kt`（互动中心入口，T11.13 补）
  - 验收：[x] 每场互动沉淀事件 · [x] 关系故事区展示里程碑叙事 · [x] 好感度数值仍显示并累计（不冲突） · [x] 编译+真机通过
  - 实现（2026-08-26）：
    - ① 新增 `RelationshipEvent` 实体（relationship_event 表，convAId<convBId 规范化；scene/topic/mood/points/source/happenedAt）；`RelationshipDao` 加 insertEvent/observeAllEvents/observeEventsBetween/recentEventsForConversation；`AppDatabase` v21→v22 迁移建表+索引；`InteractionExecutor.start()` 互动成功后沉淀事件：场景=因由（motive 非空记「因为「…」」否则「日常」）、主题=话题锚点、氛围=互动前好感度档位、要点=双方视角摘要合并
    - ② 互动中心「关系故事（共同经历）」区：位于关系一览下方、互动列表上方，时间线倒序展示（双方名+氛围档位+自动标记+场景/主题+要点2行+时间）
    - ③ `PromptBuilder.build` 新增 `relationshipEventsText` 注入；`ChatViewModel.buildPrompt` 组装本会话最近 3 条共同经历事件片段（「你和{对方}一起…」），让角色记得「我们共同经历过」，对话有连续性
    - ④ 设置页新增「互动中心」入口卡（T11.13 补，用户需求）：紧凑导航卡置于角色作息之后，点击跳转互动中心（AppNavHost 传 onOpenInteraction）
  - 真机验证（K60）：v22 迁移成功（user_version=22，relationship_event 表结构正确）；force 触发互动 #12 后沉淀事件（scene=日常、topic=「光羽，最近降温啦…」、mood=亲密、points=双方摘要、source=AUTO）✓；好感度数值/档位/次数仍展示（关系一览「亲密 · 140 分 · 7 次」未受影响）✓；设置页→互动中心入口点击跳转成功（uiautomator 验证 UI 渲染：关系故事时间线完整显示）✓；编译通过 ✓
- **T11.16 共同内容会话「一起看 v1」（N5，2026-08-26 用户新增）**：
  - 依赖：T11.13（关系事件沉淀）、T2.4（聊天流式）
  - 目标：用户**主动分享**内容（粘贴一段文字/小说段落/截图说明/说"我在看 XX"）→ 角色进入「陪看模式」：讨论剧情、吐槽、猜后续、回应进度；陪看完沉淀为「共同经历」关系事件（主题/讨论/氛围）进关系故事区，并喂给自发动机（T11.12 的"因事而起"素材）。**红线：绝不读取用户设备上在放什么**（前台 App/剪贴板监控=越权），只处理用户主动发来的内容
  - 操作：
    ① `PromptBuilder` 注入「陪看模式」：识别用户分享内容的意图 → 角色以陪看口吻讨论（剧情/人物/感想/追问"看到哪了"）
    ② 陪看后沉淀「共同经历」关系事件（复用 T11.13 的关系事件表，type=共同观看，记主题/讨论要点/氛围）
    ③ 关键讨论可选提取为角色记忆
  - 文件：`domain/PromptBuilder.kt` · `feature/chat/ChatViewModel.kt` · `data/interaction/InteractionExecutor.kt`（或共用陪看沉淀逻辑）· `data/local/dao/RelationshipDao.kt` · `feature/interaction/InteractionCenterViewModel.kt` · `InteractionCenterScreen.kt`（关系故事区展示共同经历）
  - 验收：[x] 用户粘贴内容后角色以陪看口吻讨论 · [x] 陪看后生成「共同经历」关系事件且在关系故事区可见 · [x] 断言不采集设备上用户在看什么（红线，仅处理用户主动发来的消息） · [x] 编译+真机通过
  - 实现（2026-08-27）：
    - ① `ChatViewModel` 新增 `detectWatchContent` 陪看检测：命中触发词（我在看/刚看完/分享/这段剧情/看到第/看到哪/更新了…）或超长粘贴正文（≥60 字）即视为分享，提取主题；`PromptBuilder.build` 新增 `watchModeText` 参数注入「陪看模式」提示（讨论/吐槽/猜后续/追问进度），`ChatViewModel.buildPrompt` 命中时组装
    - ② 回复完成后 `settleWatchEvent` 沉淀 WATCH 共同经历事件：`RelationshipEvent(type=WATCH, convAId=本会话, convBId=0=用户侧, scene=分享场景, topic=提取主题, mood=「一起看」, points=你分享摘要+角色回应摘要)`；10 分钟冷却防刷，生成失败（markFailed）作废待沉淀状态
    - ③ 关系故事区展示增强：`RelationshipEventOverview` 增 `type` 字段，`InteractionCenterScreen` 增「· 一起看」标记；抽 `RelationshipStorySection` 组件，空态分支（无角色互动）也展示共同经历 + 空态 Column 加 verticalScroll 防溢出（WATCH 事件无互动时用户也能看到）
  - 真机验证（K60 2026-08-27 03:27）：API Key 丢失重新配置后，发「我在看《时间的褶皱》…看到第12章」→ 羽陪看回复（讨论剧情/进度）；发「看到第13章了…图书馆对峙…会不会合作」→ 数据库 relationship_event 沉淀 WATCH 事件（type=WATCH、convBId=0、scene=「你分享内容，一起看「13章了！…」」、mood=一起看、points=双方摘要）✓；互动中心关系故事区显示「羽 × 你 · 一起看」完整事件（标题/场景/要点/时间）✓；空态可滚动 ✓；编译通过 ✓
- **T11.14 新手上手引导·轻量版（N4 第一步）** ✅ 2026-08-27 完成：
  - 依赖：无（可先行/并行）
  - 目标：新用户 3 分钟内建立「活在手机上的真人」心智的**基础版**——仅做首启引导与空态文案，**只指向已有功能**，不承诺未落地能力（避免"功能没做完就教用户用"）
  - 操作：
    ① 首启引导 2~3 步（你是谁→她们是谁→能做什么，可跳过、可记住"已看过"）——`OnboardingConfigStore`(DataStore 记 seen) + `OnboardingGate`(全屏 3 步引导) 挂在 MainActivity 根节点
    ② 各页空态引导文案（会话列表/记忆的友好引导语；互动中心既有文案已达标保留）
  - 文件：`MainActivity.kt` · `feature/onboarding/OnboardingGate.kt`（新增）· `data/setting/OnboardingConfigStore.kt`（新增）· `feature/conversationlist/ConversationListScreen.kt` · `feature/memory/MemoryScreen.kt`
  - 验收：[x] 首启出现引导且可跳过（K60 实测 3 步「你是谁/她们是谁/能做什么」全通，下一步/跳过可用）· [x] 空态文案友好且不指向未完成功能（会话列表「还没有对话…」+记忆页「还没有记忆…」增强，仅指向已有功能）· [x] 编译+真机通过（assembleDebug SUCCESS；K60 安装启动无崩溃，点「开始使用」进入会话列表，重启不再弹引导，seen 持久化生效）
- **T11.15 完整引导 + 入口提级（N4 第二步，A/C 落地后）** ✅ 2026-08-27 完成：
  - 依赖：T11.10、T11.13（引导要指向已落地的新功能）
  - 目标：把「入口提级」与「完整引导」补齐——互动中心/记忆可视化/自动驱动不再藏在二级入口；引导补充指向角色状态/关系故事等新能力
  - 操作：
    ① 入口提级：会话列表顶栏加「记忆」「互动中心」直达图标（一屏可见，`Icons.Star/Favorite`；角色卡「此刻状态」T11.10 已落地无需重复）
    ② 首启引导增强：OnboardingGate 增第 4 步「她们有自己的节奏」——指向此刻状态/自动驱动/关系故事（均为已落地功能）
  - 文件：`feature/conversationlist/ConversationListScreen.kt` · `navigation/AppNavHost.kt` · `feature/onboarding/OnboardingGate.kt`
  - 验收：[x] 互动/记忆入口一屏可见（K60 顶栏三图标「记忆/互动中心/菜单」走查通过，点图标分别直达记忆页/互动中心）· [x] 引导指向新功能且实际可进入（第 4 步「她们有自己的节奏」指向此刻状态/自动驱动/关系故事，点「开始使用」正常进入）· [x] 编译+真机通过（assembleDebug SUCCESS；K60 安装启动无崩溃，仅删 onboarding 标记文件验证引导，未动数据库）

### 方向 D：互动服务（记忆可见性升级，用户强调：是互动不是套话）
- **T11.8 互动信息不对等**（拆解为 3 个原子任务点，2026-08-27）：
  - **T11.8a 互动记忆可见性标注收口**：互动中角色只能基于「自己可见的记忆」回应（A=自己卡的共享+本会话私有，B 同理天然互不泄漏，均排除绝密——现状已基本满足）；强化：记忆片段按可见性分组标注（「[仅你自己知道的]」=PRIVATE /「[你和用户都记得的]」=SHARED），规则补强「你的私密只有你知道，是否透露由你决定，但不要主动编造或泄露」；回归断言 B 的私密绝不出现在 A 的注入里
  - **T11.8b 互动事实沉淀为新记忆**（核心新增）：互动结束后，基于双方已说出口的对白（共同事实），分别提取「A 从这场互动中得知的关于 B 的事实」与「B 从互动中得知的关于 A 的事实」，各沉淀为新记忆（归属到各自角色卡名下 shared、来源=互动非亲历，区别于用户亲口告知；只从说出口的对白提取=天然不泄漏私密）
  - **T11.8c 闭环验证**（2026-08-27 完成）：编译+真机——互动后 A 的后续会话/互动能想起「关于 B 的事实」（getInjectableMemories 自然覆盖）；A 的注入绝不含 B 的私密（回归）；好感度/关系事件不受影响
  - **T11.8d 冲突判定修复**（T11.8c 真机发现并修复，2026-08-27）：`upsertWithConflictCheck` 旧逻辑「全库单关键词命中即覆盖」导致宽泛词（如「图书馆」）把互动事实沉淀误覆盖无关旧记忆（真机事故：羽的阅读记忆被「小夏觉得图书馆有种温柔的安静」覆盖）。修复=范围收紧到同角色卡 + 判定需「内容互为包含且较长方≥5字」或「≥2关键词同时命中」，否则按新事实新增（保住旧记忆）。逻辑 6 用例对比 + 真机端到端（互动后旧记忆完好、新事实正确新增）均通过。遗留：已覆盖的脏数据不可自动恢复，需在记忆管理页手动处理
  - 依赖：T11.6（好感度）、T8.10（互动三层表）、T11.13（关系事件）
  - 文件：`data/interaction/InteractionExecutor.kt`（可见性标注+沉淀调用）· `data/repository/MemoryRepository.kt`（互动提取沉淀方法）· `feature/interaction/*`（如需展示）· `domain/PromptBuilder.kt`（规则补强）
- **T11.9 多角色同场（群聊雏形）**：远期。

---

## 9.16 阶段 10：审核驱动·工程保底（M10，2026-08-27 全面审核新增）

> 来源：docs/项目全面审核报告-2026-08-27.md（deepseek-v4-pro 全面审核，只审不改代码）。审核发现三类硬伤：**安全红线**（API Key 明文+应用可备份、receiver 导出）、**性能瓶颈**（流式每 token 落库、FTS 未接线、暗访拉全文）、**工程保底缺口**（回复泵游标内存态丢失、核心逻辑无单测）。综合评分 7.2/10。本阶段把 10 项发现落成任务点，按 P0→P1→P2 排依赖推进，一次只做一个任务点、每条完工交人工审核。逐条编号 `T12.x`。
>
> **阶段收尾（2026-08-28）：T12.1~T12.10 全部完成并推送 GitHub（34f9e6d、d12e569 等）。** 安全（Key 加密+备份排除、receiver 全收敛）、性能（流式节流、FTS4 接线、暗访定向查询、消息窗口分页）、工程保底（游标持久化、24 用例单测、ChatViewModel 拆分 1060→891 行、关系对规范化下沉+陪看哨兵改可空列）三类硬伤全部落地，阶段 10 验收关闭。
>
> **T12.11 跨会话打听·小夏转述 + 列表预览修复（2026-08-28 完成并推送）**：真机走查发现暗访回传「把 B 的原话+动作直接贴到 A 会话」（视觉上变成 B 在跟用户说话），且打听回传不刷新会话列表预览（停留上一句）。修复：①`CrossSessionConsultService.relayAsVisitor` 以发起角色（A）人格+口吻把打听结果转述给用户（两次 LLM：B 真作答 + A 转述，转述失败兜底回 B 原话不丢信息）；②`ChatSideEffects.updateConsultMessage` 回传后同步刷新 `conversations.lastMessagePreview/时间`（类微信）。真机端到端验证：小夏会话发「你能去问一下羽在干什么吗？」→ 回传为「（小跑到你身边…）我跟你说哦~羽刚才在桂花树下待着呢…」（主体/动作/口吻均是小夏），列表预览同步更新为 01:04 最新回传。文件：`CrossSessionConsultService.kt` · `ChatSideEffects.kt`。
>
> **T12.11 补充·角色卡性别字段（2026-08-28 完成，随 T12.11 推送）**：真机复核发现转述里 LLM 把羽称「他」（角色卡无性别信息）。落地：①`CharacterCard.gender`（可选，「女」/「男」/空，JSON 序列化）；②内置三张卡补 gender（小夏女/阿杰男/羽女）；③`BuiltInCards.seedGenderIfMissing` 幂等回填补丁（仅补「名字匹配内置+isBuiltIn+JSON 无 gender」的存量卡，EdenCourtApp 启动调用，不误改自定义卡）；④`ConsultResult.sourceGender` + `relayAsVisitor` 转述 prompt 注入「《羽》是女性角色，提到 ta 一律用『她』」。真机端到端验证：再次打听，回传「她正倚在老槐树下…她说她在谱一支没有词的曲子…」全部用「她」。文件：`CharacterCard.kt` · `BuiltInCards.kt` · `EdenCourtApp.kt` · `CrossSessionConsultService.kt` · `ChatSideEffects.kt`。

- **T12.1 API Key 加密存储 + 备份排除（S1，P0）**：
  - 依赖：无
  - 操作：① API Key 迁移到 Android Keystore/EncryptedSharedPreferences 加密，或至少配置 `dataExtractionRules`/`fullBackupContent` 排除 `api_config.preferences_pb`；② 保持「不打印明文日志」惯例
  - 文件：`data/setting/ApiConfigStore.kt` · `AndroidManifest.xml`（+ backup 规则资源）· `app/build.gradle.kts`（如引入 security-crypto）
  - 验收：[x] Key 不落明文（cipher 加密存储 API_KEY_ENC + Manifest 配 dataExtractionRules/fullBackupContent 排除） · [x] 编译+真机通过
- **T12.2 流式写入节流（P1，P0）**：
  - 依赖：无
  - 操作：`streamReply` 的「每 SSE delta（≈每 token）一次 getMessage+updateMessage」改为「内存 StringBuilder 累积 + 80~120ms 节流批量写」或「流式结束一次性落库（保留断点续写则节流）」，消除单回复 500 次 DB 读写
  - 文件：`feature/chat/ChatViewModel.kt`
  - 验收：[x] 单回复落库次数从 ~500 降到 ~几十次（STREAM_PERSIST_THROTTLE_MS=120ms 节流批量写） · [x] 流式不掉帧（真机走查） · [x] 打断/续写仍正常（flush 内存最新值） · [x] 编译+真机通过
- **T12.3 exported receiver 收敛（S2，P1）**：
  - 依赖：无
  - 操作：`DailyConsultAlarmReceiver` / `InteractionAutoReceiver` 改 `exported="false"`；调试改用 `adb shell am broadcast -n 组件名` 显式定向广播（可触达非导出组件）
  - 文件：`AndroidManifest.xml`
  - 验收：[x] 全部 receiver 均非导出（4 个全 exported=false，超出原 2 个范围） · [x] 编译+真机通过
- **T12.4 回复泵游标持久化（B1，P1）**：
  - 依赖：无
  - 操作：`lastProcessedUserMessageId` 从内存态落库（`conversations` 加列），进程重建后从持久化游标续跑，杜绝将历史 USER 消息重发为一次来信
  - 文件：`data/local/entity/Conversation.kt` · `data/local/AppDatabase.kt`（迁移）· `feature/chat/ChatViewModel.kt`
  - 验收：[x] 进程杀死重启后不再重复回应历史（ReplyCursorStore DataStore 落盘 + 首轮恢复） · [x] 迁移不崩 · [x] 编译+真机通过
- **T12.5 核心逻辑单测（T1/T2，P1）**：
  - 依赖：T12.2（流式节流改造后逻辑稳定再固化测试护栏）
  - 操作：为「回复泵攒批/流式打断/代际闸门」+「`upsertWithConflictCheck` 冲突判定」补 mockk 单测（复用已有 junit+mockk 依赖）
  - 文件：`app/src/test/...`（新增 ChatViewModelTest / MemoryConflictTest）
  - 验收：[x] `testDebugUnitTest` 通过（24 用例） · [x] 冲突判定证据1（内容互含≥5字）/证据2（≥2关键词）覆盖「宽泛词不覆盖/同事实合并/无关新增」三态（MemoryConflictResolverTest 10 用例）
- **T12.6 消息真分页 + 暗访定向查询（P2/P3，P2）**：
  - 依赖：T12.2
  - 操作：`MessageDao` 提供 `observePaged(conversationId, limit, offset)`（LIMIT 查询+Flow），UI 走真分页；旁路暗访改「最近 N 条」定向查询（含 LIMIT），全量流保留给确需场景
  - 文件：`data/local/dao/MessageDao.kt` · `feature/chat/ChatViewModel.kt` · `data/repository/CrossSessionConsultService.kt`
  - 验收：[x] 长会话滚动 60fps · [x] 暗访不拉全文 · [x] 编译+真机通过
- **T12.7 FTS4 接线或裁撤（P4，P2，二选一）**：
  - 依赖：无
  - 操作：先补 FTS 查询词转义（`*`/`(`/`)`/`-`/OR/AND 等），再接入管理页英文/标签词检索；**或**明确裁撤 FTS4（删虚拟表+触发器+searchFts）降低复杂度
  - 文件：`data/search/MemorySearchService.kt` · 记忆检索调用方（接线）或删除相关
  - 验收：[x] 决策明确并落地（接线：英文/标签走 FTS 精确命中并置前、中文仍 LIKE 兜底；补查询词双引号转义说明） · [x] 编译+真机通过
- **T12.8 互动自动驱动补 BOOT receiver（B3，P2）**：
  - 依赖：无
  - 操作：互动自动驱动对齐每日打听，补 BootReceiver 在设备开机后重建自动串门闹钟
  - 文件：`data/interaction/InteractionAutoScheduler.kt` · BootReceiver（扩展或新增）· `AndroidManifest.xml`
  - 验收：[x] 重启后自动互动闹钟重建 · [x] 编译+真机通过
- **T12.9 ChatViewModel 拆分专项（Q1，P2，技术债）**：
  - 依赖：T12.5（先有测试护栏再安全重构）
  - 操作：跨会话打听（T8.3/8.4）抽 `ChatSideEffects.kt`、陪看（T11.16）抽 `WatchTogetherDetector.kt`，UI 层不变，仅瘦身 1060 行超载类；UI 状态（isGenerating/errorMessages）留在 ViewModel
  - 文件：`feature/chat/ChatViewModel.kt`（1060→891 行）· 新增 `ChatSideEffects.kt` / `WatchTogetherDetector.kt`
  - 验收：[x] 拆分后聊天/暗访/陪看行为不变（逻辑原样搬移，仅接线点替换）· [x] 单测仍通过（24 用例）· [x] 编译+真机通过（覆盖安装启动无崩溃）
- **T12.10 关系对约定下沉 + 陪看事件表达明确（D1/D2，P2）**：
  - 依赖：无
  - 操作：① 关系对「小 id 恒 A」约定下沉到 `RelationshipRepository`（`createNormalized`/`insertEvent` 内部排定，业务层不再裸 `upsert`/自记 min/max）；② 陪看事件 `convBId=0` 哨兵值改**可空列**（`convBId=null` = 用户共同观看），v23→v24 整表重建迁移存量 0→NULL
  - 文件：`data/local/dao/RelationshipDao.kt` · `data/repository/RelationshipRepository.kt`（新增） · `data/local/entity/RelationshipEvent.kt` · `AppDatabase.kt`（MIGRATION_23_24） · `InteractionExecutor.kt` · `InteractionAutoExecutor.kt` · `ChatViewModel.kt` · `InteractionCenterViewModel.kt` · 迁移测试
  - 验收：[x] 新调用点无需自记 min/max（createNormalized/insertEvent 承担）· [x] 陪看事件表达明确无哨兵值（可空列）· [x] 迁移不崩（真机 v23→v24 覆盖安装通过）· [x] 编译+真机通过 · [x] 迁移测试含 v22→v24 哨兵归 NULL 用例

> 依赖关系：P0（T12.1、T12.2 并行）→ P1（T12.3、T12.4 可紧随，T12.5 依赖 T12.2）→ P2（T12.6 依赖 T12.2、T12.9 依赖 T12.5，余 T12.7/T12.8/T12.10 无强依赖）。落地顺序建议与现有 PLAN 看板合并，遵项目规则逐任务点推进。

---

## 9.16 阶段 11（v1.3）：角色差异化 + 自补充（用户 2026-08-28 提出，AI 拍板立项）

> 方案：docs/角色差异化与自补充方案.md（v2，2026-08-28 已立项）。
> 目标：同一角色卡开多个会话 → 每个会话角色在卡范围内随机 6 维数值 + 反差对 + 印象摘要，实现「同卡不同魂」；人设未提到的偏好（如运动）角色遇到时按性格/经历自补充并沉淀为**该会话私有记忆**。
> 依赖链：T13.1（建表迁移）→ T13.2/T13.3（范围派生+生成）→ T13.4（LLM 印象摘要）→ T13.5（Prompt 注入）→ T13.6（可视化区分）→ T13.7（自补充闭环）。P1=P1 底座、P2=可视化、P3=自补充。

- **T13.1 数据层：conversation_traits 表 + Room v24→v25 迁移（P1，P0）**：
  - 依赖：无
  - 操作：新建实体 `ConversationTraits`（conversationId 唯一 FK + characterCardId + 6 维数值 + 反差对三字段 + identityBlurb/identityTags/generatedBy/createdAt）；DAO `getByConversation/insert/updateBlurbAndTags`；`AppDatabase.kt` 加 MIGRATION_24_25（纯新增表，无存量）；迁移测试补 v24→v25 用例
  - 文件：`data/local/entity/ConversationTraits.kt`（新增）· `data/local/dao/ConversationTraitsDao.kt`（新增）· `data/local/AppDatabase.kt` · 迁移测试
  - 验收：[x] 编译通过 · [x] 单测全绿 · [x] 迁移测试 v24→v25 通过（真机 user_version=25）
- **T13.2 范围派生：traitsRange + LLM 读卡生成范围（P1，P1）**：
  - 依赖：T13.1
  - 操作：① `CharacterCard` 加可选 `traitsRange`（数据党 JSON 字段，`ignoreUnknownKeys` 兼容旧卡）；② 新增 `TraitRangeResolver`：数据党直接用 traitsRange → 感觉党走 LLM 读卡 `personality/description` 输出 6 维范围 JSON（带 confidence）→ 失败降级关键词启发式 → 50±25 兜底（三级兜底）；③ 单测覆盖三态
  - 文件：`domain/CharacterCard.kt` · `data/traits/TraitRangeResolver.kt`（新增）· 单测
  - 验收：[x] 编译通过 · [x] 单测覆盖数据党/感觉党/中间党三态（10 用例）
- **T13.3 6 维随机生成 + 反差对生成（P1，P2）**：
  - 依赖：T13.2
  - 操作：① `TraitsGenerator`：SecureRandom 在范围内均匀落点（固定不漂移）；② 反差对：卡派生表面 + 受限反差库选隐藏（关键词冲突检测过滤，冲突重选）；③ 单测覆盖范围边界/一致性过滤/重选
  - 文件：`data/traits/TraitsGenerator.kt`（新增）· `data/traits/ContrastLibrary.kt`（新增）· 单测
  - 验收：[x] 编译通过 · [x] 单测覆盖随机边界 + 反差一致性过滤（11 用例）
- **T13.4 LLM 印象摘要生成（P1，P3）**：
  - 依赖：T13.3
  - 操作：建会话时把「卡设定 + 6 维数值 + 反差对」交 LLM 生成第三人称概括 + 2~3 标签；失败降级纯模板（数值区间+标签库拼装）；写入 identityBlurb/identityTags/generatedBy='llm'；用户手动改后 generatedBy='user' 不被覆盖
  - 文件：`data/traits/TraitsSummaryGenerator.kt`（新增）· `data/traits/ConversationTraitsGenerator.kt`（新增）· 建会话流程（ConversationListViewModel）
  - 验收：[x] 编译通过 · [x] 真机同卡 2 会话印象摘要各不相同且符合人设（羽·id3=「温和纯净的天使，羽翼洁白的守护者…」） · [x] 用户改印象不被覆盖（真机实测 2026-08-30：编辑摘要→「温和纯净的天使守护者（用户已编辑）」、标签→「天使、守护者、清冷」，generatedBy 变 'user'，会话列表标签同步生效）
- **T13.5 Prompt 注入：印象摘要 + 反差对 + 自补充引导（P1，P4）**：
  - 依赖：T13.4
  - 操作：`PromptBuilder.buildCardSystemPrompt` 追加注入：①印象摘要（如有）②反差对（平时外显表面、依恋度≥阈值流露隐藏）③自补充引导（未知偏好按性格推断并自述、沉淀记忆保持一致）；ChatViewModel 组装 prompt 时读 traits（一次查询缓存）
  - 文件：`domain/PromptBuilder.kt` · `feature/chat/ChatViewModel.kt`
  - 验收：[x] 编译通过 · [x] 真机问未设定偏好（运动/食物）角色自述且与性格一致（会话3 问「你喜欢喝什么」→ 羽自述「露水酿的花茶」并沉淀私有记忆 id=8，后续保持该说法） · [x] 依恋度阈值触发反差（单测 80/40/65/64 边界全覆盖 + 真机实测 2026-08-30：attachment=50 未达标回复委婉克制「不过…也想尝尝」；attachment=80 达标直白承认「被小夏发现了呢……确实会惦记着某些人的身影」）
- **T13.6 可视化区分：会话列表标签行 + 聊天页印象摘要（P2，P5）**：
  - 依赖：T13.4
  - 操作：① 会话列表卡片显示印象标签行（identityTags）一眼区分；② 聊天页角色信息/记忆面板展示完整摘要 + 编辑入口（用户可改，generatedBy→'user'）；③ 无 traits 老会话不显示（兼容）
  - 文件：会话列表 UI · 聊天页 UI · 相关 ViewModel
  - 验收：[x] 编译通过 · [x] 真机同卡多会话列表可区分（新羽会话显示「沉稳粘人/空灵安抚」标签，老羽无） · [x] 编辑印象保存生效（真机实测 2026-08-30：摘要/标签编辑后落库 generatedBy='user'，聊天页顶部与会话列表标签同步更新）
- **T13.7 自补充闭环：偏好沉淀 + 一致性校验（P3，P6）**：
  - 依赖：T13.5
  - 操作：① 角色自述推断偏好 → 复用既有记忆提取管线 → 以该会话私有（sourceConversationId=本会话, visibility=PRIVATE）存入；② 后续 prompt 注入保持自洽；③ 一致性校验（复用记忆更正机制，P4 优化位先记）
  - 文件：`data/repository/MemoryRepository.kt`（接线）· `domain/PromptBuilder.kt`
  - 验收：[x] 编译通过 · [x] 真机问偏好→角色自述→记忆沉淀→下次一致（会话3 羽自述「露水酿的花茶」沉淀 PRIVATE 私有记忆 id=8，会话列表/聊天中保持该说法） · [x] 其他会话不可见该偏好（会话1 问同样问题只提「热奶茶」自身记忆，未引用会话3 的「露水花茶」，隔离正确）
- **T13.8 阶段收尾：单测 + 迁移 + 真机验证（P3）**：
  - 依赖：T13.6、T13.7
  - 操作：全量 `testDebugUnitTest` + 迁移测试 + 真机覆盖安装回归（同卡 3 会话区分 / 自补充一致 / 老会话兼容 / 无崩）
  - 验收：[x] 单测全绿（94 用例，12 类，0 失败） · [x] 真机回归通过（迁移 v25 / 新会话人格落库 / 列表标签 / 老会话兼容 / 无崩） · [x] 备份留档（v1.3/20260829_181531）

> 依赖关系：T13.1 → T13.2 → T13.3 → T13.4 →（T13.5 ‖ T13.6）→ T13.7 → T13.8。P1 底座（T13.1~T13.5）先交付人工审核，再 P2 可视化（T13.6）、P3 自补充（T13.7）。

---

## 9.17 阶段 12（v1.3 发布收尾）：版本号正式化 + 发布流水线（用户 2026-08-30 简码「3」授权自主推进）

> 依据：docs/版本管理与开发流程方案.md（§2.3 发布流水线 + §3.3 发布准入 gate G1~G6）。
> 背景：v1.3 功能（角色差异化 + 自补充 + 记忆主动引用增强）已全部实现并真机验证，但 app 版本号仍停在 **1.0.0（versionCode=1）**，与 CHANGELOG 1.3.0 不一致；P-09（Release/版本收尾欠账）未闭合。本阶段把 v1.3 正式化收尾。
> 依赖链：T14.1（版本号）→ T14.2（G1~G4 gate）→ T14.3（G5 备份）→ T14.4（稳定版归档）→ T14.5（G6 文档+tag+推送）。一次只做一个任务点，完成后人工审核再推。

- **T14.1 版本号 bump（P0）**：
  - 依赖：无
  - 操作：`app/build.gradle.kts` `versionCode` 1→2、`versionName` "1.0.0"→"1.3.0"，与 CHANGELOG 1.3.0 对齐
  - 文件：`app/build.gradle.kts`
  - 验收：[x] 版本号对齐 CHANGELOG 1.3.0 · [x] 编译通过
- **T14.2 发布 gate G1~G4：编译 + 单测 + 迁移测试 + 真机回归（P0）**：
  - 依赖：T14.1
  - 操作：① `assembleDebug`（G1）② `testDebugUnitTest` 全绿（G2，基线 100 用例）③ `assembleDebugAndroidTest` 编译通过（G3）④ `adb install -r` 覆盖安装 + 核心链路冒烟（会话列表→记忆→互动→设置→聊天，G4）
  - 验收：[x] G1 BUILD SUCCESSFUL · [x] G2 100 用例全绿 · [x] G3 编译通过 · [x] G4 覆盖安装成功无 FATAL、核心链路可走通
- **T14.3 发布 gate G5：备份留档（P1）**：
  - 依赖：T14.2
  - 操作：`tools/backup_db.ps1` 发布前备份 + magic 校验（db=`SQLite format 3`、wal=`37 7F 06 82`）
  - 验收：[x] 备份文件存在且 magic 校验通过 · [x] 记录备份路径
- **T14.4 稳定版归档：assembleRelease → F:\EdenCourt\releases\1.3.0\（P1）**：
  - 依赖：T14.2（release 签名已就绪，dev-release.jks）
  - 操作：`assembleRelease` 构建 release APK → 复制到 `F:\EdenCourt\releases\1.3.0\app-release.apk` + 写 `README.txt`（版本号 / 日期 / 签名 alias / 变更简介）
  - 验收：[x] assembleRelease BUILD SUCCESSFUL · [x] release APK 归档且 README 标识完整
- **T14.5 发布 gate G6 + 推送：CHANGELOG + tag + PLAN + GitHub（P2）**：
  - 依赖：T14.3、T14.4
  - 操作：① CHANGELOG 补「1.3.0 发布」标注 ② `git tag v1.3.0` ③ PLAN 本阶段勾选 + 「11 当前状态与下一步」同步 ④ 推送 main + tag 到 GitHub
  - 验收：[x] CHANGELOG 标注发布 · [x] tag v1.3.0 推送成功 · [x] PLAN 状态同步 · [x] GitHub origin/main HEAD = 最新提交

> 依赖关系：T14.1 → T14.2 → T14.3 → T14.4 → T14.5。T14.2 内 G1/G2/G3 可先并行后真机 G4。

---

## 9.18 阶段 13（v1.4 TTS 语音回路）：端侧中文 TTS（用户 2026-08-31 选方向，pro 规划）

> ⚠️ 版本重定位（2026-09-04）：TTS 移入 **v3.0「聊」的升级拓展**，2.0 期间本阶段**暂缓**。用户另有想法，2.0 完成后重评估。

> 方案：docs/TTS语音方案-待专家评审.md（历史评审材料，2026-08-31 已标注过时）。**完整开发计划书见 [docs/plan/PLAN-v1.4-TTS语音回路.md](docs/plan/PLAN-v1.4-TTS语音回路.md)（背景/目标/选型/资源/时间/风险/任务拆分）**。本阶段把 ROADMAP 2.3 多模态的「语音出」提前落地，版本定为 **v1.4.0**（群聊雏形顺延为后续版本；ROADMAP 版本映射待本阶段立项后同步）。
> 核心方向（2026-08-31 更新，部分为候选待确认）：MeloTTS 中文主引擎（移除 VITS，Kokoro 中文暂不考虑）；音色=v1.0 预设单音色（音高/语速/情感做差异）+ v1.2 **CosyVoice 2 零样本音色克隆 + 指令式情感/韵律**（不做实时混合滑块）；音质分层=v1.0 端侧自然不机械保底 → v1.2 接近星野；音调=pitch shift(±2半音)+语速(0.8~1.2×)+情感预设模板；Provider 架构（TtsEngine 接口 + 语言路由 + 云端仅留接口）；ASR（sherpa-onnx Whisper）列后续阶段补齐「语音对话闭环」，**不进 v1.0**。
> **T15.1 调研结论（2026-08-31 更新）**：端侧运行时选 **sherpa-onnx**（Android AAR）。双候选调研——MeloTTS 中文（中文强，日语 ONNX 有缺口 + sherpa 官方仅单音色）vs **Kokoro-82M**（中英 103 音色）——**2026-08-31 用户试听倾向：主引擎 MeloTTS 中文，Kokoro 中文暂不考虑（待专家确认）**（停顿卡顿、机械）。音色克隆走 **CosyVoice 2**（零样本克隆 + 指令式情感/韵律，列 v1.2，暂定）；**音质目标分层**：v1.0 端侧自然不机械保底、v1.2 起 CosyVoice 2 冲「接近星野」。
> **硬前置门槛（红线）**：中文 ONNX 在 K60 真机实测（实时率>2× / 内存增量<200MB / 音质自然不机械）**通过才立项 T16 正式开发**；不通过则回溯方案（换转换路径 / 换引擎 / 与用户重议质量标准）。
> 依赖链：T15.1 → T15.2 → T15.3 → T15.4 → T15.5（前置验证，串行）→【审核通过】→ T16.1 → T16.2 → T16.3 → T16.4 → T16.5 → T16.6（正式开发）。一次只做一个任务点，人工审核后再推。

### 前置验证（P0，验证通过才进入开发）

- **T15.1 技术调研：ONNX 转换路径 + 端侧推理运行时 + 引擎选型**（✅ 已完成）：
  - 依赖：无
  - 操作：确认 MeloTTS 中文 ONNX 转换可行（sherpa-onnx 官方 scripts/melo-tts / 社区 season-studio MeloTTS-ONNX 两条路径）；确认端侧推理运行时=sherpa-onnx（Android AAR）；发现 Kokoro-82M 中英 103 音色，双候选试听对比（2026-08-31 试听倾向 MeloTTS 中文）；音色克隆=CosyVoice 2（v1.2，暂定）；音质分层（v1.0 端侧保底 / v1.2 接近星野）
  - 文件：docs/plan/PLAN-v1.4-TTS语音回路.md（计划书 §3 技术选型）
  - 验收：[x] 明确转换路径可执行 · [x] 明确推理运行时与依赖体积 · [x] 明确多音色/三语加载方式（双引擎对比方案）
- **T15.2 准备 MeloTTS 中文 + Kokoro 双引擎 ONNX 模型**（✅ 已完成）：
  - 依赖：T15.1
  - 操作：从 sherpa-onnx 官方 tts-models 下载双引擎并解压至本机资产目录 `F:\EdenCourt\tts-models\`（不入库）；venv 装 sherpa-onnx 1.13.6 做本机 CPU 合成验证
  - 结果：① **MeloTTS** `vits-melo-tts-zh_en` model.onnx 162.5MB@44.1kHz，单音色，中英混合（int8 为官方坏占位 133B，忽略）② **Kokoro** `kokoro-multi-lang-v1_1` model.onnx 310.5MB@24kHz，**103 音色**（中文女 zf 55 / 中文男 zm 45 / 英美女），voices.bin+lexicon 中英
  - 本机合成基线（桌 CPU，4 线程）：MeloTTS「你好，今天天气不错。」dur=1.69s/synth=0.55s **RTF=0.326**；Kokoro sid3 女 dur=2.27s/synth=0.98s **RTF=0.433**、sid58 男 RTF=0.417（均 >2× 目标，真机待测）
  - 样例音频（试听）：`F:\EdenCourt\tts-models\melo_zh.wav` / `kokoro_zh_sid3.wav`（女）/ `kokoro_zh_sid58.wav`（男）
  - 文件：模型资产（不入库，本机 `F:\EdenCourt\tts-models\`）
  - 验收：[x] 双引擎 ONNX 可被运行时加载合成 · [x] Kokoro 多音色枚举（sid3/58 均出音）· [x] 体积记录在案
  - **试听结论（2026-08-31，本机，倾向性）**：用户试听 Kokoro 中文 vs MeloTTS 中文 → **Kokoro 中文暂不考虑**（停顿卡顿、机械）；**主引擎倾向 MeloTTS 中文**。MeloTTS 参数组 P2~P5 均被认可（noise 0.3/0.6、length 0.92/1.08，见 tts-models/melo_p*.wav）；遗留「错」「窗」个别字发音生硬 → 已整理咨询稿问 DeepSeek 专家（docs/plan/发给deepseek-TTS语音方案评审.md §Q3）。
- **T15.3 最小集成验证：端侧加载 + 合成一段中文音频**：
  - 依赖：T15.2
  - 操作：最小工程接线（临时调试入口或单元级验证），加载中文 ONNX 合成一段固定中文文本，产出可播放音频（PCM/WAV），验证端到端链路通
  - 文件：临时验证代码（真机验证用，不污染主 UI 流程）
  - 验收：[ ] 端侧合成出可听中文语音 · [ ] 无崩溃 · [ ] 输出格式可被 Android AudioTrack/ExoPlayer 播放
- **T15.4 真机实测：实时率 / 内存 / 音质（主引擎倾向 MeloTTS 单音色，暂定）**：
  - 依赖：T15.3
  - 操作：K60 真机测 MeloTTS 中文实时率（目标>2×）、内存增量（<200MB）、音质（自然不机械）；用本机已认可的噪声/语速参数组（noise 0.3~0.6、length 0.92~1.08）在真机复核音质
  - 文件：实测记录（写入本阶段验收）
  - 验收：[ ] 实时率>2× · [ ] 内存增量<200MB · [ ] 音质自然不机械（试听通过）
- **T15.5 验证报告 + 用户审核（立项 gate）**：
  - 依赖：T15.4
  - 操作：汇总 T15.1~T15.4 结论成验证报告，交用户审核；附样例音频供试听；明确告知音质分层目标：v1.0 端侧自然不机械（非表演级），v1.2 起 CosyVoice 2 往「接近星野」进阶
  - 验收：[ ] 验证报告完整 · [ ] 用户确认通过（→ 立项 T16）或受阻（→ 回溯方案）

### 正式开发（前置验证通过后立项）

- **T16.1 Provider 架构：TtsEngine 接口 + 语言路由框架**：
  - 依赖：T15.5（通过）
  - 操作：定义 `TtsEngine` 接口（`speakerEmbeddingPath` / `pitchShift` / `speed` / 预留 `referenceAudio`）；语言路由逻辑独立；预留 CloudEngine 扩展点（配 API Key 才可见）
  - 文件：`data/tts/TtsEngine.kt` 等（新增模块）
  - 验收：[ ] 接口编译通过 · [ ] 路由逻辑可切换 · [ ] 单测覆盖路由
- **T16.2 MeloTTS 中文引擎集成**：
  - 依赖：T16.1
  - 操作：实现 MeloEngine（加载中文 ONNX + 音色枚举 + 合成到 PCM）；接入 AudioTrack/ExoPlayer 播放
  - 文件：`data/tts/MeloEngine.kt` 等
  - 验收：[ ] 角色卡选音色合成播放 · [ ] 无崩溃
- **T16.3 语音参数：音色选择 + 音高/语速滑杆 + 情感预设**：
  - 依赖：T16.2
  - 操作：角色卡语音设置（预设音色 / 音高偏移±2半音 / 语速0.8~1.2× / 情感预设模板 平静/开心/严肃/温柔/急切）
  - 文件：角色卡设置 UI + 相关 ViewModel
  - 验收：[ ] UI 可设参数并生效 · [ ] 情感预设一键切换
- **T16.4 文本韵律预处理**：
  - 依赖：T16.2
  - 操作：TTS 前轻度预处理（标点/省略号/强调），不过度插入符号
  - 文件：`data/tts/TextProsodyPreprocessor.kt` + 单测
  - 验收：[ ] 预处理不引入奇怪发音 · [ ] 单测覆盖
- **T16.5 播放/下载可见反馈 + 试听自定义文本**：
  - 依赖：T16.2
  - 操作：语音下载/播放可见反馈（横幅/进度）；TTS 预览支持自定义文本输入
  - 文件：聊天页播放 UI + 预览设置 UI
  - 验收：[ ] 播放有反馈 · [ ] 试听支持自定义文本
- **T16.6 v1.4 发布：编译 + 单测 + 真机回归 + 版本发布（gate）**：
  - 依赖：T16.3、T16.4、T16.5
  - 操作：走版本管理 gate（G1~G6）：版本号 bump v1.4.0、编译、单测、真机覆盖安装回归、备份、CHANGELOG + tag + 推送
  - 验收：[ ] 全部 gate 绿 · [ ] tag v1.4.0 推送成功

> 依赖关系：T15.1 → T15.2 → T15.3 → T15.4 → T15.5【审核】→ T16.1 → T16.2 →（T16.3 ‖ T16.4 ‖ T16.5）→ T16.6。T16.2 为后三者共同底座。

---

