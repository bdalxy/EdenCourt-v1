# PLAN.md — EdenCourt 执行看板

> 本文件是**唯一的执行看板**（企划书 6.15 执行纪律 1）。日常开发只看它，不频繁改其他文档。
> 每个任务点按「原子任务」粒度拆分：**一次只做一个任务点**，做完→验收→更新状态→再做下一个。
> 这是为了适配后续「flash 版弱模型」逐步开发：任务点够小，单次上下文就能装下，不易出错。

---

## 0. 项目核心决策（已定，勿再改）

| 项 | 结论 | 来源 |
|---|---|---|
| 项目名 | EdenCourt / 永恒庭院 | 企划书 1.1 |
| 包名 applicationId | `com.bdalxy.edencourt` | 用户 2026-08-23 拍板 |
| 架构 | 完全重写，**纯 Kotlin 全栈**，不引入 Python/Chaquopy | 企划书 3.2.5 |
| UI 框架 | Jetpack Compose + Material 3 | 企划书 3.3.1 |
| 数据 | Room（会话/消息/角色卡/记忆）+ DataStore（设置） | 企划书 3.3.2 |
| 网络 | OkHttp + Retrofit（REST）、OkHttp SSE（流式）、kotlinx.serialization（JSON） | 企划书 3.3.3 |
| minSdk | 29（Android 10） | 企划书 11.7 |
| compileSdk / targetSdk | 36 / 36（本机仅装 android-36） | 与 v0.5 一致 |
| 主色（临时） | 樱羽淡粉蓝 `#A8C6F5` + 点缀 `#FFC9D4` | 企划书 5.1.2 |
| API 服务商 | 先 DeepSeek 官网 API（OpenAI 兼容） | 企划书 3.6.1 |
| 导航 | 单 Activity + Compose Navigation | 企划书 5.2.2 |
| 版本组合 | AGP 8.7.0 / Kotlin 2.0.20 / Gradle 8.9（本机已缓存）/ Compose BOM 2024.10.01 | 与 v0.5 旧项目完全一致，本机已验证可编译 |

### 目录结构约定（从根开始）

```
app/src/main/
  java/com/bdalxy/edencourt/
    EdenCourtApp.kt              # Application 入口
    MainActivity.kt              # 单 Activity 宿主
    core/                        # 全局工具/常量（主题、AppConfig）
      ui/theme/                  # Theme.kt / Color.kt / Type.kt
      AppConfig.kt               # 集中常量（包名、默认参数）
    data/                        # 数据层
      local/                     # Room：entity / dao / AppDatabase
      repository/                # Repository（屏蔽数据来源）
      setting/                   # DataStore
    domain/                      # 业务模型 / 领域（可选，按需）
    feature/                     # 按功能分包
      conversationlist/          # 会话列表
      chat/                      # 聊天页
      character/                 # 角色卡
      memory/                    # 记忆
      settings/                  # 设置
      about/                     # 关于
    navigation/                  # 路由定义 + NavHost
  res/                           # 资源（string/color/theme）
```

> 约定：**文件超过 500 行必须拆分**；**每个功能闭环（引擎+入口+UI+测试）才标记完成**（企划书 3.2.1）。

---

## 1. 任务点格式说明

每个任务点固定包含五要素，flash 模型**只读它要做的那个任务点**即可：

```
### T{x.y} {任务名}
- 依赖：T{a.b}（或「无」）
- 参考：企划书 X.X 段落
- 操作：一句话说清要做什么（不展开，细节已经写死在参考文档里）
- 文件：本任务将创建/修改的文件清单（绝对路径）
- 验收：[ ] 可勾选的一条条可验证标准
```

- 状态图标：`[ ]` 未开始 · `[~]` 进行中 · `[x]` 已完成 ✅
- **依赖**：有依赖的必须先完成依赖项。无依赖的可以并行。

---

## 2. 任务总览与依赖关系

