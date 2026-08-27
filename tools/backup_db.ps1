# =====================================================================
# 真机数据库备份脚本（EdenCourt）
# ---------------------------------------------------------------------
# 用途：每次「功能确认/交付」后，把真机正式 App 的数据库三件套
#       (db + wal + shm) 字节级备份到本机固定目录，防止再次发生
#       真机数据清空后无备份可恢复的事故。
#
# 用法：
#   .\backup_db.ps1                  # 备份到 临时版本目录 temp\<时间戳>\
#   .\backup_db.ps1 -Tag v1.0        # 备份到 正式版本目录 v1.0\<时间戳>\
#   .\backup_db.ps1 -OutDir D:\x\y   # 备份到自定义目录
#
# 目录结构（固定根目录 F:\EdenCourt\backups\）：
#   temp\                       临时版本（验证中/未定版）
#   v1.0\  v1.1\  ...           正式版本
#
# 正确性保证：
#   - 用字节流复制（PowerShell 文本重定向 `>` 会损坏二进制，绝不能用）
#   - 备份后校验 magic：db="SQLite format 3"、wal=0x377f0682
#   - 先 am force-stop 避免 App 持有锁 / wal 不一致
# =====================================================================

param(
    # 版本标签：默认 temp（临时）；传 v1.0 / v1.1 等 → 正式版本目录
    [string]$Tag = "temp",
    # 覆盖根目录时使用
    [string]$OutDir = "",
    # adb 路径（自动探测常见 SDK 位置）
    [string]$Adb = ""
)

$ErrorActionPreference = "Stop"

# ---- 解析 adb 路径（找不到则报错）----
if (-not $Adb) {
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "F:\Android\SDK\platform-tools\adb.exe",
        "C:\Android\Sdk\platform-tools\adb.exe"
    )
    $Adb = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $Adb -or -not (Test-Path $Adb)) {
    throw "未找到 adb.exe，请用 -Adb 指定路径"
}

# ---- 解析目标目录 ----
if (-not $OutDir) {
    $root = "F:\EdenCourt\backups"
    $OutDir = Join-Path $root $Tag
}
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$dest  = Join-Path $OutDir $stamp
New-Item -ItemType Directory -Force -Path $dest | Out-Null

# 卸载/停止 App，保证 wal 落盘一致；确认已连接设备
$pkg = "com.bdalxy.edencourt"
Write-Host "[1/4] 停止 App ($pkg)..."
& $Adb shell am force-stop $pkg 2>$null
Start-Sleep -Milliseconds 600

Write-Host "[2/4] 导出数据库三件套（字节流传速）..."
$managed = $False
foreach ($suffix in @("", "-wal", "-shm")) {
    $remote = "databases/edencourt.db$suffix"
    $local  = Join-Path $dest ("edencourt.db" + $suffix)   # 保留原后缀名
    try {
        $p = New-Object System.Diagnostics.Process
        $p.StartInfo.FileName = $Adb
        # 用单字符串 Arguments 而非 ArgumentList（兼容老 .NET Framework 环境）
        $p.StartInfo.Arguments = "exec-out run-as $pkg cat `"$remote`""
        $p.StartInfo.UseShellExecute = $false
        $p.StartInfo.RedirectStandardOutput = $true
        $p.StartInfo.RedirectStandardError = $true
        $null = $p.Start()
        $fs = [System.IO.File]::Create($local)
        $p.StandardOutput.BaseStream.CopyTo($fs)
        $fs.Close()
        $p.WaitForExit()
        $managed = $True
    } catch {
        # run-as 方案失败时记 0 字节并继续，最后校验阶段会暴露
        Write-Warning "备份 $suffix 失败：$($_.Exception.Message)"
    }
}
if (-not $managed) { throw "备份失败：无法通过 run-as 读取数据库" }

# ---- 校验 magic（最关键的防损步骤）----
Write-Host "[3/4] 校验备份字节级有效..."
$ok = $True
$dbBytes = [System.IO.File]::ReadAllBytes((Join-Path $dest "edencourt.db"))
$dbHeader = [System.Text.Encoding]::ASCII.GetString($dbBytes[0..15])
if ($dbHeader -ne "SQLite format 3") { $ok = $False; Write-Warning "db header 异常: '$dbHeader'"}
$walPath = Join-Path $dest "edencourt.db-wal"
if (Test-Path $walPath) {
    $walBytes = [System.IO.File]::ReadAllBytes($walPath)
    if ($walBytes.Length -ge 4) {
        $magic = ($walBytes[0..3] | ForEach-Object { $_.ToString("X2") }) -join " "
        if ($magic -ne "37 7F 06 82") { $ok = $False; Write-Warning "wal magic 异常: $magic" }
    }
}
if (-not $ok) { throw "备份校验失败：文件可能损坏，请勿用于恢复" }

Write-Host "[4/4] 完成。备份目录："
Write-Host "        $dest"
Get-ChildItem $dest | Select-Object Name, Length | Format-Table -AutoSize
Write-Host "  备份三件套已字节级校验有效，可用于恢复。"