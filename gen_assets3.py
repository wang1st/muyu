# -*- coding: utf-8 -*-
"""木鱼 v2 素材：108 念珠环 13 态、回向印章、敲击帧、3 音色 + 磬"""
import math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = "/var/minis/workspace/woodfish/app/src/main/res"
os.makedirs(f"{OUT}/drawable-nodpi", exist_ok=True)
os.makedirs(f"{OUT}/raw", exist_ok=True)

GOLD = (0xE8, 0xC8, 0x78)
GOLD_DIM = (0x3A, 0x2C, 0x1E)

# ---------- 108 环：12 刻度 ----------
def ring_image(lit, size=512, flash=0.0):
    S = size * 4
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = S / 2
    R = S * 0.40
    n = 12
    tw = S * 0.028      # 刻度宽
    th = S * 0.075      # 刻度长（径向）
    for i in range(n):
        ang = -math.pi / 2 + i * (2 * math.pi / n)
        on = i < lit
        px, py = cx + math.cos(ang) * R, cy + math.sin(ang) * R
        # 径向短棒
        dx, dy = math.cos(ang) * th / 2, math.sin(ang) * th / 2
        nx, ny = -math.sin(ang) * tw / 2, math.cos(ang) * tw / 2
        quad = [(px - dx + nx, py - dy + ny), (px + dx + nx, py + dy + ny),
                (px + dx - nx, py + dy - ny), (px - dx - nx, py - dy - ny)]
        if on:
            d.polygon(quad, fill=GOLD + (255,))
        else:
            d.polygon(quad, fill=GOLD_DIM + (255,))
    # 已点亮部分的柔光
    if lit > 0:
        glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        for i in range(lit):
            ang = -math.pi / 2 + i * (2 * math.pi / n)
            px, py = cx + math.cos(ang) * R, cy + math.sin(ang) * R
            r = S * 0.05
            gd.ellipse([px - r, py - r, px + r, py + r], fill=GOLD + (90,))
        img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(S * 0.02)))
    if flash > 0:
        fl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(fl).ellipse([cx - R * 1.12, cy - R * 1.12, cx + R * 1.12, cy + R * 1.12],
                                   outline=(0xFF, 0xE8, 0xB8, int(220 * flash)), width=int(S * 0.012))
        img.alpha_composite(fl.filter(ImageFilter.GaussianBlur(S * 0.006)))
    return img.resize((size, size), Image.LANCZOS)

for i in range(13):
    ring_image(i).save(f"{OUT}/drawable-nodpi/ring_{i}.png")
ring_image(12, flash=1.0).save(f"{OUT}/drawable-nodpi/ring_full_flash.png")

# ---------- 回向印章 ----------
seal = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
sd = ImageDraw.Draw(seal)
sd.rounded_rectangle([8, 8, 248, 248], radius=48, outline=(0xC8, 0x6A, 0x50, 255), width=12)
try:
    f = ImageFont.truetype("/usr/share/fonts/noto/NotoSerifCJK-Bold.ttc", 130)
except Exception:
    f = ImageFont.load_default()
sd.text((128, 128), "回", font=f, fill=(0xD8, 0x7A, 0x5A, 255), anchor="mm")
seal.save(f"{OUT}/drawable-nodpi/seal.png")

# 印章激活态（确认中）
seal2 = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
sd = ImageDraw.Draw(seal2)
sd.rounded_rectangle([8, 8, 248, 248], radius=48, fill=(0xB8, 0x30, 0x28, 210))
sd.text((128, 128), "回", font=f, fill=(0xFF, 0xF0, 0xE0, 255), anchor="mm")
seal2.save(f"{OUT}/drawable-nodpi/seal_armed.png")

# ---------- 敲击帧（基于现有 fish.png） ----------
base = Image.open(f"{OUT}/drawable-nodpi/fish.png").convert("RGBA")
def press(squash, glow):
    S = 2048
    b = base.resize((S, S), Image.LANCZOS)
    h2 = int(S * squash)
    sq = b.resize((S, h2), Image.LANCZOS)
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    canvas.paste(sq, (0, S - h2 - int(S * 0.01)), sq)
    if glow > 0:
        g = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ImageDraw.Draw(g).ellipse([S*0.18, S*0.14, S*0.62, S*0.46], fill=(0xFF, 0xEC, 0xC8, int(80*glow)))
        g = g.filter(ImageFilter.GaussianBlur(S*0.03))
        g.putalpha(Image.composite(g.split()[3], Image.new("L", (S, S), 0), canvas.split()[3]))
        canvas = Image.alpha_composite(canvas, g)
    return canvas.resize((512, 512), Image.LANCZOS)
press(0.93, 1.0).save(f"{OUT}/drawable-nodpi/fish_press1.png")
press(0.97, 0.4).save(f"{OUT}/drawable-nodpi/fish_press2.png")

# ---------- 音效：3 种木鱼 + 磬 ----------
sr = 44100
rng = np.random.default_rng(7)

def knock(freq, decay, bright, thud, seed):
    r = np.random.default_rng(seed)
    t = np.arange(int(sr * 0.26)) / sr
    env = np.exp(-t * decay)
    sig = (np.sin(2*np.pi*freq*t) * 1.0
           + 0.55*np.sin(2*np.pi*freq*1.52*t)
           + bright*0.3*np.sin(2*np.pi*freq*3.1*t)
           + thud*0.5*np.sin(2*np.pi*freq*0.5*t)) * env
    n0 = int(sr*0.006)
    click = r.standard_normal(n0) * np.exp(-np.arange(n0)/55)
    out = np.zeros_like(sig)
    out[:n0] += click * 0.3
    out += sig * 0.8
    return np.tanh(out) * 0.9

import wave
def save_wav(name, data):
    pcm = (data * 32767).astype(np.int16)
    with wave.open(f"{OUT}/raw/{name}", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes(pcm.tobytes())

save_wav("knock1.wav", knock(480, 26, 1.0, 0.10, 11))
save_wav("knock2.wav", knock(520, 30, 0.7, 0.05, 22))
save_wav("knock3.wav", knock(440, 22, 1.2, 0.18, 33))

# 磬：非谐波分音 + 长衰减 + 拍频
t = np.arange(int(sr * 2.6)) / sr
parts = [(392, 1.0, 1.8), (392*2.76, 0.5, 2.6), (392*5.40, 0.28, 3.4), (392*8.93, 0.15, 4.5)]
bowl = np.zeros_like(t)
for f0, amp, dec in parts:
    bowl += amp * np.sin(2*np.pi*f0*t + np.sin(2*np.pi*0.7*t)*0.4) * np.exp(-t*dec)
fade_in = np.clip(t/0.01, 0, 1)
save_wav("bowl.wav", np.tanh(bowl * 0.5 * fade_in) * 0.85)

print("assets v3 done")
