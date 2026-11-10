#!/usr/bin/env python3
"""2.5D 画稿 v5：高度场（圆顶 + 开口凹槽 + 玉枕隆起）→ 法线 → Lambert/高光/边缘光 + AO"""
import numpy as np
from PIL import Image
from scipy import ndimage

N = 1024
al = np.asarray(Image.open('/var/minis/workspace/woodfish/work25/relief_raw.png').convert('RGBA'))[..., 3].astype(np.float32) / 255
S0 = al > 0.5


def disk(r):
    y, x = np.ogrid[-r:r+1, -r:r+1]
    return x * x + y * y <= r * r


pad = 120
Sp = np.pad(S0, pad, constant_values=False)
closed = ndimage.binary_closing(Sp, structure=disk(50))
full_p = ndimage.binary_fill_holes(closed)
Mp = full_p & ~Sp
lab, n = ndimage.label(Mp)
sizes = ndimage.sum(Mp, lab, range(1, n + 1))
Mp = lab == (int(np.argmax(sizes)) + 1)
Ss = full_p[pad:-pad, pad:-pad]
M = Mp[pad:-pad, pad:-pad]
body = Ss & ~M


def blur(a, s):
    return ndimage.gaussian_filter(np.asarray(a, np.float32), s, mode='nearest')


