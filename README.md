# 木魚 · Muyu Widget

> 一个只在桌面上敲的木鱼。点一下，木声一响，数字加一，经卷前行一字。
> **A minimal wooden-fish (木鱼) home-screen widget — built entirely on a phone, without a computer.**

<p align="center">
  <img src="docs/preview-widget.png" width="300" alt="桌面小部件" />
  <img src="docs/preview-app.png" width="300" alt="App 设置页" />
</p>

---

## ✦ 这个项目是「纯手机开发」

**没有电脑、没有 Android Studio、没有 Gradle、没有外接键盘。**

从第一行代码到最后签名的 APK，全部在一台 Xiaomi 手机（HyperOS / Android 16 / aarch64）上完成：写代码、画矢量素材、合成音效、编译打包、签名、安装、截图验证——工具链全部跑在手机本地。

### 开发环境全景

| 层 | 工具 | 在项目里干什么 |
|---|---|---|
| **终端** | [Termux](https://f-droid.org/packages/com.termux/)（F-Droid 版） | 手机上的 Linux 环境，所有构建都在这里执行 |
| **JDK** | `openjdk-17` / `openjdk-21`（Termux pkg） | 提供 `javac`、`keytool`、`apksigner` 运行时 |
| **打包** | `aapt`（Termux pkg，原生 aarch64） | 编译 `res/` 资源、生成 `R.java`、封 APK |
| **转 dex** | `dx`（Termux pkg） | `javac` 产物 → `classes.dex` |
| **签名** | `apksigner`（Termux pkg） | v1+v2+v3 签名（自签密钥 `woodfish.jks`） |
| **SDK** | `platform-36_r01.zip` → `android-36/android.jar` | 编译期 API 依赖（aarch64 上直接 `curl` 下载，不装 SDK Manager） |
| **构建入口** | `build.sh` | 一条命令跑完 `aapt → javac → dx → zip → apksigner` |
| **特权通道** | [Shizuku](https://shizuku.rikka.app/) | 通过 adb 级权限执行 `pm install`、`dumpsys`、`screencap`、`logcat` |
| **AI 编码** | Minis（运行在同一台手机上） | 读写源码、调用 Termux 构建、抓日志、截图比对、迭代 UI |
| **图形素材** | Python 3 + Pillow + NumPy | 参数化生成木鱼剪影（钢笔路径法）、敲击帧、图标、预览图 |
| **音效** | NumPy + `wave` | 纯代码合成木鱼音（3 音色）与磬声，无任何采样版权问题 |
| **3D 原型（已废弃）** | WebGL2 + raymarching（SDF） | 早期做过一版可旋转的 3D 木鱼，手机上用浏览器实时预览 |

### 「没有电脑」是怎么做到的

```
手机屏幕 ── 写代码（编辑器 / AI）
      │
      ├─ Termux：javac + aapt + dx + apksigner → APK
      │
      ├─ Shizuku：pm install → 装到桌面
      │
      └─ logcat / dumpsys appwidget / screencap → 回看效果，继续改
```

几个让这件事成立的关键点：

1. **不依赖 Gradle**：`aapt` 手动打包资源 + `javac` 直接编译 + `dx` 转 dex，总构建时间约 20 秒。
2. **不依赖 SDK Manager**：`android.jar` 直接 `curl` 下载官方 zip 解压。
3. **不依赖 adb/电脑**：安装与调试走 Shizuku（`pm install`、`dumpsys`、`screencap`、`logcat` 全部可用）。
4. **素材也不需要电脑**：木鱼剪影用「钢笔路径法」Python 脚本参数化生成，可导出 SVG 给设计师继续改；音效用 NumPy 合成。
5. **验证闭环**：每次改动 → 构建 → 安装 → `screencap` 截图 → 像素级比对（例如用亮度投影计算经文行是否有 1px 位移）。

> 换句话说：**这台手机既是设计工具、编译机，也是测试机。**

---

## ✦ 功能

- **桌面即产品**：整张卡片就是木鱼，点一下即敲；不需要打开 App。
- **今日功德**：跨零点自动归零；累计功德与轮次永久保留。
- **108 一轮**：每敲满 108 次响一声磬、轮次 +1（跨天连续累计，每个 108 倍数都会响）。
- **经卷行**：两行 × 9 字，方框逐字前进；可选《金刚经》（5,214 字）或《心经》（260 字），读满循环。
- **回向**：清空今日数字并记入「修習記錄」，不动经文位置。
- **全部可调**：数字位置（不显示 / 木鱼下 / 经文右下）、敲击时数字是否加亮、经文对比模式（同色 / 已读亮 / 未读亮）、音效与震动开关。

---

## ✦ 目录结构

```
.
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/
│   │   ├── sutra.txt          # 金刚经（繁体、无标点，5,214 字）
│   │   └── xinjing.txt        # 心经（260 字）
│   └── res/
│       ├── layout/            # widget.xml（小部件） / activity_main.xml（设置页）
│       └── drawable-nodpi/    # 剪影、敲击帧、图标前景
├── java/com/minis/muyu/
│   ├── FishWidget.java        # 小部件：敲击、位图渲染、音效、振动
│   ├── MuyuState.java         # 状态与设置（SharedPreferences）
│   ├── SutraText.java         # 经文加载
│   └── MainActivity.java      # 设置页 / 修習記錄 / 關於
├── build.sh                   # 一键构建（Termux）
├── muyu_shape.py              # 剪影生成（钢笔路径 + SVG 导出）
├── gen_sounds.py              # 音效合成（木鱼 ×3 + 磬）
├── gen_icon.py                # 图标生成（含 Android 13 主题图标）
└── build/woodfish-v0.9.apk    # 可安装的成品
```

---

## ✦ 构建

### 1. 准备 Termux

```bash
pkg install -y openjdk-17 aapt dx apksigner zip unzip curl
mkdir -p ~/woodfish && cd ~/woodfish
# android.jar（约 66MB）
curl -LO https://dl.google.com/android/repository/platform-36_r01.zip
unzip platform-36_r01.zip && mv android-36/android.jar ./sdk-android.jar
```

### 2. 构建

把本仓库放到 Termux 可访问的位置（如 `~/woodfish`），然后：

```bash
bash build.sh        # 产物：build/woodfish.apk
```

`build.sh` 的完整流程：

```bash
aapt package -f -M AndroidManifest.xml -S res -A assets -I android.jar -J gen -F base.apk
javac --release 8 -cp android.jar -d classes @sources.txt
dx --dex --output=classes.dex classes
zip base.apk classes.dex
apksigner sign --ks woodfish.jks --out woodfish.apk base.apk
```

> 首次运行会自动用 `keytool` 生成 `woodfish.jks`。**这个密钥务必保留**，否则升级安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

### 3. 安装

```bash
# 有 Shizuku / adb 均可
pm install -r build/woodfish.apk
```

---

## ✦ 重新生成素材

```bash
python3 muyu_shape.py     # 剪影 + 敲击帧 + SVG（输出到 app/src/main/res/drawable-nodpi/）
python3 gen_sounds.py     # 木鱼 3 音色 + 磬（输出到 res/raw/）
python3 gen_icon.py       # 图标 + 自适应图标 + 主题图标
```

`muyu_shape.py` 顶部集中了全部形状参数（轮廓平滑度、鱼口位置/张口、末端收细、底部压平比例），并同时导出 `muyu.svg`，方便在 Figma / Illustrator 里继续编辑。

---

## ✦ 平台坑（MIUI / HyperOS 实测）

| 现象 | 原因 | 处理 |
|---|---|---|
| 「支持小部件应用」列表里找不到本应用 | HyperOS 只收录应用商店分发的应用 | 从桌面「**安卓小部件**」分区添加，功能完全正常 |
| 桌面点击无反应 | MIUI **Greezer** 冻结后台进程，广播被系统丢弃（`ignored_background`） | 系统设置里允许本应用**自启动** |
| 勾选了震动却感觉不到 | Android 13+ 忽略后台应用的 `USAGE_TOUCH` 振动 | 代码改用 `USAGE_ALARM` + 满振幅 24ms |
| 自定义字体在小部件里不生效 | 启动器在 RemoteViews 里不加载 `res/font` | 数字与经文行改为**运行时绘制 Bitmap**，`setImageViewBitmap` 下发 |
| 敲击后文字跟着轻微位移 | 布局重新测量 + 底部锚点压缩 | 剪影改为**中心锚点收缩**，行位图宽高恒定（实测文字位移 0px） |

---

## ✦ License

MIT

## ✦ 聯繫

- 微博：[@王药师](https://weibo.com/n/%E7%8E%8B%E8%8D%AF%E5%B8%88)
- 推特：[@qq831261](https://twitter.com/qq831261)
