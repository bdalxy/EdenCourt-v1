package com.bdalxy.edencourt

import com.bdalxy.edencourt.data.debug.SeedDataGenerator
import com.bdalxy.edencourt.data.debug.SeedDataTool

/**
 * debug 专用 Application（仅 debug 源集存在）。
 * 覆盖 EdenCourtApp.seedDataGenerator，注入压测种子数据实现 SeedDataTool；
 * release 构建不含本类与 SeedDataTool，seedDataGenerator 保持 null。
 * 由 debug 源集 AndroidManifest.xml 覆盖 application android:name 指定。
 */
class DebugEdenCourtApp : EdenCourtApp() {

    /** 压测工具按需懒加载（debug 构建设置页「开发者工具」区才真正触达） */
    override val seedDataGenerator: SeedDataGenerator? by lazy {
        SeedDataTool(
            chatRepository = chatRepository,
            messageDao = messageDao,
            memoryDao = memoryDao,
        )
    }
}