def ss(e0, e1, x):
    t = np.clip((x - e0) / max(1e-6, e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)

# ---------- 高度场 ----------
D = ndimage.distance_transform_edt(Ss)
Dn = np.clip(D / (0.155 * N), 0, 1)
# 椭球高度场：法线覆盖整个体面（不是只在边缘转折），再加边沿收敛
ys0, ys1 = np.where(Ss)[0].min(), np.where(Ss)[0].max()
xs0, xs1 = np.where(Ss)[0].min() * 0 + np.where(Ss)[1].min(), np.where(Ss)[1].max()
cx, cy = (xs0 + xs1) / 2.0, (ys0 + ys1) / 2.0
rx, ry = (xs1 - xs0) / 2.0 * 0.98, (ys1 - ys0) / 2.0 * 0.98
uu = np.clip((xx - cx) / rx, -1, 1)
vv = np.clip((yy - cy) / ry, -1, 1)
He = np.sqrt(np.clip(1 - uu ** 2 - vv ** 2, 0, 1)) ** 0.75
H = He * np.clip(D / (0.035 * N), 0, 1) ** 0.45

# 开口：沿槽深方向挖一道凹槽，边缘圆滑（口沿因此有真正的转折）
distM = ndimage.distance_transform_edt(M)
groove_shape = np.clip(distM / (0.014 * N), 0, 1)
groove = 0.42 * blur(M.astype(np.float32), N * 0.004) * (0.25 + 0.75 * ss(0, 0.7, groove_shape))
H = H - groove
H = blur(H, N * 0.0022)

gy, gx = np.gradient(H)
k = N * 0.022
nx, ny = -gx * k, -gy * k
nz = np.ones_like(nx)
nn = np.sqrt(nx * nx + ny * ny + nz * nz)
nx, ny, nz = nx / nn, ny / nn, nz / nn

L = np.array([-0.50, -0.55, 0.67], np.float32); L /= np.linalg.norm(L)
lam = nx * L[0] + ny * L[1] + nz * L[2]

# ---------- 着色 ----------
diff = ss(0.02, 0.92, lam)                       # 明暗交界柔和
lum = 0.22 + 0.78 * diff

# 镜面（清漆感）：Blinn-Phong，一点宽一点窄
Hh = L + np.array([0, 0, 1], np.float32); Hh /= np.linalg.norm(Hh)
sp = np.clip(nx * Hh[0] + ny * Hh[1] + nz * Hh[2], 0, 1)
lum = lum + 0.10 * sp ** 12 + 0.14 * sp ** 60

# 边缘光（受光侧的轮廓提亮 + 背光侧淡淡的反射光）
fres = 1 - np.clip(nz, 0, 1)
lum = lum + 0.16 * fres ** 2.2 * ss(0.0, 0.5, lam) - 0.10 * fres ** 2.0 * (1 - ss(0.0, 0.5, lam))

# ---------- 口内：近壁 / 槽底 / 远壁 三层 ----------
distM, (iy, ix) = ndimage.distance_transform_edt(M, return_indices=True)
sideUp = (iy < yy - 0.5)      # 最近边界在上 → 近侧（上唇）内壁，背光
sideDn = (iy > yy + 0.5)      # 最近边界在下 → 远侧（下唇）内壁，受光
d = distM

# 远壁：靠唇沿最亮，往槽心迅速转暗（弧形受光面）
far = 0.09 + 0.33 * (1 - np.clip(d / 24.0, 0, 1)) ** 1.25
# 近壁：几乎全黑，只在贴唇沿处有一丝掠射光
near = 0.025 + 0.075 * (1 - np.clip(d / 9.0, 0, 1)) ** 2
# 槽底：中间最深，压到近黑
cav = np.where(sideDn, far, np.where(sideUp, near, 0.02))
cav *= (1 - 0.70 * np.clip((d - 18.0) / 22.0, 0, 1))
# 远壁上缘的一道内唇亮线（口内最亮的细弧）
farEdge = np.where(sideDn, np.clip(1 - d / 3.5, 0, 1), 0.0)
cav = np.clip(cav + 0.34 * farEdge, 0, 1)
# 远壁内侧再一条极淡的分界（内壁的第二层转折）
innerLine = np.exp(-((d - 11.0) / 2.2) ** 2) * sideDn * 0.10
cav = np.clip(cav + innerLine, 0, 1)

out = np.where(M, cav, lum)

# ---------- 口周遮挡 + 唇沿亮线 ----------
ao1 = blur(M.astype(np.float32), N * 0.020)
ao2 = blur(M.astype(np.float32), N * 0.005)
out = np.where(body, out * (1 - 0.42 * np.clip(ao1, 0, 1)) * (1 - 0.34 * np.clip(ao2, 0, 1)), out)

edge_band = (blur(M.astype(np.float32), 2.5) > 0.2) & body
rim_edge = blur(edge_band.astype(np.float32), 1.1)
out = np.clip(out + rim_edge * 0.55 * ss(0.1, 0.8, lam), 0, 1)

out = np.clip(out, 0, 1)

# ---------- 接触阴影 ----------
alpha = Ss.astype(np.float32)
sh = ndimage.shift(blur(Ss.astype(np.float32), N * 0.020), (N * 0.022, 0), order=1)
sh = np.clip(sh * 0.40, 0, 1) * (1 - alpha)
alpha_out = np.clip(alpha + sh, 0, 1)
rgb = out * alpha

img = np.dstack([rgb * 255, rgb * 255, rgb * 255, alpha_out * 255]).astype(np.uint8)
res = Image.fromarray(img, 'RGBA').resize((512, 512), Image.LANCZOS)
res.save('/var/minis/workspace/woodfish/work25/fish_relief_v2.png')

tints = [(0xF3, 0xEE, 0xE2), (0xF8, 0xE7, 0xC9), (0xE6, 0xEC, 0xF0)]
px = np.asarray(res).astype(np.float32) / 255.0
g, a2 = px[..., 0], px[..., 3]
sheet = Image.new('RGB', (900, 300), (0x14, 0x12, 0x10))
for i, (r, gg, b) in enumerate(tints):
    im = Image.fromarray(np.dstack([g*r, g*gg, g*b, a2*255]).astype(np.uint8), 'RGBA').resize((280, 280), Image.LANCZOS)
    sheet.paste(im, (i*300+10, 10), im)
sheet.save('/var/minis/workspace/woodfish/work25/tone_sheet_v2.png')

big = Image.fromarray(np.dstack([g*0xF3, g*0xEE, g*0xE2, a2*255]).astype(np.uint8), 'RGBA').resize((640, 640), Image.LANCZOS)
bg = Image.new('RGB', (640, 640), (0x14, 0x12, 0x10)); bg.paste(big, (0, 0), big)
bg.save('/var/minis/workspace/woodfish/work25/relief_big.png')
print('done')