```
阶段0（规划/环境）        T0.0（人工装环境）──┐
                                            ↓
阶段1（骨架）  T1.1→T1.2→T1.3→T1.4→T1.5→T1.6→T1.7→T1.8（可编译运行）
                                            ↓
阶段2（聊天+存储）  T2.1→T2.2→T2.3→T2.4→T2.5→T2.6
                        →T2.7→T2.8→T2.9→T2.10→T2.11（本地聊天可用）
                                            ↓
阶段3（接API） T3.1→T3.2→T3.3→T3.4→T3.5→T3.6→T3.7→T3.8→T3.9（能流式收到回复）
                                            ↓
阶段4（角色卡） T4.1→T4.2→T4.3→T4.4→T4.5→T4.6→T4.7（导入角色卡生效）
                                            ↓
阶段5（记忆）   T5.1→T5.2→T5.3→T5.4→T5.5→T5.6（跨会话记住信息）
                                            ↓
阶段6（打磨）   T6.1→T6.2→T6.3→T6.4→T6.5（MVP 可试用）
```

> 里程碑：M1=阶段1完成 · M2=阶段2 · M3=阶段3 · M4=阶段4 · M5=阶段5 · M6=阶段6（对应企划书 6.14）。

---

## 3. 阶段 0：规划与环境

### T0.0 确认开发环境（已确认，见开发准备手册「四、本机已装环境路径清单」）
- 依赖：无
- 参考：开发准备手册「四、本机已装环境路径清单（2026-08-23 实测）」
- 操作：环境已装齐（Android Studio `F:\Android\Android Studio`、JDK 17 `F:\Java\jdk-17`、SDK `F:\Android\SDK`（android-36）、gradle-8.9 已缓存）；已创建 `local.properties`（sdk.dir）、复制 gradlew wrapper
- 文件：`local.properties` · `gradle/wrapper/gradle-wrapper.jar` · `gradlew.bat` · `gradlew`
- 验收：[x] JDK 17 可用 · [x] SDK android-36 可用 · [x] gradle-8.9 wrapper 已就位 · [ ] 真机开启 USB 调试可被 AS 识别

---

## 4. 阶段 1：安卓项目骨架（M1）

> 目标：一个可安装、可启动、能切换 6 个空页面的空应用。

### T1.1 创建 Gradle 项目基础结构
- 依赖：无（T0.0 不影响写文件）
- 参考：企划书 6.3.2 任务 1.1
- 操作：创建 `settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`、版本目录 `gradle/libs.versions.toml`
- 文件：`settings.gradle.kts` · `build.gradle.kts` · `gradle.properties` · `gradle/libs.versions.toml`
- 验收：[ ] 包名 `com.bdalxy.edencourt` 写入 · [ ] 使用 Kotlin DSL · [ ] 版本目录集中管理依赖版本

### T1.2 配置依赖（骨架阶段只装最小集，其余按阶段增量加）
- 依赖：T1.1
- 参考：企划书 3.3.1、6.3.2 任务 1.2
- 操作：①在 `gradle/libs.versions.toml` 集中声明**全部**阶段要用的依赖版本（含 Room/DataStore/Retrofit/OkHttp/Serialization/Coil/KSP，仅声明不引用）；②`app/build.gradle.kts` 骨架阶段**只引用最小集**：compose BOM、material3、activity-compose、core-ktx、lifecycle（runtime/viewmodel-compose）、navigation-compose
- 文件：`gradle/libs.versions.toml` · `app/build.gradle.kts`
- 验收：[ ] 版本号全部集中在版本目录，无散落 · [ ] compileSdk 35 / minSdk 29 / targetSdk 35 · [ ] enableCompose 插件（org.jetbrains.kotlin.plugin.compose）· [ ] 骨架阶段**不**引用 Room/网络等（留待 T2.1~T2.5、T3.x 按需引入，避免过早引入注解处理器导致编译失败）

