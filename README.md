# FloatingNavBar — LSPatch 悬浮导航栏模块

一个 **Xposed 模块**（可直接被 LSPatch 嵌入打包），把 Android 应用里基于
Material Components 的底部导航栏改造成**悬浮胶囊导航栏**。

## 效果

- 底部导航栏脱离屏幕底边，四周留 14dp 边距，底部居中悬浮
- 圆角 28dp、半透明深灰背景、带阴影（浮起感）
- 适配 `BottomNavigationView` / `NavigationBarView` / `BottomAppBar`（含旧 support 包）

## 重要说明（先读）

1. **"通用"的真实边界**：本模块通过识别 Material 官方组件实现"通用"。
   目标 App 必须使用了上述 Material 控件；完全自绘导航栏的 App 无法生效。
2. 在**无 Root**手机上使用 LSPatch 打补丁后，APK 签名会变化，
   对签名有校验的 App（如微信、部分银行/支付类）可能无法登录。
3. 建议在 LSPatch 中勾选"隐藏模块"相关选项时自行评估风险。

## 项目结构

```
FloatingNavBar/
├── settings.gradle
├── build.gradle
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/xposed_init
│       ├── java/com/lspatch/floatingnav/MainHook.java
│       └── res/values/{strings.xml, arrays.xml}
└── README.md
```

## 一、构建模块 APK

方式 A（推荐）：用 Android Studio 打开项目 → Build → Build APK(s)，
得到 `app/build/outputs/apk/release/app-release.apk`。

方式 B（命令行）：

```bash
# 需要 JDK 17 与 Android SDK（设置 local.properties 或 ANDROID_HOME）
gradle wrapper            # 首次生成 wrapper
./gradlew assembleRelease
```

## 二、用 LSPatch 嵌入模块并安装（手机端，免 Root）

1. 在 GitHub 发布页下载 **LSPatch Manager** 最新版并安装：
   https://github.com/LSPosed/LSPatch/releases
2. 打开 LSPatch Manager → **管理** → **+** → 选择要改造的目标应用
   （可以从"已安装的应用"或"存储的 APK"选择）
3. 修补模式选 **本地模式**（仅本机生效）
4. 在 **嵌入模块** 处点击，选择本仓库构建出的 `app-release.apk` 模块
5. 点击 **开始修补**，完成后会生成打过补丁的新 APK，点击 **安装**
6. 打开目标应用，底部导航栏即变为悬浮胶囊样式

有 Root + LSPosed 的环境则无需打补丁：直接安装模块 APK，
在 LSPosed 管理器中对目标应用启用即可。

## 三、自定义

- 颜色/圆角/边距/阴影：修改 `MainHook.java` 顶部常量
  （`DP_MARGIN`、`DP_CORNER`、`DP_ELEV`、`bg.setColor(0xE6242830)`）
- 只作用于指定 App：把 `xposed_scope` 里的 `*` 换成包名列表

## 原理简述

`handleLoadPackage` 对每个应用进程：

1. hook `Activity.onPostResume`，在页面就绪后递归扫描视图树，
   查找 Material 导航控件实例；
2. hook `ViewGroup.addView`，捕获运行时动态添加的导航控件；
3. 命中后重写 `LayoutParams`（底部居中 + 边距）、替换圆角半透明背景、
   提升 elevation，并让父容器 `clipChildren=false` 使阴影可见。

## License

MIT

## 零门槛编译：用 GitHub Actions 在线出 APK（无需安装任何软件）

如果你不想装 Android Studio / JDK / Gradle，可以用 GitHub 免费服务器自动编译：

1. 注册并登录 https://github.com
2. 点右上角 **+** → **New repository** → 名字填 `FloatingNavBar` → Create
3. 点 **uploading an existing file**，把本仓库**所有文件**（连同文件夹 `.github/workflows/build.yml`）拖进去，点 Commit
4. 等 1~2 分钟，点仓库顶部的 **Actions** 标签 → 看到绿色 ✓ 即编译成功
5. 点进那次运行，最下方 **Artifacts** 里下载 **FloatingNavBar-APK**，解压就是模块 APK

以后只要修改代码并 push，Actions 会自动重新编译。
