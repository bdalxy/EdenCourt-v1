# -*- coding: utf-8 -*-
"""打印 UI dump 中所有文本节点"""
import re, os, sys

p = os.path.join(os.environ["TEMP"], sys.argv[1] if len(sys.argv) > 1 else "ui2.xml")
s = open(p, encoding="utf-8").read()
for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s):
    t = m.group(1)
    if t:
        print(t, "|", m.group(2), m.group(3), m.group(4), m.group(5))