> 约定：**依赖在真正用到的阶段再往 `app/build.gradle.kts` 里加**（版本号已在目录里，只加一行引用）。这样每个阶段的编译风险最小，flash 模型一次只动一处。

### T1.3 创建 Application 与 MainActivity
- 依赖：T1.2
- 参考：企划书 5.2.2「单 Activity + NavHost」
- 操作：创建 `EdenCourtApp.kt`（继承 Application）与 `MainActivity.kt`（setContent 调 AppRoot）
- 文件：`EdenCourtApp.kt` · `MainActivity.kt`
- 验收：[ ] Manifest 中 `android:name` 指向 EdenCourtApp · [ ] MainActivity 只做 setContent，逻辑不堆积在此

### T1.4 定义樱羽主题（Theme/Color/Type）
- 依赖：T1.3
- 参考：企划书 5.1.2 视觉风格
- 操作：创建 `Color.kt`（主色 `#A8C6F5`、点缀 `#FFC9D4`、浅/深背景与文字色）、`Theme.kt`（浅/深色跟随系统）、`Type.kt`
- 文件：`core/ui/theme/Color.kt` · `Theme.kt` · `Type.kt`
- 验收：[ ] 主色用临时方案樱羽淡粉蓝 · [ ] 支持动态跟随系统深浅色 · [ ] 浅色主题占比视觉上 ≥75%（后续 UI 遵循）

### T1.5 定义导航路由与 NavHost
- 依赖：T1.4
- 参考：企划书 5.2.2 路由表
- 操作：创建路由常量（`conversation_list`/`chat/{conversationId}`/`character_cards`/`memory_list`/`settings`/`about`）、`AppNavHost.kt`
- 文件：`navigation/NavRoutes.kt`（或放入 AppConfig） · `navigation/AppNavHost.kt`
- 验收：[ ] 6 条路由定义齐全 · [ ] chat 路由带参数 conversationId · [ ] 默认起点 conversation_list

### T1.6 创建 6 个空页面占位 Composable
- 依赖：T1.5
- 参考：企划书 5.3.1~5.3.6 页面清单
- 操作：每个功能包建一个占位 Screen（居中显示页名即可），接到 NavHost
- 文件：`feature/conversationlist/ConversationListScreen.kt` · `feature/chat/ChatScreen.kt` · `feature/character/CharacterScreen.kt` · `feature/memory/MemoryScreen.kt` · `feature/settings/SettingsScreen.kt` · `feature/about/AboutScreen.kt`
- 验收：[ ] 6 个页面均能通过导航切换 · [ ] 每页有可辨识标题 · [ ] 无编译错误

### T1.7 配置 AndroidManifest
- 依赖：T1.3
- 参考：企划书 3.10.3（初期仅 INTERNET 权限）
- 操作：声明 Application、MainActivity、`INTERNET` 权限；`android:label` 用「永恒庭院」
- 文件：`app/src/main/AndroidManifest.xml`
- 验收：[ ] 仅声明 INTERNET 权限 · [ ] launcher activity 是 MainActivity · [ ] 应用名显示「永恒庭院」

### T1.8 编译并安装到真机/模拟器
- 依赖：T1.7、T0.0（环境已确认）
- 参考：开发准备手册「四、环境配置要点」
- 操作：`.\gradlew assembleDebug` 编译（已执行）→ 用 Android Studio 安装到红米 K60 / 荣耀 Win RT（真机 USB 调试）
- 文件：无（验证步骤）
- 验收：[x] `BUILD SUCCESSFUL`（2026-08-23 实测 2m3s，35 任务 0 错误）· [x] debug APK 已生成（app-debug.apk 24.06MB）· [x] APK 安装成功（adb install Success，真机 K60）· [x] 启动无崩溃（PID 存活，无 FATAL）· [x] 可切换 6 个页面（uiautomator 逐页验证 + 截图见 `.trae/screenshots/`）

> **阶段 1 人工审核门槛**：编译通过证据 + 真机上 6 页切换截图/录像，交用户确认后再进阶段 2。

