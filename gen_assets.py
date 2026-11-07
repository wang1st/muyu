# -*- coding: utf-8 -*-
"""按参考图重画：侧视木鱼（圆盖 + 横鱼口 + 右尾柄），带木纹、光影、敲击帧"""
import math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = "/var/minis/workspace/woodfish/app/src/main/res"
for d in ["drawable-nodpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi",
          "mipmap-xxxhdpi", "raw", "layout", "xml", "values"]:
    os.makedirs(f"{OUT}/{d}", exist_ok=True)

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def bez(p0, p1, p2, p3, n=24):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1-t)**3*p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t*t*p2[0] + t**3*p3[0]
        y = (1-t)**3*p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t*t*p2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts

def grain_layer(S, mask, seed=11):
    """木纹：横向漂移的细波浪线，裁剪到 mask"""
    rng = random.Random(seed)
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(10):
        y0 = rng.uniform(0.20, 0.70) * S
        x0 = rng.uniform(0.10, 0.40) * S
        x1 = rng.uniform(0.55, 0.78) * S
        amp = rng.uniform(0.004, 0.012) * S
        n = 26
        pts = []
        for i in range(n + 1):
            t = i / n
            x = x0 + (x1 - x0) * t
            y = y0 + amp * math.sin(t * math.pi * 2.2 + rng.uniform(0, 6)) * math.sin(t * math.pi)
            pts.append((x, y))
        shade = rng.randint(10, 20)
        d.line(pts, fill=(0x5A, 0x2E, 0x10, shade), width=max(1, int(S * 0.0022)), joint="curve")
    # 几个年轮结
    for _ in range(2):
        x, y = rng.uniform(0.2, 0.6) * S, rng.uniform(0.3, 0.6) * S
        for k in range(3):
            rr = S * (0.012 + k * 0.008)
            d.ellipse([x - rr, y - rr * 0.55, x + rr, y + rr * 0.55],
                      outline=(0x5A, 0x2E, 0x10, 26), width=max(1, int(S * 0.0018)))
    layer.putalpha(Image.composite(layer.split()[3], Image.new("L", (S, S), 0), mask.point(lambda v: 255 if v > 128 else 0)))
    return layer

