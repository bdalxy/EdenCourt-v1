# -*- coding: utf-8 -*-
"""
生成 Android 矢量图标资源（水晶樱羽 · 樱花雨版，专家 v4 方案定稿）。
把专家 SVG 的 <use transform="rotate"> 在 Python 里展开成独立 path，
避免 VectorDrawable group 旋转导致线性渐变方向错乱。

产出：
  app/src/main/res/drawable/ic_launcher_background.xml
  app/src/main/res/drawable/ic_launcher_foreground.xml
  app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
"""
import os
import math
import re
import random

APP_RES = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "app", "src", "main", "res")


def parse_path(d):
    """解析 SVG path 字符串（仅 M/L/C/Z），返回点列表（108 坐标系）"""
    seq = []
    for c, n in re.findall(r"([MLCZ])\s*|([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)", d):
        seq.append(c if c else float(n))
    pts, cur, i, cmd = [], (0.0, 0.0), 0, None
    while i < len(seq):
        t = seq[i]
        if isinstance(t, str):
            cmd, i = t, i + 1
            continue
        if cmd == "M":
            cur = (t, seq[i + 1]); pts.append(cur); i += 2; cmd = "L"
        elif cmd == "L":
            cur = (t, seq[i + 1]); pts.append(cur); i += 2
        elif cmd == "C":
            x1, y1, x2, y2, x, y = seq[i], seq[i + 1], seq[i + 2], seq[i + 3], seq[i + 4], seq[i + 5]
            for s in range(1, 33):
                u = s / 32.0
                pts.append(((1 - u) ** 3 * cur[0] + 3 * (1 - u) ** 2 * u * x1 + 3 * (1 - u) * u ** 2 * x2 + u ** 3 * x,
                            (1 - u) ** 3 * cur[1] + 3 * (1 - u) ** 2 * u * y1 + 3 * (1 - u) * u ** 2 * y2 + u ** 3 * y))
            cur = (x, y); i += 6
        elif cmd == "Z":
            break
    return pts


def rot_pt(x, y, cx, cy, deg):
    a = math.radians(deg)
    dx, dy = x - cx, y - cy
    return (cx + dx * math.cos(a) - dy * math.sin(a),
            cy + dx * math.sin(a) + dy * math.cos(a))


def fmt_pts(pts, closed=True):
    """点列表 -> SVG path 字符串（保留 1 位小数）"""
    s = "M%.1f,%.1f" % pts[0]
    for x, y in pts[1:]:
        s += "L%.1f,%.1f" % (x, y)
    if closed:
        s += "Z"
    return s


def fmt_path_with_c(commands, rot=0):
    """按 SVG 命令序列生成 path 字符串；M/C/L 命令坐标统一旋转（rot 度，绕 54,54）。
    返回带曲线控制的矢量 path（VectorDrawable 原生支持 C 三次贝塞尔）"""
    out = []
    for cmd, nums in commands:
        pts = [(nums[i], nums[i + 1]) for i in range(0, len(nums), 2)]
        rp = [rot_pt(x, y, 54, 54, rot) for x, y in pts]
        if cmd == "M":
            out.append("M%.1f,%.1f" % rp[0])
            for p in rp[1:]:
                out.append("L%.1f,%.1f" % p)
        elif cmd == "C":
            # 三次贝塞尔：两个控制点 + 终点
            out.append("C%.1f,%.1f %.1f,%.1f %.1f,%.1f"
                       % (rp[0][0], rp[0][1], rp[1][0], rp[1][1], rp[2][0], rp[2][1]))
        elif cmd == "L":
            out.append("L%.1f,%.1f" % rp[0])
    return "".join(out)


def parse_path_cmds(d):
    """解析 SVG path 为 [(命令, [数字...])] 序列（仅 M/C/L/Z）"""
    seq = []
    for c, n in re.findall(r"([MLCZ])\s*|([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)", d):
        seq.append(c if c else float(n))
    cmds, i = [], 0
    while i < len(seq):
        t = seq[i]
        if isinstance(t, str):
            cmds.append([t, []]); i += 1
            continue
        cmds[-1][1].append(t); i += 1
    # 转为成对的 (命令, 坐标列表) 三元组形式，供 fmt_path_with_c 使用
    out = []
    for cmd, nums in cmds:
        if cmd in "MLC":
            out.append((cmd, nums))
    return out


def circle_path(cx, cy, r):
    """圆形 path 数据"""
    return ("M%.1f,%.1f a%.1f,%.1f 0 1,0 %.3f,0 a%.1f,%.1f 0 1,0 %.3f,0 Z"
            % (cx - r, cy, r, r, 2 * r, r, r, -2 * r))