---

## 5. 阶段 2：聊天界面 + 本地存储（M2）

> 目标：类微信聊天 UI + 消息本地保存（此时还不接 AI）。

### T2.1 Room 实体 Conversation
- 依赖：T1.8
- 参考：企划书 4.1.1 会话表
- 操作：创建 `Conversation` 实体（id 自增、title、characterCardId 可空、createdAt/updatedAt、isPinned、lastMessagePreview、lastMessageTime）
- 文件：`data/local/entity/Conversation.kt`
- 验收：[x] 字段与 4.1.1 一致（id/title/characterCardId/createdAt/updatedAt/isPinned/lastMessagePreview/lastMessageTime）· [x] `@Entity` + 主键自增 · [x] updatedAt 降序索引 · [x] 编译通过（BUILD SUCCESSFUL 1m30s，引入 KSP+Room 依赖）

### T2.2 Room 实体 Message
- 依赖：T2.1
- 参考：企划书 4.1.2 消息表
- 操作：创建 `Message` 实体（conversationId、role、content、createdAt、status、tokenCount、modelUsed）
- 文件：`data/local/entity/Message.kt`
- 验收：[x] 外键 conversationId ON DELETE CASCADE · [x] 复合索引 conversationId+createdAt · [x] 角色/状态枚举（Role/MessageStatus）随实体定义 · [x] 编译通过（BUILD SUCCESSFUL 17s）

### T2.3 DAO（ConversationDao + MessageDao）
- 依赖：T2.1、T2.2
- 参考：企划书 4.1 索引说明
- 操作：写查询/插入/更新/删除接口，会话按 updatedAt 降序，消息按 createdAt 正序
- 文件：`data/local/dao/ConversationDao.kt` · `data/local/dao/MessageDao.kt`
- 验收：[x] 增删改查 + Flow 响应式查询齐备（observeAll/observeById/observeByConversation）· [x] 编译通过（BUILD SUCCESSFUL 17s，KSP 校验 SQL 通过）

### T2.4 AppDatabase
- 依赖：T2.3
- 参考：企划书 3.3.2
- 操作：创建 Room 数据库单例，挂载两个 DAO
- 文件：`data/local/AppDatabase.kt`
- 验收：[x] 单例获取（双重检查锁）· [x] entity/dao 全部注册（Conversation+Message / ConversationDao+MessageDao）· [x] schema 导出已配置（app/schemas/1.json，迁移基础）· [x] 编译通过（BUILD SUCCESSFUL 28s）

### T2.5 ChatRepository
- 依赖：T2.4
- 参考：企划书 3.2.3 数据流
- 操作：封装 DAO 调用，对上层暴露 Flow / suspend 方法
- 文件：`data/repository/ChatRepository.kt`
- 验收：[x] UI 不直接碰 DAO，统一经 Repository（会话/消息全部走 ChatRepository）· [x] 编译通过（BUILD SUCCESSFUL 17s）

### T2.6 会话列表页 UI
- 依赖：T2.5
- 参考：企划书 5.3.1、5.8.1 原型
- 操作：LazyColumn 按 updatedAt 降序；头像+标题+预览+时间；FAB 新建；长按删除（二次确认）
- 文件：`feature/conversationlist/ConversationListScreen.kt` + `ConversationListViewModel.kt`
- 验收：[ ] 列表展示会话 · [ ] 可新建（暂默认会话）· [ ] 长按删除有二次确认

### T2.7 聊天页 UI（气泡）
- 依赖：T2.6
- 参考：企划书 5.3.2、5.8.2 原型
- 操作：接收 conversationId；用户右气泡/AI 左气泡；LazyColumn 自动滚底；时间戳
- 文件：`feature/chat/ChatScreen.kt`
- 验收：[ ] 消息按时间正序 · [ ] 气泡样式区分用户/AI · [ ] 发送后自动滚到底部

