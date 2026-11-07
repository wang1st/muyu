#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Shizuku 写 /sdcard 驱动 Termux 轮询执行；含文件推送与轮询"""
import base64, json, subprocess, sys, time

DIR = "/sdcard/Download/woodfish"
CHUNK = 7500

def shizuku(cmd, timeout=120):
    r = subprocess.run(["android-shizuku-cli", "exec", cmd],
                       capture_output=True, text=True, timeout=timeout)
    try:
        d = json.loads(r.stdout)
        return d.get("data", {}).get("combined", r.stdout)
    except Exception:
        return r.stdout + r.stderr

def task(script, label="task"):
    """把脚本放进共享目录等 Termux 执行，轮询直到 done 出现"""
    shizuku(f"rm -f {DIR}/done {DIR}/out.log")
    b64 = base64.b64encode(script.encode()).decode()
    for i in range(0, len(b64), CHUNK):
        shizuku(f"echo '{b64[i:i+CHUNK]}' >> {DIR}/task.b64")
    shizuku(f"base64 -d {DIR}/task.b64 > {DIR}/task.sh && rm -f {DIR}/task.b64")
    for _ in range(360):  # 最长 30 分钟
        time.sleep(5)
        out = shizuku(f"cat {DIR}/done 2>/dev/null")
        if "RC=" in out:
            print(f"[{label}] {out.strip()}")
            print(shizuku(f"tail -30 {DIR}/out.log"))
            shizuku(f"rm -f {DIR}/done")
            return
    print(f"[{label}] TIMEOUT")
    print(shizuku(f"tail -30 {DIR}/out.log 2>/dev/null"))

def push(local, remote_name):
    data = open(local, "rb").read()
    b64 = base64.b64encode(data).decode()
    remote = f"{DIR}/{remote_name}"
    shizuku(f"rm -f {remote}.b64")
    for i in range(0, len(b64), CHUNK):
        shizuku(f"echo '{b64[i:i+CHUNK]}' >> {remote}.b64", timeout=30)
    out = shizuku(f"base64 -d {remote}.b64 > {remote} && rm -f {remote}.b64 && wc -c < {remote}")
    print(f"[push] {remote_name}: local={len(data)} remote={out.strip()}")

if __name__ == "__main__":
    if sys.argv[1] == "task":
        task(open(sys.argv[2]).read(), sys.argv[3] if len(sys.argv) > 3 else "task")
    elif sys.argv[1] == "taskstr":
        task(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else "task")
    elif sys.argv[1] == "push":
        push(sys.argv[2], sys.argv[3])
