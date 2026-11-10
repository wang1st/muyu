#!/usr/bin/env python3
"""干净的 2.5D：体面单一底色 + 光渐变；口腔用渐变 + 内壁分层"""
import numpy as np, subprocess, os, re
from PIL import Image
from scipy import ndimage

W = '/var/minis/workspace/woodfish/work25/ref'
m = np.load(f'{W}/masks.npz')
fish, mouth, outline = m['fish'], m['mouth'], m['outline']
lum = m['lum']

def clean(a, s=2.0, thr=0.5):
    return ndimage.gaussian_filter(a.astype(np.float32), s) > thr

body = clean(ndimage.binary_fill_holes(fish), 1.6)
cav = clean(mouth, 2.0)

# 口腔内部：按每列上下边界给出 t（0 上唇内缘 → 1 下唇内缘）
H, Wd = cav.shape
tmap = np.zeros_like(cav, np.float32)
for x in range(Wd):
    col = np.where(cav[:, x])[0]
    if len(col) < 3:
        continue
    y0, y1 = col.min(), col.max()
    tmap[y0:y1+1, x] = (np.arange(y0, y1+1) - y0) / max(1.0, (y1 - y0))

near = clean(cav & (tmap < 0.18), 1.2)      # 近唇内壁（最暗）
deep = clean(cav & (tmap >= 0.22) & (tmap < 0.42), 1.0)
far = clean(cav & (tmap >= 0.42), 1.2)   # 远侧内壁（受光）
far_hi = clean(cav & (tmap >= 0.85), 0.8)

# 体面：统一底色；只用极轻的两块光的层次（不在体面上分色块）
# 从参考图的强平滑亮度里取 10% 分位做一块淡淡的核心阴影，其余保持一色
lum_b = ndimage.gaussian_filter(np.where(body, lum, 0.9), 34)
if body.sum():
    lo, hi = np.percentile(lum_b[body], 5), np.percentile(lum_b[body], 95)
    lum_b = np.clip((lum_b - lo) / max(1e-4, hi - lo), 0, 1)
shade = clean((lum_b < 0.42) & body & ~cav, 22)

rim_top = np.zeros_like(cav)
rim_bot = np.zeros_like(cav)
for x in range(Wd):
    col = np.where(cav[:, x])[0]
    if len(col) < 3:
        continue
    y0, y1 = col.min(), col.max()
    rim_top[max(0, y0 - 5):y0, x] = True
    rim_bot[y1 + 1:min(H, y1 + 5), x] = True
rim_top &= body
rim_bot &= body
rim_top = clean(rim_top, 1.0)
rim_bot = clean(rim_bot, 1.0)

d2out = ndimage.distance_transform_edt(body)
lum_b2 = ndimage.gaussian_filter(np.where(body, lum, 0.9), 14)
mouth_zone = ndimage.binary_dilation(ndimage.gaussian_filter(cav.astype(np.float32), 6) > 0.15, iterations=40)
side = clean(body & (d2out < 48) & (lum_b2 < 0.62) & ~mouth_zone, 3.0)   # 侧壁/厚度面
yy2, xx2 = np.mgrid[0:H, 0:Wd]
glow = clean(body & (d2out < 9) & ~side & (yy2 > H * 0.55) & (xx2 < Wd * 0.62), 1.6)   # 左下边缘透光

# —— 单一基色，所有颜色都由它按亮度系数派生（同色相，只有明暗）——
BASE = np.array([246, 239, 221], np.float32)

def jc(f):
    c = np.clip(BASE * f, 0, 255).astype(int)
    return '#%02X%02X%02X' % (c[0], c[1], c[2])

def potrace_path(mask, turd=120, name='x'):
    Image.fromarray(((~mask) * 255).astype(np.uint8)).save(f'{W}/{name}.pbm')
    subprocess.run(['potrace', f'{W}/{name}.pbm', '-s', '-o', f'{W}/{name}.svg',
                    '--turdsize', str(turd), '--alphamax', '1.4', '-u', '10'], check=True)
    txt = open(f'{W}/{name}.svg').read()
    ds = re.findall(r'<path d="([^"]+)"', txt)
    return ds[0] if ds else ''

