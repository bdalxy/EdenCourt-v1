# -*- coding: utf-8 -*-
"""yaml+: uiautomator dump XML 几何检测器（UI 检查流水线 ② 层第 1 个落地件）。

目标：把「视觉问题」转成「文本可分析数据」，供纯文本模型接手审图。
检测三类：
  1) 越界  —— 节点 bounds 超出屏幕、或坐标出现负数；
  2) 重叠  —— 两个「互不为祖先」的渲染节点 bbox 产生实质性重叠（>30% 重叠率）；
  3) 按行摘要 —— 把叶子节点按 Y 投影分组成「行」，输出紧凑的
                 [x1-x2 / y1-y2 / text/desc] 排布，供人眼/模型判断是否错位。

输入：Android `adb shell uiautomator dump` 生成的 XML 文件路径。
用法：python tools/detect_geometry.py <dump.xml>
"""

import re
import sys
import xml.etree.ElementTree as ET


class Node:
    """单个 dump 节点：抽出几何 + 可读文本。"""

    __slots__ = ("text", "desc", "cls", "nodes_extra", "x1", "y1", "x2", "y2",
                 "left", "top", "radix_class")

    def __init__(self, x1, y1, x2, y2, text="", desc="", cls="", resource_id=""):
        self.x1, self.y1, self.x2, self.y2 = x1, y1, x2, y2
        self.left = x1          # 左缘，供行内列位判断
        self.text = text
        self.desc = desc
        self.cls = cls
        # 仅保留类名末段（去掉 android.widget./androidx.compose 前缀，缩小文本量）
        self.nodes_extra = resource_id
        self.radix_class = cls.split(".")[-1] if cls else ""

    @property
    def area(self):
        return max(self.x2 - self.x1, 0) * max(self.y2 - self.y1, 0)

    @property
    def readable(self):
        """有意义的可见文案：text 或 content-desc，否则为空。"""
        return (self.text or self.desc).strip()

    def is_render(self):
        """判定节点是否「真正画了东西」：可作为检测候选。"""
        return self.area > 0 and (self.readable or self.radix_class in ("TextView",) or self.cls)

    def __repr__(self):
        label = self.readable if self.readable else f"<{self.radix_class}>"
        return f"[{self.x1},{self.y1}][{self.x2},{self.y2}] {label}"


