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
阶段5.5（真人式交互）T5.7→T5.8→T5.9→T5.10（类微信：连发/动作神态/输入中/可打断）
                                            ↓
阶段6.5（真人化体验） T6.6→T6.7→T6.8→T6.9 + T6.10→T6.11（类微信体验打磨）
                                            ↓
阶段6.6（用户反馈修复+记忆隔离） T6.12→T6.13→T6.14（+T6.15 独立）（个人体验反馈修复+记忆按角色隔离）
                                            ↓
阶段6（发布）   T6.4（朋友试用）→T6.5（README+Release v1.0）（MVP 正式可用）
```

> 里程碑：M1=阶段1完成 · M2=阶段2 · M3=阶段3 · M4=阶段4 · M5=阶段5 · M5.5=真人式交互 · M6=阶段6（对应企划书 6.14）。

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
- 验收：[x] 列表展示会话（头像「新」+标题「新对话」+预览「暂无消息」+时间，真机实测）· [x] 可新建（FAB 创建会话并跳转聊天页）· [x] 长按删除有二次确认（对话框实测→确认后删除回空态）· [x] 编译+真机通过（BUILD SUCCESSFUL；K60 无崩溃）

### T2.7 聊天页 UI（气泡）
- 依赖：T2.6
- 参考：企划书 5.3.2、5.8.2 原型
- 操作：接收 conversationId；用户右气泡/AI 左气泡；LazyColumn 自动滚底；时间戳
- 文件：`feature/chat/ChatScreen.kt`
- 验收：[x] 消息按时间正序（SQL ORDER BY createdAt ASC）· [x] 气泡样式区分用户/AI（AI 左+用户右气泡真机实测）· [x] 发送后自动滚到底部（实测）· [x] 编译+真机通过（BUILD SUCCESSFUL；K60 无崩溃）

### T2.8 输入框 + 发送（存库）
- 依赖：T2.7
- 参考：企划书 5.4.3
- 操作：多行输入框（1~5 行自动扩展）+ 发送按钮（有内容才高亮）；发送后写入 Message 并清空输入
- 文件：`feature/chat/ChatScreen.kt` + `feature/chat/ChatViewModel.kt`
- 验收：[x] 有内容发送按钮才可点（空内容按钮 disabled 实测）· [x] 发送后消息入库（用户消息「hello」显示+会话标题/预览/时间自动更新，列表页实测）· [x] 输入框清空（发送后恢复 placeholder 实测）· [x] 编译+真机通过

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
- 验收：[x] 退出重开记录保留（K60 实测：杀进程重开会话+消息均在）· [x] 删除会话级联生效（删会话→杀进程重开→空状态无残留）

> **阶段 2 人工审核门槛**：类微信手感 + 存储可靠（重开不丢）——**已通过（K60 真机实测）**，交用户确认后进入阶段 3。

---

## 6. 阶段 3：接入远程 AI API（M3）

> 目标：能流式收到 DeepSeek 回复（此时角色卡/记忆为最简占位）。

### T3.1 API 配置数据模型 + DataStore
- 依赖：T2.11
- 参考：企划书 3.6.1
- 操作：定义 ApiConfig（baseUrl/key/model/temperature/maxTokens/topP），DataStore 保存
- 文件：`data/setting/ApiConfigStore.kt`（或 SettingsRepository）
- 验收：[x] Key 存 DataStore 不落明文日志（ApiConfigStore 仅 DataStore 持久化，无明文日志）· [x] 默认 DeepSeek baseUrl 与 model 可配（DEFAULT_BASE_URL/DEFAULT_MODEL + update() 可改，已挂载 EdenCourtApp）· [x] 编译通过（BUILD SUCCESSFUL 46s）

### T3.2 请求/响应 DTO（kotlinx.serialization）
- 依赖：T3.1
- 参考：企划书 3.6.2
- 操作：定义 ChatCompletion 请求/响应 DTO 与 `@Serializable` 数据类
- 文件：`data/remote/ApiModels.kt`
- 验收：[x] 字段与 OpenAI 兼容格式一致（ChatMessage/ChatCompletionRequest/Response/Chunk 全 @Serializable）· [x] stream 字段支持（请求体 stream 开关，流式 chunk 与增量 delta）· [x] 编译通过（BUILD SUCCESSFUL 55s，启用 kotlinx-serialization 插件+依赖）

### T3.3 设置页（API 配置 UI）
- 依赖：T3.2
- 参考：企划书 5.3.5 设置页第 1 区
- 操作：Base URL/Key（可切明文）/模型名/温度/max_tokens 输入项 + 即时保存 + 测试连接按钮
- 文件：`feature/settings/SettingsScreen.kt`
- 验收：[x] 输入即时写入 DataStore（各输入 onValueChange 即写，SettingsViewModel 封装 update）· [x] Key 默认密码框可切明文（PasswordVisualTransformation + 显示/隐藏切换）· [x] 编译通过（BUILD SUCCESSFUL 21s；测试连接按钮留待 T3.9）

### T3.4 SSE 流式解析器
- 依赖：T3.2
- 参考：企划书 3.6.3
- 操作：用 OkHttp 发 `Accept: text/event-stream`，逐行解析 `data:`，取 `choices[0].delta.content`，识别 `[DONE]`
- 文件：`data/remote/SseStreamParser.kt`（或并入 service）
- 验收：[x] 能解析增量 content（parseDataContent 返回 deltaContent，JSON 失败容错）· [x] 识别结束标志（isDone/DONE_MARKER）· [x] 不阻塞主线程（纯函数无 IO，OkHttp 依赖已引入）· [x] 编译通过（BUILD SUCCESSFUL 39s）

### T3.5 AiChatService 接口 + RemoteApiChatService 实现
- 依赖：T3.4
- 参考：企划书 3.2.2
- 操作：定义 `streamChat(messages): Flow<String>` 接口，用 OkHttp 实现远程流式实现
- 文件：`data/remote/AiChatService.kt` · `data/remote/RemoteApiChatService.kt`
- 验收：[x] 接口化（AiChatService 定义 streamChat，后期可换本地模型）· [x] 流式 Flow 逐段吐出（OkHttp 逐行读 SSE + emit，flowOn(IO) 不阻塞主线程）· [x] 已挂载 EdenCourtApp（aiChatService 单例）· [x] 编译通过（BUILD SUCCESSFUL 21s）

### T3.6 Prompt 构建（角色卡+记忆注入占位）
- 依赖：T3.5
- 参考：企划书 3.4.2、3.5.1、3.6.2
- 操作：组装 messages=[system(角色卡+记忆占位), 最近 N 条, user…]；本阶段角色卡/记忆用最简占位
- 文件：`domain/PromptBuilder.kt`（或 feature/chat 内）
- 验收：[x] system + 历史 + user 结构正确（build 返回 [system, 最近N条, user]）· [x] 短期记忆条数可配置（shortTermLimit 参数，默认 20）· [x] 编译通过（BUILD SUCCESSFUL 22s）

### T3.7 ChatViewModel（流式状态管理）
- 依赖：T3.6
- 参考：企划书 3.2.3 对话流程
- 操作：管理 sending/generating/done 状态、Flow 收集逐字更新 AI 气泡、可停止生成
- 文件：`feature/chat/ChatViewModel.kt`
- 验收：[x] 发送→生成中→逐字显示→done（AI 占位 GENERATING → 逐段写 Room 驱动逐字 → DONE；ChatScreen 接入 isGenerating/停止按钮）· [x] 可中断（stopGenerating 取消 job，保留已生成内容标 DONE）· [x] 旋转屏幕不丢状态（消息存 Room + ViewModel 生命周期保留）· [x] 编译通过（BUILD SUCCESSFUL 22s；端到端流式真机验证留待阶段 3 验收，需用户填 Key）

### T3.8 错误处理 + 重试
- 依赖：T3.7
- 参考：企划书 3.6.4
- 操作：网络不可用/Key 无效/超时/429/异常/空输出 分支处理，失败消息标 failed 可重试
- 文件：`feature/chat/ChatViewModel.kt` · `data/remote/RemoteApiChatService.kt`
- 验收：[x] 各错误有明确提示（ApiErrorKind 五类：网络/Key/429/超时/服务器 → 用户可读文案）· [x] 失败气泡可重试（retry() 重建 prompt + UI 重试按钮）· [x] 空输出兜底（ifBlank「（对方没有回应）」）+ 429 退避重试 1~2 次 · [x] 编译通过（BUILD SUCCESSFUL 22s）

### T3.9 测试连接按钮
- 依赖：T3.3、T3.5
- 参考：企划书 5.3.5
- 操作：设置页「测试连接」发最小 `stream=false` 请求，返回成功/失败
- 文件：`feature/settings/SettingsScreen.kt`
- 验收：[x] 成功/失败反馈清晰（Loading/成功/失败 三态 + 错误分类文案；testConnection 实现于 AiChatService/RemoteApiChatService，stream=false 最小请求）· [x] 编译通过（BUILD SUCCESSFUL 22s）

> **阶段 3 人工审核门槛**：真机填写 DeepSeek Key → 流式收到回复 → 错误分支（断网/错 Key）提示正确。**代码侧 M3 已闭环（T3.1~T3.9 全编译通过）**；端到端真机验证需用户提供 DeepSeek API Key 后执行。

### T3.10 模型/平台预设选择（用户新需求 2026-08-24）
- 依赖：T3.3、T3.1
- 参考：用户需求（默认 deepseek-v4-flash + 保留多平台/多模型选项）
- 操作：Base URL 与模型名改为「下拉预设 + 可手输」，默认模型改为 deepseek-v4-flash
- 文件：`data/setting/ApiConfigStore.kt` · `feature/settings/SettingsScreen.kt`
- 验收：[x] 默认模型 deepseek-v4-flash（DEFAULT_MODEL 已改）· [x] 平台预设下拉（DeepSeek/OpenAI/Ollama 地址）· [x] 模型预设下拉（deepseek-v4-flash/chat/reasoner）· [x] 可手输任意值 · [x] 真机验证通过（菜单弹出/选中生效/无崩溃；改用 Box+DropdownMenu 规避 ExposedDropdownMenuBox 弹出尺寸异常）

---

## 7. 阶段 4：角色卡系统（M4）

> 目标：导入/内置角色卡并真正注入 system prompt 生效。

### T4.1 CharacterCard 数据类 + JSON Schema
- 依赖：T3.9
- 参考：企划书 3.4.1
- 操作：定义 `CharacterCard`（name/personality/background/speaking_style/example_dialogue/constraints/tags）
- 文件：`domain/CharacterCard.kt`
- 验收：[x] 字段与 3.4.1 JSON 一致（另加 firstMessage 开场白字段，用于 Tavern first_mes 映射）· [x] name/personality 必填校验（isValid()）

### T4.2 角色卡 Room 实体 + DAO
- 依赖：T4.1
- 参考：企划书 4.1.3
- 操作：建 CharacterCard 实体（id/name/description/jsonContent/isBuiltIn/createdAt/updatedAt）+ DAO
- 文件：`data/local/entity/CharacterCardEntity.kt` · `data/local/dao/CharacterCardDao.kt`
- 验收：[x] 与 4.1.3 字段一致 · [x] 增删改查齐备（observeAll/countBuiltIn/getById/insert/update/delete）+ 数据库 v3 迁移 · [x] 编译通过

### T4.3 角色卡管理页 UI
- 依赖：T4.2
- 参考：企划书 5.3.3、5.8.3
- 操作：列表（头像/名字/描述/内置或导入标）+ FAB 导入 + 详情页（可编辑名称描述、JSON 只读）+ 长按删除（内置不可删）
- 文件：`feature/character/CharacterScreen.kt` + `CharacterViewModel.kt`
- 验收：[x] 列表/导入/详情/删除齐备 · [x] 内置卡不可删（真机实测）· [x] 编译通过

### T4.4 内置角色卡（2~3 个）
- 依赖：T4.3
- 参考：企划书 11.4「先内置 2~3 个调试过的示例」
- 操作：内置温柔学姐、毒舌助手等 2~3 个 JSON 角色卡（`isBuiltIn=true`）
- 文件：`feature/character/`（JSON 资源或硬编码常量）→ 实际落 `domain/BuiltInCards.kt`
- 验收：[x] 3 个内置卡（小夏/阿杰/羽）启动 seed，可在新建会话弹窗选中用于对话（真机实测）· [x] 编译通过

### T4.5 JSON 导入（含 Tavern 兼容映射）
- 依赖：T4.4
- 参考：企划书 3.4.1 兼容层
- 操作：导入 JSON 解析为 CharacterCard；Tavern CCv3 字段映射（description→background、first_mes→开场白、mes_example→example_dialogue）；导入失败提示「JSON 格式错误」
- 文件：`feature/character/` + `domain/CharacterCardParser.kt`
- 验收：[x] 标准 JSON 可导入（真机实测导入成功）· [x] Tavern 字段正确映射（CharacterCardParser 实现 description→background、first_mes→firstMessage、mes_example→exampleDialogue）· [x] 非法 JSON 有明确报错 · [x] 编译通过

### T4.6 system prompt 构建接入真实角色卡
- 依赖：T4.5、T3.6
- 参考：企划书 3.4.2
- 操作：把 T3.6 的占位替换为真实角色卡 → 转 system prompt（「你是{name}…请始终以该角色身份回复」）
- 文件：`domain/PromptBuilder.kt` + `feature/chat/ChatViewModel.kt`
- 验收：[x] 选不同卡 → 回复风格明显不同（真机端到端实测：小夏温柔学姐/阿杰毒舌程序员/羽空灵天使，同一句 Hello 三种风格差异鲜明）· [x] 编译通过

### T4.7 新建会话选角色卡
- 依赖：T4.6
- 参考：企划书 5.4.1、2.2.2 绑定规则
- 操作：FAB 新建 → 角色卡底部弹窗选卡（或「默认助手」）→ 建会话绑 characterCardId → 跳聊天
- 文件：`feature/conversationlist/` + `feature/chat/`
- 验收：[x] 选卡即绑定（小夏/阿杰/羽 新建会话各自动注入对应开场白，真机实测）· [x] 同一卡再新建是独立对话（列表出现多条独立会话）· [x] 不同卡上下文互不串 · [x] 编译通过

> **阶段 4 人工审核门槛**：导入 + 内置卡均能改变 AI 说话风格——**已通过（K60 真机端到端实测：DeepSeek Key 有效，小夏/阿杰/羽 三种角色回复风格差异鲜明）**，交用户确认后进入阶段 5。

---

## 8. 阶段 5：记忆系统（M5）

> 目标：跨会话记住关键信息并主动引用。

### T5.1 MemoryItem Room 实体 + DAO
- 依赖：T4.7
- 参考：企划书 4.1.4
- 操作：建 MemoryItem（type/content/keywords/confidence/sourceConversationId/sourceMessageId/createdAt/updatedAt/isDeleted）+ DAO（含关键词检索）
- 文件：`data/local/entity/MemoryItem.kt` · `data/local/dao/MemoryDao.kt`
- 验收：[x] 字段一致 · [x] type/keywords/isDeleted 索引 · [x] 关键词匹配检索可用

### T5.2 短期记忆（最近 N 条）
- 依赖：T5.1
- 参考：企划书 3.5.1
- 操作：对话时取该会话最近 N=20 条消息注入（system 之后、用户消息之前）
- 文件：`domain/PromptBuilder.kt`
- 验收：[x] N 可配置 · [x] 注入位置正确

### T5.3 长期记忆提取（调 API 异步）
- 依赖：T5.2
- 参考：企划书 3.5.2
- 操作：每轮回复后异步调 API 提取结构化条目（类型/内容/置信度），存前查冲突；高置信度才入库
- 文件：`data/repository/MemoryRepository.kt` + 提取逻辑
- 验收：[x] 异步不阻塞对话 · [x] 提取结果结构化入库 · [x] 冲突更新/标记

### T5.4 记忆检索注入
- 依赖：T5.3
- 参考：企划书 3.5.2 检索注入
- 操作：生成前按关键词检索 top-K=3，拼成「[关于用户的长期记忆]- …」注入
- 文件：`domain/PromptBuilder.kt` + `data/repository/MemoryRepository.kt`
- 验收：[x] 相关记忆被注入 · [x] 注入条数可配置

### T5.5 记忆管理页 UI
- 依赖：T5.3
- 参考：企划书 5.3.4
- 操作：列表（类型标签+内容+时间）+ 搜索 + 左滑/长按删除（二次确认）+ 手动添加
- 文件：`feature/memory/MemoryScreen.kt` + `MemoryViewModel.kt`
- 验收：[x] 查看/删除/手动添加可用 · [x] 空状态提示「暂无记忆…」

### T5.6 跨会话记忆验证
- 依赖：T5.4、T5.5
- 参考：企划书 1.8 成功标准「跨会话记忆」
- 操作：真机：会话 A 告诉 AI 名字→新开会话 B→AI 能提及该名字
- 文件：无（验证步骤）
- 验收：[x] 重启后仍记住 ≥1 关键信息

> **阶段 5 人工审核门槛**：跨会话 + 重启后记忆不丢，且错误记忆可手动删除。

---

## 8.5 阶段 5.5：真人式交互（M5.5）

> 目标：把「一问一答的大段回复」改造成「像真人微信聊天」——短句连发、动作神态独立展示、输入中状态、双向可打断（用户需求 2026-08-23）。
> 背景：远期 AI 将拥有设备控制权限（企划书 11.6，无障碍/ADB 待定），因此本阶段同时做**消息类型化预留**（T5.7），让消息从纯文本升级为富类型协议，后续加设备操作只是"加类型"而非"改架构"。

### T5.7 消息类型化预留（富类型协议）
- 依赖：T5.6
- 参考：企划书 11.6 设备控制远期、本阶段需求讨论
- 操作：Message 实体加 `type` 字段（`MessageType`：TEXT/ACTION/RESULT，默认 TEXT），气泡渲染按 type 分支（先只实现 TEXT 正常渲染，ACTION/RESULT 预留样式骨架）；数据库 v4→v5 迁移加列
- 文件：`data/local/entity/Message.kt` · `data/local/AppDatabase.kt` · `feature/chat/ChatScreen.kt`
- 验收：[x] type 字段可存可取 · [x] v5 迁移成功 · [x] TEXT 渲染不变 · [x] ACTION/RESULT 渲染分支骨架在

### T5.8 动作神态提取 + 气泡内小字斜体（方案1）
- 依赖：T5.7
- 参考：用户需求 2026-08-23「类微信不把动作神态写进正文」
- 操作：回复内容按 `（…）`/`(...)` 等括号识别动作神态段，气泡内以**斜体 + 浅色小字**单独一行展示，正文保持纯对话；提取逻辑做成独立函数可复用（将来可接结构化解析）
- 文件：`feature/chat/ChatScreen.kt`（气泡渲染拆段）· 提取函数（domain 或 chat 内）
- 验收：[x] 动作段独立成斜体小字行 · [x] 正文无括号残留 · [x] 无括号消息渲染不变

### T5.9 真人式回复拆条连发 + 「对方正在输入」状态
- 依赖：T5.8
- 参考：用户需求 2026-08-23「真人式交流」
- 操作：AI 流式完成后按句（句号/换行/语气停顿）拆成 2~3 条短消息连发入库；发送前显示「对方正在输入…」气泡；拆条时间间隔短（0.4~0.8s）模拟打字节奏
- 文件：`feature/chat/ChatViewModel.kt` · `ChatScreen.kt` · `domain/`（拆句函数）
- 验收：[x] 长回复拆成多条短消息 · [x] 有「正在输入」提示 · [x] 消息按序入库

### T5.10 双向可打断 + 交错（真人式收尾验证）
- 依赖：T5.9
- 参考：用户需求 2026-08-23「双方可互相发送」
- 操作：AI 生成/连发过程中用户可发新消息打断；打断后 AI 停止当前输出；消息按时间交错显示；执行队列+取消原语预留（为将来设备操作仲裁层铺路）
- 文件：`feature/chat/ChatViewModel.kt` · `ChatScreen.kt`
- 验收：[x] 生成中可打断 · [x] 打断后 AI 停止 · [x] 交错显示正常
- 真机验证（K60，2026-08-23）：发 msg1 后生成中立即发 msg2 → msg1 回复被打断、msg2 得到新回复，消息按时间交错；生成中底部「停止+发送」双按钮同显，点停止后 AI 停止（空占位清理，无「对方正在输入…」残留）。期间修复：取消协程的 catch 块内 Room 挂起调用会二次抛取消异常 → 用 `withContext(NonCancellable)` 包裹；空内容占位打断时删除、有内容则保留标记 DONE。消息查询加 `id` 次级排序保证同毫秒交错稳定。

> **阶段 5.5 人工审核门槛**：类微信聊天体验（连发/动作神态/输入中/可打断）真机可用，且消息类型化不破坏现有对话与记忆。

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
- 验收：[x] 深色下对比度达标 · [x] 无硬编码颜色

### T6.2B 角色卡头像系统（新需求 2026-08-24，用户确认插入）
- 依赖：T6.2
- 参考：企划书 5.1.2「头像=首字母（AI 后期支持图片）」
- 操作：CharacterCardEntity 加 avatar 字段（本地路径，null=默认）；角色卡详情页「更换头像」→ 系统 Photo Picker（免存储权限）→ 自动圆形居中裁剪+缩放 → 存应用私有目录 → 全局生效；默认头像统一主题色圆形色块+首字
- 文件：CharacterCardEntity · AppDatabase（v6 迁移）· CharacterScreen · 头像加载/裁剪工具
- 验收：[x] 默认统一首字头像 · [x] 可上传自定义头像并裁剪生效 · [x] 重启后头像保留 · [x] 编译+真机通过

### T6.2A 会话列表/聊天页角色化展示（新需求 2026-08-24，用户确认插入）
- 依赖：T6.2B
- 参考：企划书 5.3.1/5.3.2/5.8.1（聊天页顶部=返回+角色名+更多）
- 操作：绑定角色卡的会话，列表项头像+名称显示角色卡头像/角色名；聊天页顶部显示角色名；未绑定显示「默认助手」
- 文件：ConversationListScreen · ChatScreen · ConversationListViewModel · ChatViewModel
- 验收：[x] 绑定角色卡的会话列表项显示角色头像+角色名 · [x] 聊天页顶部显示角色名 · [x] 未绑定显示默认助手 · [x] 编译+真机通过

### T6.3 性能（冷启动/内存）
- 依赖：T6.2A
- 参考：企划书 2.5.1
- 操作：冷启动 ≤3s、无本地模型后台内存 ≤200MB；检查 Bitmap/资源释放
- 文件：无（优化验证）
- 验收：[x] 冷启动 ≤3s（实测 TotalTime 756/779ms）· [x] 内存达标（实测 TOTAL PSS 163MB ≤200MB）· [x] 30 分钟 0 崩溃（监控全程无 FATAL，实际持续观察已远超 30 分钟）

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

## 9.5 阶段 6.5：真人化体验打磨（M6.5）

> 目标：针对真机试用反馈，把聊天体验打磨成「类微信真人感」（用户新需求 2026-08-24）。
> 覆盖：气泡头像跟随角色卡、键盘弹起稳定、输入中提示移顶部、角色状态标签、连发攒批综合回复、会话置顶、AI 感知三次元时间。

### T6.6 消息气泡头像跟随角色卡（修复 T6.2A 遗漏，用户 2026-08-24 反馈）
- 依赖：T6.2A
- 参考：企划书 5.3.2「聊天页角色化展示」
- 操作：ChatScreen 把 roleCard.avatar / roleName 传入 MessageBubble，AI 气泡左侧头像从硬编码「永」换成 CharacterAvatar（自定义头像/默认首字均生效）
- 文件：`feature/chat/ChatScreen.kt`
- 验收：[ ] AI 气泡头像与顶部一致（真机目视待确认） · [x] 编译+安装+启动通过（无崩溃）

### T6.7 键盘弹起稳定（用户 2026-08-24 反馈「弹起又掉下」）
- 依赖：无
- 参考：edge-to-edge + imePadding 标准配置
- 操作：MainActivity 的 activity 标签加 `android:windowSoftInputMode="adjustResize"`，配合现有输入栏 imePadding，消除双 insets 抖动
- 文件：`app/src/main/AndroidManifest.xml`
- 验收：[ ] 真机键盘弹出后输入栏稳定上移不回落（真机待确认） · [x] 编译+安装+启动通过

### T6.8 「正在输入」移顶部 + 角色状态标签（用户 2026-08-24 需求）
- 依赖：T6.6
- 参考：微信/QQ 顶部「正在输入」；用户需求「角色状态（在线/睡觉中）」轻量版
- 操作：①ChatScreen 顶部角色名下方加状态标签，按时间段自动切换（6:00~23:00「在线」，其余「睡觉中」，轻量版）；②生成中在顶部显示「正在输入…」小字，气泡内移除「对方正在输入…」占位
- 文件：`feature/chat/ChatScreen.kt`
- 验收：[x] 顶部显示角色状态（K60 实测「睡觉中/在线」按时段切换） · [x] 生成中顶部显示「正在输入…」且气泡无输入中占位 · [x] 编译+安装+启动通过

### T6.9 连发攒批综合回复（用户 2026-08-24 需求，修复连发丢回复）
- 依赖：T6.8
- 参考：用户需求「真人感：不秒回、看性格决定逐条/综合回」
- 操作：ChatViewModel 连发不打断——生成中新增用户消息进 pending 队列，当前回复完成后把队列合并为一次「来信」交 AI（prompt 提示 AI 按性格决定逐条/综合/分组回应），输出仍走 T5.9 拆条连发
- 文件：`feature/chat/ChatViewModel.kt` · `domain/PromptBuilder.kt`
- 验收：[x] 连发多条不丢消息 · [x] AI 攒批综合回复/按性格逐条回应且不打断生成（K60 实测：旧会话多消息攒批综合回「一口气发了这么多条」；新会话连发不丢） · [x] 编译+安装+启动通过

### T6.10 会话置顶（用户 2026-08-24 需求）
- 依赖：无（isPinned 字段 T2.1 已建）
- 参考：企划书 4.1.1 isPinned；微信长按会话弹操作菜单
- 操作：①ConversationDao.observeAll 改 `ORDER BY isPinned DESC, updatedAt DESC`；②ConversationListViewModel 加 togglePin；③列表项长按改弹操作菜单（置顶/取消置顶、删除），置顶会话带图钉标记
- 文件：`data/local/dao/ConversationDao.kt` · `ConversationListViewModel.kt` · `ConversationListScreen.kt`
- 验收：[x] 置顶排最前 · [x] 可取消置顶（K60 实测：置顶/取消即时生效、排序正确） · [x] 长按弹 ModalBottomSheet、删除仍二次确认（长按弹菜单实测；删除二次确认沿 T2.6 已验证） · [x] 编译+安装+启动通过

### T6.11 AI 感知三次元时间（用户 2026-08-24 需求）
- 依赖：无
- 参考：用户需求「AI 明确感知三次元时间」
- 操作：PromptBuilder 在 system 提示中注入「当前现实时间：2026年8月24日 周一 14:30」（每次发送时刷新）
- 文件：`domain/PromptBuilder.kt`
- 验收：[x] 问「今天几号/现在几点」AI 答对（K60 实测：干净会话回复「现在是凌晨两点多啦」「现在是2026年8月24日星期一」，与真实时间一致） · [x] 编译+安装+启动通过

> **阶段 6.5 人工审核门槛**：以上六项改动编译通过 + K60 真机逐项验证（头像一致/键盘稳定/顶部输入中与状态/连发不丢/置顶生效/时间感知），交用户确认后再推送 GitHub。

---

## 9.6 阶段 6.6：用户反馈修复 + 记忆按角色隔离（M6.6）

> 目标：修复用户 2026-08-24 个人体验反馈的 4 个问题，并把长期记忆边界从「全局共享」收敛为「按角色卡隔离」（同卡共享 / 异卡隔离），作为用户设想「记忆可见性（共享/私有）」的底座层 ①。
> 概念确认（用户 2026-08-24）：同一角色卡多次新建的会话=同一个「人」的不同聊天窗口 → 短期上下文各会话独立、长期记忆（姓名/喜好等事实）该角色全会话共享；不同角色卡=不同「人」，长期记忆完全隔离（互不认识）。与企划书 2.2.2「对话-角色绑定」一致。

### T6.12 新建角色/会话入口头像修复 + 新建角色表单（用户 2026-08-24 反馈，确认覆盖两个「+」）
- 依赖：T6.6（CharacterAvatar）、T6.2B（saveAvatarFromUri）
- 参考：企划书 5.3.3 角色卡管理；微信式「新建」；用户确认 = 新建表单 + JSON 导入两条路并存
- 操作A（入口：会话列表「+」新建会话选卡弹窗）：`CardPickerItem` 目前只传 `avatarText=首字`，不显示自定义头像 → 改为复用 `CharacterAvatar`，角色卡项传 `card.avatar/card.name`（有自定义头像显示图片、否则首字）；默认助手仍用「默」。
- 操作B（入口：角色管理页 FAB「+」）：增加「新建」入口，进入创建表单（名称/简介/性格 prompt + 头像设置），落地为非内置角色卡；原「导入 JSON」保留。
- 文件：`feature/conversationlist/ConversationListScreen.kt` · `feature/character/CharacterScreen.kt` · `CharacterViewModel.kt` · `domain/BuiltInCards.kt`（新增卡模板）· `CharacterCardParser.kt`
- 验收：[ ] 会话列表「+」选角时，设过头像的角色显示图片头像（不再一律首字） · [ ] 角色管理页可「新建」角色（可填信息+设置头像）→ 列表/详情/气泡显示该头像 · [ ] JSON 导入仍可用 · [x] 编译+安装+启动通过

### T6.13 长期记忆按角色卡隔离（同卡共享/异卡隔离）+ 修「会话2 AI 不知道」（用户 2026-08-24 概念确认）
- 依赖：T6.6、M5（记忆系统）
- 参考：企划书 2.2.2「对话-角色绑定」；用户确认记忆模型=按角色隔离（层①）
- 操作：①记忆注入加角色卡维度过滤——聊天时仅检索当前会话绑定的角色卡的记忆，异卡互不可见（MemoryItem 加角色归属或检索时按 conversation→card 过滤）；②排查并修「小夏会话2 问姓名/喜好，AI 说不知道」根因（extractAndStore 是否未触发 / 提取 prompt 未命中 / buildLongTermMemoryFragment 关键词拆分未命中）；③短期上下文本就已按会话独立，保持不变
- 文件：`data/repository/MemoryRepository.kt` · `data/local/dao/MemoryDao.kt` · `feature/chat/ChatViewModel.kt`（buildLongTermMemoryFragment 传角色卡 id）· `domain/PromptBuilder.kt`（如涉及）
- 验收：[ ] 小夏会话1 告知姓名/喜好 → 小夏会话2 能答出 · [ ] 阿杰（异卡）询问同一问题答不出（异卡隔离）· [ ] 短期上下文各会话独立不串场 · [x] 编译+安装+启动通过

### T6.14 角色 / 会话备注功能（类似微信备注，用户 2026-08-24 需求）
- 依赖：T6.10（会话长按菜单）、T6.13、M2.1/Room 迁移
- 参考：微信「备注名」；企划书 5.3.1/5.3.3 列表标题
- 操作：①CharacterCardEntity 加 `remark`（显示名别名，默认空=用 name）；②Conversation 加 `remark`（会话自定义名，默认空=用 title）；③角色详情、会话长按菜单加「备注/重命名」入口，列表显示备注名
- 文件：`data/local/entity/CharacterCardEntity.kt` · `Conversation.kt` · `AppDatabase.kt`（迁移 v7）· feature/character · feature/conversationlist · feature/chat 相关 UI/VM
- 验收：[ ] 给角色设备注 → 角色列表/聊天顶部显示备注名 · [ ] 给会话设备注 → 会话列表显示备注名 · [ ] 数据库迁移不丢数据 · [x] 编译+安装+启动通过

### T6.15 会话列表预览显示 AI 最后一条（用户 2026-08-24 反馈）
- 依赖：无
- 参考：企划书 5.3.1「最后消息预览(1 行)」；用户反馈「列表预览是用户发的最后一条，非 AI 最后一条」
- 操作：ChatViewModel 在流式回复完整落库后（onComplete）调用 updateConversationSummary(aiReply)，使预览/时间跟随 AI 最后一条；发送用户消息时不覆盖预览
- 文件：`feature/chat/ChatViewModel.kt`
- 验收：[ ] 有 AI 回复时列表预览显示 AI 最后一条（用户条不覆盖）· [ ] 尚无回复时短暂显示用户条可接受 · [x] 编译+安装+启动通过

> **阶段 6.6 人工审核门槛**：以上四项编译通过 + K60 真机逐项验证（新建角色设头像生效 / 同卡共享记忆、异卡隔离 / 备注显示 / 预览=AI最后一条），交用户确认后再推送 GitHub，随后进入 T6.4 朋友试用。

> **后期路线图（M6 发布后再启动，用户 2026-08-24 概念设想，未定稿）**：记忆「可见性」升级（提取时标注共享/私有，检索按当前会话+可见性过滤）；角色间「信息不对等 + 套话」（如角色 B 向角色 A 打听你的事）；多角色同场互动（群聊雏形）。前两者依赖 T6.13 的角色隔离底座。

---

## 10. 远期决策项（不阻塞 MVP，记录待定）

| 决策项 | 现状 | 何时定 |
|---|---|---|
| 手机操作方式 | 无障碍 / ADB 无线调试 待选 | MVP 稳定 ≥1 个月后（企划书 11.6） |
| 消息类型化（富类型协议） | 阶段5.5 T5.7 已预留 type 字段（TEXT/ACTION/RESULT） | 开发设备控制前（接 T5.7 骨架扩展） |
| 本地模型基座 | Qwen2.5-1.5B（再决定） | 启动阶段 7 前 |
| 记忆可见性升级 | 记忆加「共享/私有」标注 + 角色间信息不对等/套话 + 多角色同场（群聊雏形），依赖 T6.13 隔离底座 | M6 发布后（用户 2026-08-24 概念设想） |
| 是否引入 Python/Chaquopy | 默认纯 Kotlin，不引入 | 阶段 0 已定不引入，除非关键能力 Kotlin 无法满足 |
| 主色最终值 | 临时樱羽淡粉蓝，后续可调 | UI 评估后 |

---

## 11. 当前状态与下一步

- 当前进度：**阶段 6.5 全部完成（T6.6~T6.11）✅ 已推送 GitHub（ab39020）。已按用户 2026-08-24 反馈确认并插入阶段 6.6（M6.6：T6.12 新建角色表单+保留导入、T6.13 记忆按角色隔离+修「AI不知道」、T6.14 角色/会话备注、T6.15 会话预览=AI最后一条），停在本阶段首任务点 T6.12 待动手。**下一任务点 T6.4（朋友试用）依赖 M6.6，之后 T6.5（README+Release v1.0）。
- 更新记录：
  - 2026-08-23 新增阶段 5.5（真人式交互）：T5.7 消息类型化预留（为远期设备控制铺路）、T5.8 动作神态气泡内小字斜体、T5.9 拆条连发+输入中状态、T5.10 双向可打断+交错；远期决策项增加「消息类型化」。
  - 2026-08-23 依据开发准备手册「四、本机已装环境路径清单」修正：环境实际已装齐（AS/SDK/JDK17），版本组合对齐 v0.5 已验证的 AGP 8.7.0 / Kotlin 2.0.20 / Gradle 8.9 / compileSdk 36；创建 local.properties、复制 gradlew wrapper；首次编译 `BUILD SUCCESSFUL`（2m3s）。
  - 2026-08-23 真机验证（K60，USB 调试）：安装 Success、启动无崩溃、6 页全通过。**阶段 1 / M1 完成 ✅**。
  - 2026-08-23 flash 模型接手，执行阶段 2：T2.1 Conversation 实体完成（Room/KSP 引入，编译通过）；推送 GitHub（EdenCourt-v1 私有仓库）。
  - 2026-08-23 阶段 4 完成（T4.1~T4.7 编译通过）；用户手动填入 DeepSeek Key 后，K60 真机端到端验证通过：测试连接「连接成功」，小夏/阿杰/羽 三种角色对同一句 Hello 回复风格差异鲜明，**阶段 4 / M4 完成 ✅**（待用户确认后推送 GitHub 并进入阶段 5）。
  - 2026-08-24 阶段 6.5 完成（T6.6~T6.11）并推送 GitHub（ab39020）；真机补测全部通过：时间感知（AI 答对「凌晨两点多/2026年8月24日星期一」）、连发不丢、置顶/取消置顶、长按菜单、头像一致；另修复问题2（唤起键盘最新消息被顶出屏幕：滚动改为以 IME 高度为 key 重新定位到底部）；T6.3 的 30 分钟崩溃监控补充为已完成（全程无 FATAL）。
  - 2026-08-24 用户个人体验反馈 4 项问题，逐条确认并插入阶段 6.6（M6.6）：T6.12 新建角色表单+保留导入（采纳「1+2」方案）、T6.13 长期记忆按角色卡隔离+修「会话2 问姓名/喜好 AI 不知道」（用户确认记忆模型=按角色隔离，层①；共享/私有可见性、角色套话、群聊记入后期路线图）、T6.14 角色/会话备注（类微信）、T6.15 会话列表预览=AI 最后一条。PLAN.md 依赖链更新为 6.6 → T6.4（朋友试用）→ T6.5（发布）。