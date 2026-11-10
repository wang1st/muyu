#!/usr/bin/env python3
"""render.npz → 几何导出的分层（体面 3 阶 + 口内 3 阶）→ potrace → SVG → PNG"""
import numpy as np, subprocess, os, re
from PIL import Image
from scipy import ndimage

W = '/var/minis/workspace/woodfish/work25'
SIZE = 1024
d = np.load(f'{W}/render.npz')
depth, nrmap, mask = d['depth'], d['nrmap'], d['mask']
nrmap = np.nan_to_num(nrmap)

L = np.array([-0.42, 0.52, 0.74], np.float32); L /= np.linalg.norm(L)
lam = np.clip((nrmap * L).sum(2), 0, 1)
lam = ndimage.gaussian_filter(lam, 3)

# 深度 → 腔体
dv = np.where(mask, depth, np.nan)
med = ndimage.median_filter(np.nan_to_num(dv, nan=np.nanmax(dv)), size=45)
diff = np.clip(np.nan_to_num(dv, nan=0) - med, 0, None)
cav = ndimage.gaussian_filter(np.clip(diff / 0.09, 0, 1), 4)
cav_mask = (cav > 0.30) & mask

def clean(m, s=3.0, thr=0.5):
    return ndimage.gaussian_filter(m.astype(np.float32), s) > thr

body = clean(ndimage.binary_fill_holes(mask), 1.5)

# ---- 口腔：几何给出的细缝 → 展开成可读开口，再按近壁/槽底/远壁分层 ----
thin = mask & (diff > 0.05)
thin = clean(thin, 1.5)
band = ndimage.binary_closing(ndimage.binary_dilation(thin, iterations=9), iterations=6)
lab, n = ndimage.label(band)
if n:
    sz = ndimage.sum(band, lab, range(1, n + 1))
    band = lab == (int(np.argmax(sz)) + 1)
band = band & body
# 每列的上下边界 → 归一化位置 t（0 = 上唇内缘，1 = 下唇内缘）
H_, W_ = band.shape
tmap = np.zeros_like(band, np.float32)
for x in range(W_):
    col = np.where(band[:, x])[0]
    if len(col) < 3:
        continue
    y0, y1 = col.min(), col.max()
    tmap[y0:y1+1, x] = (np.arange(y0, y1+1) - y0) / max(1, (y1 - y0))
in_band = band & (tmap > 0)

near_wall = clean(in_band & (tmap < 0.34), 1.2)
deep_line = clean(in_band & (tmap >= 0.34) & (tmap < 0.58), 1.0)
far_wall = clean(in_band & (tmap >= 0.58), 1.2)
# 远壁上缘的一条内唇线（口内最亮）
far_edge = clean(in_band & (tmap >= 0.58) & (tmap < 0.72), 0.8)

print('body', int(body.sum()), 'band', int(band.sum()),
      'near', int(near_wall.sum()), 'deep', int(deep_line.sum()), 'far', int(far_wall.sum()))

layers = []
layers.append(('body', body, '#FAF6EC'))
for th, col in [(0.78, '#EDE6D6'), (0.58, '#D6CDB9'), (0.38, '#ABA290')]:
    layers.append((f'b{th:.2f}', clean((lam < th) & body & ~band, 3.5), col))
# 口内三层：近壁最暗 → 槽底近黑 → 远壁受光
layers.append(('cav0', in_band, '#2B241C'))
layers.append(('cav1', near_wall, '#171310'))
layers.append(('cav2', deep_line, '#0A0908'))
layers.append(('cav3', far_wall, '#7C7059'))
layers.append(('cav4', far_edge, '#A99C80'))

os.makedirs(f'{W}/svg', exist_ok=True)
paths = {}
for name, m, col in layers:
    Image.fromarray(((~m) * 255).astype(np.uint8)).save(f'{W}/svg/{name}.pbm')
    subprocess.run(['potrace', f'{W}/svg/{name}.pbm', '-s', '-o', f'{W}/svg/{name}.svg',
                    '--turdsize', '120', '--alphamax', '1.2', '-u', '10'], check=True)
    txt = open(f'{W}/svg/{name}.svg').read()
    ds = re.findall(r'<path d="([^"]+)"', txt)
    paths[name] = ds[0] if ds else ''
    print(name, int(m.sum()), len(paths[name]))

svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{SIZE}" height="{SIZE}" viewBox="0 0 {SIZE} {SIZE}">',
       '<g transform="translate(0,1024) scale(0.1,-0.1)">']
for name, m, col in layers:
    if paths.get(name):
        svg.append(f'<path d="{paths[name]}" fill="{col}" fill-rule="evenodd"/>')
svg.append('</g></svg>')
open(f'{W}/relief.svg', 'w').write('\n'.join(svg))

import cairosvg
cairosvg.svg2png(url=f'{W}/relief.svg', write_to=f'{W}/relief_svg.png', output_width=SIZE, output_height=SIZE)
im = Image.open(f'{W}/relief_svg.png').convert('RGBA')
sm = im.resize((640, 640), Image.LANCZOS)
bg = Image.new('RGB', (640, 640), (0x14, 0x12, 0x10)); bg.paste(sm, (0, 0), sm)
bg.save(f'{W}/relief_big.png')
print('done')
