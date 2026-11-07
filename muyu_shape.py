# -*- coding: utf-8 -*-
"""
木鱼剪影生成器（钢笔路径法）
=============================

流程：
  参考图 → 物体蒙版 → 径向采样取点 → 高斯平滑 → Catmull-Rom 闭合样条
        → 鱼口（内部暗部）按列拟合中线/厚度 → 端点收细 → 底部压平 → 8× 超采样渲染
  同时导出 SVG 矢量路径（可直接在 Figma / Illustrator 编辑）

输入：REF（参考图，浅色背景上的木鱼）
输出：
  res/drawable-nodpi/fish_flat.png        静息剪影（纸白，镂空鱼口）
  res/drawable-nodpi/fish_flat_press.png  敲击帧（压缩 95.5% + 加深）
  res/drawable-nodpi/fish_flat_press2.png 回弹帧（压缩 98.5%）
  build/muyu.svg                          SVG 矢量路径

所有可调参数集中在「参数区」，改完直接 `python3 muyu_shape.py`。
"""
import math
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# ============================== 参数区 ==============================
REF = "/var/minis/attachments/uploads/1000183948.jpg"   # 参考图
RES_DIR = "/var/minis/workspace/woodfish/app/src/main/res"
SVG_OUT = "/var/minis/workspace/woodfish/build/muyu.svg"

PAPER = (0xE9, 0xE4, 0xDA)          # 纸白
PAPER_PRESS = 0.90                  # 敲击帧亮度系数
OUT_SIZE = 512                      # 输出位图尺寸
SS = 8                              # 渲染超采样倍数

# —— 轮廓 ——
CONTOUR_SAMPLES = 480               # 径向采样射线数
CONTOUR_SMOOTH = 41                 # 圆周高斯平滑窗口（越大越圆，越小越贴原图）
SPLINE_ANCHORS = 6                  # 每几个采样点取一个样条锚点(越少越平滑)
SPLINE_SUBDIV = 10                  # 每段样条插值点数

# —— 鱼口 ——
MOUTH_MIN_DARK = 118                # 判定"口内暗部"的亮度阈值
MOUTH_ERODE_STEPS = 6               # 排除外轮廓描边的腐蚀次数
MOUTH_FIT_DEGREE = 2                # 中线/厚度拟合多项式阶数
MOUTH_HALF_MIN = 2.0                # 缝的最小半高(像素,参考图坐标)
MOUTH_HALF_MAX = 40.0               # 缝的最大半高
MOUTH_SHIFT_DOWN = 34.0             # 整条缝下移量（越大上半部越大）
MOUTH_TAIL_DROOP = 26.0             # 尾部下压量（越靠右越低）
MOUTH_TAIL_TAPER = 0.45             # 末端收细比例(0=不收,0.45=收45%)

# —— 底部压平（"坐得住"）——
FLAT_PIVOT = 0.62                   # 从画面高度 62% 处开始压
FLAT_RATIO = 0.45                   # 底部纵向压缩比（越小越平）

# ============================== 实现 ==============================


def load_object_mask(path):
    img = Image.open(path).convert("RGB")
    a = np.asarray(img).astype(np.float32)
    bg = np.array([a[5, 5, 0], a[5, 5, 1], a[5, 5, 2]])
    dist = np.sqrt(((a - bg) ** 2).sum(axis=2))
    return img, (dist > 26)


def radial_contour(mask, n):
    ys, xs = np.where(mask)
    cx, cy = xs.mean(), ys.mean()
    pts = []
    for i in range(n):
        ang = 2 * math.pi * i / n
        dx, dy = math.cos(ang), math.sin(ang)
        r, rr = 0.0, 0.0
        while True:
            x, y = int(cx + dx * rr), int(cy + dy * rr)
            if x < 0 or y < 0 or x >= mask.shape[1] or y >= mask.shape[0]:
                break
            if mask[y, x]:
                r = rr
            rr += 1.0
        pts.append((cx + dx * r, cy + dy * r))
    return np.array(pts)


def smooth_closed(pts, k):
    n = len(pts)
    ker = np.ones(k) / k
    out = np.zeros_like(pts)
    for d in range(2):
        ext = np.concatenate([pts[:, d][-k:], pts[:, d], pts[:, d][:k]])
        out[:, d] = np.convolve(ext, ker, mode="same")[k:k + n]
    return out


