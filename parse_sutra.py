# -*- coding: utf-8 -*-
"""维基文库 wikitext → 纯繁体经文（无标点）+ 品名索引"""
import re, os

RAW = "/tmp/sutra_kj.txt"
OUT_DIR = "/var/minis/workspace/woodfish/app/src/main/assets"
os.makedirs(OUT_DIR, exist_ok=True)

text = open(RAW, encoding="utf-8").read()

# 去掉模板（尽量处理嵌套）
def strip_templates(s):
    prev = None
    while prev != s:
        prev = s
        s = re.sub(r"\{\{[^{}]*\}\}", "", s)
    return s

text = strip_templates(text)
# 文件链接整行删除
text = re.sub(r"\[\[File:[^\n]*", "", text)
# 内部链接 → 显示文字
text = re.sub(r"\[\[[^\]|]*\|([^\]]*)\]\]", r"\1", text)
text = re.sub(r"\[\[([^\]]*)\]\]", r"\1", text)
text = re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.S)
text = re.sub(r"<[^>]+>", "", text)

lines = text.splitlines()
chapters = []      # (start, title)
body = []
started = False

for ln in lines:
    ln = ln.strip()
    if not ln:
        continue
    m = re.match(r"^=+\s*(.+?)\s*=+$", ln)
    if m:
        title = m.group(1)
        title = re.split(r"[『（(\[]", title)[0].strip()
        if "分" in title:
            chapters.append([len(body), title])
        continue
    if not started:
        if "如是我聞" in ln:
            started = True
        else:
            continue
    for ch in ln:
        if "\u3400" <= ch <= "\u4dbf" or "\u4e00" <= ch <= "\u9fff" or ch in "　":
            if ch != "　":
                body.append(ch)

sutra = "".join(body)
print("chars:", len(sutra), "chapters:", len(chapters))
for c in chapters[:3]:
    print(c)

with open(f"{OUT_DIR}/sutra.txt", "w", encoding="utf-8") as f:
    f.write(sutra)
with open(f"{OUT_DIR}/sutra_chapters.txt", "w", encoding="utf-8") as f:
    for idx, title in chapters:
        f.write(f"{idx}|{title}\n")
print("written to", OUT_DIR)
