# PLAN v1.2 — 角色间互动 +「互动中心」· 定稿（吸收 DeepSeek 专家评审 + 互动资格开关）

> 里程碑：M8 延伸（ROADMAP 1.2 套话 → 校正升级为「角色间互动」）。资料：本文件已吸收 DeepSeek 网页版专家模式评审（原始见 `docs/plan/发给deepseek-角色互动.md`）及用户新增的「互动资格开关」。
> 性质：把已做 P2「定时自动打听」校正并升级为**角色自发相互交往**，配独立展示「互动中心」，且**由用户掌控哪些会话参与**。
> 待人工审核；确认后拆任务点插入 `PLAN.md` 看板。

***

## 1. 定位一句话

**角色之间会自己往来互动**——但不是不受控地自动发生，而是**由用户提前授权**哪些会话可参与；互动过程可随时在「互动中心」回看。**这一步先把"角色聊天有没有看头"验证通**，再谈自动化。

## 2. 互动资格开关（用户新增，MVP 必做，化解"角色失控"）

- 会话表新增 `interactionEnabled: Boolean`，**新建会话默认** **`false`（默认不参与互动）**。
- 用户可在**会话聊天页**的「互动」入口打开面板，在其中开启「本会话是否开放互动」；互动中心另设全局总开关。
- **只有** **`interactionEnabled=true`** **的会话**才可作为互动发起方(A)或目标方(B)；其余一律不参与。
- 全局另设一个总开关（关闭则所有角色都不互动）。

## 3. 由谁触发（分两版）

### v0 MVP：手动触发（先验证可看性）

- 授权会话内的显式入口：「去找某某会话聊聊」→ 用户选好 A 和 B（B 也须已授权）→ 触发一次互动。
- 不做定时自动；不主动打扰。

### v1（后续）：定时自动（仅对已授权会话）

- 每日固定 1\~2 个窗口（默认晚 20:00 / 凌晨 02:00），每天至多一次，全局可开关。
- 防打扰：**App 前台不触发**；后台触发前检查活跃会话（A/B 正被用户看或近 2 分钟有活动则跳过并 30 分钟后重试）；全局互斥锁（同一时间只一次）；Wi-Fi + 电量>20% 检查，不满足则放弃本次。

## 4. 多轮互动执行（状态机逐轮调 LLM）

```
INITIATING → A_TURN → B_TURN → A_TURN → B_TURN → CLOSING → COMPLETED
```

- `INITIATING`：先让 A 生成「本次想聊的话题种子」，作为整场锚点。
- `A_TURN/B_TURN` 交替调用，每轮仅注入**当前说话者**私密记忆（过滤后最多 5 条最相关）+ 该角色卡共享记忆 + 自己记忆里对对方的认知 + 本次话题种子 + 最近 6 条对话历史；指令：只输出当前说话者下一句台词，不替对方说话，不暴露秘密，超 60 字、无话可说可自然结束。
- ！！！话题深度可有好感度决定？
- `CLOSING`：**硬性 2\~3 轮上限强制收敛**，不靠 LLM 自判结束。temp≈0.7。
- 原则：双方私有记忆互不可见；**唯一共享事实 = 已发生的对话历史**；每轮不要凭空引入新信息。
- 打断保护：若 A/B 正在被用户实时聊天，本次互动见 §3 v1 时机规则。

## 5. 关系起点（最小一期：标签 + 排序优先级，不做数值）

- 新增关系表 `conv_relationship(a_id, b_id, relation_status, affinity_score?, interaction_count, last_interaction_at, note_summary?)`。
- `relation_status` 枚举：`stranger / acquaintance / friendly / close / rift`，初始 `stranger`。
- `affinity_score` **字段先建、默认 0、不参与核心逻辑**（为了以后升级时不用再迁移）。
- 同卡加成：**不解释为"更熟"**，而是「同卡角色基础性格/背景相近 → 更容易有共同话题 → 更易破冰」，标记 `same_card_bonus=true`。
- 选 B 排序：`同卡共同话题加成 > 最近互动时间 > 互动次数`；outcast 前先过滤 `interactionEnabled=true`。

