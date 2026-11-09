# -*- coding: utf-8 -*-
"""木鱼音色重制：脆瞬态 + 干短木腔共鸣（无金属长尾）"""
import numpy as np
import wave

OUT = "/var/minis/workspace/woodfish/app/src/main/res/raw"
SR = 44100


def knock(f0, seed, decay_scale=1.0):
    rng = np.random.default_rng(seed)
    t = np.arange(int(SR * 0.32)) / SR

    modes = [
        (f0 * 1.00, 1.00, 30.0),    # 主腔：再短一点
        (f0 * 2.55, 0.55, 46.0),    # 木块二次模：抬高、加强
        (f0 * 4.10, 0.30, 62.0),    # 中高"脆"分音：本质变化
    ]
    body = np.zeros_like(t)
    for f, amp, dec in modes:
        body += amp * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t * dec * decay_scale)

    thump = 0.28 * np.sin(2 * np.pi * f0 * 0.50 * t) * np.exp(-t * 22 * decay_scale)  # 低频减半、衰减更快

    n = int(SR * 0.006)
    click = rng.standard_normal(n)
    click = np.diff(click, prepend=0.0)
    click = np.convolve(click, np.ones(3) / 3, mode="same")   # 保留更多高频，让"嗒"更脆
    click *= np.exp(-np.arange(n) / (SR * 0.0011))
    trans = np.zeros_like(t)
    trans[:n] = click * 0.38

    n2 = int(SR * 0.05)
    tex = rng.standard_normal(n2)
    tex = np.convolve(tex, np.ones(12) / 12, mode="same")
    tex *= np.exp(-np.arange(n2) / (SR * 0.012))
    texn = np.zeros_like(t)
    texn[:n2] = tex * 0.26

    sig = body + thump + trans + texn
    attack = np.clip(t / 0.001, 0, 1)
    sig = np.tanh(sig * 0.75) * attack   # 少压一点，保留瞬态亮度
    sig *= np.exp(-t * 9)
    return sig / (np.max(np.abs(sig)) + 1e-6) * 0.92


def bowl():
    t = np.arange(int(SR * 2.4)) / SR
    parts = [(330, 1.00, 1.5), (330 * 2.76, 0.35, 2.4), (330 * 5.4, 0.12, 3.6)]
    s = np.zeros_like(t)
    for f, amp, dec in parts:
        s += amp * np.sin(2 * np.pi * f * t + np.sin(2 * np.pi * 0.6 * t) * 0.5) * np.exp(-t * dec)
    s = np.tanh(s * 0.55)
    s *= np.clip(t / 0.004, 0, 1)
    return s / (np.max(np.abs(s)) + 1e-6) * 0.85


def save(name, data):
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with wave.open(f"{OUT}/{name}", "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


save("knock1.wav", knock(430, 11, 1.00))
save("knock2.wav", knock(465, 22, 0.90))
save("knock3.wav", knock(405, 33, 1.10))
save("bowl.wav", bowl())
print("sounds regenerated")

for nm in ["knock1.wav", "knock2.wav", "knock3.wav"]:
    with wave.open(f"{OUT}/{nm}", "rb") as w:
        d = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float)
    spec = np.abs(np.fft.rfft(d))
    freqs = np.fft.rfftfreq(len(d), 1 / SR)
    centroid = (spec * freqs).sum() / spec.sum()
    env = np.abs(d)
    thresh = env.max() * 0.03
    idx = np.where(env > thresh)[0]
    dur_ms = (idx[-1] - idx[0]) / SR * 1000 if len(idx) else 0
    print(nm, "centroid=%.0fHz" % centroid, "dur=%.0fms" % dur_ms)