def parse_dump(path):
    """解析 dump XML，返回 (屏幕尺寸, 节点扁平列表)。屏幕尺寸取自根节点。"""
    with open(path, "r", encoding="utf-8") as f:
        root = ET.fromstring(f.read())

    # 屏幕尺寸 = 根 hierarchy 下首个节点的 bounds 右下角
    screen_w, screen_h = 0, 0
    first = root.find(".//node")
    if first is not None:
        m = re.search(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", first.get("bounds", ""))
        if m:
            screen_w, screen_h = int(m.group(3)), int(m.group(4))

    nodes = []

    def walk(elem):
        # 遍历整棵视图树，扁平化所有带 bounds 的节点
        b = elem.get("bounds", "")
        m = re.search(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            nd = Node(x1, y1, x2, y2,
                      text=elem.get("text", ""),
                      desc=elem.get("content-desc", ""),
                      cls=elem.get("class", ""),
                      resource_id=elem.get("resource-id", ""))
            nodes.append(nd)
        for child in elem.findall("node"):
            walk(child)

    walk(root)
    return (screen_w, screen_h), nodes


def contains(n1, n2):
    """n2 完全被 n1 包住（用于祖先后代判定，消除父容器误报警）。"""
    return (n1.x1 <= n2.x1 and n1.y1 <= n2.y1
            and n1.x2 >= n2.x2 and n1.y2 >= n2.y2)


def overlap_area(n1, n2):
    """两 bbox 交集面积。"""
    w = min(n1.x2, n2.x2) - max(n1.x1, n2.x1)
    h = min(n1.y2, n2.y2) - max(n1.y1, n2.y1)
    if w <= 0 or h <= 0:
        return 0.0
    return float(w * h)


def detect_boundary(screen_w, screen_h, nodes):
    """① 越界检测：负坐标 / 超出右缘 / 超出下缘。"""
    issues = []
    for n in nodes:
        label = n.readable or f"<{n.radix_class}>"
        if n.x1 < 0 or n.y1 < 0 or n.y2 > screen_h or n.x2 > screen_w:
            issues.append(
                f"越界 {label} bounds=[{n.x1},{n.y1}][{n.x2},{n.y2}] "
                f"屏幕0,0-{screen_w},{screen_h}"
            )
    return issues


def detect_overlap(nodes):
    """② 重叠检测（收紧版）：两个「都带可读文本」的节点 bbox 交集 > 较小者 30%。

    为什么必须双侧有文本：
    Compose 的 Scaffold 顶层栏 / 悬浮按钮 / 抽屉遮罩 / 屏边装饰 本来就覆盖在内容之上，
    它们大多是无文本容器。只对「两个可见文案」重叠报警，能大幅消掉有意分层的误报，
    留下的才是真正的文本互相遮挡问题。
    """
    # 候选：带可读文本 + 有面积的可绘制节点
    candi = [n for n in nodes if n.is_render() and n.readable]
    issues = []
    for i in range(len(candi)):
        for j in range(i + 1, len(candi)):
            a, b = candi[i], candi[j]
            if contains(a, b) or contains(b, a):
                continue
            inter = overlap_area(a, b)
            if inter == 0:
                continue
            smaller = min(a.area, b.area) or 1
            if inter / smaller > 0.30:
                issues.append(
                    f"文本重叠 {a} <-> {b} (交集{int(inter)}px, "
                    f"{int(inter / smaller * 100)}%小框)"
                )
    return issues


def group_rows(nodes):
    """把渲染节点按 Y 投影装箱成「行」，行内按 X 排序，供对齐摘要。"""
    rows = []
    buckets = []
    for n in nodes:
        if not n.is_render():
            continue
        placed = False
        for bk in buckets:
            # 行装箱：垂直投影有交叠即归同一行
            if not (n.y2 <= bk[1] or bk[0] <= n.y1):
                bk[2].append(n)
                bk[0] = min(bk[0], n.y1)
                bk[1] = max(bk[1], n.y2)
                placed = True
                break
        if not placed:
            buckets.append([n.y1, n.y2, [n]])
    for bk in sorted(buckets, key=lambda b: b[0]):
        bk[2].sort(key=lambda n: n.x1)
        rows.append(bk[2])
    return rows


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else None
    if not path:
        print("用法: python tools/detect_geometry.py <uiautomator_dump.xml>")
        sys.exit(2)

    (screen_w, screen_h), nodes = parse_dump(path)
    print("=" * 60)
    print(f"解析文件: {path}")
    print(f"屏幕 0,0-{screen_w}×{screen_h} | 节点数 {len(nodes)}")

    # --- ① 越界 ---
    bnd = detect_boundary(screen_w, screen_h, nodes)
    print("\n[① 越界检测]")
    print(f"  共 {len(bnd)} 处" if bnd else "  无")

    # --- ② 重叠 ---
    ovl = detect_overlap(nodes)
    print("\n[② 重叠检测]")
    print(f"  共 {len(ovl)} 处" if ovl else "  无")

    # --- ③ 按行对齐摘要 ---
    print("\n[③ 按行排布摘要]（左缘右缘 → 观察是否错位）")
    rows = group_rows(nodes)
    for ri, row in enumerate(rows):
        desc = " | ".join(
            f"{n.x1}-{n.x2}x{n.y1}-{n.y2}:{n.readable or '<' + n.radix_class + '>'}"
            for n in row
        )
        print(f"  行{ri:02d}: {desc}")

    # --- 汇总行，供纯文本模型快速抓重点 ---
    print("\n=== 汇总 ===")
    print(f"越界 {len(bnd)} 处 | 重叠 {len(ovl)} 处 | 行数 {len(rows)}")

    if bnd:
        print("\n--- 越界明细 ---")
        print("\n".join("  " + x for x in bnd[:30]))
    if ovl:
        print("\n--- 重叠明细 ---")
        print("\n".join("  " + x for x in ovl[:30]))


if __name__ == "__main__":
    main()