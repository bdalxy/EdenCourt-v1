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
阶段7（v1.1 可见性底座）  T7.1→T7.2→T7.3→T7.4→T7.5（记忆共享/私有）
阶段8（v1.2 跨会话打听）  T8.0→T8.1→T8.2→T8.3→T8.4（P1 用户话语触发）→T8.5→T8.6（P2 定时自驱动）
阶段8.5（v1.2b 角色间互动）T8.7→T8.8→T8.9→T8.10→T8.11→T8.12（互动资格开关→关系→执行器→落位→互动中心→手动触发 · MVP 手动触发）
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

---

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

## 10. 远期决策项（不阻塞 MVP，记录待定）

| 决策项 | 现状 | 何时定 |
|---|---|---|
| 手机操作方式 | 无障碍 / ADB 无线调试 待选 | MVP 稳定 ≥1 个月后（企划书 11.6） |
| 消息类型化（富类型协议） | 阶段5.5 T5.7 已预留 type 字段（TEXT/ACTION/RESULT） | 开发设备控制前（接 T5.7 骨架扩展） |
| 本地模型基座 | Qwen2.5-1.5B（再决定） | 启动阶段 7 前 |
| 记忆可见性升级 | 记忆加「共享/私有」标注 + 角色间信息不对等（由角色间**互动**自然产生，非"套话"）+ 多角色同场（群聊雏形），依赖 T6.13 隔离底座 | M6 发布后（用户 2026-08-24 概念设想，2026-08-25 强调：是互动不是套话） |
| 是否引入 Python/Chaquopy | 默认纯 Kotlin，不引入 | 阶段 0 已定不引入，除非关键能力 Kotlin 无法满足 |
| 主色最终值 | 临时樱羽淡粉蓝，后续可调 | UI 评估后 |

---

## 11. 当前状态与下一步

- 当前进度：
  - **全面审核完成（2026-08-27，deepseek-v4-pro）**：生成 docs/项目全面审核报告-2026-08-27.md（只审不改代码），综合评分 7.2/10（代码质量 8.0 / 架构 8.2 / 性能 6.5 / 潜在 Bug 7.0 / 安全 6.0 / 测试 6.0）。发现 10 项需处理：安全 S1（API Key 明文+allowBackup）、S2（receiver exported）；性能 P1（流式每 token 落库）、P2/P3（窗口分页治标不治本+暗访拉全文）、P4（FTS 未接线）；稳定性 B1（回复泵游标内存态）；测试 T1/T2（核心逻辑无单测）；一致性 B3（互动自动驱动无 BOOT receiver）；可维护 Q1（ChatViewModel 超载）；数据模型 D1/D2。已落成任务点 T12.1~T12.10（阶段 10「审核驱动·工程保底」，见上），按 P0→P1→P2 排依赖推进。
  - **方向 B'（角色活力）T11.10/T11.11/T11.12/T11.13 已完成并真机验证（待推送）**：T11.10 角色生活状态机（个性派生作息+角色卡/会话两级自定义+跨天入睡）；T11.11 状态驱动对话与互动调度（睡眠不串门）；T11.12 事件驱动自发动机（记忆挑「因由」注入开场白+7天去重+定时兜底+DB v21 motive）；T11.13 关系事件叙事（每次互动沉淀 relationship_event 共同经历事件，互动中心「关系故事」时间线区+Prompt 注入连续性，DB v22；好感度数值体系保留不冲突）。真机 force 验证：#9 因由注入（Nana）✓、#11 因由去重回落兜底 ✓、#12 事件沉淀（scene=日常/mood=亲密/双方摘要）✓、v22 迁移 ✓。代码已本地提交待推送。
  - **改进方案（docs/IMPROVEMENT-PLAN.md）已落盘待用户审核**：诊断 10 项问题 → 7 个改进方案 → 双维复杂度量化评估 → 优选组合（A基础→D轻量→A深化→C→D完整→B→F→G，E 转远期；审阅后调整含新增方案 A' 软感知陪伴）。用户已批准 5 条新增需求 N1~N5 插入看板（见阶段 9.15 方向 B'，T11.10~T11.16）。**待人工审核方案文档后按任务点推进**。
  - **阶段 8.6（记忆系统深化 · 方向B 互动+好感度）T11.6/T11.7 已完成并真机验证（已推送 b3f9b84）**：T11.6 好感度数值体系（互动累计好感度、分档驱动氛围与对话、好感度细分到会话角色、会话记忆面板升级）；T11.7 互动自动驱动（每日窗口 AlarmManager + 概率 + 冷却期去重 + AUTO 来源标记 + 互动中心自动驱动设置区）。真机实测：自动互动「小夏→光羽」COMPLETED 且摘要回写会话、重复触发命中冷却期去重、force 调试通道可用、手动/自动来源区分正确。
  - **阶段 8.5（v1.2b 角色间互动+用户身份）T8.7~T8.13 全部实现并真机验证通过**（commit `d0b23a8` 已推 main）：互动资格开关/关系底座/多轮执行器/三层表+digest/互动中心/手动触发/用户自设身份。真机实测：手动触发「光羽→学姐」真实多轮对话完成、摘要回写双方会话、氛围随关系标签（首次陌生寒暄）、身份「测友」全角色一致、Room v12→v15 迁移无损。「当前进度」本段看板更新待推送。
  - **阶段 8（v1.2 跨会话打听）P1/P2 均已完成并推送**（commit `3fc6af5`/`0cc8415`）：T8.0~T8.6。P1 用户话语触发、P2 每日定时自驱动均真机跑通。
  - **阶段 7（v1.1 记忆「会话级私有」底座）已全部完成并推送**：T7.1~T7.5。
