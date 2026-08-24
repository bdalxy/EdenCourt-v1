# 永恒庭院 · EdenCourt

> 一个类微信的 AI 角色扮演聊天应用（Android）。

永恒庭院就像你手机里的一个「庭院」，庭院里的每一个角色都有独立的人设、头像与记忆。你可以像和真人微信聊天一样：短句连发、动作神态、正在输入、随时打断——只是对面是一个被角色卡「注入灵魂」的大模型。

- 🌸 樱羽主题（浅色为主），淡粉蓝 + 淡樱粉，清晰柔和
- 🤖 接入 DeepSeek 等 OpenAI 兼容大模型，可选平台与模型
- 🎭 角色卡系统：内置 3 个角色，支持 JSON 导入与你自建角色
- 🧠 跨会话长期记忆，且按角色隔离（不同角色互不认识）

> ⚠️ **测试产品**：本项目处于 MVP 阶段，不承诺 AI 回复的准确性，也不承担因 AI 回复引发的任何后果。仅供个人娱乐与学习用途。

---

## 功能

- **真人式聊天**：短句拆条连发、括号动作神态独立展示、「正在输入…」提示、生成中可打断、连发攒批综合回复
- **AI 接入**：设置页配置 Base URL / API Key / 模型（DeepSeek / OpenAI / Ollama 预设，也可手输），支持流式输出、失败重试、测试连接
- **角色卡**：内置「小夏（温柔学姐）」「阿杰（毒舌程序员）」「羽（空灵天使）」三张，支持 Tavern 格式 JSON 导入、自建角色、自定义头像、备注名
- **记忆系统**：跨会话长期记忆提取与检索注入，按角色卡隔离（同卡共享 / 异卡隔离），记忆管理页可筛选、手动添加与删除
- **会话管理**：置顶、备注、长按菜单、预览显示 AI 最后一条回复
- **体验细节**：AI 感知现实时间、顶部角色状态标签（在线/睡觉中）、键盘稳定不抖动、深浅色主题跟随系统

## 环境要求

- Android 10（minSdk 29）及以上
- 需自备大模型 API Key（如 DeepSeek）

## 安装

1. 下载最新 Release 的 APK（见 GitHub Releases）
2. 手机「设置 → 安全 → 允许安装未知来源应用」开启
3. 安装后打开 App，进入 **设置** 填入你的 API Key（也可切换模型/平台）
4. 返回列表，**＋** 新建会话并选择一个角色，开始聊天

## 开发指南

### 技术栈

| 层 | 方案 |
|---|---|
| 语言 | 纯 Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + Compose Navigation + MVVM |
| 数据 | Room + DataStore |
| 网络 | OkHttp + Retrofit（SSE 流式，kotlinx.serialization） |

### 目录结构

```
app/src/main/java/com/bdalxy/edencourt/
  core/            全局工具/常量/主题（AppConfig、ui/theme、Avatar）
  data/            local(Room)/repository/setting(DataStore)/remote(API)
  domain/          业务模型与逻辑（角色卡、PromptBuilder、解析器）
  feature/         按功能分包（conversationlist/chat/character/memory/settings/about）
  navigation/      路由 + NavHost
```

### 版本与依赖

- 依赖版本集中在 `gradle/libs.versions.toml`，按阶段引入 `app/build.gradle.kts`
- 全部代码带中文注释

### 构建

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 免责声明

- 本项目为**测试产品**，AI 回复由第三方大模型生成，可能存在错误、偏激或不准确内容，请勿全信。
- 使用本项目产生的任何后果由使用者自行承担。
- 所有数据默认存储在本地设备，不擅自上传。

## License

本项目为个人项目，不做商业化授权。（待补充具体 License）