def ellipse_path(cx, cy, rx, ry):
    """椭圆 path 数据（两段半椭圆）"""
    return ("M%.1f,%.1f a%.1f,%.1f 0 1,0 %.3f,0 a%.1f,%.1f 0 1,0 %.3f,0 Z"
            % (cx - rx, cy, rx, ry, 2 * rx, rx, ry, -2 * rx))


def line_path(x1, y1, x2, y2):
    return "M%.1f,%.1f L%.1f,%.1f" % (x1, y1, x2, y2)


# ---------- 专家 SVG 数据 ----------
PETAL_D = ("M54,54 L50,38 L46,28 L48,24.5 L51,26.5 L54,25 "
           "L57,26.5 L60,24.5 L62,28 L58,38 Z")
FEATHER_D = ("M80,48 C79,46 77,46 75,47 C74,45 72,45 71,47 "
             "C69,48 68,50 68,51 C70,53 72,53 74,52 C76,53 78,52 79,51 "
             "C81,50 81,49 80,48 Z")
STAR_D = "M54,42 L55,51 L64,54 L55,57 L54,66 L53,57 L44,54 L53,51 Z"

petal_pts = parse_path(PETAL_D)
feather_pts = parse_path(FEATHER_D)


def petal_gradient_attr(deg):
    """每片花瓣的线性渐变（基部 54,54 -> 尖端方向），输出 <aapt:attr> 片段"""
    tip = rot_pt(54, 25, 54, 54, deg)  # 尖端旋转
    return ('<aapt:attr name="android:fillColor">'
            '<gradient android:type="linear" '
            'android:startX="54.0" android:startY="54.0" '
            'android:endX="%.1f" android:endY="%.1f">'
            '<item android:offset="0.0" android:color="#F9C5D5"/>'
            '<item android:offset="0.45" android:color="#FDE0E8"/>'
            '<item android:offset="0.8" android:color="#FFF5F7"/>'
            '<item android:offset="1.0" android:color="#FFFFFF"/>'
            '</gradient></aapt:attr>' % tip)


# ---------- 前景：5 片花瓣 + 切割线 + 光羽 + 花蕊/星芒 ----------
fg_parts = []

# ① 5 片花瓣（含香槟金描边 + 切割线）
for i in range(5):
    deg = 72 * i
    rp = [rot_pt(x, y, 54, 54, deg) for x, y in petal_pts]
    pd = fmt_pts(rp)
    fg_parts.append(
        '<path android:pathData="%s" android:strokeColor="#D4B896" '
        'android:strokeWidth="0.7" android:strokeAlpha="0.7" '
        'android:strokeLineJoin="round">%s</path>' % (pd, petal_gradient_attr(deg)))
    # 切割线：左 (54,54)->(48,28) 右 (54,54)->(60,28) 横 (46,38)->(62,38)
    for seg, al in [(((54, 54), (48, 28)), 0.5), (((54, 54), (60, 28)), 0.35), (((46, 38), (62, 38)), 0.25)]:
        pa, pb = seg
        p1 = rot_pt(pa[0], pa[1], 54, 54, deg)
        p2 = rot_pt(pb[0], pb[1], 54, 54, deg)
        fg_parts.append(
            '<path android:pathData="%s" android:strokeColor="#FFFFFF" '
            'android:strokeWidth="0.8" android:strokeAlpha="%s"/>'
            % (line_path(p1[0], p1[1], p2[0], p2[1]), al))

# ② 5 枚间隙光羽（保留 C 三次贝塞尔，旋转控制点后直接输出）
feather_cmds = parse_path_cmds(FEATHER_D)
for i in range(5):
    deg = 36 + 72 * i
    fd = fmt_path_with_c(feather_cmds, deg) + "Z"
    fg_parts.append(
        '<path android:pathData="%s" android:fillColor="#FFFFFF" android:fillAlpha="0.65" '
        'android:strokeColor="#D4B896" android:strokeWidth="0.3" android:strokeAlpha="0.4"/>'
        % fd)

# ③ 珍珠（径向渐变）
fg_parts.append(
    '<path android:pathData="%s">'
    '<aapt:attr name="android:fillColor">'
    '<gradient android:type="radial" android:centerX="54.0" android:centerY="54.0" '
    'android:gradientRadius="5.5">'
    '<item android:offset="0.0" android:color="#FFFFFF"/>'
    '<item android:offset="0.35" android:color="#FFF5F0"/>'
    '<item android:offset="0.7" android:color="#F5E0D8"/>'
    '<item android:offset="1.0" android:color="#E8CCD0"/>'
    '</gradient></aapt:attr></path>' % circle_path(54, 54, 5.5))
# 珍珠高光
fg_parts.append(
    '<path android:pathData="%s" android:fillColor="#FFFFFF" android:fillAlpha="0.9"/>'
    % ellipse_path(52, 49, 2.2, 1.8))