def draw_muyu(size=512, squash=1.0, drop=0.0, flash=0, ring_r=0.0, ring_a=0, ring_w=0.012):
    """侧视木鱼。squash<1 为敲击压扁；flash 顶部高光增强；ring 为冲击波圆环"""
    SS = 4
    S = size * SS
    W = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    D = ImageDraw.Draw(W)
    f = lambda x, y: (x * S, y * S)

    # ---- 身体轮廓（闭合路径，按参考图比例 v4）----
    L    = f(0.115, 0.425)
    p_top = bez(L, f(0.160, 0.245), f(0.320, 0.155), f(0.475, 0.155))
    p_tr  = bez(f(0.475, 0.155), f(0.660, 0.160), f(0.815, 0.270), f(0.825, 0.360))
    p_mu  = bez(f(0.825, 0.360), f(0.830, 0.395), f(0.815, 0.415), f(0.800, 0.425))   # 上唇右角
    p_mi  = bez(f(0.800, 0.425), f(0.600, 0.455), f(0.420, 0.478), f(0.330, 0.487))   # 口内尖
    p_ml  = bez(f(0.330, 0.487), f(0.460, 0.468), f(0.630, 0.492), f(0.785, 0.565))   # 下唇上缘（上鼓）
    p_bl  = bez(f(0.785, 0.565), f(0.815, 0.620), f(0.700, 0.725), f(0.505, 0.735))   # 右下
    p_bm  = bez(f(0.505, 0.735), f(0.300, 0.745), f(0.155, 0.665), f(0.130, 0.585))   # 左下
    p_le  = bez(f(0.130, 0.585), f(0.100, 0.530), f(0.095, 0.470), L)                 # 左回升

    body_pts = p_top + p_tr + p_mu + p_mi + p_ml + p_bl + p_bm + p_le
    mouth_pts = p_mu + p_mi + p_ml

    # 压扁变换（以底部为锚点）
    ax, ay = 0.40 * S, 0.70 * S
    def T(p):
        return (p[0], ay - (ay - p[1]) * squash + drop * S)
    body_T = [T(p) for p in body_pts]
    mouth_T = [T(p) for p in mouth_pts]

    # ---- 地面投影 ----
    sh = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([S*0.10, S*0.655, S*0.72, S*0.755], fill=(0, 0, 0, 120))
    W.alpha_composite(sh.filter(ImageFilter.GaussianBlur(S * 0.02)))

    # ---- 尾柄（先画，身体压住根部）----
    hx0, hy0 = 0.745 * S, 0.470 * S
    hx1, hy1 = 0.895 * S, 0.535 * S
    hd2 = ImageDraw.Draw(W)
    hd2.line([(hx0, hy0), (hx1, hy1)], fill=(0x6A, 0x33, 0x12, 255), width=int(S*0.052))
    hd2.line([(hx0, hy0 - S*0.004), (hx1, hy1 - S*0.006)], fill=(0xCE, 0x8A, 0x48, 255), width=int(S*0.036))
    hd2.line([(hx0, hy0 - S*0.014), (hx1, hy1 - S*0.016)], fill=(0xF2, 0xB8, 0x74, 130), width=int(S*0.008))
    import math as _m
    ang = _m.atan2(hy1 - hy0, hx1 - hx0)
    nx, ny = -_m.sin(ang), _m.cos(ang)
    for k in range(3):
        t = 0.50 + k * 0.17
        px, py = hx0 + (hx1 - hx0) * t, hy0 + (hy1 - hy0) * t
        rr = S * 0.030
        hd2.line([(px - nx * rr, py - ny * rr), (px + nx * rr, py + ny * rr)],
                 fill=(0x58, 0x28, 0x0C, 210), width=int(S * 0.0055))
    r = S * 0.030
    hd2.ellipse([hx1 - r, hy1 - r, hx1 + r, hy1 + r], fill=(0xB0, 0x6C, 0x32, 255))
    hd2.ellipse([hx1 - r*0.6, hy1 - r*0.8, hx1 + r*0.15, hy1 - r*0.05], fill=(0xE2, 0xA6, 0x64, 255))

    # ---- 身体填充：竖直渐变 + 左上高光 ----
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).polygon(body_T, fill=255)

    base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bd = ImageDraw.Draw(base)
    top_c, mid_c, bot_c = (0xF2, 0xB9, 0x70, 255), (0xD9, 0x93, 0x4C, 255), (0x9A, 0x53, 0x22, 255)
    for y in range(S):
        t = y / S
        if t < 0.45: col = lerp(top_c, mid_c, t / 0.45)
        else: col = lerp(mid_c, bot_c, (t - 0.45) / 0.55)
        bd.line([(0, y), (S, y)], fill=col)
    W.paste(base, (0, 0), mask)

    # 左上大高光（径向）
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hl)
    hx, hy, hr = 0.40 * S, 0.285 * S, 0.31 * S
    for i in range(28, 0, -1):
        t = i / 28
        rr = hr * t
        a = int((1 - t) ** 1.5 * (95 + flash * 120))
        hd.ellipse([hx - rr, hy - rr * 0.72, hx + rr, hy + rr * 0.72], fill=(0xFF, 0xEC, 0xCC, a))
    hl = hl.filter(ImageFilter.GaussianBlur(S * 0.02))
    W.alpha_composite(Image.composite(hl, Image.new("RGBA", (S, S), (0, 0, 0, 0)), mask))

    # 木纹
    W.alpha_composite(grain_layer(S, mask))

    # 左缘/底部暗部
    edge = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(edge).ellipse([S*0.02, S*0.42, S*0.30, S*0.74], fill=(0x3A, 0x1A, 0x08, 90))
    edge = edge.filter(ImageFilter.GaussianBlur(S * 0.035))
    W.alpha_composite(Image.composite(edge, Image.new("RGBA", (S, S), (0, 0, 0, 0)), mask))

    # ---- 鱼口内部 ----
    mouth_mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mouth_mask).polygon(mouth_T, fill=255)
    mimg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    md = ImageDraw.Draw(mimg)
    for y in range(S):
        t = y / S
        col = lerp((0x2A, 0x13, 0x05, 255), (0x14, 0x09, 0x03, 255), abs(t - 0.5) * 2) if 0.4 < t < 0.6 else (0x33, 0x18, 0x07, 255)
        md.line([(0, y), (S, y)], fill=col)
    W.alpha_composite(Image.composite(mimg, Image.new("RGBA", (S, S), (0, 0, 0, 0)), mouth_mask))
    # 上唇下表面投下的软阴影
    us = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(us).polygon([T(p) for p in p_mi] + [(x, y + S * 0.045) for x, y in [T(p) for p in p_mi]][::-1],
                               fill=(0x2A, 0x12, 0x05, 110))
    W.alpha_composite(us.filter(ImageFilter.GaussianBlur(S * 0.012)))
    # 口内上壁反射光 + 下唇受光
    D.line([T(p) for p in p_mi], fill=(0x7A, 0x40, 0x18, 150), width=int(S*0.006), joint="curve")
    D.line([T(p) for p in p_ml], fill=(0xFF, 0xC8, 0x88, 130), width=int(S*0.006), joint="curve")

    # ---- 轮廓压暗 ----
    outline = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(outline).polygon(body_T, outline=(0x3E, 0x1C, 0x08, 255), width=int(S * 0.008))
    W.alpha_composite(outline.filter(ImageFilter.GaussianBlur(S * 0.002)))

    # ---- 敲击冲击波 ----
    if ring_r > 0:
        ring = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        rd = ImageDraw.Draw(ring)
        cx, cy = 0.46 * S, 0.40 * S
        r = ring_r * S
        rd.ellipse([cx - r, cy - r * 0.8, cx + r, cy + r * 0.8],
                   outline=(0xFF, 0xE2, 0xB0, ring_a), width=max(2, int(S * ring_w)))
        W.alpha_composite(ring.filter(ImageFilter.GaussianBlur(S * 0.004)))

    # 裁切 & 缩放
    bbox = W.getbbox()
    img = W.crop(bbox)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    img.thumbnail((size, size), Image.LANCZOS)
    canvas.paste(img, ((size - img.width) // 2, (size - img.height) // 2), img)
    return canvas

# ---- 输出 ----
normal = draw_muyu(512)
normal.save(f"{OUT}/drawable-nodpi/fish.png")
draw_muyu(512, squash=0.90, drop=0.012, flash=1, ring_r=0.20, ring_a=185, ring_w=0.014).save(f"{OUT}/drawable-nodpi/fish_press1.png")
draw_muyu(512, squash=0.96, drop=0.004, flash=0.4, ring_r=0.30, ring_a=70, ring_w=0.010).save(f"{OUT}/drawable-nodpi/fish_press2.png")

# 图标
def make_icon(px):
    S = px * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, S, S], radius=S * 0.22, fill=(0x2A, 0x1C, 0x10, 255))
    d.rounded_rectangle([S*0.03, S*0.03, S*0.97, S*0.97], radius=S*0.20,
                        outline=(0xC0, 0x8A, 0x4A, 255), width=int(S*0.012))
    fimg = draw_muyu(int(S * 0.92))
    img.alpha_composite(fimg, (int(S * 0.04), int(S * 0.04)))
    return img.resize((px, px), Image.LANCZOS)

for px, dn in [(72, "mipmap-hdpi"), (96, "mipmap-xhdpi"), (144, "mipmap-xxhdpi"), (192, "mipmap-xxxhdpi")]:
    make_icon(px).save(f"{OUT}/{dn}/ic_launcher.png")

# widget 预览
pv = Image.new("RGBA", (512, 384), (0x24, 0x1A, 0x12, 255))
pd = ImageDraw.Draw(pv)
pd.rounded_rectangle([0, 0, 511, 383], radius=40, fill=(0x2C, 0x21, 0x16, 255))
fimg = draw_muyu(300)
pv.alpha_composite(fimg, (106, 18))
try:
    font = ImageFont.truetype("/usr/share/fonts/noto/NotoSansCJK-Bold.ttc", 42)
except Exception:
    font = ImageFont.load_default()
pd.text((256, 330), "功德 +1", font=font, fill=(0xE8, 0xC8, 0x88), anchor="mm")
pv.save(f"{OUT}/drawable-nodpi/widget_preview.png")

print("素材 v2 生成完成")