### T2.8 输入框 + 发送（存库）
- 依赖：T2.7
- 参考：企划书 5.4.3
- 操作：多行输入框（1~5 行自动扩展）+ 发送按钮（有内容才高亮）；发送后写入 Message 并清空输入
- 文件：`feature/chat/ChatScreen.kt` + `feature/chat/ChatViewModel.kt`
- 验收：[ ] 有内容发送按钮才可点 · [ ] 发送后消息入库 · [ ] 输入框清空

### T2.9 消息状态枚举
- 依赖：T2.8
- 参考：企划书 4.1.2 status 字段
- 操作：定义 `sending/sent/failed/generating/done` 枚举；本阶段发送的直接置 `sent`
- 文件：`data/local/entity/Message.kt`（或 domain 枚举）
- 验收：[ ] 枚举齐全 · [ ] 本阶段消息状态为 sent

### T2.10 删除会话级联删消息
- 依赖：T2.6
- 参考：企划书 4.1.2 外键级联
- 操作：确认删除会话时，消息按外键级联自动清除；重命名/置顶备用
- 文件：`data/repository/ChatRepository.kt`
- 验收：[ ] 删会话后对应消息同步消失

### T2.11 存储验证
- 依赖：T2.10
- 参考：企划书 6.4.3
- 操作：真机：发消息→杀进程→重开→记录仍在；删会话→重开→消息不残留
- 文件：无（验证步骤）
- 验收：[ ] 退出重开记录保留 · [ ] 删除会话级联生效

> **阶段 2 人工审核门槛**：类微信手感 + 存储可靠（重开不丢），交用户确认后再进阶段 3。

---

## 6. 阶段 3：接入远程 AI API（M3）

> 目标：能流式收到 DeepSeek 回复（此时角色卡/记忆为最简占位）。

### T3.1 API 配置数据模型 + DataStore
- 依赖：T2.11
- 参考：企划书 3.6.1
- 操作：定义 ApiConfig（baseUrl/key/model/temperature/maxTokens/topP），DataStore 保存
- 文件：`data/setting/ApiConfigStore.kt`（或 SettingsRepository）
- 验收：[ ] Key 存 DataStore 不落明文日志 · [ ] 默认 DeepSeek baseUrl 与 model 可配

### T3.2 请求/响应 DTO（kotlinx.serialization）
- 依赖：T3.1
- 参考：企划书 3.6.2
- 操作：定义 ChatCompletion 请求/响应 DTO 与 `@Serializable` 数据类
- 文件：`data/remote/ApiModels.kt`
- 验收：[ ] 字段与 OpenAI 兼容格式一致 · [ ] stream 字段支持

### T3.3 设置页（API 配置 UI）
- 依赖：T3.2
- 参考：企划书 5.3.5 设置页第 1 区
- 操作：Base URL/Key（可切明文）/模型名/温度/max_tokens 输入项 + 即时保存 + 测试连接按钮
- 文件：`feature/settings/SettingsScreen.kt`
- 验收：[ ] 输入即时写入 DataStore · [ ] Key 默认密码框可切明文

### T3.4 SSE 流式解析器
- 依赖：T3.2
- 参考：企划书 3.6.3
- 操作：用 OkHttp 发 `Accept: text/event-stream`，逐行解析 `data:`，取 `choices[0].delta.content`，识别 `[DONE]`
- 文件：`data/remote/SseStreamParser.kt`（或并入 service）
- 验收：[ ] 能解析增量 content · [ ] 识别结束标志 · [ ] 不阻塞主线程

### T3.5 AiChatService 接口 + RemoteApiChatService 实现
- 依赖：T3.4
- 参考：企划书 3.2.2
- 操作：定义 `streamChat(messages): Flow<String>` 接口，用 OkHttp 实现远程流式实现
- 文件：`data/remote/AiChatService.kt` · `data/remote/RemoteApiChatService.kt`
- 验收：[ ] 接口化（后期可换本地模型）· [ ] 流式 Flow 逐段吐出

