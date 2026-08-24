package com.bdalxy.edencourt.core

/** 全项目集中常量：避免魔法值散落各处。 */
object AppConfig {
    const val APP_NAME = "EdenCourt"
    const val APP_NAME_CN = "永恒庭院"
    const val VERSION = "1.0.0"

    // 远程 API 默认配置（阶段 3 接入 DeepSeek 时使用）
    const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-chat"
    const val DEFAULT_TEMPERATURE = 0.8f
    const val DEFAULT_TOP_P = 1.0f
    const val DEFAULT_MAX_TOKENS = 512

    // 记忆默认参数（阶段 5 记忆系统使用）
    const val DEFAULT_SHORT_TERM_COUNT = 20
    const val DEFAULT_MEMORY_INJECT_COUNT = 3
}