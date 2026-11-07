# 木魚 · 桌面小部件

一个"敲木鱼、积功德、读经文"的 Android 桌面小部件。桌面是主界面，App 只做设置与记录。

## 形态

```
┌──────────────────────────┐
│        ▁▁▁▁▁▁            │
│       ╱  木鱼  ╲          │   ← 纸白剪影，点一下 = 敲击
│       ╲  ‿‿‿  ╱          │
│           421            │   ← 今日功德（闲 15% / 敲 60%，可隐藏/换位置）
│  降伏其心佛言善哉〔善〕      │   ← 两行 × 9 字，方框逐字推进
│  哉須菩提如汝所說如         │
└──────────────────────────┘
```

- 敲击：**木声（3 音色随机）+ 振动 + 剪影轻微收缩 + 方框前进一字**
- 每 108 次：一声磬，轮次 +1（108 倍数都会响）
- 今日功德：跨零点自动归零；累计功德与轮次永久保留
- 回向：清空今日数字并记一笔，**不动经文位置**
- 经文：金剛經（5,214 字）/ 心經（260 字）可切换，读满循环

## 构建（Termux + Shizuku，本机编译）

```bash
# Termux 里一次性准备
pkg install -y openjdk-17 aapt dx apksigner zip unzip curl
# android.jar: platform-36_r01.zip → android-36/android.jar

# 构建
bash build.sh          # 输出 /sdcard/Download/woodfish/woodfish.apk
```

工程不含 Gradle，`build.sh` 全流程：`aapt 打包资源 → javac → dx → zip → apksigner(v3)`。

> 签名用 `woodfish.jks`（build.sh 首次自动生成，务必保留，否则无法覆盖安装）。

## 代码结构

```
java/com/minis/muyu/
  FishWidget.java   小部件：敲击逻辑、位图渲染（经卷行/数字）、音效、振动
  MuyuState.java    状态：今日/累计/轮次/经文索引/各项设置/回向记录
  SutraText.java    经文加载（assets/sutra.txt、xinjing.txt）
  MainActivity.java 设置页：开关、经文选择、经文归零、回向、修習記錄
app/src/main/res/   布局与素材（剪影、敲击帧、图标）
app/src/main/assets/ 经文全文（繁体、无标点）
muyu_shape.py       剪影生成脚本（参数化 + 导出 SVG）
gen_sounds.py       音效合成脚本（木鱼 3 音 + 磬）
gen_icon.py         图标生成（墨底 + 纸白剪影，含主题图标）
```

## 可调参数速查

| 位置 | 参数 | 说明 |
|---|---|---|
| `FishWidget.rowBitmap` | `fs = 52f` | 经文字号（px） |
| 同上 | `lineH = fs * 1.62f` | 行距 |
| 同上 | `numFs = fs * 0.52f` | 数字字号 |
| 同上 | `0xA6 / 0x26` | 敲击/闲时数字透明度 |
| `app/src/main/res/layout/widget.xml` | padding | 卡片内边距 |
| `muyu_shape.py` | `MOUTH_*` / `FLAT_*` | 剪影形状（鱼口、底部压平） |

## 已知平台限制

- MIUI/HyperOS 的「支持小部件应用」列表只收录商店应用；本应用需从**「安卓小部件」**分区添加（正常现象）
- MIUI Greezer 会拦截后台广播：**需在系统设置里允许本应用自启动**，否则桌面点击无响应
- Android 13+ 会忽略后台应用的 TOUCH 类振动，代码已改用 `USAGE_ALARM`
- RemoteViews 不支持补间动画，敲击反馈用 2 帧位图切换实现
