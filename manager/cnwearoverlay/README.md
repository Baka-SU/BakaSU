# CN Wear Overlay

为小米 / OPPO 手表补充 Wear Compose 表冠触觉兼容，并修复小米部分触觉反馈常量导致的震感异常：将 `CLOCK_TICK` / `SEGMENT_FREQUENT_TICK` 映射为 `SEGMENT_TICK`。

项目提供两种接入方式：可修改应用源码时使用 **Gradle 插件**；需要在应用进程内安装 Hook 时使用 **Xposed 模块**。运行时根据系统类识别设备，检测到系统自带 Google Wear 触觉 SDK 时不介入。

## Gradle 插件使用说明

在应用项目根目录（`settings.gradle.kts` 所在目录）把本仓库添加为 git submodule：

```
git submodule add https://github.com/Star-ZER0/cnwearoverlay.git cnwearoverlay
```

后续升级时只需执行 `git submodule update --remote cnwearoverlay` 即可自动同步本仓库。

在应用项目的 `settings.gradle.kts` 中，将 `includeBuild` 加入已有的 `pluginManagement` 块，保留原有配置：

```kotlin
pluginManagement {
    includeBuild("cnwearoverlay")
    // 保留项目原有的 repositories 等配置。
}
```

在应用模块（通常为 `app/`）的 `build.gradle.kts` 中，将插件 ID 加入已有的 `plugins` 块：

```kotlin
plugins {
    // 保留项目原有的 Android、Kotlin 等插件。
    id("cc.star0.wear.lib.cnwearoverlay")
}
```

同步 Gradle，然后按原有方式构建应用即可。插件由应用项目的 Gradle 自动编译并应用，无需单独构建或发布插件，也无需修改业务代码。

**可选**配置写在同一应用模块的 `build.gradle.kts` 中：

```kotlin
// 可选，以下均为默认值。
cnWearOverlay {
    enabled.set(true)
    patchHapticsKt.set(true)
    remapXiaomiConstants.set(true)
    generateStub.set(true)
}
```

| 选项 | 作用 |
| --- | --- |
| `enabled` | 总开关；关闭后不注入运行时或修改 ASM 字节码 |
| `patchHapticsKt` | 包装 Wear Compose 的表冠常量选择逻辑 |
| `remapXiaomiConstants` | 改写应用及依赖的 `View.performHapticFeedback` 调用，运行时仅在小米设备上重映射常量 |
| `generateStub` | 注入缺失的 `com.google.wear.input.WearHapticFeedbackConstants` 桩类；与 `patchHapticsKt` 同时开启时才包装 `hasWearSDK` |

插件应用于 `com.android.application` 模块；应用于 Android library 模块时仅提示，不执行修改。运行时 JAR 和所需 R8 规则会自动注入，无需手动复制。

## Xposed 模块

`xposed/` 是独立 Android 工程，使用 libxposed API 102。模块在目标应用的 `onPackageReady` 回调中安装 Hook，并在缺失 Wear SDK 时追加兼容 DEX。

构建、签名并安装 APK 后，在支持对应现代 libxposed API 的框架（如 LSPosed）中启用模块并选择目标应用作用域，然后重新启动目标应用。模块没有独立配置界面；当前只处理应用进程。LSPatch 等内嵌框架场景需确认所用版本支持对应 API，并在目标设备验证。

### 构建 Xposed 模块

先设置 `ANDROID_HOME`，或在 `xposed/local.properties` 中配置 `sdk.dir`。以下两组命令均从本仓库根目录执行，选择对应平台的一组：

**Windows（PowerShell）：**

```powershell
cd xposed
.\gradlew.bat :app:assembleRelease
```

**macOS / Linux（终端）：**

```bash
cd xposed
gradlew :app:assembleRelease
```

Xposed 工程使用 Gradle **9.7.1**、AGP **9.3.3**、Android SDK **37**，Java 源码目标版本为 **17**。

## 兼容范围

- Gradle 插件编译依赖 AGP API **9.4.1**，使用方的 AGP 兼容性需随具体项目验证。
- 当前 Compose 补丁针对 Wear Compose **1.6.2** 的类结构与方法签名；升级依赖后需重新验证。
- 小米的表冠常量为 `(19, 18, 20)`，OPPO 为 `(12, 12, 12)`，顺序为 focus / tick / limit。
- Xposed 声明 `minSdk 26`，独立 Wear SDK bridge DEX 按 `min-api 29` 生成；低版本系统的完整功能兼容性尚需验证。
- Xposed 按名称查找 Compose 类。目标应用混淆这些名称后，Compose Hook 可能跳过；View Hook 与 SDK bridge 仍会分别尝试安装，实际效果取决于目标应用和设备。

## 目录内容

| 目录 | 内容 |
| --- | --- |
| `plugin/` | Gradle 插件、ASM 字节码改写与运行时打包 |
| `runtime/` | 设备识别、触觉常量、Google Wear 桩类和 consumer R8 规则 |
| `xposed/` | 独立的 libxposed 模块工程 |

开发与维护说明见 [AGENTS.md](AGENTS.md)。

## 许可证

见 [LICENSE](LICENSE)（GNU LGPL v3）。
