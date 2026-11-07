# -*- coding: utf-8 -*-
"""钢笔路径法：径向采样取点 → 高斯平滑 → 三次闭合样条 → 超高分辨率渲染"""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

REF = "/var/minis/attachments/uploads/1000183948.jpg"
OUT = "/var/minis/workspace/woodfish/app/src/main/res"
PAPER = (0xE9, 0xE4, 0xDA)
PAPER_D = (0xC2, 0xBA, 0xAA)
SS = 8
SIZE = 512
S = SIZE * SS

img = Image.open(REF).convert("RGB")
W, H = img.size
a = np.asarray(img).astype(np.float32)
L = 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]
bg = np.array([a[5, 5, 0], a[5, 5, 1], a[5, 5, 2]])
dist_bg = np.sqrt(((a - bg) ** 2).sum(axis=2))
obj = dist_bg > 26

def radial_contour(mask, n=480):
    ys, xs = np.where(mask)
    cx0, cy0 = xs.mean(), ys.mean()
    pts = []
    for i in range(n):
        ang = 2 * math.pi * i / n
        dx, dy = math.cos(ang), math.sin(ang)
        r = 0.0
        rr = 0.0
        while True:
            x = int(cx0 + dx * rr)
            y = int(cy0 + dy * rr)
            if x < 0 or y < 0 or x >= mask.shape[1] or y >= mask.shape[0]:
                break
            if mask[y, x]:
                r = rr
            rr += 1.0
        pts.append((cx0 + dx * r, cy0 + dy * r))
    return np.array(pts)

outer = radial_contour(obj, 480)

def smooth_closed(pts, k=21):
    n = len(pts)
    ker = np.ones(k) / k
    out = np.zeros_like(pts)
    for d in range(2):
        ext = np.concatenate([pts[:, d][-k:], pts[:, d], pts[:, d][:k]])
        sm = np.convolve(ext, ker, mode="same")[k:k + n]
        out[:, d] = sm
    return out

outer = smooth_closed(outer, 41)

def catmull_closed(pts, samples_per_seg=10):
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1 = pts[(i - 1) % n], pts[i]
        p2, p3 = pts[(i + 1) % n], pts[(i + 2) % n]
        for j in range(samples_per_seg):
            t = j / samples_per_seg
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2*p1[0]) + (-p0[0]+p2[0])*t +
                       (2*p0[0]-5*p1[0]+4*p2[0]-p3[0])*t2 +
                       (-p0[0]+3*p1[0]-3*p2[0]+p3[0])*t3)
            y = 0.5 * ((2*p1[1]) + (-p0[1]+p2[1])*t +
                       (2*p0[1]-5*p1[1]+4*p2[1]-p3[1])*t2 +
                       (-p0[1]+3*p1[1]-3*p2[1]+p3[1])*t3)
            out.append((x, y))
    return out

outer_path = catmull_closed(outer[::6], 10)

# 只取物体内部的暗部（排除外轮廓描边）
er = Image.fromarray((obj * 255).astype(np.uint8))
for _ in range(6):
    er = er.filter(ImageFilter.MinFilter(9))
inner = np.asarray(er) > 128
dark = (L < 118) & inner

# 只保留最大暗部连通域（即鱼口）
from collections import deque
def largest(mask):
    h, w = mask.shape
    seen = np.zeros_like(mask, bool)
    best, bs = None, 0
    for y0 in range(0, h, 6):
        for x0 in range(0, w, 6):
            if mask[y0, x0] and not seen[y0, x0]:
                q = deque([(y0, x0)]); seen[y0, x0] = True; comp = []
                while q:
                    y, x = q.popleft(); comp.append((y, x))
                    for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                        ny, nx = y+dy, x+dx
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True; q.append((ny, nx))
                if len(comp) > bs:
                    bs, best = len(comp), comp
    out = np.zeros_like(mask)
    for y, x in best:
        out[y, x] = True
    return out
dark = largest(dark)
ys, xs = np.where(dark)
x0, x1 = xs.min(), xs.max()
cols = []
for x in range(x0, x1 + 1):
    yy = np.where(dark[:, x])[0]
    if len(yy) > 2:
        cols.append((x, (yy.min() + yy.max()) / 2.0, (yy.max() - yy.min()) / 2.0))