## 6. 落位与展示（互动中心 + 会话摘要）

- **数据模型三层表**：
  - `interaction`（id, initiator\_conv\_id, target\_conv\_id, status\[scheduled/running/completed/skipped], topic\_seed, started\_at, ended\_at, turn\_count, created\_at）
  - `interaction_participant`（id, interaction\_id, conv\_id, role\[initiator/target], summary\[本会话视角摘要], read\_at\[NULL=未读→红点], created\_at）
  - `interaction_turn`（id, interaction\_id, turn\_index, speaker\_conv\_id, content, created\_at）
- **会话内摘要**：在 `message` 表插入一条特殊消息 `type=interaction_digest`（A 视角"我今天去找 B 聊了…"、B 视角"A 今天来找我聊了…"），`metadata` 存 `interaction_id/participant_id`，点击可跳互动中心详情。复用现有未读机制。
- **入口放会话聊天页**：聊天页 top bar 新增「互动」图标（与「记忆」并列），打开互动面板 → 本会话互动记录 + 本会话「开放互动」开关 + 手动触发入口；面板可跳全局互动中心（预览列表→详情）。红点 = 存在 `read_at IS NULL` 的参与者行。空态（无互动/未开放）不给红点。

## 7. 数据库与 Room 迁移（开发时统一协调版本号）

- `conversation` 表加 `interactionEnabled`；`message` 加/识别 `interaction_digest` 类型与 `metadata`。
- 新增 `conv_relationship`、`interaction`、`interaction_participant`、`interaction_turn` 四表。
- 注意与「记忆系统升级」（阶段一，草案用迁移 v12）错开版本号，避免冲突；真机升级验证无崩、旧数据无损。

## 8. 任务点拆分（确认后插入 PLAN.md，按依赖）

- T8.7 互动资格开关（库）：
  - 会话加 `interactionEnabled` 表结构
  - Room 迁移 + 真机升级验证
- T8.8 关系底座 `conv_relationship`：
  - 表结构 + 初始 `stranger` + 同卡共同话题标记 + 排序权重
- T8.9 多轮互动执行器 `InteractionExecutor`：
  - 状态机逐轮、记忆注入、隐私隔离、2\~3 轮硬收敛、话题种子、打断保护（实现 §4）
- T8.10 数据模型与落位：
  - `interaction/participant/turn` 三表 + `interaction_digest` 摘要消息（§6）
- T8.11 互动中心 UI：
  - 聊天页「互动」图标入口（前端）
  - 互动面板（本会话互动记录 + 开放互动开关 + 手动触发）→ 互动中心
  - 互动中心（预览列表、详情、全局总开关）
- T8.12 手动触发入口：
  - 授权会话内「去找某会话聊聊」，选 A/B → 执行
- 〔后续 v1 候选〕定时自动(前台不触发/互斥/Wi-Fi电量/固定窗口)、简单关系标签演进、异卡互动权重
- 〔远期候选〕数值好感度、事件卡、跨用户拜访、群聊同类——验证通过后再加

## 9. 验收标准（真机 + 人工）

① 新建会话默认 `不参与互动`，未授权会话不会被选为 A/B；
② 手动触发 A 找 B，互动中心出现 1 条记录、红点亮起，可看完整 A↔B 多轮对话且双方人设在位、隐私不外泄；
③ 双方会话流里各出现一条 `interaction_digest` 摘要，点击可跳互动中心详情；
④ 查看后红点消除；总开关关闭后没有任何互动发生；App 前台/用户正在聊时不被打断；
⑤ 编译 BUILD SUCCESSFUL；冷启动无崩溃；互动有轮次硬上限无死循环；Room 迁移真机无崩、旧数据无损。