### T3.6 Prompt 构建（角色卡+记忆注入占位）
- 依赖：T3.5
- 参考：企划书 3.4.2、3.5.1、3.6.2
- 操作：组装 messages=[system(角色卡+记忆占位), 最近 N 条, user…]；本阶段角色卡/记忆用最简占位
- 文件：`domain/PromptBuilder.kt`（或 feature/chat 内）
- 验收：[ ] system + 历史 + user 结构正确 · [ ] 短期记忆条数可配置

### T3.7 ChatViewModel（流式状态管理）
- 依赖：T3.6
- 参考：企划书 3.2.3 对话流程
- 操作：管理 sending/generating/done 状态、Flow 收集逐字更新 AI 气泡、可停止生成
- 文件：`feature/chat/ChatViewModel.kt`
- 验收：[ ] 发送→生成中→逐字显示→done · [ ] 可中断 · [ ] 旋转屏幕不丢状态

### T3.8 错误处理 + 重试
- 依赖：T3.7
- 参考：企划书 3.6.4
- 操作：网络不可用/Key 无效/超时/429/异常/空输出 分支处理，失败消息标 failed 可重试
- 文件：`feature/chat/ChatViewModel.kt` · `data/remote/RemoteApiChatService.kt`
- 验收：[ ] 各错误有明确提示 · [ ] 失败气泡可重试 · [ ] 空输出兜底

### T3.9 测试连接按钮
- 依赖：T3.3、T3.5
- 参考：企划书 5.3.5
- 操作：设置页「测试连接」发最小 `stream=false` 请求，返回成功/失败
- 文件：`feature/settings/SettingsScreen.kt`
- 验收：[ ] 成功/失败反馈清晰

> **阶段 3 人工审核门槛**：真机填写 DeepSeek Key → 流式收到回复 → 错误分支（断网/错 Key）提示正确。

---

## 7. 阶段 4：角色卡系统（M4）

> 目标：导入/内置角色卡并真正注入 system prompt 生效。

### T4.1 CharacterCard 数据类 + JSON Schema
- 依赖：T3.9
- 参考：企划书 3.4.1
- 操作：定义 `CharacterCard`（name/personality/background/speaking_style/example_dialogue/constraints/tags）
- 文件：`domain/CharacterCard.kt`
- 验收：[ ] 字段与 3.4.1 JSON 一致 · [ ] name/personality 必填校验

### T4.2 角色卡 Room 实体 + DAO
- 依赖：T4.1
- 参考：企划书 4.1.3
- 操作：建 CharacterCard 实体（id/name/description/jsonContent/isBuiltIn/createdAt/updatedAt）+ DAO
- 文件：`data/local/entity/CharacterCardEntity.kt` · `data/local/dao/CharacterCardDao.kt`
- 验收：[ ] 与 4.1.3 字段一致 · [ ] 增删改查齐备

### T4.3 角色卡管理页 UI
- 依赖：T4.2
- 参考：企划书 5.3.3、5.8.3
- 操作：列表（头像/名字/描述/内置或导入标）+ FAB 导入 + 详情页（可编辑名称描述、JSON 只读）+ 长按删除（内置不可删）
- 文件：`feature/character/CharacterScreen.kt` + `CharacterViewModel.kt`
- 验收：[ ] 列表/导入/详情/删除齐备 · [ ] 内置卡不可删

### T4.4 内置角色卡（2~3 个）
- 依赖：T4.3
- 参考：企划书 11.4「先内置 2~3 个调试过的示例」
- 操作：内置温柔学姐、毒舌助手等 2~3 个 JSON 角色卡（`isBuiltIn=true`）
- 文件：`feature/character/`（JSON 资源或硬编码常量）
- 验收：[ ] 2~3 个内置卡可选中用于对话

