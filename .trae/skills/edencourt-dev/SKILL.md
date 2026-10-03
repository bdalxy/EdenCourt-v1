---
name: "edencourt-dev"
description: "EdenCourt 项目专属开发约束与流程导航：卡住安装发布/schema 迁移等硬约束红线，并指向权威文档。在本项目做开发、构建、真机安装、APK 发布、Room schema 变更、GitHub 推送、UI 检查时调用。Invoke for any EdenCourt dev/build/install/release/push task."
---

# EdenCourt 项目开发约束（项目专属）

> 本 skill 只做两件事：①卡住不可违反的硬约束红线；②指路权威文件（**只引用、不复制**，防内容漂移）。
> 项目：Android 原生 App（Kotlin + Jetpack Compose + Room），仓库根即本项目目录。

## 一、触发场景（命中任一即适用）

- 开发/修改任何代码（app/ 下）
- 构建、编译验证、单元测试
- 真机安装 / 覆盖升级 / 卸载 / 降级
- APK 发布（GitHub Releases）
- Room 数据库 schema 任何变更（实体/字段/索引/迁移）
- 界面 UI 验证、截图检查
- git 提交 / 推送

## 二、硬约束红线（不可违反）

1. **数据永远优先**：任何安装/覆盖/卸载/降级前，必须先跑备份脚本（db+wal+shm 三件套 + 校验 user_version/count），**未备份 = 禁止安装**，不依赖用户提醒。
   命令：`python .trae\scripts\backup_db.py com.bdalxy.edencourt .trae\backups`
2. **能覆盖绝不重装**：同包名同签名一律 `adb install -r -t`；**绝不先卸载**（卸载清数据）。包名同签名异 = 无法覆盖，停下问用户。
3. **永久禁跑 `connectedDebugAndroidTest`**：该通道强制弹窗且会移除旧包、无论同意/取消均丢数据（已实测 3 次）。仪器/迁移测试实跑只放干净模拟器/专用测试机；本机仅允许编译级 `compileDebugAndroidTestKotlin`。
4. **Room schema 变更 7 条强制清单**（缺一不可，详见治理专属模型）：
   ① bump `@Database version`（+1）② 重导 schema json ③ 补 `MIGRATION_{N-1}_{N}` ④ 补迁移测试用例 ⑤ `@ColumnInfo(defaultValue)` 逐列核对真机 `PRAGMA table_info` ⑥ 迁移实跑放干净模拟器 ⑦ 跑 `python tools/check_schema_drift.py`，退出码 0 才放行。
5. **数据库导出禁 PowerShell 重定向**（`>` 会损坏二进制）：用 `adb exec-out` + 脚本落盘，或 `adb pull`。
6. **推送前必须先告知用户「我要推送了，请开梯子」**，用户回「2」或明确同意后才推；代理 `127.0.0.1:7897`，推送优先用 GitHub MCP。**用户没说 = 不推**。
7. **GitHub 入库边界**：`.planning/`、报告、截图、交接文档、`*.db`、`*.log` 不进 GitHub；`git add` 只加具体文件名（禁 `-A` 与 `.`）；禁破坏性 git（stash / checkout -- / clean -f / reset --hard）。
8. **PowerShell 兼容**：不支持 `&&`/`||`（命令链用 `;`）；`>` 重定向会损坏二进制。

## 三、标准流程（从任务到验收）

1. **需求判断**：先查 ROADMAP.md、PLAN.md（及 `.planning\11_2x阶段\需求台账.md`），确认是"新需求"还是"既定任务"；新需求先征询用户后入台账/看板。
2. **复杂度分级** L0~L3：Room schema 迁移 / 引擎核心 / 安全 = 必 L3（先出意图卡交用户拍板 + 隐私命中检查）。本项目无 Python/Chaquopy，纯 Kotlin。
3. **开发**：一次只做一个任务点；只改当前任务相关文件；**全项目中文注释**、对话中文。
4. **交付验证门禁 G1~G6**：G1 编译 `assembleDebug` / G2 单测（用例数只增不减）/ G3 迁移（涉 schema）/ G4 真机（先备份）/ G5 备份校验 / G6 文档（CHANGELOG + PLAN 状态同步）。
5. **用户验收**：提供 **3~5 步人工验证清单**（打开什么 → 点哪 → 应看到什么）+ 合格标准，由用户亲手判定；**未经验证不得称"完成"**。
6. **推送**：按红线 6 的流程执行。

## 四、权威文件导航（只引用，内容以源文件为准）

| 内容 | 路径 |
|---|---|
| 任务看板（需求冻结载体） | `PLAN.md` |
| 版本蓝图 | `ROADMAP.md` |
| 会话衔接 | `.trae\HANDOFF.md` |
| 安装发布约束（7 条铁律全文 + 决策树） | `docs\通用约束模型-Android安装与发布.md` |
| 治理专属模型（门禁 0~4、G1~G6、schema 清单、一页速查卡） | `.planning\05_规划方案\AI协作开发治理·EdenCourt专属模型.md` |
| UI 检查流水线操作手册（唯一入口） | `.planning\05_规划方案\20260906_UI检查流水线_操作手册.md` |
| 版本管理与开发流程 | `docs\版本管理与开发流程方案.md` |
| 2.x 规划（19 包体系 / 冻结稿 / 总览台账） | `.planning\11_2x阶段\` |
| schema 漂移门禁脚本 | `tools\check_schema_drift.py` |
| 数据库备份脚本 | `.trae\scripts\backup_db.py`、`tools\backup_db.ps1` |
| UI 几何检测器 | `tools\detect_geometry.py`（用法：`python tools\detect_geometry.py <dump.xml>`） |
| Releases 发布脚本 | `tools\gh_release.py` |
| adb（不在 PATH） | `F:\Android\SDK\platform-tools\adb.exe` |

## 五、环境与协作约定

- **代理**：Clash Verge Rev，`127.0.0.1:7897`（推拉 GitHub / 下载依赖用）。
- **会话开头声明当前模型**；上下文压缩后记不清须主动询问用户，不得臆测。
- 用户快捷语：「1」= 继续、「2」= 梯子已开可推送、「3」= 你看着办、「4」= 打临时版标签。
- 报告、截图、`.planning` 等本地资料默认不进仓库；本 skill 自身位于 `.trae\skills\`（同样默认本地保留）。