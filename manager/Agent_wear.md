# ReSukiSU Wear Manager UI 重构规范

## 1. 项目目标

对 ReSukiSU Manager 进行一次完整的 **Wear OS 原生 UI 重构**。

目标不是简单把手机端 Material 3 UI 缩小到手表屏幕，而是：

> 使用 **Wear OS Material 3 + Jetpack Compose** 重新设计整个 Wear Manager UI，同时复用现有的业务逻辑、数据层、ksud 通信和权限管理逻辑。

Wear Manager 应作为一个真正针对 Wear OS 设计的独立 UI。

------

# 2. 最重要的设计原则

实现时必须遵循以下优先级：

1. **Google 官方 Wear OS Design Guidelines**
2. **Android Developers 官方 Wear OS / Compose 文档**
3. 当前项目实际使用的 `androidx.wear.compose.material3` API
4. Material / Accessibility 相关规范
5. ReSukiSU 的业务需求
6. 普通手机 Material 3 UI 设计习惯

不要为了复用手机 UI 而牺牲 Wear OS 的交互方式。

尤其禁止：

- 把手机端 Bottom Navigation 原样缩小
- 把手机端 Scaffold 直接套到手表
- 使用手机 Material 3 组件代替 Wear Material 3
- 通过缩小字体/间距解决圆形屏幕适配
- 为了简单实现而删除 Wear OS 特有的交互

------

# 3. UI 技术栈

Wear UI 必须使用：

- Kotlin
- Jetpack Compose
- Wear Compose Material 3
- Wear OS 官方推荐的 Navigation / Pager / Scroll API
- Android Architecture Components
- ViewModel
- StateFlow / Flow

优先使用：

```kotlin
androidx.wear.compose.material3.*
```

而不是：

```kotlin
androidx.compose.material3.*
```

手机端 Material 3 与 Wear Material 3 是不同的设计系统。

------

# 4. Wear UI 必须独立设计

Wear Manager 不应该是：

```text
Phone UI
   ↓
缩小
   ↓
Wear UI
```

而应该是：

```text
Shared Business/Core
        │
        ├── ksud
        ├── Repository
        ├── Model
        ├── ViewModel
        │
        ├──────────────┐
        ↓              ↓
 Phone UI          Wear UI
 Material 3        Wear Material 3
```

可以共享：

- Model
- Repository
- ViewModel
- ksud communication
- 权限逻辑
- 模块管理逻辑
- 日志数据
- 设置数据

但 Wear UI 的：

- Theme
- Layout
- Navigation
- Gesture
- Components
- Spacing
- Typography
- Surface
- Pager

应该独立实现。

------

# 5. 圆形屏幕适配

Wear Manager 必须针对圆形 Wear OS 屏幕设计。

重点考虑：

- Pixel Watch 系列圆形显示屏
- 内容安全区域
- 圆角/圆形边缘裁切
- 首屏信息密度
- 大字号情况下的布局
- 横向 Pager 的边缘显示
- Card 与屏幕边缘距离
- 长文本换行
- Loading / Empty / Error 状态

不要简单依赖：

```kotlin
Modifier.fillMaxSize()
```

然后假设手机矩形布局能够自动适配。

必要时使用 Wear OS 提供的：

- ScreenScaffold
- TimeText
- ScalingLazyColumn
- TransformingLazyColumn
- PositionIndicator
- ScreenScaffold
- Wear Material 3 官方布局组件

具体 API 以项目当前依赖实际提供的版本为准。

------

# 6. 页面滑动切换 —— 强制保留

## 这是本项目的硬性要求

**必须保留左右滑动切换一级页面。**

重构过程中不得因为改用 Wear Material 3、Navigation 或其他官方组件而删除该功能。

核心交互：

```text
← 左滑                       右滑 →

[ Home ]  ←→  [ Superuser ]  ←→  [ Modules ]
                                  ←→ [ Logs ]
                                  ←→ [ Settings ]
```

具体页面顺序可以根据现有项目功能合理调整，但一级页面必须支持横向手势切换。

------

# 7. 横向与纵向手势必须正确区分

