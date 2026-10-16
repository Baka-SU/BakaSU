# Manager 当前结构与 Wear 入口

Manager 是一个 Android Gradle 工程，`settings.gradle.kts` 包含 `:app`。手机和手表共用
`app/src/main`、应用包 `org.bakasu.bakasu`、数据层、用例及 Koin 容器，并非两个独立应用模块。
开发约定见 `AGENTS.md`；本文件说明当前代码结构。

## 启动与依赖

- `app/src/main/AndroidManifest.xml` 声明共用启动入口、Wear Activity、文件 Provider 和服务。
- `resukisu/KernelSUApplication.kt` 的类属于 `org.bakasu.bakasu`，启动 Koin 后调用
  `InitializeApplicationUseCase`。目录名与 Kotlin 包名并不总是一致。
- `ui/ui/MainActivity.kt` 的类属于 `org.bakasu.bakasu.ui`。启动时检测
  `PackageManager.FEATURE_WATCH`，手表转交 `ui/wear/WearMainActivity.kt`。
- `di/AppModules.kt` 提供 `coreModule`、`repositoryModule`、`useCaseModule` 和
  `viewModelModule`，同时注册手机和 Wear 共用依赖。
- `WearMainActivity` 应用语言、初始化 Manager、刷新主页与应用列表，根据 `StartupState`
  显示加载、错误或 `WearManagerScreen`。

## 数据与状态

代码目录相对于 `app/src/main/java/org/bakasu/bakasu/`：

| 目录 | 当前职责与 Wear 相关实现 |
| --- | --- |
| `data/startup`、`data/application` | 启动及进程初始化 |
| `data/shell`、`data/kernel`、`data/system` | ksud/SU 命令、内核能力和系统信息 |
| `data/profile`、`data/packageinfo` | 应用分组、授权配置、模板和应用操作 |
| `data/module` | 已安装模块、模块动作、在线目录 `ModuleCatalogRepository` |
| `data/download`、`data/update` | 下载状态与 Manager/模块更新 |
| `data/flash` | 模块安装、内核修补与刷写 |
| `data/susfs`、`data/logging` | SUSFS 配置/命令和 SU 日志 |
| `data/file` | Wear 文件列表、读写暂存、`WearFileProvider` |
| `data/network`、`data/webui` | 网络、手机链接、Wear WebUI 桥接服务 |
| `data/settings`、`data/theme` | 共用设置、主题和背景持久化 |
| `domain/model`、`domain/usecase` | 共用模型与领域操作 |
| `ui/viewmodel` | 将用例结果转换为页面状态和事件 |

页面通过 `koinViewModel` 获取 ViewModel。主要状态由 `HomeViewModel`、`SuperUserViewModel`、
`ModuleViewModel`、`SettingsViewModel`、`SulogViewModel` 提供；SUSFS、动态管理器、在线模块
及应用详情复用各自已有 ViewModel。`WearPreferencesViewModel` 管理手表专用的选择器、链接、
屏幕形态、长按反馈、常亮和返回键偏好。

文件仓库根据实际 Shell 的 `isRoot` 选择 `SuFile` 或普通 `File`，后者仍受 Android 进程权限
限制。安装前使用共用 `ZipFileDetector` 读取 ZIP 的 `module.prop`。

## Wear 界面与导航

- `ui/wear/WearManager.kt` 持有活动级共用状态、一个 `AppScaffold`、四页主 Pager 和
  Navigation 3 `NavDisplay`。`WearRoutes.kt` 定义可序列化目的地。
- Pager 经 `ui.component.HorizontalPagerWithInteraction` 处理交互；页面使用
  `WearList` 的 `ScreenScaffold` 和 `TransformingLazyColumn`，共用圆屏变换与方屏适配。
- `WearManagerTheme` 应用 DPI、屏幕形态、触觉反馈、常亮、动态主题、背景及卡片透明度。
  颜色配置与手机共用 `ThemeConfig`、`BackgroundManager` 和设置用例。
- `WearActivity` 只在用户开启相应选项时拦截 `KEYCODE_BACK`。`WearSwipeToDismissBox`
  在 API 36 以下提供应用内滑动；API 36 及以上交给平台预测性返回和 Wear Nav3。
- `WearSubPage` 承载同一目的地内部的输入/详情子页；页面返回不直接操作数据仓库。
- `WearDisplayPages`、`WearSelectionPage` 提供外观主题与显示触控设置；
  `WearAboutPage`、`WearLicensePage` 提供应用信息、版权弹窗和独立许可详情。
- `WearDetails` 展示已安装模块与授权应用详情。`WearModuleRepoPage` 复用
  `ModuleRepoViewModel`、`ModuleDetailViewModel` 与下载用例，下载后进入同一安装确认流程。
- `WearSuSFSPage` 组织状态和功能导航，`WearSuSFSStandardPage`、`WearSuSFSEntriesPage`
  展示一般设置、路径列表、添加与详情，命令仍由 `SuSFSViewModel` 执行。
- `WearAdvancedPages` 包含动态管理器、Umount 与卸载页；`WearLogsPage` 仅渲染共用日志状态。
- `WearFilePickerActivity` 承接缺少系统 DocumentsUI 时的选择器请求；
  `WearWebUIActivity` 承接模块 WebUI。链接模式由 `WearLinkViewModel` 选择手表或手机处理。

现有 Wear 组件与设置适配器位于 `ui/wear/component`；共用组件的 Kotlin 包为
`org.bakasu.bakasu.ui.component`，物理目录中也存在 `ui/component/component`。
新建可复用组件遵循 `AGENTS.md` 的共用包约定，不复制设置行、弹窗或手势实现。

## GMS 使用现状

当前 `play-services-wearable` 仍被手机链接、WebUI Data Layer 消息和在线模板能力使用，
`WearWebUiService` 的 Manifest 声明与其相连。此次检查未发现可确认废弃的 GMS 代码，
因此保留这些仍有调用的依赖与功能。

## 验证入口

在 Manager 根目录运行 `gradlew.bat spotlessCheck`、`gradlew.bat assembleRelease` 和适用的
`lintRelease`。Wear Compose 升级后还需 `assembleDebug`。Gradle 构建不能替代手表上的
SU 授权、SUSFS 内核、圆/方屏布局、预测性返回和手机连接验证。
