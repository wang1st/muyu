#!/usr/bin/env python3
"""参考图 → 结构分层（轮廓/厚度/倒角/口腔）→ 矢量化 SVG"""
import numpy as np, subprocess, os, re
from PIL import Image
from scipy import ndimage

W = '/var/minis/workspace/woodfish/work25/ref'
os.makedirs(W, exist_ok=True)
im = Image.open('/var/minis/attachments/uploads/1000182052.png').convert('RGBA')
sc = 1024 / im.size[0]
im = im.resize((1024, int(im.size[1] * sc)), Image.LANCZOS)
a = np.asarray(im).astype(np.float32) / 255
al = a[..., 3]
fish = al > 0.5
lum = 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
H, Wd = fish.shape
print('size', im.size, 'fish', int(fish.sum()))

# 去掉木纹细节，保留结构
lum_s = ndimage.gaussian_filter(np.where(fish, lum, 0.9), 7)
lum_s = np.where(fish, lum_s, 0)

dark = fish & (lum_s < 0.33)
lab, n = ndimage.label(dark)
sizes = ndimage.sum(dark, lab, range(1, n + 1))
order = np.argsort(sizes)[::-1] + 1
print('dark comps', n, [(int(i), int(sizes[i - 1])) for i in order[:5]])

# 用「暗部厚度」区分：外轮廓是细线（~8px），口腔是厚块
# 暗部用「未平滑」亮度取，才抓得到细描边
dark_raw = fish & (lum < 0.30)
d2out = ndimage.distance_transform_edt(fish)
outline = dark_raw & (d2out < 18)
mouth = dark_raw & (d2out >= 18)
lab_m, n_m = ndimage.label(mouth)
if n_m:
    sz_m = ndimage.sum(mouth, lab_m, range(1, n_m + 1))
    mouth = lab_m == (int(np.argmax(sz_m)) + 1)
mouth = ndimage.binary_closing(mouth, iterations=3)
outline = outline & ~ndimage.binary_dilation(mouth, iterations=1)
print('outline', int(outline.sum()), 'mouth', int(mouth.sum()))

mouth_deep = mouth & (lum_s < 0.22)
mouth_soft = mouth & ~mouth_deep

# 体面用强平滑的亮度分档（去掉木纹噪声，只留体积/倒角）
lum_b = ndimage.gaussian_filter(np.where(fish, lum, 0.9), 22)
lum_b = np.where(fish, lum_b, 0)
body = fish & ~outline & ~mouth
# 体面按「相对亮度」重新归一，让分档更好用
if body.sum():
    lo_, hi_ = np.percentile(lum_b[body], 2), np.percentile(lum_b[body], 98)
    lum_b = np.clip((lum_b - lo_) / max(1e-4, hi_ - lo_), 0, 1)
bands = []
for lo, hi, name in [(0.0, 0.45, 'b1'), (0.45, 0.62, 'b2'), (0.62, 0.78, 'b3'),
                     (0.78, 0.90, 'b4'), (0.90, 1.01, 'b5')]:
    bands.append((name, body & (lum_b >= lo) & (lum_b < hi)))
print('bands', [(nm, int(m.sum())) for nm, m in bands])

pal = {'outline': 30, 'md': 15, 'ms': 60, 'b1': 95, 'b2': 140, 'b3': 185, 'b4': 230, 'b5': 252}
prev = np.zeros((H, Wd), np.uint8)
prev[fish] = 200
for nm, m in bands:
    prev[m] = pal[nm]
prev[mouth_soft] = pal['ms']
prev[mouth_deep] = pal['md']
prev[outline] = pal['outline']
rgba = np.dstack([np.dstack([prev] * 3), (fish * 255).astype(np.uint8)])
out = Image.fromarray(rgba, 'RGBA')
bg = Image.new('RGB', out.size, (20, 18, 16)); bg.paste(out, (0, 0), out)
bg.save(f'{W}/bands_preview.png')
np.savez(f'{W}/masks.npz', fish=fish, mouth=mouth, mouth_deep=mouth_deep, mouth_soft=mouth_soft, outline=outline, lum=lum, lum_s=lum_s)
print('saved preview')


# ---------------- 矢量化 ----------------
layers = [('sil', fish, '#F7F2E6')]
layers += [(nm, m, c) for (nm, m), c in zip(bands, ['#B0A793', '#D9D0BC', '#F1EBDB', '#FBF7EC', '#FFFFFF'])]
layers += [('ms', mouth_soft, '#6B5B44'), ('md', mouth_deep, '#14100C')]

paths = {}
for name, m, col in layers:
    if m.sum() < 40:
        print('skip', name); continue
    Image.fromarray(((~m) * 255).astype(np.uint8)).save(f'{W}/{name}.pbm')
    subprocess.run(['potrace', f'{W}/{name}.pbm', '-s', '-o', f'{W}/{name}.svg',
                    '--turdsize', '80', '--alphamax', '1.2', '-u', '10'], check=True)
    txt = open(f'{W}/{name}.svg').read()
    ds = re.findall(r'<path d="([^"]+)"', txt)
    paths[name] = ds[0] if ds else ''
    print('trace', name, int(m.sum()), len(paths[name]))

svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{Wd}" height="{H}" viewBox="0 0 {Wd} {H}">',
       f'<g transform="translate(0,{H}) scale(0.1,-0.1)">']
for name, m, col in layers:
    if not paths.get(name):
        continue
    if name == 'sil':
        svg.append(f'<path d="{paths[name]}" fill="{col}" stroke="#3A2C1E" stroke-width="7" stroke-linejoin="round"/>')
    elif name == 'b5' and False:
        svg.append(f'<path d="{paths[name]}" fill="{col}" fill-opacity="0.85" fill-rule="evenodd"/>')
    else:
        svg.append(f'<path d="{paths[name]}" fill="{col}" fill-rule="evenodd"/>')
svg.append('</g></svg>')
open(f'{W}/relief_ref.svg', 'w').write('\n'.join(svg))

import cairosvg
cairosvg.svg2png(url=f'{W}/relief_ref.svg', write_to=f'{W}/relief_ref.png',
                 output_width=Wd, output_height=H)
im2 = Image.open(f'{W}/relief_ref.png').convert('RGBA')
bg2 = Image.new('RGB', im2.size, (20, 18, 16)); bg2.paste(im2, (0, 0), im2)
bg2.save(f'{W}/relief_ref_prev.png')
print('svg done')
