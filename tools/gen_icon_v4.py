# -*- coding: utf-8 -*-
"""
按 DeepSeek 专家「水晶樱羽」方案渲染预览图（第二次讨论定稿）。
结构：水晶棱面樱花（折线花瓣 + 切割线 + 香槟金描边）
      + 珍珠花蕊 + 星芒 + 间隙光羽 + 背景放射光纹。
专家给出 108x108 viewBox SVG，本脚本按它逐像素还原。
输出 tools/icon_candidates/v4/（圆角方形 + 圆形）
"""
import os
import math
import re
import numpy as np
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon_candidates", "v4")
os.makedirs(OUT, exist_ok=True)

VB = 108          # 专家 viewBox
SIZE = 1080       # 渲染分辨率
SC = SIZE / VB    # 缩放系数

# ---------- 配色（专家配色总表） ----------
BG_STOPS = [(0.00, (255, 248, 251)), (0.55, (253, 232, 240)),
            (0.80, (236, 228, 248)), (1.00, (224, 232, 250))]  # 珍珠白→粉→紫→蓝
PETAL_STOPS = [(0.0, (249, 197, 213)), (0.45, (253, 224, 232)),
               (0.80, (255, 245, 247)), (1.0, (255, 255, 255))]  # 珍珠粉→白
GOLD = (212, 184, 150)      # 香槟金 #D4B896
PEARL_STOPS = [(0.0, (255, 255, 255)), (0.35, (255, 245, 240)),
               (0.70, (245, 224, 216)), (1.0, (232, 204, 208))]


# ---------- SVG path 解析（支持 M/L/C/Z，坐标可带空格逗号） ----------
def parse_path(d):
    """解析 SVG path 字符串，返回点列表（108 坐标系）。仅支持 M/L/C/Z 绝对命令"""
    nums = re.findall(r"[-+]?\d*\.?\d+(?:[eE][-+]?\d+)?", d)
    cmds = re.findall(r"[MLCZ]", d)
    pts = []
    cur = (0.0, 0.0)
    idx = 0
    # 重建命令+参数序列
    tokens = re.findall(r"([MLCZ])\s*|([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)", d)
    seq = []
    for c, n in tokens:
        if c:
            seq.append(c)
        else:
            seq.append(float(n))
    i = 0
    while i < len(seq):
        t = seq[i]
        if isinstance(t, str):
            cmd = t
            i += 1
            continue
        if cmd == "M":
            cur = (t, seq[i + 1]); pts.append(cur); i += 2; cmd = "L"
        elif cmd == "L":
            cur = (t, seq[i + 1]); pts.append(cur); i += 2
        elif cmd == "C":
            x1, y1, x2, y2, x, y = seq[i], seq[i + 1], seq[i + 2], seq[i + 3], seq[i + 4], seq[i + 5]
            for s in range(1, 33):
                u = s / 32.0
                bx = (1 - u) ** 3 * cur[0] + 3 * (1 - u) ** 2 * u * x1 + 3 * (1 - u) * u ** 2 * x2 + u ** 3 * x
                by = (1 - u) ** 3 * cur[1] + 3 * (1 - u) ** 2 * u * y1 + 3 * (1 - u) * u ** 2 * y2 + u ** 3 * y
                pts.append((bx, by))
            cur = (x, y); i += 6
        elif cmd == "Z":
            break
    return pts


def rot_pt(x, y, cx, cy, deg):
    """绕中心旋转（108 坐标系）"""
    a = math.radians(deg)
    dx, dy = x - cx, y - cy
    return (cx + dx * math.cos(a) - dy * math.sin(a),
            cy + dx * math.sin(a) + dy * math.cos(a))


# ---------- 专家给出的 path 数据 ----------
PETAL_D = ("M54,54 L50,38 L46,28 L48,24.5 L51,26.5 L54,25 "
           "L57,26.5 L60,24.5 L62,28 L58,38 Z")
FEATHER_D = ("M80,48 C79,46 77,46 75,47 C74,45 72,45 71,47 "
             "C69,48 68,50 68,51 C70,53 72,53 74,52 C76,53 78,52 79,51 "
             "C81,50 81,49 80,48 Z")
STAR_D = "M54,42 L55,51 L64,54 L55,57 L54,66 L53,57 L44,54 L53,51 Z"


def poly_mask(pts_px, size):
    """多边形 mask（numpy bool），pts_px 为像素坐标点"""
    img = Image.new("L", (size, size), 0)
    ImageDraw.Draw(img).polygon(pts_px, fill=255)
    return np.asarray(img, dtype=bool)


