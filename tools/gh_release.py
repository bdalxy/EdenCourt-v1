# -*- coding: utf-8 -*-
"""发布到 GitHub Release（不用 gh CLI，直连 GitHub API）。

用法：
    python tools/gh_release.py <tag> <标题> <apk路径> <资产名>
    release notes 从标准输入读取（支持多行中文）。

依赖：本机 git credential 已配置 GitHub 凭据（credential.helper=manager），
本脚本通过 `git credential fill` 在进程内取 token，绝不打印、绝不落盘。
"""
import sys
import json
import subprocess
import urllib.request

REPO = "bdalxy/EdenCourt-v1"


def get_token():
    """从 git credential 获取 GitHub token（仅存进程内存，绝不打印）。"""
    proc = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n\n",
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    for line in proc.stdout.splitlines():
        if line.startswith("password="):
            return line[len("password="):]
    return None


def create_release(token, tag, title, body):
    """创建 Pre-release，返回 release 响应 JSON。"""
    url = "https://api.github.com/repos/%s/releases" % REPO
    payload = {
        "tag_name": tag,
        "name": title,
        "body": body,
        "prerelease": True,  # 测试版 = Pre-release
    }
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": "token " + token,
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def upload_asset(token, upload_url, apk_path, asset_name):
    """上传 APK 资产，返回资产响应 JSON。"""
    url = upload_url.replace("{?name,label}", "?name=" + asset_name)
    with open(apk_path, "rb") as f:
        data = f.read()
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": "token " + token,
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/vnd.android.package-archive",
        },
        method="POST",
    )
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def main():
    if len(sys.argv) != 5:
        print("用法: python tools/gh_release.py <tag> <标题> <apk路径> <资产名>")
        sys.exit(2)
    tag, title, apk_path, asset_name = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    body = sys.stdin.read()

    token = get_token()
    if not token:
        print("错误：无法从 git credential 获取 GitHub token", file=sys.stderr)
        sys.exit(1)

    release = create_release(token, tag, title, body)
    print("创建 release 成功:", release.get("html_url"))

    asset = upload_asset(token, release["upload_url"], apk_path, asset_name)
    print("上传资产成功:", asset.get("browser_download_url"))


if __name__ == "__main__":
    main()