### T4.5 JSON 导入（含 Tavern 兼容映射）
- 依赖：T4.4
- 参考：企划书 3.4.1 兼容层
- 操作：导入 JSON 解析为 CharacterCard；Tavern CCv3 字段映射（description→background、first_mes→开场白、mes_example→example_dialogue）；导入失败提示「JSON 格式错误」
- 文件：`feature/character/` + `domain/CharacterCardParser.kt`
- 验收：[ ] 标准 JSON 可导入 · [ ] Tavern 字段正确映射 · [ ] 非法 JSON 有明确报错

### T4.6 system prompt 构建接入真实角色卡
- 依赖：T4.5、T3.6
- 参考：企划书 3.4.2
- 操作：把 T3.6 的占位替换为真实角色卡 → 转 system prompt（「你是{name}…请始终以该角色身份回复」）
- 文件：`domain/PromptBuilder.kt`
- 验收：[ ] 选不同卡 → 回复风格明显不同

### T4.7 新建会话选角色卡
- 依赖：T4.6
- 参考：企划书 5.4.1、2.2.2 绑定规则
- 操作：FAB 新建 → 角色卡底部弹窗选卡（或「默认助手」）→ 建会话绑 characterCardId → 跳聊天
- 文件：`feature/conversationlist/` + `feature/chat/`
- 验收：[ ] 选卡即绑定 · [ ] 同一卡再新建是独立对话 · [ ] 不同卡上下文互不串

> **阶段 4 人工审核门槛**：导入 + 内置卡均能改变 AI 说话风格。

---

## 8. 阶段 5：记忆系统（M5）

> 目标：跨会话记住关键信息并主动引用。

### T5.1 MemoryItem Room 实体 + DAO
- 依赖：T4.7
- 参考：企划书 4.1.4
- 操作：建 MemoryItem（type/content/keywords/confidence/sourceConversationId/sourceMessageId/createdAt/updatedAt/isDeleted）+ DAO（含关键词检索）
- 文件：`data/local/entity/MemoryItem.kt` · `data/local/dao/MemoryDao.kt`
- 验收：[ ] 字段一致 · [ ] type/keywords/isDeleted 索引 · [ ] 关键词匹配检索可用

### T5.2 短期记忆（最近 N 条）
- 依赖：T5.1
- 参考：企划书 3.5.1
- 操作：对话时取该会话最近 N=20 条消息注入（system 之后、用户消息之前）
- 文件：`domain/PromptBuilder.kt`
- 验收：[ ] N 可配置 · [ ] 注入位置正确

### T5.3 长期记忆提取（调 API 异步）
- 依赖：T5.2
- 参考：企划书 3.5.2
- 操作：每轮回复后异步调 API 提取结构化条目（类型/内容/置信度），存前查冲突；高置信度才入库
- 文件：`data/repository/MemoryRepository.kt` + 提取逻辑
- 验收：[ ] 异步不阻塞对话 · [ ] 提取结果结构化入库 · [ ] 冲突更新/标记

### T5.4 记忆检索注入
- 依赖：T5.3
- 参考：企划书 3.5.2 检索注入
- 操作：生成前按关键词检索 top-K=3，拼成「[关于用户的长期记忆]- …」注入
- 文件：`domain/PromptBuilder.kt` + `data/repository/MemoryRepository.kt`
- 验收：[ ] 相关记忆被注入 · [ ] 注入条数可配置

### T5.5 记忆管理页 UI
- 依赖：T5.3
- 参考：企划书 5.3.4
- 操作：列表（类型标签+内容+时间）+ 搜索 + 左滑/长按删除（二次确认）+ 手动添加
- 文件：`feature/memory/MemoryScreen.kt` + `MemoryViewModel.kt`
- 验收：[ ] 查看/删除/手动添加可用 · [ ] 空状态提示「暂无记忆…」