页面手势模型：

```text
                    横向
             ←────────────→
              一级页面切换


                    当前页面
                       │
                       │
                       ↕
                    纵向滚动


                    Rotary
                       │
                       ↕
                 当前页面滚动
```

要求：

### 横向手势

用于：

> 一级页面切换

### 纵向手势

用于：

> 当前页面内容滚动

### Rotary

用于：

> 当前页面纵向滚动

### Back

用于：

> 返回详情页 / 上一级页面 / 退出当前导航层级

不得让纵向滚动和横向 Pager 相互抢事件。

不得通过：

```kotlin
pointerInput {
    awaitPointerEventScope {
        // 拦截所有手势
    }
}
```

这种粗暴方式解决手势冲突。

------

# 8. Pager 架构

如果当前项目的一级页面适合 Pager，应优先采用 Wear Compose 当前版本提供的官方 Pager API。

例如：

```text
PagerState
    │
    ├── Home
    ├── Superuser
    ├── Modules
    ├── Logs
    └── Settings
```

不要让每个页面自行维护一级页面导航状态。

Pager 状态应该统一管理。

需要保证：

- 页面切换动画自然
- 滑动过程中不卡顿
- 当前页状态正确
- 页面重组不会无故丢失状态
- ViewModel 状态不因为 Pager 重组而丢失
- 当前页恢复正常
- 手势速度与动画自然匹配

如果当前 Wear Compose 版本提供更合适的官方 Pager 实现，使用当前版本 API，不要机械照抄旧版示例。

------

# 9. 页面层级

建议将 Wear Manager 组织为：

## 一级页面

### Home

显示：

- ReSukiSU 状态
- 当前版本
- KernelSU / ksud 状态
- Root 状态
- 设备基本信息
- 最近状态
- 快速操作

首页应该做到：

> 一眼看到核心状态。

不要塞入大量低价值信息。

------

### Superuser

显示：

- Root 权限状态
- 最近授权应用
- 权限相关信息

可以进入：

```text
Superuser
    ↓
Superuser Detail
```

------

### Modules

显示：

- 已安装模块
- 模块状态
- 启用/禁用状态

可以进入：

```text
Modules
   ↓
Module Detail
```

------

### Logs

显示：

- KernelSU / ReSukiSU 日志
- ksud 日志
- 操作状态
- 错误信息

日志页面应该针对手表优化：

- 长文本分段
- 可滚动
- 不要一次显示大量密集文字
- 必要时支持复制/导出等操作

------

### Settings

显示：

- Manager 设置
- Root / Superuser 设置
- UI 设置
- 关于
- 调试相关选项

------

### About

显示：

- ReSukiSU
- Manager 版本
- ksud 版本
- Kernel 信息
- 开源信息

------

# 10. 二级页面

一级页面可以通过横向 Pager 切换。

进入具体对象后，可以使用传统层级导航：

```text
Modules
   ↓
Module Detail
   ↓
Back
   ↓
Modules
```

或者：

```text
Superuser
   ↓
Superuser Detail
   ↓
Back
   ↓
Superuser
```

二级页面不需要继续复制一级 Pager。

------

# 11. 返回行为

必须正确支持：

- Wear OS 系统 Back
- 系统返回手势
- 页面层级返回
- Pager 页面返回
- Dialog / Sheet 返回

原则：

```text
详情页
   ↓ Back
列表页

列表页
   ↓ Back
Wear Manager 一级页面

一级页面
   ↓ Back
退出 Manager
```

不要拦截系统返回手势。

------

# 12. Rotary 输入

Pixel Watch 等 Wear OS 设备的 Rotary 输入必须得到正确支持。

原则：

```text
Rotary
   ↓
当前页面滚动
```

不要让 Rotary：

- 切换一级页面
- 触发按钮
- 修改不相关状态
- 与 Pager 手势产生冲突

对于：

- Modules
- Logs
- Settings
- Superuser

等长内容页面，Rotary 应该可以自然滚动。

------

# 13. UI 组件

优先使用 Wear Material 3 提供的官方组件。

