# 通用约束模型：Android 真机安装与 APK 发布

> 版本：v1（2026-09-06）
> 适用范围：**任何 Android 项目**的真机安装、覆盖升级、数据备份恢复、GitHub Releases 发布。
> 本文件为通用模板：`<PACKAGE>`、`<PROJECT>`、`<APK路径>` 等为占位符，使用时替换为本项目值。
> 建立动机：2026-09-06 真实事故——adb install 被设备端取消（INSTALL_FAILED_USER_RESTRICTED），Android 流式安装「先移除旧包」语义导致应用数据目录被清。以下规则为教训固化。

---

## 零、最高优先级总则（不可违反）

1. **数据永远优先（命令级强制，禁止跳过）**：任何「安装 / 覆盖 / 卸载 / 降级」操作执行前，必须先运行备份脚本完成数据备份并校验有效，**未备份 = 禁止执行安装**。不依赖用户提醒，AI 自己执行 install 前必须自己先跑备份。
2. **能覆盖绝不重装**：目标包已存在且签名一致 → 一律 `adb install -r` 覆盖升级（保留数据）；**绝不先卸载再装**（卸载清数据目录）。
3. **失败不盲目重试**：安装被拦截时先诊断根因（设备放行 / 签名 / 版本），确认后再动；连续重试同一命令是浪费时间与风险。
4. **签名是覆盖的硬门槛**：包名相同 + 签名不同 = 无法覆盖（UPDATE_INCOMPATIBLE），必须明确告知用户走卸载或换包名。

---

## 一、安装决策树（通用）

```
[准备]
  1. adb devices            # 确认设备在线（device，非 unauthorized/offline）
  2. adb shell pm list packages | grep <PACKAGE>   # 目标包是否存在？
  3. 核对签名（同包名覆盖时）：
       keytool -printcert -jarfile <新APK>          # 看新包签名指纹
       adb shell dumpsys package <PACKAGE> | grep -A2 "signatures"  # 对比已装包
  4. **运行备份脚本（强制前置，禁止跳过）**：`python backup_db.py <包名> <备份目录>` → 校验输出含 user_version 与各表 count 后，才允许进入安装
  5. **本机禁跑 `connectedDebugAndroidTest`**：该通道（PackageInstallerSession）每次强制弹窗且可能移除旧包，无论同意/取消均不安全；迁移/仪器测试实跑一律放干净模拟器或专用测试机（本机仅允许编译级验证 `compileDebugAndroidTestKotlin`）
  6. 设备未配置免弹窗时：告知用户「即将弹安装确认框」，由用户自行选择放行；本机已开「通过USB验证应用」则 adb install 免弹窗，不打扰用户

[安装]
  包已存在 + 签名一致        → adb install -r <apk>        # 覆盖升级，保留数据
  包已存在 + 签名不一致       → 停止。告知用户需卸载或换包名
  包不存在                   → adb install <apk>            # 全新安装
  需降级                     → adb install -r -d <apk>      # 允许降级+保留数据
  测试包(-t)                 → 追加 -t

[结果判定]
  Success                    → 完成
  INSTALL_FAILED_USER_RESTRICTED / "Install canceled by user"
                              → 设备端拦截，见二
  其他失败                    → 查具体错误码再决策（签名/ABI/版本/空间）
```

## 二、INSTALL_FAILED_USER_RESTRICTED 处理流程（厂商拦截）

**本质**：系统级安装限制（Android 8.0+ 引入、11+ 强制「通过 USB 安装」开关默认关闭 + 厂商 ROM 额外策略）。不是签名/权限/空间问题；`pm grant` 无法绕过，root 也不通用。

按序排查（每步后重试一次，别再盲目循环）：
1. 手机是否**解锁**（锁屏时弹窗无法出现，系统直接拒绝）？
2. 开发者选项 → **「通过 USB 安装 / USB安装（Install via USB）」** 开关是否开启？（Android 11+ 必须；厂商机可能叫「USB安装」「通过USB验证应用」等，可能要锁屏密码/账号验证）
3. 弹窗是否被误点/未点？重新执行让弹窗再次出现。
4. 兜底（稳定路径）：`adb push <apk> /sdcard/Download/` → 用户在手机上用**文件管理器手动安装**（可绕过 adb 限制，同 APK 能成功）。

**注意事项**：本错误出现时，Android 流式安装可能已进入「先移除旧包」流程；取消后旧包可能已被清、数据目录丢失。所以本模型总则①「先备份」尤其重要。

## 三、数据备份 / 恢复规范（跨平台原则）

### Android（SQLite / Room）
- 备份对象：`<dataDir>/databases/*.db` + `-wal` + `-shm` 三件套。
- 前置：`adb shell am force-stop <PACKAGE>`（保证 checkpoint）。
- **禁 PowerShell 直接重定向**（`>` 会损坏二进制）；统一用：
  - `adb exec-out run-as <PACKAGE> cat <file> > local` 由脚本/python 落盘（二进制安全），或
  - `run-as <PACKAGE> cp <file> <可读目录>` 再 `adb pull`。
- 校验：本地用 sqlite3 打开，读 `PRAGMA user_version` + 关键表 count，确认有效才叫"备份成功"。
- 恢复：覆盖 `databases/<db>`（删旧 wal/shm）→ 启动应用让 Room 自动迁移（旧版本库可被当前版本自动升级）→ 校验 user_version 与数据量。
- 恢复属敏感写操作：**执行前单独征得用户同意**。

## 四、GitHub Releases 发布规范（通用）

1. **命名**：APK 统一重命名 `{Project}-{versionName}-{channel}.apk`，channel ∈ `debug` / `beta` / `release`（例：`EdenCourt-2.0.0-beta.apk`、`MyApp-1.0.0-release.apk`）。不用默认名（易混淆）。
2. **Tag**：语义化 `vMAJOR.MINOR.PATCH`；测试版 `vX.Y.Z-beta`（或 `-alpha/-rc`）。
3. **类型**：测试版勾 **Pre-release**；正式版不勾（正式 release）。
4. **标注内容**（每次发布必写）：版本号、渠道、包名 `<PACKAGE>`、构建日期、签名类型（debug/release）、变更摘要、安装说明。
5. **构建物不入 git 历史**：APK 只作为 Releases 资产上传。
6. **发布门禁**：编译 + 单测 + 真机自测通过 → 告知用户开网络代理 → 再创建 release + 上传资产。

## 五、参数速查

| 参数 | 含义 | 场景 |
|---|---|---|
| `-r` | 覆盖安装，**保留数据** | 同签名升级 |
| `-t` | 允许测试包 | debug 构建 |
| `-d` | 允许版本降级 | 回退版本 |
| `-g` | 授予全部运行时权限 | 免手动授权 |
| `pm clear <PACKAGE>` | 清应用数据（**全丢**） | 仅修复损坏配置，慎用 |

## 六、一句话决策

> 先备份 → 核对包名+签名 → 签名一致走 `-r` 覆盖、不一致停下问人 → 被拦查 USB 安装开关/解锁 → 兜底文件管理器手动装 → 发布走 Releases 标注清楚。
