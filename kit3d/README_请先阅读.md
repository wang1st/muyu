# 和田玉木鱼 3D 模型 · Android 集成套件

版本：2026-11-10 — 圆润开口 + 双侧对称 + 中空壳体

## 0. 这次改了什么

- 重新塑造左侧头部曲面，不再使用明显的附加圆球。
- 木鱼整体保持近球形鼓腹，不采用二维剪影挤厚方式。
- 嘴缝末端改为平滑的圆形收尾，上下唇交接使用软布尔交集与细分平滑处理。
- 通过同一个连续内部空腔贯通，前后两侧按原始 Y 轴镜像；不是只在一侧画一道假缝。
- 高精度与移动版皆为可导入软件、可旋转检查的真正 GLB 模型；不是渲染图替代品。

**说明**：本套件是基于参考图与三维约束的程序化建模版本，形体仍需要在真机和 Blender 中检查与进一步打磨；不是宣称已完整复刻任何特定商品的扫描模型。

## 1. 文件清单

- `models/woodfish_jade_high.glb`：高精度，约 19 万三角面，Blender 编辑优先使用。
- `models/woodfish_jade_mobile.glb`：减面至约 5.9 万三角面，Android 实时 3D 优先使用。
- `preview_front.png` 等：均从本次实际三维网格渲染（不是 AI 生成图）。
- `comparison_old_new.png`：上版与本次开口、头部变化对照。
- `app/src/main/assets/models/`：直接复制到安卓工程的模型资源目录。
- `app/src/main/java/com/example/woodfish/MainActivity.kt`：SceneView 加载 GLB 的 Compose 示例。
- `app/src/main/java/com/example/woodfish/WoodfishWidgetProvider.kt`：桌面 Widget 计数与点击示例。
- `app/src/main/res/drawable-nodpi/woodfish_idle.png`：从三维渲染提取的透明静帧。
- `app/src/main/res/layout/widget_woodfish.xml` 和 `app/src/main/res/xml/woodfish_widget_info.xml`：Widget 布局和配置。
- `AndroidManifest-snippet.xml`：需要合并到你的应用 Manifest 的 receiver 配置。
- `build.gradle.kts-snippet.txt`：需要合并到现有模块 Gradle 的配置。
- `Blender/import_edit_export.py`：Blender 中进一步精修开口的导入/导出辅助脚本（未在 Blender 中实测）。
- `sources/`：程序化网格生成源代码。Python 依赖 `numpy scipy scikit-image trimesh vtk Pillow`。

## 2. 安卓 App 里显示真正的 3D 木鱼

在 Android Studio 中创建支持 Jetpack Compose 的 Kotlin 工程；如果已有工程，请只合并下面文件。

1. 确认你的 Android 构建工具链符合当前 SceneView 文档要求：`compileSdk 37`、`AGP >= 9.4`、`Kotlin >= 2.4`、`JDK 21`、`minSdk >= 24`。这个门槛较高，老项目不要直接升级到最新 SceneView 而不检查兼容性。
2. 在 `app/build.gradle.kts` 的 `dependencies` 中加入：
   ```kotlin
   implementation("io.github.sceneview:sceneview:4.67.0")
   ```
3. 将 `app/src/main/assets/models/woodfish_jade_mobile.glb` 放入你的项目相同相对路径。
4. 在自己的 Activity 或 Compose 页面中参考 `MainActivity.kt` 的 `SceneView`、`rememberModelLoader`、`rememberModelInstance`、`ModelNode` 调用。
5. 启动 App：等待异步加载，随后手势拖动可以旋转、缩放查看内壁和前后开口。

**轴向**：本套件的 GLB 已转为 glTF 所需的 **+Y 朝上**，不需要在 Android 中再旋转 90 度；X 轴沿木鱼长度，Z 轴对应正反方向。`scaleToUnits = 1.0f` 使模型约 1 个世界单位宽。

**材质**：GLB 内嵌 PBR 参数：浅乳白，`metallic = 0`，`roughness = 0.87`，并以顶点色稍微加深内腔表面。想让光泽更弱可增加 roughness；为了稳定模拟和田玉，建议使用柔和环境光，不要将 metallic 调高。真正的玉质次表面散射不是标准 glTF 核心材质能完整表达的。

## 3. 安卓桌面小部件不能直接加载 GLB

标准 Android App Widget 依赖 RemoteViews，不支持把 `SceneView` / OpenGL Surface 嵌进桌面。这与 App 内的三维实时渲染不同。

本套件采用 **GLB 实时 3D 用于 App 页面；由同一个 3D 模型渲染的透明 PNG 用于桌面 Widget**。为了方便测试，附有 `WoodfishWidgetProvider.kt`、布局、元数据和透明图像。

集成步骤：

1. 复制 `app/src/main/res/drawable-nodpi/woodfish_idle.png` 到项目相同目录。
2. 复制 `app/src/main/res/layout/widget_woodfish.xml` 和 `app/src/main/res/xml/woodfish_widget_info.xml`。
3. 调整 `WoodfishWidgetProvider.kt` 的 `package` 名称以匹配现有应用包名，放进对应的源文件目录。
4. 将 `AndroidManifest-snippet.xml` 中的 `<receiver>` 节点合并至项目已有 `<application>` 内。
5. 编译运行，将「木鱼」Widget 添加到桌面。每次点击图片时计数 +1；这份演示代码还没有添加音效、震动、108 次逻辑。

如要为桌面生成不同光照、玉色或触摸压缩效果，应在 App 内先渲染 Bitmap，再通过 `RemoteViews.setImageViewBitmap()` 更新；桌面不能实时显示可旋转的 GLB。频繁调用 Widget 更新需考虑厂商桌面限制和耗电。

## 4. 编辑模型 / Blender

在 Blender 里 File → Import → glTF 2.0 (.glb/.gltf)，选择 `woodfish_jade_high.glb`。可在编辑模式局部调整开口边缘、雕刻半径及内壁曲率，建议开启双面检查并保持封闭拓扑。可参考 `Blender/import_edit_export.py`；这是辅助脚本、未在本环境实测。

若想重新程序化生成几何，运行：

```bash
python -m pip install numpy scipy scikit-image trimesh vtk pillow
python sources/generate_3d_mesh.py
python sources/make_android_glbs.py
```

生成结果位于 `regenerated_outputs/` 与 `regenerated_android/models/`；不要把 Blender 导出的尺寸与旧代码的缩放混用。

## 5. 已检查与尚未检查

- ✅ 两个 GLB 可被 Python `trimesh` 重新导入；均为单组件、封闭且可计算体积的三角形网格。
- ✅ 按镜像函数定义前后开口，内部真实中空。
- ✅ 高精度和移动版具有 glTF 标准的 +Y 朝上轴向。
- ✅ GLB 有粗糙度、非金属、和田玉色调材质。
- ⚠️ 尚未在 Android 真机安装编译整个 App，Kotlin 是按 SceneView 4.67 官方示例整理的集成样例。
- ⚠️ `Widget` 样例只提供点击计数，不含低延迟木鱼音色、触觉或自适应壁纸阴影。
- ⚠️ 预览图片是基于同一真实几何的 VTK 静态渲染，但照明系统与 Android Filament 并不完全相同，真机观感会有差异。

官方参考：
- SceneView Quickstart: https://sceneview.github.io/docs/quickstart/
- SceneView Module: https://github.com/SceneView/sceneview/blob/main/sceneview/Module.md
- Android App Widgets Overview: https://developer.android.com/develop/ui/views/appwidgets/overview