p_body = potrace_path(body, 200, 'c_body')
p_cav = potrace_path(cav, 80, 'c_cav')
p_shade = potrace_path(shade, 300, 'c_shade')
p_near = potrace_path(near, 60, 'c_near')
p_deep = potrace_path(deep, 60, 'c_deep')
p_far = potrace_path(far, 60, 'c_far')
p_hi = potrace_path(far_hi, 60, 'c_hi')
p_rt = potrace_path(rim_top, 40, 'c_rt')
p_side = potrace_path(side, 300, 'c_side')
p_glow = potrace_path(glow, 150, 'c_glow')
p_rb = potrace_path(rim_bot, 40, 'c_rb')

svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{Wd}" height="{H}" viewBox="0 0 {Wd} {H}">
  <defs>
    <linearGradient id="bodyG" x1="20%" y1="4%" x2="84%" y2="98%">
      <stop offset="0%"   stop-color="{jc(1.04)}"/>
      <stop offset="40%"  stop-color="{jc(1.00)}"/>
      <stop offset="78%"  stop-color="{jc(0.94)}"/>
      <stop offset="100%" stop-color="{jc(0.87)}"/>
    </linearGradient>
    <linearGradient id="cutG" x1="10%" y1="0%" x2="60%" y2="100%">
      <stop offset="0%"   stop-color="{jc(0.84)}"/>
      <stop offset="55%"  stop-color="{jc(0.74)}"/>
      <stop offset="100%" stop-color="{jc(0.66)}"/>
    </linearGradient>
    <linearGradient id="cavG" x1="0%" y1="0%" x2="14%" y2="100%">
      <stop offset="0%"   stop-color="{jc(0.30)}"/>
      <stop offset="55%"  stop-color="{jc(0.42)}"/>
      <stop offset="100%" stop-color="{jc(0.44)}"/>
    </linearGradient>
    <linearGradient id="farG" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%"   stop-color="{jc(0.44)}"/>
      <stop offset="100%" stop-color="{jc(0.60)}"/>
    </linearGradient>
    <radialGradient id="sheenG" cx="50%" cy="50%" r="50%">
      <stop offset="0%"   stop-color="#FFFFFF" stop-opacity="0.55"/>
      <stop offset="100%" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="shG" cx="50%" cy="50%" r="50%">
      <stop offset="0%"   stop-color="#000000" stop-opacity="0.42"/>
      <stop offset="55%"  stop-color="#000000" stop-opacity="0.22"/>
      <stop offset="100%" stop-color="#000000" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <ellipse cx="{Wd*0.52:.0f}" cy="{H*0.965:.0f}" rx="{Wd*0.40:.0f}" ry="{H*0.055:.0f}" fill="url(#shG)"/>
  <g transform="translate(0,{H}) scale(0.1,-0.1)">
    <path d="{p_body}" fill="url(#bodyG)" stroke="{jc(0.50)}" stroke-width="6" stroke-linejoin="round" fill-rule="evenodd"/>
    <path d="{p_glow}" fill="{jc(1.03)}" opacity="0.55" fill-rule="evenodd"/>
    <path d="{p_side}" fill="url(#cutG)" fill-rule="evenodd"/>
    <path d="{p_cav}" fill="url(#cavG)" fill-rule="evenodd"/>
    <path d="{p_far}" fill="url(#farG)" fill-rule="evenodd"/>
    <path d="{p_near}" fill="{jc(0.34)}" fill-rule="evenodd"/>
    <path d="{p_cav}" fill="none" stroke="{jc(0.46)}" stroke-width="5" stroke-linejoin="round" opacity="0.9"/>
    <path d="{p_rb}" fill="{jc(1.03)}" fill-rule="evenodd"/>
  </g>
</svg>'''
open(f'{W}/relief_clean.svg', 'w').write(svg)

import cairosvg
cairosvg.svg2png(url=f'{W}/relief_clean.svg', write_to=f'{W}/relief_clean.png', output_width=Wd, output_height=H)
im = Image.open(f'{W}/relief_clean.png').convert('RGBA')
bg = Image.new('RGB', im.size, (20, 18, 16)); bg.paste(im, (0, 0), im)
bg.save(f'{W}/relief_clean_prev.png')
print('clean svg done')