def catmull_closed(pts, subdiv):
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1, p2, p3 = pts[(i - 1) % n], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        for j in range(subdiv):
            t = j / subdiv
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t +
                       (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 +
                       (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t +
                       (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 +
                       (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            out.append((x, y))
    return out


def largest_region(mask):
    h, w = mask.shape
    seen = np.zeros_like(mask, bool)
    best, bs = None, 0
    for y0 in range(0, h, 6):
        for x0 in range(0, w, 6):
            if mask[y0, x0] and not seen[y0, x0]:
                q = deque([(y0, x0)])
                seen[y0, x0] = True
                comp = []
                while q:
                    y, x = q.popleft()
                    comp.append((y, x))
                    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            q.append((ny, nx))
                if len(comp) > bs:
                    bs, best = len(comp), comp
    out = np.zeros_like(mask)
    for y, x in best:
        out[y, x] = True
    return out


def mouth_outline(img, obj):
    """返回鱼口的关键点：(腔体圆心, 半径, 上唇曲线点列, 下唇曲线点列)"""
    a = np.asarray(img).astype(np.float32)
    L = 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]

    er = Image.fromarray((obj * 255).astype(np.uint8))
    for _ in range(MOUTH_ERODE_STEPS):
        er = er.filter(ImageFilter.MinFilter(9))
    dark = largest_region((L < MOUTH_MIN_DARK) & (np.asarray(er) > 128))

    ys, xs = np.where(dark)
    x0, x1 = xs.min(), xs.max()
    cols = []
    for x in range(x0, x1 + 1):
        yy = np.where(dark[:, x])[0]
        if len(yy) > 2:
            cols.append((x, (yy.min() + yy.max()) / 2.0, (yy.max() - yy.min()) / 2.0))
    cols = np.array(cols)

    cy_fit = np.polyfit(cols[:, 0], cols[:, 1], MOUTH_FIT_DEGREE)
    hw_fit = np.polyfit(cols[:, 0], cols[:, 2], MOUTH_FIT_DEGREE)

    x_end = np.where(obj.any(axis=0))[0].max() + 4    # 切到轮廓右缘，形成开口
    xs_fit = np.linspace(cols[0, 0], x_end, 260)
    center = np.polyval(cy_fit, xs_fit) + MOUTH_SHIFT_DOWN
    t = (xs_fit - xs_fit[0]) / max(1e-6, xs_fit[-1] - xs_fit[0])
    center = center + MOUTH_TAIL_DROOP * (t ** 1.8)
    half = np.clip(np.polyval(hw_fit, xs_fit), MOUTH_HALF_MIN, MOUTH_HALF_MAX)
    half = half * (1.0 - MOUTH_TAIL_TAPER * np.clip((t - 0.82) / 0.18, 0, 1))

    upper = np.stack([xs_fit, center - half], axis=1)
    lower = np.stack([xs_fit, center + half], axis=1)
    return (xs_fit[0], center[0]), half[0], upper, lower


def flatten_bottom(mask_img, pivot_ratio, ratio):
    arr = np.asarray(mask_img).astype(np.float32)
    h, w = arr.shape
    pivot = int(h * pivot_ratio)
    rows = np.arange(h, dtype=np.float32)
    src = np.where(rows < pivot, rows, pivot + (rows - pivot) / ratio)
    src = np.clip(src, 0, h - 1.001)
    y0 = np.floor(src).astype(int)
    frac = (src - y0)[:, None]
    out = arr[y0] * (1 - frac) + arr[np.minimum(y0 + 1, h - 1)] * frac
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def svg_path(points, close=True):
    d = "M " + " L ".join("%.2f,%.2f" % (x, y) for x, y in points)
    return d + (" Z" if close else "")


def main():
    img, obj = load_object_mask(REF)
    outer = smooth_closed(radial_contour(obj, CONTOUR_SAMPLES), CONTOUR_SMOOTH)
    outer_path = catmull_closed(outer[::SPLINE_ANCHORS], SPLINE_SUBDIV)

    (hx, hy), hr, upper, lower = mouth_outline(img, obj)

    # ---- 参考图坐标 → 画布坐标 ----
    ys0, xs0 = np.where(obj)
    bx0, bx1, by0, by1 = xs0.min(), xs0.max(), ys0.min(), ys0.max()
    S = OUT_SIZE * SS
    sc = (S * 0.86) / max(bx1 - bx0, by1 - by0)
    ox = (S - (bx1 - bx0) * sc) / 2 - bx0 * sc
    oy = (S - (by1 - by0) * sc) / 2 - by0 * sc
    T = lambda p: (p[0] * sc + ox, p[1] * sc + oy)

    outer_px = [T(p) for p in outer_path]
    upper_px = [T(p) for p in upper]
    lower_px = [T(p) for p in lower]
    hx_px, hy_px = T((hx, hy))
    hr_px = hr * sc

    # ---- 渲染 ----
    mask = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(mask)
    d.polygon(outer_px, fill=255)
    d.polygon(upper_px + lower_px[::-1], fill=0)
    d.ellipse([hx_px - hr_px, hy_px - hr_px, hx_px + hr_px, hy_px + hr_px], fill=0)
    mask = mask.resize((OUT_SIZE, OUT_SIZE), Image.LANCZOS)

    # ---- 底部压平 ----
    mask = flatten_bottom(mask, FLAT_PIVOT, FLAT_RATIO)
    bb = mask.getbbox()
    mask = mask.crop(bb)
    canvas = Image.new("L", (OUT_SIZE, OUT_SIZE), 0)
    s2 = min(OUT_SIZE * 0.94 / mask.width, OUT_SIZE * 0.94 / mask.height)
    m2 = mask.resize((max(1, int(mask.width * s2)), max(1, int(mask.height * s2))), Image.LANCZOS)
    canvas.paste(m2, ((OUT_SIZE - m2.width) // 2, int(OUT_SIZE * 0.97) - m2.height))
    mask = canvas

    def write_png(color, mul, name):
        arr = np.zeros((OUT_SIZE, OUT_SIZE, 4), np.uint8)
        arr[..., 0], arr[..., 1], arr[..., 2] = [int(c * mul) for c in color]
        arr[..., 3] = np.asarray(mask)
        Image.fromarray(arr, "RGBA").resize(
            (OUT_SIZE, OUT_SIZE), Image.LANCZOS).save(f"{RES_DIR}/drawable-nodpi/{name}")

    write_png(PAPER, 1.0, "fish_flat.png")

    # 敲击帧：底部锚点纵向压缩
    for factor, mul, name in [(0.955, PAPER_PRESS, "fish_flat_press.png"),
                              (0.985, 0.96, "fish_flat_press2.png")]:
        h2 = int(OUT_SIZE * factor)
        sq = mask.resize((OUT_SIZE, h2), Image.LANCZOS)
        c2 = Image.new("L", (OUT_SIZE, OUT_SIZE), 0)
        c2.paste(sq, (0, OUT_SIZE - h2))
        arr = np.zeros((OUT_SIZE, OUT_SIZE, 4), np.uint8)
        arr[..., 0], arr[..., 1], arr[..., 2] = [int(c * mul) for c in PAPER]
        arr[..., 3] = np.asarray(c2)
        Image.fromarray(arr, "RGBA").save(f"{RES_DIR}/drawable-nodpi/{name}")

    # ---- SVG 导出（512×512 视图，evenodd 镂空）----
    def to_svg_pts(pts):
        return [(x / SS, y / SS) for x, y in pts]

    outer_svg = to_svg_pts(outer_px)
    mouth_svg = to_svg_pts(upper_px + lower_px[::-1])
    d_outer = svg_path(outer_svg)
    d_mouth = svg_path(mouth_svg)
    cx, cy = hx_px / SS, hy_px / SS
    r = hr_px / SS
    d_cav = "M %.2f,%.2f a %.2f,%.2f 0 1,0 %.1f,0 a %.2f,%.2f 0 1,0 %.1f,0 Z" % (
        cx - r, cy, r, r, 2 * r, r, r, -2 * r)
    svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <path fill-rule="evenodd" fill="#%02X%02X%02X"
        d="%s %s %s" />
</svg>
""" % (PAPER[0], PAPER[1], PAPER[2], d_outer, d_mouth, d_cav)
    with open(SVG_OUT, "w") as f:
        f.write(svg)

    print("生成完成：")
    print("  fish_flat.png / fish_flat_press.png / fish_flat_press2.png")
    print("  SVG:", SVG_OUT)


if __name__ == "__main__":
    main()
