#!/usr/bin/env bash
# GitHub Actions 用构建脚本：不依赖 Gradle，流程与原 build.sh 一致
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"

# 取最新的 build-tools 与 platform
BT="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)"
PLAT="$(find "$SDK/platforms" -maxdepth 1 -mindepth 1 -type d -name 'android-*' | sort -V | tail -1)"
AJ="$PLAT/android.jar"
echo "build-tools: $BT"
echo "platform:    $PLAT"

OUT="$ROOT/out"
WORK="$ROOT/build/ci"
rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT"
cd "$WORK"

# 1) 资源 + 清单 → base.apk（含 R.java）
mkdir -p gen
"$BT/aapt" package -f \
  -M "$ROOT/app/src/main/AndroidManifest.xml" \
  -S "$ROOT/app/src/main/res" \
  -A "$ROOT/app/src/main/assets" \
  -I "$AJ" -J gen -F base.apk

# 2) 编译 → dex
find "$ROOT/java" gen -name '*.java' > sources.txt
javac --release 8 -cp "$AJ" -d classes @sources.txt
"$BT/d8" --min-api 26 --output . $(find classes -name '*.class')
zip -q base.apk classes.dex

# 3) 对齐 + 签名
"$BT/zipalign" -f -p 4 base.apk aligned.apk

KEYSTORE="$ROOT/ci.jks"
if [ -n "${KEYSTORE_BASE64:-}" ]; then
  echo "$KEYSTORE_BASE64" | base64 -d > "$KEYSTORE"
  KS_PASS="${KEYSTORE_PASS:-minis123}"
  KS_ALIAS="${KEYSTORE_ALIAS:-minis}"
else
  # 无密钥：生成一次性调试密钥（CI 产物仅用于测试）
  keytool -genkeypair -keystore "$KEYSTORE" -storepass minis123 -keypass minis123 \
    -alias minis -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Muyu CI" >/dev/null 2>&1
  KS_PASS=minis123
  KS_ALIAS=minis
fi

"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
  --out "$OUT/muyu-ci.apk" aligned.apk
"$BT/apksigner" verify --print-certs "$OUT/muyu-ci.apk" | head -3

# 4) 版本号写入文件名
VER="$(grep -o 'android:versionName="[^"]*"' "$ROOT/app/src/main/AndroidManifest.xml" | head -1 | cut -d'"' -f2)"
cp "$OUT/muyu-ci.apk" "$OUT/muyu-v${VER}.apk"
ls -lh "$OUT"