例如根据当前依赖版本选择：

- Button
- CompactButton
- IconButton
- Card
- ListHeader
- Text
- CircularProgressIndicator
- Dialog
- AlertDialog
- Chip
- Slider
- ToggleButton
- ScalingLazyColumn
- TransformingLazyColumn
- ScreenScaffold
- PositionIndicator

不要为了复刻手机 Material 3 而自行创建大量替代组件。

如果官方 Wear Material 3 已经提供对应组件，优先使用官方组件。

------

# 14. 颜色系统

Wear UI 必须支持系统动态颜色以及 Wear Material 3 Theme。

不要硬编码整个 UI：

```kotlin
Color(0xFF...)
```

尤其不要：

```kotlin
background = Color.Black
```

然后让所有页面固定黑色。

应该通过 Theme 提供：

```kotlin
MaterialTheme.colorScheme
```

或者当前 Wear Material 3 版本对应的主题 API。

聊天/状态/Card 等 UI 的颜色也应该来自主题。

------

# 15. Dark Mode

Wear OS 通常以深色环境为主，但不能把：

> Dark Mode = 全部写死黑色

作为实现方式。

应该：

- 支持系统主题
- 使用 Wear Material 3 ColorScheme
- 保证文字对比度
- 保证状态颜色可辨识
- 不依赖固定背景色

------

# 16. Dynamic Color

如果项目当前版本支持 Wear OS Dynamic Color，应正确接入。

重点：

```text
System Color
      ↓
Wear Material 3 Theme
      ↓
所有页面
      ↓
Cards / Buttons / Text / Status / Dialog
```

不要出现：

```text
Home      → 动态颜色
Modules   → 固定绿色
Logs      → 固定灰色
Settings  → 固定黑色
```

这种颜色系统不一致。

------

# 17. Typography

字体必须适配：

- 圆形屏幕
- 小尺寸
- 大字号
- Accessibility Font Scale

不要通过固定：

```kotlin
fontSize = 12.sp
```

解决所有情况。

优先使用 Wear Material 3 Typography。

需要确保：

- 标题不会裁切
- 数字状态不会溢出
- 长文本正常换行
- Font Scale 增大后布局仍然可用

------

# 18. Accessibility

必须考虑：

- TalkBack
- 内容描述
- Touch Target
- Font Scale
- 对比度
- 状态信息不能只依赖颜色
- IconButton 必须具有语义

例如：

```kotlin
IconButton(
    onClick = ...,
    modifier = ...,
) {
    Icon(
        imageVector = ...,
        contentDescription = "..."
    )
}
```

不要为了节省空间把点击区域做得过小。

------

# 19. Loading / Empty / Error

所有重要数据页面必须处理：

```text
Loading
   ↓
Success
   ↓
Empty

或者

Loading
   ↓
Error
```

不能只实现成功状态。

例如 Modules：

```text
Loading
    ↓
Modules List
```

没有模块：

```text
No modules installed
```

读取失败：

```text
Failed to load modules
Retry
```

这些状态必须使用 Wear OS 合适的布局。

------

# 20. Superuser 页面

Superuser 不应该只是简单复制手机列表。

应该针对手表优化：

```text
Superuser

● Root Active

Recent
────────────

App A
Allowed

App B
Denied

App C
Allowed
```

点击进入详细信息。

状态应具有：

- 清晰文字
- 图标
- 颜色辅助

不能只使用：

```text
绿色 = Allow
红色 = Deny
```

必须让无障碍用户即使无法区分颜色也能理解状态。

------

# 21. Modules 页面

模块列表应适合手表：

```text
Modules

┌──────────────┐
│ Module A     │
│ Enabled      │
└──────────────┘

┌──────────────┐
│ Module B     │
│ Disabled     │
└──────────────┘
```

避免：

- 过多副标题
- 巨型 Card
- 大量按钮
- 手机式复杂列表

点击进入 Module Detail。

------

# 22. Logs 页面

日志页面必须考虑 Wear OS 小屏幕。

不要：

```text
一次性显示几千行日志
```

应该：

