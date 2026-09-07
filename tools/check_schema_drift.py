#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Schema 漂移检测器（防再犯机制 · 2026-09-07 落地）。

背景：
  2026-09-06 提交 71847ce 在版本号 v35 内改实体 @ColumnInfo(defaultValue) 注解，
  但忘记 bump @Database version，导致真机存量 v35 库的 Room identityHash 与新实体期望
  不一致，覆盖安装后 App 启动即崩溃（"cannot verify the data integrity"）。

  本脚本的作用：在发布 gate（G0，G1 编译之后）机器兜底拦截这一类「改了 @Entity 表结构，
  却忘记 bump 版本号」导致的同一版本号内 identityHash 漂移。

原理：
  Room 开启 exportSchema=true 时，每次编译会把「当前实体结构」的 identityHash 写入
  最新版本号的 schema json（app/schemas/.../N.json）。
  - 若正确 bump 了版本号：会出现新的 N+1.json，旧 N.json 保持不变 → 无漂移。
  - 若改实体但没 bump 版本号：Room 会重写同一个 N.json，其 identityHash 与 git HEAD
    里已锁定的 N.json 不一致 → 检测到漂移，报错拦截。

用法：
  python tools/check_schema_drift.py            # 从仓库根或任意子目录运行均可
退出码：
  0 = 通过（无漂移）
  1 = 检测到 identityHash 漂移（需 bump 版本号）
  2 = 环境异常（缺 schema 目录 / 缺 git）
"""

import json
import os
import re
import subprocess
import sys
from typing import Dict, Optional

# Room schema 导出目录（相对仓库根）
SCHEMA_DIR_REL = "app/schemas/com.bdalxy.edencourt.data.local.AppDatabase"


def repo_root():
    # type: () -> str
    """定位 git 仓库根目录（返回绝对路径；失败返回空串）。"""
    try:
        out = subprocess.check_output(
            ["git", "rev-parse", "--show-toplevel"],
            stderr=subprocess.DEVNULL,
        )
        return out.decode("utf-8").strip()
    except Exception:
        return ""


def read_schema_versions(schema_dir):
    # type: (str) -> Dict[int, str]
    """读取 schema 目录下所有 N.json，返回 {版本号(int): identityHash(str)}。"""
    result = {}  # type: Dict[int, str]
    if not os.path.isdir(schema_dir):
        return result
    for name in os.listdir(schema_dir):
        m = re.fullmatch(r"(\d+)\.json", name)
        if not m:
            continue
        path = os.path.join(schema_dir, name)
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            result[int(m.group(1))] = data.get("database", {}).get("identityHash", "")
        except (OSError, json.JSONDecodeError):
            continue
    return result


def git_head_hash(schema_dir_rel, version):
    # type: (str, int) -> Optional[str]
    """读取 git HEAD 里对应版本 json 的 identityHash；不存在则返回 None。"""
    path = "{}/{}.json".format(schema_dir_rel, version)
    try:
        out = subprocess.check_output(
            ["git", "show", "HEAD:{}".format(path)],
            stderr=subprocess.DEVNULL,
        )
        data = json.loads(out.decode("utf-8"))
        return data.get("database", {}).get("identityHash", "")
    except Exception:
        return None


def main():
    # type: () -> int
    root = repo_root()
    if not root:
        print("[check_schema_drift] 环境异常：请在本项目的 git 仓库内运行（未找到 .git）。")
        return 2

    schema_dir = os.path.join(root, SCHEMA_DIR_REL)
    versions = read_schema_versions(schema_dir)
    if not versions:
        print("[check_schema_drift] 环境异常：未找到 schema json（{} 为空）。".format(schema_dir))
        print("                 请先执行一次编译（./gradlew assembleDebug）以导出 schema。")
        return 2

    cur_version = max(versions)
    cur_hash = versions[cur_version]
    head_hash = git_head_hash(SCHEMA_DIR_REL, cur_version)

    if head_hash is None:
        # git HEAD 里没有当前版本 json → 本次已 bump 出新版本，正常
        print("[check_schema_drift] 通过：v{} 为新增版本（identityHash={}），未检测到漂移。".format(cur_version, cur_hash))
        return 0

    if head_hash == cur_hash:
        print("[check_schema_drift] 通过：v{} 的 identityHash 未漂移（{}）。".format(cur_version, cur_hash))
        return 0

    print("[check_schema_drift] ✗ 检测到 Schema 漂移！")
    print("  版本号 v{} 未变，但 identityHash 已改变：".format(cur_version))
    print("    git HEAD（已锁定）: {}".format(head_hash))
    print("    当前工作区（编译后）: {}".format(cur_hash))
    print("  疑似「改了 @Entity 表结构却忘记 bump @Database version」。")
    print("  处理：bump 版本号 → 重导 schema → 补 Migration → 补迁移测试 → 重跑本脚本。")
    return 1


if __name__ == "__main__":
    sys.exit(main())