# -*- coding: utf-8 -*-
"""检查 v4 渲染：金线/珍珠/星芒/光羽等元素是否出现"""
from PIL import Image
from collections import Counter

im = Image.open(r"f:\Trae AI\EdenCourt-v1\tools\icon_candidates\v4\v4_rounded.png").convert("RGBA")
px = im.load()
w, h = im.size
c = Counter()
for x in range(0, w, 7):
    for y in range(0, h, 7):
        r, g, b, a = px[x, y]
        c[(r // 16 * 16, g // 16 * 16, b // 16 * 16)] += 1
print("top colors:", c.most_common(8))
cw, ch = w // 2, h // 2
print("center(珍珠附近):", px[cw, ch])
# 扫描香槟金 #D4B896 (212,184,150)
gold = sum(1 for x in range(0, w, 3) for y in range(0, h, 3)
           if abs(px[x, y][0] - 212) < 25 and abs(px[x, y][1] - 184) < 25 and abs(px[x, y][2] - 150) < 25)
print("香槟金采样点数:", gold)
