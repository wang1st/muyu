#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""通过 Shizuku am startservice 驱动 Termux RUN_COMMAND，并向 Termux 推送文件"""
import base64, json, subprocess, sys, time

WORK = "/data/data/com.termux/files/home/woodfish"
LOG = "/sdcard/Download/woodfish/build.log"
CHUNK = 7500

def shizuku(cmd, timeout=60):
    r = subprocess.run(["android-shizuku-cli", "exec", cmd],
                       capture_output=True, text=True, timeout=timeout)
    try:
        d = json.loads(r.stdout)
        return d.get("data", {}).get("combined", r.stdout)
    except Exception:
        return r.stdout + r.stderr

def run(cmd, label=None):
    """在 Termux 中执行命令（后台），输出追加到 build.log"""
    full = f"mkdir -p /sdcard/Download/woodfish; echo '### {label or cmd[:40]}' >> {LOG}; ({cmd}) >> {LOG} 2>&1; echo \"EXIT=$? @{label or ''}\" >> {LOG}"
    quoted = full.replace("'", "'\\''")
    sh = f"am startservice -n com.termux/com.termux.app.TermuxService -a com.termux.service_execute_command --es commandline '{quoted}' --es workdir '{WORK}' >/dev/null"
    out = shizuku(sh)
    print("run:", label or cmd[:50], "|", out.strip()[:80])

def push(local, remote=None):
    """base64 分块推送文件到 Termux 的 home 下"""
    data = open(local, "rb").read()
    b64 = base64.b64encode(data).decode()
    remote = remote or (WORK + "/" + local.split("/")[-1])
    run(f"rm -f {remote}", "push-clean")
    time.sleep(1)
    for i in range(0, len(b64), CHUNK):
        chunk = b64[i:i+CHUNK]
        run(f"echo '{chunk}' >> {remote}.b64", f"chunk{i//CHUNK}")
    run(f"base64 -d {remote}.b64 > {remote} && rm -f {remote}.b64 && wc -c {remote}", "push-done")

def log(tail=40):
    print(shizuku(f"tail -{tail} {LOG} 2>/dev/null || echo NO_LOG"))

def pull(remote, local):
    out = shizuku(f"base64 -w0 {remote}", timeout=120)
    # shizuku 输出是 JSON；取 combined 里的 base64
    try:
        d = json.loads(out) if out.strip().startswith("{") else None
        txt = d["data"]["combined"] if d else out
    except Exception:
        txt = out
    b64 = "".join(txt.split())
    open(local, "wb").write(base64.b64decode(b64))
    print("pulled", local, len(b64)*3//4, "bytes")

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "run":
        run(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    elif cmd == "push":
        push(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
    elif cmd == "log":
        log(int(sys.argv[2]) if len(sys.argv) > 2 else 40)
    elif cmd == "pull":
        pull(sys.argv[2], sys.argv[3])
