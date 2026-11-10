#!/usr/bin/env python3
"""用 3D 模型算出的明暗绘制 2.5D 画稿：
   形体/开口取自设计轮廓，明暗来自模型，口内按近壁/槽底/远壁分层矢量化。"""
import numpy as np, subprocess, os, re
from PIL import Image
from scipy import ndimage

W = '/var/minis/workspace/woodfish/work25'
SIZE = 1024
d = np.load(f'{W}/render.npz')
r_nrm, r_mask = np.nan_to_num(d['nrmap']), d['mask']
def unit(v):
    v = np.array(v, np.float32); return v / np.linalg.norm(v)
# 主光左上、补光右前：让体面有贯穿的明暗过渡，而不是只有右上一块
L1, L2 = unit([-0.72, 0.40, 0.57]), unit([0.55, 0.05, 0.83])
r_lam = (0.85 * np.clip((r_nrm * L1).sum(2), 0, 1)
         + 0.30 * np.clip((r_nrm * L2).sum(2), 0, 1)) / 1.15

# ---------- 设计轮廓 ----------
flat = Image.open('/var/minis/workspace/woodfish/app/src/main/res/drawable-nodpi/fish_flat.png').convert('RGBA').resize((SIZE, SIZE), Image.LANCZOS)
S = np.asarray(flat)[..., 3].astype(np.float32) / 255 > 0.5

def disk(r):
    y, x = np.ogrid[-r:r+1, -r:r+1]
    return x * x + y * y <= r * r

pad = 120
Sp = np.pad(S, pad, constant_values=False)
full = ndimage.binary_fill_holes(ndimage.binary_closing(Sp, structure=disk(50)))
holes = full & ~Sp
lab, n = ndimage.label(holes)
sz = ndimage.sum(holes, lab, range(1, n + 1))
mouth = (lab == (int(np.argmax(sz)) + 1))[pad:-pad, pad:-pad]
body = full[pad:-pad, pad:-pad]
print('body', int(body.sum()), 'mouth', int(mouth.sum()))

def clean(m, s=3.0, thr=0.5):
    return ndimage.gaussian_filter(np.asarray(m, np.float32), s) > thr

# ---------- 把模型明暗对齐到设计轮廓 ----------
ry, rx = np.where(r_mask)
dy, dx = np.where(body)
sc = ((dx.max() - dx.min() + 1) / (rx.max() - rx.min() + 1)
      + (dy.max() - dy.min() + 1) / (ry.max() - ry.min() + 1)) / 2
tx = dx.min() - rx.min() * sc
ty = dy.min() - ry.min() * sc
lam = ndimage.affine_transform(r_lam, [1 / sc, 1 / sc], offset=[-ty / sc, -tx / sc],
                               output_shape=(SIZE, SIZE), order=1, cval=0.5, mode='constant')
cov = ndimage.affine_transform(r_mask.astype(np.float32), [1 / sc, 1 / sc], offset=[-ty / sc, -tx / sc],
                               output_shape=(SIZE, SIZE), order=1, cval=0.0, mode='constant')
lam = np.where(cov > 0.5, lam, 0.75)
lam = ndimage.gaussian_filter(lam, 3)

# ---------- 口内分层：按每列上下边界归一化 ----------
H_, W_ = mouth.shape
tmap = np.zeros_like(mouth, np.float32)
for x in range(W_):
    col = np.where(mouth[:, x])[0]
    if len(col) < 3:
        continue
    y0, y1 = col.min(), col.max()
    tmap[y0:y1+1, x] = (np.arange(y0, y1+1) - y0) / max(1.0, (y1 - y0))

near = clean(mouth & (tmap < 0.40), 1.2)
deep = clean(mouth & (tmap >= 0.40) & (tmap < 0.70), 1.0)
far = clean(mouth & (tmap >= 0.70) & (tmap < 0.92), 1.2)
far_hi = clean(mouth & (tmap >= 0.92), 0.8)

# ---------- 图层 ----------
layers = [('body', body, '#FBF7ED')]
for th, col in [(0.74, '#EDE5D3'), (0.62, '#D8CEB8'), (0.50, '#B7AC97'), (0.38, '#8A8170')]:
    layers.append((f'b{int(th*100)}', clean((lam < th) & body & ~mouth, 3.5), col))
layers.append(('m0', mouth, '#241E18'))
layers.append(('m1', near, '#0B0908'))
layers.append(('m2', deep, '#25201A'))
layers.append(('m3', far, '#4E463A'))
layers.append(('m4', far_hi, '#6B6050'))

os.makedirs(f'{W}/svg', exist_ok=True)
paths = {}
for name, m, col in layers:
    if m.sum() < 30:
        print('skip', name); continue
    Image.fromarray(((~m) * 255).astype(np.uint8)).save(f'{W}/svg/{name}.pbm')
    subprocess.run(['potrace', f'{W}/svg/{name}.pbm', '-s', '-o', f'{W}/svg/{name}.svg',
                    '--turdsize', '60', '--alphamax', '1.2', '-u', '10'], check=True)
    txt = open(f'{W}/svg/{name}.svg').read()
    ds = re.findall(r'<path d="([^"]+)"', txt)
    paths[name] = ds[0] if ds else ''
    print(name, int(m.sum()), len(paths[name]))

svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">',
       '<g transform="translate(0,1024) scale(0.1,-0.1)">']
for name, m, col in layers:
    if paths.get(name):
        extra = ' fill-opacity="0.45"' if name == 'shadow' else ''
        svg.append(f'<path d="{paths[name]}" fill="{col}"{extra} fill-rule="evenodd"/>')
svg.append('</g></svg>')
open(f'{W}/relief.svg', 'w').write('\n'.join(svg))

import cairosvg
cairosvg.svg2png(url=f'{W}/relief.svg', write_to=f'{W}/relief_svg.png', output_width=SIZE, output_height=SIZE)
im = Image.open(f'{W}/relief_svg.png').convert('RGBA')
bg = Image.new('RGB', (1024, 1024), (20, 18, 16)); bg.paste(im, (0, 0), im)
bg.resize((640, 640), Image.LANCZOS).save(f'{W}/relief_big.png')
bg.crop((60, 200, 980, 700)).save(f'{W}/mouth_zoom.png')
print('done')