- 下一步：方向 B'（角色活力 T11.10~T11.16）、方向 D（T11.8 互动信息不对等）、P0-3 设置页分组整理均已交付；T11.8d 复核由 AI 自动验证通过。**已推进方案 F（记忆主动引用增强，IMPROVEMENT-PLAN §2 F）v1 子点——检索相关性增强**：`rankByRelevance` 新增 query 参数，聊天注入时 `PromptBuilder/buildPrompt` 传入当前用户消息，按「记忆关键词（≥2字）在当前消息中的命中数」加权，让与当前话题相关的记忆优先进入 top-K 注入名额（旧默认排序完全兼容）。Python 用例验证通过（提「奶茶」→奶茶记忆置顶/提「图书馆」→图书馆置顶/无query保原序/提「名字」→名字置顶）。**遗留**：方案 F「触发增强」（合适时机主动引用）依赖模型自觉、难确定性验证，留待后续。**接下来候选**：方案 G-① 列表懒加载+分页（P-07）/ 方案 G-③ 自动化回归集（P-08）。拆解：G-① 会话/消息/记忆 LazyColumn 分页 → G-③ 核心链路 UI 冒烟+Room 迁移升级测试。**方案 F 检索相关性增强已交付并推 GitHub（735f5ec）。方案 G-③ 回归集已建首期（Phase1）**：新增 JVM 单元测试基础设施（junit+mockk），首期落地 14 用例——好感度分档（affinityGain/relationStatusForAffinity/affinityTierLabel 档位边界）+ 记忆注入排序（T9.3 置顶>重要>更新时间 与 方案F query 相关性加权、无 query 兼容旧行为、topK 截断、保守语义）。`testDebugUnitTest` 14/14 通过（BUILD SUCCESSFUL）。**G-③ Phase2 迁移回归已完成并推送**：新增 androidTest 迁移测试（room-testing + androidTest assets 指向 schemas 目录），AppDatabase 抽出 `ALL_MIGRATIONS`（builder 与测试共用）；两个用例——v12→v23 旧数据穿越 11 次迁移不丢、v22→v23 验证 relationship_event 新增 type 列默认值；真机 connectedDebugAndroidTest 通过。实现要点：因 FTS 虚拟表 `memory_items_fts` 无实体声明、不在导出 schema 里，`runMigrationsAndValidate` 会误报 "Unexpected table"，故改「把 ALL_MIGRATIONS 当纯函数链式执行」验证迁移跑通+数据保持+新列字段可读。**遗留**：G-③ Phase3 核心链路 UI 冒烟（会话/聊天/记忆可达性 smoke）。**方案 G-① 列表懒加载+分页（聊天页侧窗口分页）已完成并推 GitHub**：调研确认记忆列表已分页（T9.5 done）、会话数量少无需分页，唯一缺口为消息列表长会话全量渲染。做法——messages 全量流不变（流式/prompt/跨会话暗访依赖全量不可截断），新增 `displayMessages` = messages 最近 windowSize 条（初始 60、`loadOlderMessages()` 每次 +40），UI 只渲染展示子集；ChatScreen 空态判断仍用全量 messages、滚底只依赖 messages.size（避免翻历史时跳回底部）、`snapshotFlow` 监听 firstVisibleItemIndex 接近顶部且 hasMoreOlder 时向上加载；LazyColumn 按 key 前插历史不跳动。编译 BUILD SUCCESSFUL + 真机覆盖安装 Success 无崩溃数据保留。**遗留**：G-③ Phase3 核心链路 UI 冒烟（instrumented，需在安全设备/备份后跑，勿再用 connectedDebugAndroidTest 打正式机）。
- 更新记录：
  - 2026-08-27 全面审核（deepseek-v4-pro）：生成 docs/项目全面审核报告-2026-08-27.md，综合 7.2/10。发现 10 项处理项（安全 S1/S2、性能 P1~P4、稳定性 B1、测试 T1/T2、一致性 B3、可维护 Q1、数据模型 D1/D2），落成阶段 10「审核驱动·工程保底」任务点 T12.1~T12.10（P0→P1→P2 带依赖）。本次不涉及代码改动。
  - 2026-08-26 系统性改进优化方案（docs/IMPROVEMENT-PLAN.md）落盘：诊断 10 问题 / 7 方案 / 双维复杂度评估 / 资源分配 / KPI 闭环。同日审阅处理（用户批注+专家意见，见方案附录 C）：新增方案 A' 软感知陪伴（不做行为观察、零新权限）；执行顺序调整为 A基础→D轻量→A深化→C→D完整→B→F→G（E 转远期）。用户批准新增需求 N1~N4 全部插入看板 → 阶段 9.15 新增「方向 B'：角色活力」（T11.10 生活状态机 / T11.11 状态驱动对话与调度 / T11.12 事件驱动自发动机(含软感知陪伴) / T11.13 关系事件叙事(数值保留) / T11.14 轻量首启引导 / T11.15 完整引导+入口提级）。同日用户新增 N5「一起看」并入方向 B' → **T11.16 共同内容会话（用户主动分享→陪看讨论→沉淀共同经历关系事件，红线不采集设备行为）**；远期台阶 2/3（进度同步/多模态同框）记入 ROADMAP 2.3。T11.6/T11.7 已推送 GitHub（b3f9b84）。
  - 2026-08-25 阶段 8.5（v1.2b）T8.7~T8.13 完成并真机验证：T8.9b 互动氛围改关系标签+性格驱动；T8.13 用户自设身份唯一绑定、全角色统一称呼。手动触发互动「光羽×学姐」真机跑通（多轮/摘要回写/氛围递进），用户身份「测友」全角色一致。代码已推 main（d0b23a8），看板状态段待补推。
  - 2026-08-23 新增阶段 5.5（真人式交互）：T5.7 消息类型化预留（为远期设备控制铺路）、T5.8 动作神态气泡内小字斜体、T5.9 拆条连发+输入中状态、T5.10 双向可打断+交错；远期决策项增加「消息类型化」。
  - 2026-08-23 依据开发准备手册「四、本机已装环境路径清单」修正：环境实际已装齐（AS/SDK/JDK17），版本组合对齐 v0.5 已验证的 AGP 8.7.0 / Kotlin 2.0.20 / Gradle 8.9 / compileSdk 36；创建 local.properties、复制 gradlew wrapper；首次编译 `BUILD SUCCESSFUL`（2m3s）。
  - 2026-08-23 真机验证（K60，USB 调试）：安装 Success、启动无崩溃、6 页全通过。**阶段 1 / M1 完成 ✅**。
  - 2026-08-23 flash 模型接手，执行阶段 2：T2.1 Conversation 实体完成（Room/KSP 引入，编译通过）；推送 GitHub（EdenCourt-v1 私有仓库）。
  - 2026-08-23 阶段 4 完成（T4.1~T4.7 编译通过）；用户手动填入 DeepSeek Key 后，K60 真机端到端验证通过：测试连接「连接成功」，小夏/阿杰/羽 三种角色对同一句 Hello 回复风格差异鲜明，**阶段 4 / M4 完成 ✅**（待用户确认后推送 GitHub 并进入阶段 5）。
  - 2026-08-24 阶段 6.5 完成（T6.6~T6.11）并推送 GitHub（ab39020）；真机补测全部通过：时间感知（AI 答对「凌晨两点多/2026年8月24日星期一」）、连发不丢、置顶/取消置顶、长按菜单、头像一致；另修复问题2（唤起键盘最新消息被顶出屏幕：滚动改为以 IME 高度为 key 重新定位到底部）；T6.3 的 30 分钟崩溃监控补充为已完成（全程无 FATAL）。
  - 2026-08-24 用户个人体验反馈 4 项问题，逐条确认并插入阶段 6.6（M6.6）：T6.12 新建角色表单+保留导入（采纳「1+2」方案）、T6.13 长期记忆按角色卡隔离+修「会话2 问姓名/喜好 AI 不知道」（用户确认记忆模型=按角色隔离，层①；共享/私有可见性、角色套话、群聊记入后期路线图）、T6.14 角色/会话备注（类微信）、T6.15 会话列表预览=AI 最后一条。PLAN.md 依赖链更新为 6.6 → T6.4（朋友试用）→ T6.5（发布）。
