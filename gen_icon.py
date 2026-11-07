# -*- coding: utf-8 -*-
"""新图标：墨底 + 纸白木鱼剪影；含自适应图标前景与单色主题图标"""
import os
import numpy as np
from PIL import Image, ImageDraw

OUT = "/var/minis/workspace/woodfish/app/src/main/res"
SIL = f"{OUT}/drawable-nodpi/fish_flat.png"
INK = (0x12, 0x10, 0x0D)
PAPER = (0xE9, 0xE4, 0xDA)

sil = Image.open(SIL).convert("RGBA")
a = np.asarray(sil)
mask = a[..., 3] > 128
arr = np.zeros_like(a)
arr[..., 0], arr[..., 1], arr[..., 2] = PAPER
arr[..., 3] = np.where(mask, 255, 0)
paper = Image.fromarray(arr, "RGBA")
paper = paper.crop(paper.getbbox())

def legacy(px, radius_ratio=0.22, margin=0.24):
    S = px * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=S * radius_ratio, fill=INK + (255,))
    inner = int(S * (1 - margin * 2))
    w, h = paper.size
    sc = min(inner / w, inner / h)
    f = paper.resize((max(1, int(w * sc)), max(1, int(h * sc))), Image.LANCZOS)
    img.alpha_composite(f, ((S - f.width) // 2, (S - f.height) // 2))
    return img.resize((px, px), Image.LANCZOS)

for px, dn in [(72, "mipmap-hdpi"), (96, "mipmap-xhdpi"),
               (144, "mipmap-xxhdpi"), (192, "mipmap-xxxhdpi")]:
    legacy(px).save(f"{OUT}/{dn}/ic_launcher.png")

fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
inner = int(432 * 0.50)
w, h = paper.size
sc = min(inner / w, inner / h)
f = paper.resize((max(1, int(w * sc)), max(1, int(h * sc))), Image.LANCZOS)
fg.alpha_composite(f, ((432 - f.width) // 2, (432 - f.height) // 2))
fg.save(f"{OUT}/drawable-nodpi/ic_launcher_foreground.png")

os.makedirs(f"{OUT}/mipmap-anydpi-v26", exist_ok=True)
with open(f"{OUT}/mipmap-anydpi-v26/ic_launcher.xml", "w") as fp:
    fp.write("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icon_bg" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
""")

with open(f"{OUT}/values/colors.xml", "w") as fp:
    fp.write("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="icon_bg">#12100D</color>
</resources>
""")

legacy(512, radius_ratio=0.22, margin=0.24).convert("RGB").save(
    "/var/minis/workspace/woodfish/ui/icon_new.png")
print("icons generated")