cols = np.array(cols)
cyf = np.polyfit(cols[:, 0], cols[:, 1], 2)
hwf = np.polyfit(cols[:, 0], cols[:, 2], 2)
x_end = np.where(obj.any(axis=0))[0].max() + 4   # 延伸到轮廓右缘，形成开口
xs_fit = np.linspace(cols[0, 0], x_end, 260)
center = np.polyval(cyf, xs_fit)
# 口缝尾部整体下压（越靠右越低）
_t = (xs_fit - xs_fit[0]) / max(1e-6, (xs_fit[-1] - xs_fit[0]))
center = center + 34.0 + 26.0 * (_t ** 1.8)   # 整体下移 + 尾部下压
half = np.clip(np.polyval(hwf, xs_fit), 2, 40)
# 末端收细（避免刀切感）
_t2 = (xs_fit - xs_fit[0]) / max(1e-6, (xs_fit[-1] - xs_fit[0]))
taper = 1.0 - 0.45 * np.clip((_t2 - 0.82) / 0.18, 0, 1)
half = half * taper
upper = np.stack([xs_fit, center - half], axis=1)
lower = np.stack([xs_fit, center + half], axis=1)
mouth_poly = list(map(tuple, upper)) + list(map(tuple, lower[::-1]))

# ---- 坐标归一化：把参考图坐标映射到画布 ----
ys0, xs0 = np.where(obj)
bx0, bx1, by0, by1 = xs0.min(), xs0.max(), ys0.min(), ys0.max()
bw, bh = bx1 - bx0, by1 - by0
sc = (S * 0.86) / max(bw, bh)
ox = (S - bw * sc) / 2 - bx0 * sc
oy = (S - bh * sc) / 2 - by0 * sc
def T(pt):
    return (pt[0] * sc + ox, pt[1] * sc + oy)

outer_path = [T(p) for p in outer_path]
mouth_poly = [T(p) for p in mouth_poly]

mask = Image.new("L", (S, S), 0)
d = ImageDraw.Draw(mask)
d.polygon(outer_path, fill=255)
d.polygon(mouth_poly, fill=0)
cx0p, cy0p = T((xs_fit[0], center[0]))
hp = half[0] * sc
d.ellipse([cx0p - hp, cy0p - hp, cx0p + hp, cy0p + hp], fill=0)

mask = mask.resize((SIZE, SIZE), Image.LANCZOS)

# ---- 底部压平：让木鱼"坐得住"（双线性重采样，无锯齿） ----
import numpy as _np
_arr = _np.asarray(mask).astype(_np.float32)
_H, _W = _arr.shape
_pivot = int(_H * 0.62)
_k = 0.45
_rows = _np.arange(_H, dtype=_np.float32)
_src = _np.where(_rows < _pivot, _rows, _pivot + (_rows - _pivot) / _k)
_src = _np.clip(_src, 0, _H - 1.001)
_y0 = _np.floor(_src).astype(int)
_frac = (_src - _y0)[:, None]
_out = _arr[_y0] * (1 - _frac) + _arr[_np.minimum(_y0 + 1, _H - 1)] * _frac
mask = Image.fromarray(_np.clip(_out, 0, 255).astype(_np.uint8))
_bb = mask.getbbox()
mask = mask.crop(_bb)
_canvas = Image.new("L", (SIZE, SIZE), 0)
_sc = min(SIZE * 0.94 / mask.width, SIZE * 0.94 / mask.height)
_m2 = mask.resize((max(1, int(mask.width * _sc)), max(1, int(mask.height * _sc))), Image.LANCZOS)
_canvas.paste(_m2, ((SIZE - _m2.width) // 2, int(SIZE * 0.97) - _m2.height))
mask = _canvas

def save(color, name):
    arr = np.zeros((SIZE, SIZE, 4), np.uint8)
    arr[..., 0], arr[..., 1], arr[..., 2] = color
    arr[..., 3] = np.asarray(mask)
    im = Image.fromarray(arr, "RGBA")
    im = im.crop(im.getbbox())
    canvas = Image.new("RGBA", (2048, 2048), (0, 0, 0, 0))
    s = int(2048 * 0.90)
    im2 = im.resize((s, s), Image.LANCZOS)
    canvas.paste(im2, ((2048 - s) // 2, (2048 - s) // 2), im2)
    canvas.resize((512, 512), Image.LANCZOS).save(f"{OUT}/drawable-nodpi/{name}")

save(PAPER, "fish_flat.png")
save(PAPER_D, "fish_flat_press.png")
print("pen-path done")
