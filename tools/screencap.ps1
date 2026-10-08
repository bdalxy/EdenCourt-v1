# 字节流方式截取真机屏幕（避免 PowerShell `>` 文本重定向损坏 PNG）
$adb = "F:\Android\SDK\platform-tools\adb.exe"
$out = $args[0]
$p = New-Object System.Diagnostics.Process
$p.StartInfo.FileName = $adb
$p.StartInfo.Arguments = "exec-out screencap -p"
$p.StartInfo.UseShellExecute = $false
$p.StartInfo.RedirectStandardOutput = $true
$null = $p.Start()
$fs = [System.IO.File]::Create($out)
$p.StandardOutput.BaseStream.CopyTo($fs)
$p.WaitForExit()
$fs.Close()
# 校验 PNG magic
$bytes = [System.IO.File]::ReadAllBytes($out)
$magic = ($bytes[0..3] | ForEach-Object { $_.ToString("X2") }) -join " "
Write-Host ("saved: {0} ({1} bytes) magic={2}" -f $out, $bytes.Length, $magic)
