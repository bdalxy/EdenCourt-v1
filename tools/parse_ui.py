# -*- coding: utf-8 -*-
"""解析 uiautomator dump：列出所有非空文本节点 bounds，找 EdenCourt"""
import re, os

p = os.path.join(os.environ["TEMP"], "ui.xml")
s = open(p, encoding="utf-8").read()
print("file len:", len(s))
found = False
for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    t = m.group(1)
    if not t:
        continue
    x1, y1, x2, y2 = int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5))
    print(f"text={t!r} bounds=[{x1},{y1}][{x2},{y2}]")
    if "eden" in t.lower() or "庭院" in t or "Eden" in t:
        found = True
print("found:", found)