- 分段
- 可滚动
- 自动保持可读性
- 使用等宽字体时注意字体大小
- 支持 Loading / Empty / Error
- 必要时提供刷新

日志内容如果特别长，应避免造成 Compose 大量 UI 节点。

------

# 23. Settings 页面

设置采用清晰的分组：

```text
Settings

General
────────────
...

Security
────────────
...

Advanced
────────────
...

About
────────────
...
```

不要直接把手机 Settings 页面压缩到手表。

------

# 24. 动画

页面切换动画应该：

- 简洁
- 快速
- 连贯
- 不产生明显延迟
- 不阻塞触摸

横向 Pager 使用自然的页面过渡。

避免：

- 复杂 3D
- 大幅缩放
- 过度透明
- 大量 Blur
- 高耗能动画

Wear OS 设备资源有限。

------

# 25. 性能

重点关注：

- Compose 重组
- Pager 页面重组
- LazyColumn
- 日志列表
- 图片
- 动画
- Flow 收集
- ksud IPC

不要在 Composable 中进行：

```text
阻塞 IO
文件读取
Shell
网络请求
大量计算
```

应该通过：

```text
ViewModel
Repository
Coroutine
Flow
```

处理。

------

# 26. 状态管理

推荐：

```text
UI
 ↓
ViewModel
 ↓
Repository
 ↓
ksud / System
```

UI 不应该直接操作底层 ksud。

例如：

```kotlin
@Composable
fun ModulesScreen(
    viewModel: ModulesViewModel
)
```

然后：

```text
ModulesViewModel
        ↓
ModulesRepository
        ↓
ksud
```

------

# 27. 不要破坏现有业务逻辑

本次任务重点是：

> UI / UX 重构。

除非确实因为 Wear UI 适配而需要修改，否则不要随意重写：

- ksud
- Root 管理
- Superuser
- Module 管理
- IPC
- 权限
- 安装逻辑
- KernelSU 核心逻辑

如果发现业务层存在问题：

1. 记录问题
2. 分析原因
3. 尽可能保持 API 兼容
4. 不进行无关重构

------

# 28. Wear 与 Phone 模块

如果项目目前已经存在独立 Wear module：

```text
:wear
:app
```

必须保持：

```text
:app
    ↓
Phone Manager

:wear
    ↓
Wear Manager
```

不要让 Wear UI 直接复用手机 Activity / Navigation UI。

可以共享：

```text
common
core
data
ksud
repository
viewmodel
```

但 UI 应独立。

------

# 29. Build 要求

至少验证：

```bash
./gradlew :wear:assembleDebug
```

以及：

```bash
./gradlew :app:assembleDebug
```

如果项目实际模块名称不同，以 `settings.gradle.kts` 为准。

同时确认：

- Kotlin 编译成功
- Compose 编译成功
- Wear Material 3 API 正确
- Resource merge 成功
- APK 正常生成
- 无新增编译错误

------

# 30. 真机测试

优先在实际 Wear OS 设备测试。

目标设备：

> Google Pixel Watch 2

测试：

### 触摸

- 点击
- 长按
- 上下滚动
- 左右滑动

### Rotary

- 向前滚动
- 向后滚动

### 返回

- Back
- 系统返回手势

### 屏幕

- 圆形边缘
- 小字体
- 大字体
- Dynamic Color
- Dark Mode

### 状态

- Root 可用
- Root 不可用
- 无模块
- 有模块
- 日志为空
- 日志加载失败
- ksud 异常
- 数据加载中

------

# 31. 绝对不要做的事情

禁止：

1. 删除左右滑动切页
2. 用手机 Bottom Navigation 完全替代 Pager
3. 使用手机 Material 3 组件作为主要 Wear UI
4. 把手机 UI 简单缩小
5. 固定整个 UI 为黑色
6. 固定所有颜色
7. 固定所有字体大小
8. 禁止 Rotary
9. 拦截所有 Pointer Input
10. 用大量自定义组件重新实现官方 Wear Material 3 组件
11. 无理由修改 ksud
12. 无理由修改 Root/Module 核心逻辑
13. 删除现有业务功能
14. 为了消除编译错误而降低关键依赖版本
15. 使用过时的 Wear API 而忽略当前项目实际依赖版本