### T5.6 跨会话记忆验证
- 依赖：T5.4、T5.5
- 参考：企划书 1.8 成功标准「跨会话记忆」
- 操作：真机：会话 A 告诉 AI 名字→新开会话 B→AI 能提及该名字
- 文件：无（验证步骤）
- 验收：[ ] 重启后仍记住 ≥1 关键信息

> **阶段 5 人工审核门槛**：跨会话 + 重启后记忆不丢，且错误记忆可手动删除。

---

## 9. 阶段 6：MVP 打磨与试用（M6）

### T6.1 空状态 / 错误提示完善
- 依赖：T5.6
- 参考：企划书 5.5
- 操作：补齐三无状态文案 + 各错误提示（企划书 5.5.3），提示可关闭且不丢输入
- 文件：各 feature UI
- 验收：[ ] 各空状态/错误提示与企划书一致

### T6.2 深色模式适配
- 依赖：T6.1
- 参考：企划书 5.1.2、5.7.2
- 操作：颜色全部走主题资源，深浅色切换对比度合格
- 文件：core/ui/theme + 各 feature
- 验收：[ ] 深色下对比度达标 · [ ] 无硬编码颜色

### T6.3 性能（冷启动/内存）
- 依赖：T6.2
- 参考：企划书 2.5.1
- 操作：冷启动 ≤3s、无本地模型后台内存 ≤200MB；检查 Bitmap/资源释放
- 文件：无（优化验证）
- 验收：[ ] 冷启动 ≤3s · [ ] 内存达标 · [ ] 30 分钟 0 崩溃

### T6.4 朋友试用 + 修复
- 依赖：T6.3
- 参考：企划书 2.8、11.2
- 操作：邀请 1~2 位朋友试用，按 P0~P3 回填缺陷清单并修复
- 文件：无（流程）
- 验收：[ ] 反馈按优先级归类 · [ ] 致命 Bug 清零

### T6.5 README + 发布
- 依赖：T6.4
- 参考：企划书 6.14 M6、11.8
- 操作：写 README（简介/功能/安装/开发指南）、CHANGELOG、GitHub Release v1.0（MVP）
- 文件：`README.md` · `CHANGELOG.md`
- 验收：[ ] README 完整 · [ ] 标注「测试产品，不承诺 AI 回复准确性」

---

## 10. 远期决策项（不阻塞 MVP，记录待定）

| 决策项 | 现状 | 何时定 |
|---|---|---|
| 手机操作方式 | 无障碍 / ADB 无线调试 待选 | MVP 稳定 ≥1 个月后（企划书 11.6） |
| 本地模型基座 | Qwen2.5-1.5B（再决定） | 启动阶段 7 前 |
| 是否引入 Python/Chaquopy | 默认纯 Kotlin，不引入 | 阶段 0 已定不引入，除非关键能力 Kotlin 无法满足 |
| 主色最终值 | 临时樱羽淡粉蓝，后续可调 | UI 评估后 |

---

## 11. 当前状态与下一步

- 当前进度：**阶段 2 进行中：T2.1~T2.5（实体/DAO/AppDatabase/ChatRepository）已完成 ✅**。下一步 T2.6（会话列表页 UI）。
- 更新记录：
  - 2026-08-23 创建 PLAN.md；确认包名 `com.bdalxy.edencourt`。
  - 2026-08-23 依据开发准备手册「四、本机已装环境路径清单」修正：环境实际已装齐（AS/SDK/JDK17），版本组合对齐 v0.5 已验证的 AGP 8.7.0 / Kotlin 2.0.20 / Gradle 8.9 / compileSdk 36；创建 local.properties、复制 gradlew wrapper；首次编译 `BUILD SUCCESSFUL`（2m3s）。
  - 2026-08-23 真机验证（K60，USB 调试）：安装 Success、启动无崩溃、6 页全通过。**阶段 1 / M1 完成 ✅**。
  - 2026-08-23 flash 模型接手，执行阶段 2：T2.1 Conversation 实体完成（Room/KSP 引入，编译通过）；推送 GitHub（EdenCourt-v1 私有仓库）。