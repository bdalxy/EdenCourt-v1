package com.bdalxy.edencourt.data.debug

/**
 * 性能压测种子数据生成器接口（M3「千条数据 60fps」验收前提）。
 * 接口留在 main 源集供设置页 ViewModel 解耦引用；真正实现 SeedDataTool 只存在于 debug 源集，
 * release 构建不含压测代码（EdenCourtApp.seedDataGenerator 默认 null，由 debug 的 DebugEdenCourtApp 注入）。
 */
interface SeedDataGenerator {
    /**
     * 生成一组压测种子数据（新建独立【压测】会话 + 千条消息/记忆）。
     * @return 生成的会话 id（失败抛异常由调用方提示）
     */
    suspend fun generate(
        tag: Int = 1,
        messageCount: Int = 1000,
        memoryCount: Int = 1000,
    ): Long
}