------

# 32. 手势交互最终模型

整个应用最终应该接近：

```text
                         ┌───────────────┐
                         │     Home      │
                         └───────┬───────┘
                                 │
                  ←──────────────┼──────────────→
                                 │
       ┌──────────────┐    ┌─────┴─────┐    ┌──────────────┐
       │  Superuser   │ ←→ │   Pager   │ ←→ │   Modules    │
       └──────────────┘    └─────┬─────┘    └──────────────┘
                                 │
                         ←───────┼───────→
                                 │
                       ┌─────────┴─────────┐
                       │       Logs        │
                       └───────────────────┘


当前页面内部：

              ↑
              │
              │ Touch
              │
              ↕
          内容纵向滚动
              ↕
              │
              │ Rotary
              ↓


进入详情：

Modules
   ↓
Module Detail
   ↓ Back
Modules
```

核心原则：

> **横向是页面导航，纵向是页面内容，Rotary 是纵向滚动，Back 是层级返回。**

四者不能互相替代。

------

# 33. Git 工作要求

当前工作分支：

```text
wear_manager
```

所有 Wear UI 修改必须在：

```text
wear_manager
```

分支完成。

不要直接修改：

```text
main
```

提交使用 Conventional Commits，例如：

```text
feat(wear): redesign manager with Wear Material 3
```

或者：

```text
refactor(wear): rebuild navigation and pager UI
```

------

# 34. 修改策略

不要一次性删除整个旧 UI 后盲目重写。

建议：

```text
1. 分析现有 UI
        ↓
2. 找出业务逻辑与 UI 的边界
        ↓
3. 保留 ViewModel / Repository / ksud
        ↓
4. 建立 Wear Theme
        ↓
5. 建立 Wear Navigation / Pager
        ↓
6. 重构 Home
        ↓
7. 重构 Superuser
        ↓
8. 重构 Modules
        ↓
9. 重构 Logs
        ↓
10. 重构 Settings
        ↓
11. Detail 页面
        ↓
12. Rotary / Gesture
        ↓
13. Accessibility
        ↓
14. 真机测试
```

每完成一个阶段都应该保证项目仍然可以编译。

------

# 35. 最终验收标准

完成后必须同时满足：

### UI

-  完整使用 Wear Material 3
-  不再是手机 UI 缩小版
-  圆形屏幕适配正常
-  Dynamic Color 正常
-  Dark Mode 正常
-  Font Scale 正常
-  Accessibility 正常

### Navigation

-  左右滑动切换一级页面
-  页面切换动画自然
-  上下滚动正常
-  Rotary 正常
-  Back 正常
-  Detail 页面层级正确
-  手势没有明显冲突

### 功能

-  Root 状态正常
-  Superuser 正常
-  Modules 正常
-  Logs 正常
-  Settings 正常
-  About 正常
-  ksud 通信正常

### 工程

-  `:wear:assembleDebug` 成功
-  `:app:assembleDebug` 成功
-  无新增编译错误
-  无明显 Compose Runtime 崩溃
-  无明显 Navigation 状态丢失
-  无明显旋转表冠滚动异常

------

# 36. Codex 最终要求

不要把这次任务理解成：

> “给现有 Manager 加一个 Wear OS UI。”

应该理解成：

> **“为 ReSukiSU 重新实现一个真正原生的 Wear OS Manager UI，同时完整保留现有业务能力和左右滑动页面导航。”**

其中：

**Wear Material 3 是 UI 基础。**

**Google Wear OS Guidelines 是设计依据。**

**左右滑动切换一级页面是硬性需求。**

**Rotary + 纵向滚动是页面内部交互。**

**Back 是层级导航。**

**手机端 UI 不得作为 Wear UI 的视觉模板。**

最终目标不是“能在手表上运行”，而是：

> **让 ReSukiSU Manager 在 Pixel Watch 2 上看起来和操作起来都像一个真正为 Wear OS 原生设计的应用。**