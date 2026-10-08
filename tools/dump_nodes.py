# -*- coding: utf-8 -*-
"""宽松解析 UI dump 节点，打印 text/content-desc/class/bounds"""
import re, os, sys

p = os.path.join(os.environ["TEMP"], sys.argv[1] if len(sys.argv) > 1 else "ui4.xml")
s = open(p, encoding="utf-8").read()
for m in re.finditer(r'<node\b[^>]*>', s):
    node = m.group(0)
    t = re.search(r'text="([^"]*)"', node)
    cd = re.search(r'content-desc="([^"]*)"', node)
    cls = re.search(r'class="([^"]*)"', node)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    tv = t.group(1) if t else ""
    cv = cd.group(1) if cd else ""
    if tv or cv:
        print(f"text={tv!r} desc={cv!r} class={cls.group(1) if cls else ''} bounds=[{b.group(1)},{b.group(2)}][{b.group(3)},{b.group(4)}]")