def render(name, round_icon=False):
    size = SIZE
    c = VB / 2.0
    cx = cy = c * SC
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64)

    # ========== 1. 背景径向渐变 ==========
    r_bg = np.hypot(xx - cx, yy - cy) / (66 * SC)
    r_bg = np.clip(r_bg, 0, 1)
    bg = np.zeros((size, size, 3), dtype=np.float64)
    for k in range(len(BG_STOPS) - 1):
        r0, c0 = BG_STOPS[k]
        r1, c1 = BG_STOPS[k + 1]
        m = (r_bg >= r0) & (r_bg <= r1)
        t = np.clip((r_bg - r0) / (r1 - r0), 0, 1)[:, :, None]
        bg[m] = np.array(c0)[None, None, :] * (1 - t[m]) + np.array(c1)[None, None, :] * t[m]
    # 超半径处用最后一色
    m_last = r_bg > BG_STOPS[-1][0]
    bg[m_last] = BG_STOPS[-1][1]
    canvas = Image.fromarray(bg.astype(np.uint8), "RGB").convert("RGBA")

    if round_icon:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size, size], fill=255)
        canvas.putalpha(mask)
    else:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size, size], radius=int(size * 0.22), fill=255)
        canvas.putalpha(mask)

    # ========== 2. 放射光纹（24 条白色 10%，半径 18→52） ==========
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    for deg in range(0, 360, 15):
        a = math.radians(deg)
        x1 = cx + math.cos(a) * 18 * SC
        y1 = cy + math.sin(a) * 18 * SC
        x2 = cx + math.cos(a) * 52 * SC
        y2 = cy + math.sin(a) * 52 * SC
        od.line([(x1, y1), (x2, y2)], fill=(255, 255, 255, 26), width=max(1, int(0.5 * SC)))
    canvas.alpha_composite(overlay)

    # ========== 2.5 樱花雨白点（小光点 + 微型花瓣，随机散布，避开中心主体） ==========
    rng = np.random.default_rng(20260829)
    rain = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rain)
    inner_r = 26 * SC          # 中心主体保护半径
    outer_r = 54 * SC          # 散布半径上限
    n_dots = 26                # 光点数量
    n_petals = 5               # 微型花瓣数量
    placed = []
    for _ in range(n_dots + n_petals):
        for _try in range(60):
            ang = rng.uniform(0, 2 * math.pi)
            rad = rng.uniform(inner_r, outer_r)
            x = cx + math.cos(ang) * rad
            y = cy + math.sin(ang) * rad
            if 8 * SC < x < (size - 8 * SC) and 8 * SC < y < (size - 8 * SC):
                placed.append((x, y, ang))
                break
    for i, (x, y, ang) in enumerate(placed):
        if i < n_dots:
            # 小光点：半径 0.8~1.8dp，白色，透明度 50~90%
            r = rng.uniform(0.8, 1.8) * SC
            al = int(rng.uniform(120, 230))
            rd.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, al))
        else:
            # 微型花瓣：小椭圆，旋转随机角度，白色偏粉，半透明
            w = rng.uniform(2.2, 3.2) * SC
            h = rng.uniform(1.4, 2.0) * SC
            rot = rng.uniform(0, 180)
            al = int(rng.uniform(130, 200))
            # 用椭圆近似花瓣，白色微粉
            rd.ellipse([x - w / 2, y - h / 2, x + w / 2, y + h / 2],
                       fill=(255, 245, 248, al))
    canvas.alpha_composite(rain)

    # ========== 3. 五片水晶花瓣 ==========
    petal_pts = parse_path(PETAL_D)
    for i in range(5):
        deg = 72 * i
        # 旋转后的花瓣点（108 -> 像素）
        rp = [rot_pt(x, y, c, c, deg) for x, y in petal_pts]
        rp_px = [(x * SC, y * SC) for x, y in rp]
        m = poly_mask(rp_px, size)

        # 花瓣线性渐变：沿花瓣方向（基部中心 -> 尖端，尖端角度 deg-90）
        tip_ang = math.radians(deg - 90)
        # 花瓣方向向量（从中心指向尖端）
        tip = rot_pt(c, c - 25, c, c, deg)  # 尖端原始 y=25
        dx, dy = tip[0] - c, tip[1] - c
        L2 = dx * dx + dy * dy
        proj = ((xx - cx) * dx + (yy - cy) * dy) / L2  # 0=基部 1=尖端
        proj = np.clip(proj, 0, 1)
        petal = np.zeros((size, size, 3), dtype=np.float64)
        for k in range(len(PETAL_STOPS) - 1):
            r0, c0 = PETAL_STOPS[k]
            r1, c1 = PETAL_STOPS[k + 1]
            mm = (proj >= r0) & (proj <= r1)
            t = np.clip((proj - r0) / (r1 - r0), 0, 1)[:, :, None]
            petal[mm] = np.array(c0)[None, None, :] * (1 - t[mm]) + np.array(c1)[None, None, :] * t[mm]
        petal[~m] = 0
        petal_img = Image.fromarray(petal.astype(np.uint8), "RGB").convert("RGBA")
        # 只保留 mask 内的像素（其余透明）
        a = Image.new("L", (size, size), 0)
        ImageDraw.Draw(a).polygon(rp_px, fill=255)
        petal_img.putalpha(a)
        canvas.alpha_composite(petal_img)

        # 香槟金描边（沿轮廓画线）
        border = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        bd = ImageDraw.Draw(border)
        bd.line(rp_px + [rp_px[0]], fill=GOLD + (178,), width=max(2, int(0.7 * SC)), joint="curve")
        canvas.alpha_composite(border)

        # 内部切割线（3 条白色）
        cuts = [((c, c), rot_pt(c, c - 26, c, c, deg)),
                ((c, c), rot_pt(c + 6, c - 26, c, c, deg)),
                (rot_pt(c - 8, c - 16, c, c, deg), rot_pt(c + 8, c - 16, c, c, deg))]
        alphas = [127, 89, 64]
        cut = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        cd = ImageDraw.Draw(cut)
        for (p1, p2), al in zip(cuts, alphas):
            cd.line([(p1[0] * SC, p1[1] * SC), (p2[0] * SC, p2[1] * SC)],
                    fill=(255, 255, 255, al), width=max(1, int(0.8 * SC)))
        canvas.alpha_composite(cut)

    # ========== 4. 间隙光羽（5 枚，36°起错开） ==========
    feat_pts = parse_path(FEATHER_D)
    for i in range(5):
        deg = 36 + 72 * i
        rp = [rot_pt(x, y, c, c, deg) for x, y in feat_pts]
        rp_px = [(x * SC, y * SC) for x, y in rp]
        fimg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        fd = ImageDraw.Draw(fimg)
        fd.polygon(rp_px, fill=(255, 255, 255, 166))
        fd.line(rp_px + [rp_px[0]], fill=GOLD + (102,), width=max(1, int(0.3 * SC)))
        canvas.alpha_composite(fimg)

    # ========== 5. 花蕊：珍珠 + 星芒 ==========
    # 珍珠径向渐变
    pr = 5.5 * SC
    pearl = np.zeros((size, size, 3), dtype=np.float64)
    pr_mask = (np.hypot(xx - cx, yy - cy) <= pr)
    rp_norm = np.clip(np.hypot(xx - cx, yy - cy) / pr, 0, 1)
    for k in range(len(PEARL_STOPS) - 1):
        r0, c0 = PEARL_STOPS[k]
        r1, c1 = PEARL_STOPS[k + 1]
        mm = pr_mask & (rp_norm >= r0) & (rp_norm <= r1)
        t = np.clip((rp_norm - r0) / (r1 - r0), 0, 1)[:, :, None]
        pearl[mm] = np.array(c0)[None, None, :] * (1 - t[mm]) + np.array(c1)[None, None, :] * t[mm]
    pearl_img = Image.fromarray(pearl.astype(np.uint8), "RGB").convert("RGBA")
    pa = Image.new("L", (size, size), 0)
    ImageDraw.Draw(pa).ellipse([cx - pr, cy - pr, cx + pr, cy + pr], fill=255)
    pearl_img.putalpha(pa)
    canvas.alpha_composite(pearl_img)

    # 珍珠高光椭圆
    hl = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hd.ellipse([cx - 2.2 * SC, cy - 1.8 * SC - 2.5 * SC, cx + 0 * SC, cy + 0 * SC - 2.5 * SC],
               fill=(255, 255, 255, 230))
    canvas.alpha_composite(hl)

    # 四角星芒
    star_pts = parse_path(STAR_D)
    star_px = [(x * SC, y * SC) for x, y in star_pts]
    simg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(simg).polygon(star_px, fill=(255, 255, 255, 191))
    canvas.alpha_composite(simg)

    # 星芒十字细线
    cross = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cross)
    cd.line([(cx, (c - 16) * SC), (cx, (c + 16) * SC)], fill=(255, 255, 255, 102), width=max(1, int(0.6 * SC)))
    cd.line([((c - 16) * SC, cy), ((c + 16) * SC, cy)], fill=(255, 255, 255, 102), width=max(1, int(0.6 * SC)))
    canvas.alpha_composite(cross)

    out = os.path.join(OUT, f"{name}.png")
    canvas.save(out)
    print("saved:", out)


if __name__ == "__main__":
    render("v4_rounded")
    render("v4_round", round_icon=True)
    print("done")
