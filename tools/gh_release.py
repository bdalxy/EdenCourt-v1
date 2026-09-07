# -*- coding: utf-8 -*-
"""发布到 GitHub Release（不用 gh CLI，直连 GitHub API）。

用法：
    python tools/gh_release.py <tag> <标题> <apk路径> <资产名>
    release notes 从标准输入读取（支持多行中文）。

行为：
    - release 不存在则创建（Pre-release，测试版）；已存在则复用。
    - 上传前先删除「同名」旧资产（避免 422 重名冲突），再上传新资产。
    - token 通过 `git credential fill` 在进程内获取，绝不打印、绝不落盘。
"""
import sys
import json
import subprocess
import urllib.request
import urllib.error

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


def api_request(token, url, data=None, method="GET", content_type="application/json"):
    """通用 GitHub API 请求，返回 (status, json 或 None)。"""
    headers = {
        "Authorization": "token " + token,
        "Accept": "application/vnd.github+json",
    }
    body = None
    if data is not None:
        body = data if isinstance(data, bytes) else json.dumps(data).encode("utf-8")
        headers["Content-Type"] = content_type
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            if not raw:
                return resp.status, None
            return resp.status, json.loads(raw.decode("utf-8"))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return e.code, None
        err = e.read().decode("utf-8", "ignore")
        raise RuntimeError("HTTP %d: %s" % (e.code, err))


def get_release_by_tag(token, tag):
    """按 tag 查 release，不存在返回 None。"""
    url = "https://api.github.com/repos/%s/releases/tags/%s" % (REPO, tag)
    status, data = api_request(token, url)
    return data if status == 200 else None


def create_release(token, tag, title, body):
    """创建 Pre-release（测试版）。"""
    url = "https://api.github.com/repos/%s/releases" % REPO
    payload = {
        "tag_name": tag,
        "name": title,
        "body": body,
        "prerelease": True,  # 测试版 = Pre-release
    }
    _, data = api_request(token, url, data=payload, method="POST")
    return data


def list_assets(token, release_id):
    """列出 release 已有资产。"""
    url = "https://api.github.com/repos/%s/releases/%s/assets" % (REPO, release_id)
    status, data = api_request(token, url, method="GET")
    return data if status == 200 else []


def delete_asset(token, asset_id):
    """删除指定资产。"""
    url = "https://api.github.com/repos/%s/releases/assets/%s" % (REPO, asset_id)
    api_request(token, url, method="DELETE")


def upload_asset(token, upload_url, apk_path, asset_name):
    """上传 APK 资产。"""
    url = upload_url.replace("{?name,label}", "?name=" + asset_name)
    with open(apk_path, "rb") as f:
        data = f.read()
    _, resp = api_request(
        token, url, data=data, method="POST",
        content_type="application/vnd.android.package-archive",
    )
    return resp


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

    # 1. 获取或创建 release
    release = get_release_by_tag(token, tag)
    if release is None:
        release = create_release(token, tag, title, body)
    print("release:", release.get("html_url"))
    release_id = release["id"]
    upload_url = release["upload_url"]

    # 2. 删除同名旧资产（避免重名 422）
    for asset in list_assets(token, release_id):
        if asset.get("name") == asset_name:
            delete_asset(token, asset["id"])
            print("已删除旧资产:", asset_name)

    # 3. 上传新资产
    uploaded = upload_asset(token, upload_url, apk_path, asset_name)
    print("上传资产成功:", uploaded.get("browser_download_url"))


if __name__ == "__main__":
    main()