# 四角星芒
fg_parts.append(
    '<path android:pathData="%s" android:fillColor="#FFFFFF" android:fillAlpha="0.75"/>' % STAR_D)
# 星芒十字细线
fg_parts.append(
    '<path android:pathData="%s" android:strokeColor="#FFFFFF" android:strokeWidth="0.6" '
    'android:strokeAlpha="0.4"/>' % line_path(54, 38, 54, 70))
fg_parts.append(
    '<path android:pathData="%s" android:strokeColor="#FFFFFF" android:strokeWidth="0.6" '
    'android:strokeAlpha="0.4"/>' % line_path(38, 54, 70, 54))

FG = ('<vector xmlns:android="http://schemas.android.com/apk/res/android" '
      'xmlns:aapt="http://schemas.android.com/aapt" '
      'android:width="108dp" android:height="108dp" '
      'android:viewportWidth="108" android:viewportHeight="108">'
      + "".join(fg_parts) + "</vector>")

# ---------- 背景：径向渐变 + 放射光纹 + 樱花雨白点 ----------
bg_parts = []
# 渐变矩形
bg_parts.append(
    '<path android:pathData="M0,0 L108,0 L108,108 L0,108 Z">'
    '<aapt:attr name="android:fillColor">'
    '<gradient android:type="radial" android:centerX="54.0" android:centerY="54.0" '
    'android:gradientRadius="66">'
    '<item android:offset="0.0" android:color="#FFF8FB"/>'
    '<item android:offset="0.55" android:color="#FDE8F0"/>'
    '<item android:offset="0.8" android:color="#ECE4F8"/>'
    '<item android:offset="1.0" android:color="#E0E8FA"/>'
    '</gradient></aapt:attr></path>')

# 放射光纹（24 条，白色 10%）
for deg in range(0, 360, 15):
    a = math.radians(deg)
    x1 = 54 + math.cos(a) * 18
    y1 = 54 + math.sin(a) * 18
    x2 = 54 + math.cos(a) * 52
    y2 = 54 + math.sin(a) * 52
    bg_parts.append(
        '<path android:pathData="%s" android:strokeColor="#FFFFFF" android:strokeWidth="0.5" '
        'android:strokeAlpha="0.10" android:strokeLineCap="round"/>'
        % line_path(x1, y1, x2, y2))

# 樱花雨白点（固定 seed，与预览图一致：26 光点 + 5 微型花瓣）
rng = random.Random(20260829)
placed = []
for _ in range(31):
    for _try in range(60):
        ang = rng.uniform(0, 2 * math.pi)
        rad = rng.uniform(26, 54)
        x = 54 + math.cos(ang) * rad
        y = 54 + math.sin(ang) * rad
        if 8 < x < 100 and 8 < y < 100:
            placed.append((x, y, ang))
            break
for i, (x, y, ang) in enumerate(placed):
    if i < 26:
        r = rng.uniform(0.8, 1.8)
        al = int(rng.uniform(120, 230)) / 255.0
        bg_parts.append(
            '<path android:pathData="%s" android:fillColor="#FFFFFF" android:fillAlpha="%.2f"/>'
            % (circle_path(x, y, r), al))
    else:
        w = rng.uniform(2.2, 3.2)
        h = rng.uniform(1.4, 2.0)
        al = int(rng.uniform(130, 200)) / 255.0
        bg_parts.append(
            '<path android:pathData="%s" android:fillColor="#FFF5F8" android:fillAlpha="%.2f"/>'
            % (ellipse_path(x, y, w / 2, h / 2), al))

BG = ('<vector xmlns:android="http://schemas.android.com/apk/res/android" '
      'xmlns:aapt="http://schemas.android.com/aapt" '
      'android:width="108dp" android:height="108dp" '
      'android:viewportWidth="108" android:viewportHeight="108">'
      + "".join(bg_parts) + "</vector>")

ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n')

# ---------- 写文件 ----------
os.makedirs(os.path.join(APP_RES, "drawable"), exist_ok=True)
os.makedirs(os.path.join(APP_RES, "mipmap-anydpi-v26"), exist_ok=True)
with open(os.path.join(APP_RES, "drawable", "ic_launcher_background.xml"), "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n' + BG + "\n")
with open(os.path.join(APP_RES, "drawable", "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n' + FG + "\n")
with open(os.path.join(APP_RES, "mipmap-anydpi-v26", "ic_launcher.xml"), "w", encoding="utf-8") as f:
    f.write(ADAPTIVE)
with open(os.path.join(APP_RES, "mipmap-anydpi-v26", "ic_launcher_round.xml"), "w", encoding="utf-8") as f:
    f.write(ADAPTIVE)
print("OK ->", APP_RES)
