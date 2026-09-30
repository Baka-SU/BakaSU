# ReSukiSU Wear Manager 开发与验收规范

更新日期：2026-09-30。适用分支：`wear_manager`。

本文是 Wear Manager 后续实现与审查的产品契约。共享手机 Manager 的业务能力和资源，以 Wear Compose Material 3 重新组织手表界面。
本次修订只更新本文，不代表需求已实现，也不授权提交、推送、发布或修改手机界面。

## 1. 依据与此前审查纠正

项目职责、资源、设置组件和验证要求以 [AGENTS.md](AGENTS.md) 为基础；本文明确规定的 Wear 呈现与交互覆盖旧文档中相冲突的要求。
API 必须存在于工程实际解析的依赖中。官方规范用于评估质量，不能被用来凭空增加产品需求；遇到实际冲突应说明具体条款与限制。

此前审查见 [拉取分支并审查 Agent Wear](codex://threads/01a0ec21-8b08-79d3-bfb7-b766b0ec7af1)。按本次维护者要求纠正如下：

| 此前条款或结论 | 本文采用的要求 |
| --- | --- |
| 必须增加手机式底部四个可点击 Tab | 撤销。保持现有 Wear 导航、底部页码指示及横向 Pager，不增加占用高度的手机四 Tab 栏。 |
| 首页、设置和日志都必须上拉刷新 | 撤销。仅 Superuser / Modules 主列表提供本次规定的刷新手势；首页和设置不刷新，日志按现有业务动作更新。 |
| 刷新与初始 Loading 都使用居中覆盖 Spinner | 区分。初始 Loading 使用品牌加载屏及 `CircularProgressIndicator`；列表刷新使用 Wear Material 3 `PullToRefreshBox` 的视觉反馈。 |
| Wear 文案需要另外复制一套手机翻译 | 纠正为直接引用手机 Manager 已有资源 ID，自动使用其 locale；仅真正新增的语义补充资源。 |
| Settings 仅缺少 Appearance 区域 | 更新为“一般、安全性、进阶、显示”四个分类入口，关于单独保留；显示内只有色彩、DPI 两个入口。 |

缺少可见 Loading 仍是需要修正的实现问题，但不得再以“没有手机式四 Tab”或“首页没有刷新”判定不合格。
历史审查中的 ABI 合并冲突、崩溃日志产物等不因本文修改而消失；后续处理主线同步时应按当时实际代码核验，不能照搬旧提交结论或宣称本轮已修复。

## 2. 工程入口与实现边界

当前工程有 `:app`、`:baselineprofile`、`:lint-rules`，没有 `:wear` 模块。不要为了本文创建独立 Wear 工程或复制整套业务层。
代码根包为 `com.resukisu.resukisu`，以下路径均相对 `manager/`：

| 职责 | 当前入口 |
| --- | --- |
| 手机 / Wear 设备分流与系统 Splash | `app/src/main/java/com/resukisu/resukisu/ui/MainActivity.kt`、`ui/StartupSplashGate.kt` |
| Wear 一级导航及二级页面 | `app/src/main/java/com/resukisu/resukisu/ui/wear/WearManager.kt` |
| Wear 页面与主题 | `app/src/main/java/com/resukisu/resukisu/ui/wear/` |
| 公共 Wear 组件 | `app/src/main/java/com/resukisu/resukisu/ui/component/wear/` |
| 通用设置组件与分组 | `app/src/main/java/com/resukisu/resukisu/ui/component/settings/` |
| 手机对应页面 | `app/src/main/java/com/resukisu/resukisu/ui/screen/main/` |
| 状态与业务动作 | `app/src/main/java/com/resukisu/resukisu/ui/viewmodel/` |
| 既有模型、UseCase | `app/src/main/java/com/resukisu/resukisu/domain/` |
| Repository、文件与平台访问 | `app/src/main/java/com/resukisu/resukisu/data/` |
| 文案与品牌资源 | `app/src/main/res/` |

实现遵循 `Screen → ViewModel → 既有 UseCase / Repository → ksud / System`。
保留现有 Root、UID 分组、授权、模块安装、更新、执行和 IPC 语义；新增的文件选择、打开方式等只增加功能必需的适配。
不得在 Composable 中执行阻塞文件操作、Shell、网络或 IPC，也不为 UI 改动重写核心逻辑。

共享组件归入 `ui.component` 及其既有子包；不要在各页面建立重复的公共组件包。
设置优先复用 `SettingsBaseWidget` 及对应 wrapper、现有 Wear 设置封装，禁止用手写 `Row`、分隔线和点击逻辑仿造标准设置行。
新增的设置呈现适配归入 `ui.component.settings`；Wear 控件使用官方 `SwitchButton`、`Button` 等，不直接把手机设置整屏缩小。
静态设置分组遵循 `SegmentedColumn` 的规则；由运行时能力、集合变化控制的分组遵循 `LazySegmentedColumn` 的规则。
与 Wear 列表组合时按现有组件 API 做最小适配，不嵌套两个争抢滚动的纵向列表。动态圆角需要自定义时复用 `settings/material3internal/AnimatedShape.kt`。
自定义对话框生命周期使用 AGENTS.md 指定的 `rememberCustomDialog`、`rememberConfirmDialog`、`rememberLoadingDialog`；Wear 内容与控件按 Wear 设计呈现。

## 3. 技术栈、列表与资源

### 3.1 Wear UI

- 主控件使用 `androidx.wear.compose.material3`；Pager、列表及 Rotary 使用相应 Wear Foundation API。
- 不以 `androidx.wear.compose.material` 的 Material 2 或手机 `androidx.compose.material3` 控件替代 Wear 主界面。
- 保留 `AppScaffold`、`ScreenScaffold`、`HorizontalPagerScaffold` 的分工及官方时间、页码、滚动指示。
- 长列表优先采用 `TransformingLazyColumn`，共享其 state 与 `ScreenScaffold`，使用 `transformedHeight`、`SurfaceTransformation` 及官方 transformation。
- 不自建整套 Scale 系统；日志等高密度内容可按实际需要使用合适的滚动容器，仍需支持 Rotary、滚动指示及无障碍。
- Rotary 只滚动当前纵向内容，不切换一级页面，不自动触发刷新或功能面板。
- 圆屏与矩形屏共享信息结构，根据窗口尺寸、形状、Density、Font Scale 调整布局。不能按设备型号复制整套页面。
- 页面基底使用纯黑；卡片采用 Wear ColorScheme 的表面层级与匹配的文字色。色彩设置不应让文字失去对比度。
- 紧凑视觉不能缩小可点击区域。遵守系统字体缩放，重要文本至少 12sp、辅助文本至少 10sp；交互目标至少 48 × 48dp。

### 3.2 多语言与品牌

Wear 与手机处于同一 `:app` 资源命名空间，直接使用已有 `R.string.*`；Compose 中用 `stringResource`。
例如一级标题使用 `home`、`superuser`、`module`、`settings`，状态使用手机首页、授权和模块的对应资源。
不为相同语义继续增加 `wear_*` 翻译副本，不要求把手机翻译复制到每个 `wear_strings.xml`。
对既有重复资源先核对语义、占位符与调用点，再改为共享 ID；不能直接删掉仍被引用或确属 Wear 独有语义的资源。
真正新增的提示、调用方式、错误和无障碍描述按 AGENTS.md 补齐项目维护的 locale，不用 `MissingTranslation` 压制代替翻译。
用户展示文本禁止硬编码；版本、状态数值等来自实际数据。图标、App 名称、Launcher 品牌优先复用项目资源。
四个一级页标题的图标移除是明确例外，不影响应用图标、列表应用图标和功能按钮图标。

### 3.3 API 可用性

当前 `gradle/libs.versions.toml` 的 Wear Compose 版本为 `1.5.0`。
本轮核对该版本本地 sources：存在 `CircularProgressIndicator`、`EdgeButton`，不存在 `PullToRefreshBox`。
Google AndroidX 源码已记录 [新增 Wear Material 3 PullToRefreshBox 的提交](https://android.googlesource.com/superprojects/androidx/+/497db5dc1a984609b57b2785d22e59fdec6555cc)，源码合入不等于当前依赖或已发布 artifact 可用。

后续实现刷新前必须确认包含该 API 的实际发布版本及兼容依赖，采用满足需求的最小必要升级，并验证 Compose / Kotlin / AGP 兼容性。
若没有可用发布版本，明确报告该需求的依赖限制；不得伪造 API、以手机同名组件替代，或声称旧版已经实现。
具体签名、手势方向及 state 控制以解析到的官方源码为准，不在本文虚构参数或锁定未经验证的版本号。

## 4. 视觉参考

参考包：[docs-self/demo.zip](docs-self/demo.zip)。本轮已查看全部 7 张图片；桌面目录不作为唯一依赖，后续 Agent 可解压此包查看原图。

| ZIP 内路径 | 用途 |
| --- | --- |
| `demo/loading,jpg.jpg` | 初始 Loading 的时间、品牌图标、名称、进度反馈层级 |
| `demo/圆弧按钮组件.jpg` | 底部弧形操作按钮 |
| `demo/色彩界面1.jpg.png` | 跟随系统强调色、三列圆形色块与选中标记 |
| `demo/色彩界面2.jpg` | 自定义十六进制色值输入与应用按钮 |
| `demo/色彩界面3.jpg` | 背景图片入口、背景变暗百分比与滑动调节 |
| `demo/dpi大小调节1.jpg`、`demo/dpi大小调节2.png` | UI 大小倍率、滑动调节、独立应用按钮 |

参考图来自其他应用，只复用布局与交互层级。使用 ReSukiSU 的图标、资源、主题和文案，不复制 WearQQ 品牌、不嵌入整张截图、不使用示例中的多边形加载动画。
示例中的屏幕形状选项不额外扩展为新功能；实际圆屏适配仍由设备配置决定。

## 5. 导航、标题与底部圆弧按钮

一级页面固定顺序：`Home ↔ Superuser ↔ Modules ↔ Settings`。保持现有横向滑动、Pager 状态和底部页面指示方式，不增加手机式四 Tab 导航栏。
四页标识标题仅显示居中的资源文本，不在旁边显示首页、盾牌、模组、设置图标。
搜索、日志、详情、分类设置、关于、文件选择和功能面板都属于二级内容，不增加第五个一级页面。
离开列表进入详情或功能面板后，返回应恢复原 Pager 页、搜索条件和列表位置。

底部适配圆弧按钮采用 Wear Material 3 `EdgeButton`。
列表页面优先接入 `ScreenScaffold.edgeButton`，让按钮随列表滚动在末尾自然显现；不要直接作为普通最后一个 list item 仿造弧形，也不要强制常驻并挤压首屏。
该用法见 [Wear Material 3 迁移指南](https://developer.android.com/training/wearables/compose/migrate-to-material3)。圆形、矩形屏均需检查按钮内容可见且可操作。

| 页面 | 圆弧按钮行为 |
| --- | --- |
| 首页末尾 | 返回上层；根页面按现有返回行为退出 Manager |
| Superuser app list 末尾 | 同一级页面的返回行为，不误标为返回顶部 |
| Modules list 末尾 | 返回当前模组列表顶部，保留搜索和排序，不退出应用 |
| 模块高级选项末尾 | 返回 Modules list，并恢复原滚动位置 |
| 二级详情、设置分类、文件选择等 | 返回所属上层；文件选择取消不得安装或导出 |

圆弧按钮补充可见操作，不替代系统 Back 和 Wear swipe-to-dismiss。二级页面滑动返回上一层，根页面按现有行为退出；不得为 Pager 或纵向手势粗暴吞掉系统返回。

## 6. 系统 Splash、Loading 与刷新

### 6.1 启动流程

设备类型复用 `PackageManager.FEATURE_WATCH` 判定，不根据屏幕是否圆形猜测设备类型。
手机维持既有启动呈现；Wear 系统 Splash 的底色及底部区域为纯黑，使用与 Launcher 一致的 App 图标。
纯黑启动背景应在系统绘制启动窗口时生效，不能只在 Compose 进入后刷黑来掩盖白色闪屏。

Wear 启动顺序：

```text
系统 Splash：纯黑背景 + Launcher 品牌图标
    → 应用内品牌 Loading 屏
    → 当前页面所需初始数据就绪
    → Home / 实际目标页面
```

系统 Splash 应直接衔接 Loading，不继续等待所有业务读取才进入空列表，也不人为增加固定启动延迟。
手机的 `StartupSplashGate` 行为保留；Wear 对应等待条件调整到应用内 Loading 屏，不顺带修改手机启动策略。
系统 Splash 在黑色背景上显示与 Launcher 一致的 48 × 48dp App 图标；应用内品牌 Loading 可以按示例与窗口尺寸布局，二者不是同一个画面。

Loading 屏使用官方 `TimeText`、项目实际 Launcher / 当前图标、`app_name`，下方显示 `androidx.wear.compose.material3.CircularProgressIndicator`。
图标、名称和指示器整体在圆屏安全区域居中；时间来自系统，不复制示例中的固定时间。
初始化失败、Root 不可用和空数据有明确状态，不无限停留在 Loading。首次加载、日志/详情读取等等待场景也需可见反馈，不能仅靠 `isLoading` 禁用手势。
实际任务完成即结束动画；切换页面、系统返回及失败路径不能留下持续旋转的残留层。

### 6.2 列表刷新

仅 Superuser / Modules 主列表保留上拉刷新，调用现有 `SuperUserUiAction.Refresh` 与 `ModuleUiAction.Refresh(manual = true)`。
刷新视觉使用 `androidx.wear.compose.material3.PullToRefreshBox`；与初始品牌 Loading 分开，不把刷新时的列表整体替换为 Splash。
保留数据、搜索条件、排序、Pager 和滚动状态，不插入巨大 Spinner item 推动布局；失败时保留旧数据并显示资源化错误。
刷新中禁止重复执行同一刷新任务，复用现有 ViewModel 的任务控制。
不增加独立刷新按钮；首页与设置不接入刷新手势。日志可沿用自身读取、切换文件或刷新动作，本次不要求它套用主列表刷新手势。

用户要求向上拉，而官方 `PullToRefreshBox` 的默认行为不能仅凭名称认定也是上拉。
必须检查实际方向及 state API，关闭会冲突的默认触发，并以最小手势适配驱动官方反馈；不能简单翻转整个界面或复制其动画自造 Spinner。
若实际 API 无法同时满足官方组件与上拉交互，应明确具体限制，不擅自改成下拉刷新或用手机组件顶替。

## 7. 上拉功能面板与刷新手势分工

以下规则已由用户确认：**短上拉松手刷新；持续上拉约 2 秒进入功能面板。**
上拉指手指向屏幕上方移动，下拉指手指向下方移动。普通滚动应先由列表消费，功能手势仅在相应列表边界启动。

| 场景 | 行为 |
| --- | --- |
| 首页列表底部持续上拉 | 短促震动并显示约 2 秒阻尼过程，然后进入重启选项面板；不刷新首页 |
| Superuser / Modules 列表底部短上拉，达到刷新距离阈值后在面板触发前松手 | 刷新该列表，显示官方刷新反馈 |
| Superuser / Modules 列表底部持续上拉，满足距离条件并保持约 2 秒 | 短促震动及阻尼反馈后进入筛选/排序面板；此手势不刷新 |
| 功能面板顶部持续下拉 | 短促震动及约 2 秒阻尼反馈后回到所属页面，恢复原列表状态；不刷新 |
| 未达到有效条件、反向拖动或取消 | 恢复当前页面，不刷新、不切换面板 |

约 2 秒指边界交互的阻尼/进入过程，不是连续震动 2 秒、阻塞主线程或每次普通滑动都强制等待。
一次有效手势只能提交一种动作。进入面板后，松手不能再补发刷新；短上拉刷新不能先切换面板再切回来。
普通横向滑动由 Pager 处理，Rotary 由当前列表处理，系统返回保留自身手势；离开页面应取消未完成的功能面板手势。
面板内继续用 Wear 控件、滚动指示及 Rotary。系统 Back 和末尾返回按钮可直接返回，不附加 2 秒等待；为无障碍提供等价操作入口和资源化语义。
实现后需在圆屏设备校准距离、持续时间与手势冲突，不能只凭静态代码宣布交互通过。

## 8. Home

参考手机 `ui/screen/main/HomePage.kt` 的 `StatusCard`、`InfoCard`，复用 `HomeViewModel`、`HomeSystemInfo`、`KernelStatus` 的数据和条件。

1. 首个状态组件沿用手机的主状态、说明、错误/警告和摘要层级，展示当前运行/授权状态及已在该组件出现的 Superuser / Modules 数量。
2. 信息分组、字段语义和条件展示与手机保持相同，以 Wear 控件重新排版，不复制手机控件尺寸。
3. 设备信息包括设备型号、Kernel release、Android 版本、KernelSU 版本、Manager 版本及满足条件的 SUSFS 版本。
4. 状态信息包括 SELinux、**seccomp**（需求中的 secomp 指该字段），以及手机按能力展示的多 Manager、Hook 类型、Zygisk、MetaModule 信息。
5. seccomp 使用手机现有“不支持 / 禁用 / 严格 / 过滤 / 未知”资源与状态映射，不把未知伪装为正常。
6. 保留手机功能模式、简单模式和能力判断；信息超出首屏则继续滚动展示，不因手表屏小删除 SELinux 等字段或强制全部挪到 About。
7. 删除末尾重复的 Superuser / Modules 数量组件，末尾加入返回 `EdgeButton`。
8. 不提供首页上拉刷新；按第 7 节进入重启面板。

重启面板复用手机动作：重新启动、软重启、Recovery；其他 Bootloader、Download、EDL、Userspace 选项遵循手机的设备支持条件。
资源与参数参考手机 `RebootDropdownItems`，调用 `HomeUiAction.Reboot(reason)` / 既有 `RebootUseCase`，不得执行另一套 Shell 命令。
保留手机现有适用的确认及错误处理；无 Root / 不支持时按真实能力显示或禁用，不能提供会虚假成功的操作。

## 9. Superuser 与 SU Log

### 9.1 应用列表

- 删除重复的 Root 检测卡片，首页负责该状态展示；权限限制仍遵循现有后端条件。
- 加入 Wear 搜索入口和 SU Log 入口。搜索使用 Wear Compose 按钮、结果列表与手表适用的输入界面，可接系统文字/语音输入，不搬入手机大搜索栏。
- 查询复用 `SuperUserUiAction.Search(query)`，保留手机应用名、标识符、音译搜索以及共享 UID 分组语义。
- 列表项可点击进入现有授权详情；只对 `allowSu` 已授权的应用使用高对比取色背景遮罩，并配套足够清晰的文字与授权标记。
- 未授权应用维持默认 Wear 表面背景，不对所有应用铺高饱和取色遮罩；不能仅凭颜色表达授权状态。
- 搜索无结果、真实空列表、读取失败和 Loading 分开显示。
- app list 末尾使用返回 `EdgeButton`；刷新及上拉面板按第 6、7 节。

### 9.2 筛选面板

持续上拉进入后，先展示“显示系统应用”，下面放置手机已有的筛选/排序控件。
复用 `SetShowSystemApps`、`SetSort`、`SetReverseOrder` 与现有 `SortType`：名称、安装时间、更新时间、大小、使用频率。
当前手机没有额外的“仅已授权”等专用筛选动作，不能只为了文档示意虚构后端支持；本轮以已有条件与排序能力为准。
保留手机 Manager、已授权应用等现有优先级规则、持久化 key 和搜索状态。设置变化后列表更新，按第 7 节下拉或 Back 返回。

### 9.3 SU Log

SU Log 属于 Superuser 的二级页面，不增加一级 Tab；Settings 中已有入口可以保留为一般分类的入口。
复用 `SuLogViewModel.kt` 中的 `SulogUiAction`、日志 Repository，以及手机 `ui/screen/SulogScreen.kt` 的实际实现。
移植启停记录、文件选择、日志展示、搜索、事件过滤、清理及既有更新动作，用 Wear Compose 重新排版。
手机已有的确认、读取权限、错误处理仍有效；错误报告导出与 SU 日志查看是不同功能，不混用数据或文件格式。

## 10. Modules

### 10.1 主列表

标题下方放置一行紧凑操作：左侧搜索、右侧安装。
两者使用相同的 Wear Compose 按钮实现与视觉规格，缩小原安装按钮的视觉占用，仍保留合格点击区域和资源化无障碍描述。
搜索可参考 Google Wear 联系人应用的入口与独立输入页模式；以当前官方可用组件实现，不凭印象虚构 Wear `SearchBar` API。
搜索复用 `ModuleUiAction.Search(query)`，保留手机现有模块名称、ID 等匹配逻辑。

模组卡片减少过深的背景遮罩，以中性 Wear 表面色为主，强调色只用于明确操作或状态；不大量使用取色背景。
保留名称、启用/禁用、待移除等快速判断信息，详情按需展开。
末尾使用返回顶部 `EdgeButton`，点击只回到该列表顶部，不清除筛选与排序。
安装接入第 13 节选择器，复用现有 `FlashViewModel`、`FlashOperation.Module(uri)` 及安装完成后的数据更新逻辑。

### 10.2 高级选项

点击模组的高级选项进入二级内容，按手机 `ModulePage.kt`、`InstalledModule` 与详情动作判断能力：

| 操作 | 显示与执行条件 |
| --- | --- |
| 打开 WebUI | `hasWebUi` 等现有能力条件成立；按第 12 节选择打开方式 |
| 执行 | `hasActionScript`，并遵循手机启用、移除等限制；复用现有执行页面 / UseCase |
| 更新 | 已有可用更新元数据 / 地址；复用检查、Changelog、下载、安装和结果处理 |

保留现有启用、停用、卸载能力，不显示模块未提供的操作，不为了有按钮而宣称不存在的能力。
执行和更新等待需要可见反馈；操作成功/失败后保持手机一致的数据更新语义。
返回按钮移到所有选项末尾，使用 `EdgeButton`，返回原模组列表。

### 10.3 排序面板

持续上拉进入后展示“执行优先”和“启动优先”。“启动优先”在当前手机逻辑中对应 `sortEnabledFirst`，不是修改模块脚本执行顺序。
复用 `ModuleUiAction.Sort(enabledFirst, actionFirst)`、`sortActionFirst`、`sortEnabledFirst` 及 `ModulePreferencesRepository` 的现有存储。
两种偏好按手机规则组合，不创造新的执行调度策略；保留当前搜索与列表状态。下拉返回按第 7 节。

## 11. Settings、显示与 About

### 11.1 分类结构及功能映射

设置首页显示“一般、安全性、进阶、显示”四个独立分类按钮，另保留关于入口。
点击分类进入该分类的 Wear 功能列表；不把前三类全部铺在同一长页面，不将关于并入四类。
首页与分类列表不提供上拉刷新。以下是当前手机功能的 Wear 分类，不是已完成状态：

| 分类 | 功能 | 复用的动作或入口 |
| --- | --- | --- |
| 一般 | Manager 更新检查、Beta 更新检查、模组更新检查 | `SetManagerUpdateCheck`、`SetBetaUpdateCheck`、`SetModuleUpdateCheck` |
| 一般 | 软重启偏好、语言、替代图标 | `SetUseSoftReboot`、`SetLanguage`、`SetAlternateIcon` |
| 一般 | SU Log 入口、错误报告保存/分享 | 既有日志页面及手机 `generateBugreport`、保存/分享流程 |
| 一般 | 网页 / WebUI 打开方式、选择文件调用方式 | 新增偏好，规则见第 12、13 节 |
| 安全性 | SU 兼容模式、Kernel 卸载挂载、默认卸载模组 | `SetSuCompatMode`、`SetKernelUmount`、`SetDefaultUmountModules` |
| 安全性 | SU Log 开关、隐藏 SELinux 状态 | `SetSuLog`、`SetSelinuxHide` |
| 安全性 | 应用配置模板 | 手机 `AppProfileTemplate` 对应能力及数据逻辑，呈现改为 Wear |
| 进阶 | ADB Root、自动解除限制 | `SetAdbRoot`、`SetAutoJailbreak`；文案直接使用现有资源 |
| 进阶 | 动态 Manager 管理、卸载挂载路径管理 | 手机 `DynamicManager`、`UmountManager` 对应能力 |
| 进阶 | 临时/永久卸载、恢复原始镜像 | 手机 `UninstallItem` 及现有 Flash 操作、确认和支持条件 |
| 进阶 | 已有返回动画/退出方向设置（若对应能力适用） | `SetPredictiveBackAnimation`、`SetPredictiveBackExitDirection`，保留 Wear 返回规则 |
| 显示 | 色彩、DPI 大小调节 | 下节规定的两个二级入口，复用 `SettingsViewModel` 与设置 Repository |
| 独立关于 | 手机 About 的项目、版本及开源信息 | `ui/screen/about/About.kt` 的信息与目标链接 |

保留手机关于功能可用性、完整功能模式、LKM、late-load、kernel_umount 等显隐/启用条件，不能无条件展示所有内核开关。
以上分类覆盖手机主设置的非主题功能；实施时对照当时 `SettingsPage.kt` 及其子页，若上游新增功能则归入最贴近的四类，不遗漏或顺带重写业务。
手机主题页面不整屏移植；本文的显示设计单独实现。点击分类不能跳入未经适配的手机页面来冒充 Wear 已完成。

### 11.2 色彩

按照第 4 节三张色彩图实现同一个可滚动的 Wear 页面：

1. 顶部显示官方时间及页面标题；先放“跟随系统强调色”控件，区分跟随系统和自定义选择。
2. 使用三列圆形色块，选中项显示勾选和边框，不能只靠颜色表示选中；窄屏、较大字体下按可操作宽度调整。
3. 下面放十六进制色值输入和“应用自定义颜色”按钮。进行必要的合法色值校验，错误使用资源文本。
4. 按示例保留背景图片选择入口、清除/默认状态及背景变暗百分比、滑动调节；图片走内置/系统选择器的图片模式。
5. 基底保持纯黑，图片与遮罩用于内容表面并保证文字对比度，不能因为换色把屏幕根背景变成手机浅色主题。

复用 `SetDynamicColor`、`SetThemeColor`、`SetCustomBackground`、`RemoveCustomBackground`、`SetBackgroundDim` 及既有颜色/背景存储。
WearTheme 需要实际消费这些设置；仅写入手机 ThemeConfig 而 Wear 仍只取系统动态色不算实现。
系统动态色不可用时使用项目已有默认 Wear 配色。颜色选择页面使用 Wear Material 3 控件，必要的色块布局复用 Compose 基础布局，不自建全局主题框架。

### 11.3 DPI 大小调节

按两张示例实现：上方控件显示 UI 大小标签和当前倍率（默认以系统基准显示 `1.0x`），下方为可分步滑动的调节条，再放独立“应用 UI 大小”按钮。
采用当前可用的 Wear Material 3 `Slider` / `Stepper` 等组件，不复制手机长输入表单。
倍率与 `systemDpi`、目标 DPI 的换算保持一致，拖动只更新待应用值，点击应用后才持久化并按既有逻辑生效；未改变时应用按钮不应执行重复操作。
复用 `SetTempDpi`、`ApplyDpi` 与 `app_dpi`，这是 Manager 应用内的 Density 调节，不执行全设备 `wm density`。
提供恢复系统默认大小的操作，保留系统 `fontScale`；检查放大后文本、返回按钮、色块和列表不会被圆形边缘裁切。

### 11.4 About

移植手机实际 App 名称、图标、`BuildConfig.VERSION_NAME / VERSION_CODE`、源码项目链接、Telegram 群组、开源许可证及素材归属等已有信息。
按“时间 → 居中标题 → App 图标 → 名称 → 版本 → 项目/群组/许可证入口 → 返回”组织 Wear 布局，长内容可滚动。
版本不得硬编码，链接目标与手机保持相同；链接统一走第 12 节打开方式。许可证需要展示手机同等信息，不仅放一个名字代替实际开源信息。
不增加一级 About Tab，不因空间小丢掉项目相关信息。

## 12. 网页与模块 WebUI 打开方式

普通 Wear OS 不保证有 WebView，厂商系统可能提供实现；按运行时能力检测，不能只判断设备品牌。
在“设置 → 一般”提供打开方式：**自动 / 系统 WebView / 系统默认浏览器 / 在手机上打开**。
默认自动依次尝试：可用系统 WebView → 可处理目标的手表默认浏览器 → on phone。
手动选择尊重用户偏好；所选方式不可用时说明实际原因并提供切换入口，不悄悄更改已保存选择。

普通 HTTP(S) 链接的 on phone 使用官方 `androidx.wear.remote.interactions.RemoteActivityHelper`，按文档发送 `ACTION_VIEW`、目标 URI 和 `CATEGORY_BROWSABLE`。
检查可用性和目标节点，处理未连接、发送失败等结果，显示 Wear 反馈；“发送请求完成”不等同于已经确认手机浏览器内容加载成功。
参考 [RemoteActivityHelper API](https://developer.android.com/reference/androidx/wear/remote/interactions/RemoteActivityHelper)。

模块 WebUI 必须另外核验内容与执行环境：当前 `ui/webui/WebViewHelper.kt` 使用 `WebViewAssetLoader` 和 `ksu` JS 桥，资源来自手表本地模块。
浏览器或手机收到相同字符串 URI，不会自动获得本地资源，也没有原生 `ksu` 桥；`localhost`、AssetLoader 域名或手表 `content://` 不能当作远端可访问地址。

- 可用本机 WebView 时复用现有 WebUI 加载、桥接及模块上下文，保持管理的是手表上的模块。
- 外部浏览器 / on phone 路径必须先解决目标可访问性与所需模块接口，再实现相同能力；普通链接成功不代表模块 WebUI 已通过验收。
- 不得在手机打开同名本地模块并误称操作了手表，不得未经需求确认开放 Root Shell / JS 桥到公共网络。
- 采用最小必要的现有能力适配，不为跳转需求顺带建立通用远程控制平台。当前不存在的桥接能力应明确报告，不能用无效 URL 假装成功。

这项 WebUI 兼容需求是必需项；具体浏览器/手机执行契约需要在实施时基于模块实际 API 解决和验证，本次文档更新不声称已具备该能力。

## 13. 内置文件选择与可选手机日志导出

### 13.1 内置选择器

Wear 上不能依赖系统一定提供文件管理/选择 Activity。内置选择器是必需项，至少用于模组 ZIP 选择、日志导出目录选择，并支持色彩页的图片选择。
“设置 → 一般 → 选择文件调用方式”提供 **自动 / 内置选择器 / 系统选择器**。
默认自动优先可用的真实系统选择器；缺失、只有不可用 stub 或启动失败时进入内置选择器。手动系统模式不可用时提示并让用户切换。
不得保留当前“文件选择器不可用”后就结束安装流程的行为作为最终实现。

内置界面使用 Wear Compose 列表，显示当前目录、上级目录、文件/文件夹名称与必要元数据，支持 Touch、Rotary、滚动指示和末尾圆弧确认/返回按钮。
按任务过滤：安装选择 ZIP；背景选择图片；导出选择可写目录及文件名。取消只返回，不触发实际任务。
目录枚举、读写、可访问性检查归入 `data/file`，ViewModel 提供状态；复用 `ModuleFileRepository`、`ModuleFileDataSource` 和现有 URI/安装契约。
必要时将所选文件转为既有安装流程可读的 URI 或暂存到现有工作目录，不另外实现 ZIP 安装器。

界面出现内置目录列表不等于获得 Android 存储权限。只展示当前已授权或项目现有 Root 文件访问能力可读写的目录，权限不足要说明原因。
不能把普通 File 路径当作 SAF 授权，也不能把手表 URI 直接交给手机读取。
遵循 [Android 文件与 SAF 指南](https://developer.android.com/training/data-storage/shared/documents-files)；日志保存使用既有错误报告生成及输出格式，并显示实际保存位置。

### 13.2 导出错误报告到手机：最低优先级，可选

必需能力是手表本地导出；手机导出不阻塞其他需求完成。
若确认 Wear 与手机通过蓝牙连接且手机接收能力可用，询问用户是否将错误报告导出到手机；用户同意后再传输，拒绝则继续本地导出。

若实施此功能：

- 使用 Wear Data Layer 的 Asset 或 Channel 等适合文件的 API；接收端必须是对应手机应用，不能只发送打开手机的 Intent。
- 检查 Android 手机、Play services、接收能力，以及 Data Layer 要求的包名和签名匹配。
- 连接节点不等同于已证明蓝牙连接；Data Layer 可能走网络，不能据此声称“蓝牙直传”。
- “手机根目录”指共享存储根目录，不是文件系统 `/`。Wear API 传输文件不授予手机写该目录的权限；只有手机端现有授权/Root 能力确实允许时才能写入。
- 无法写入根目录时说明原因并让用户选择手机端可写位置；不能静默改变目标，也不能伪造根目录导出成功。
- 以手机端实际落盘结果报告成功，显示真实位置；未连手机、接收失败或权限不足仍可本地导出。

参考 [Data Layer 概览](https://developer.android.com/training/wearables/data/overview)与[数据同步及 Asset](https://developer.android.com/training/wearables/data/sync)。不为该可选项单独扩展长期后台同步框架。

## 14. 官方质量评估与交付验证

### 14.1 审查依据

必须使用以下官方文档，按应用实际适用项评估：

- [Wear OS 开发原则](https://developer.android.com/training/wearables/principles?hl=zh-cn)：腕上任务、简洁可读、独立可用、恰当反馈及耗电。
- [Wear OS 应用质量](https://developer.android.com/docs/quality-guidelines/wear-app-quality?hl=zh-cn)：字体、触摸目标、滑动返回、状态恢复、滚动指示、黑色背景、启动画面、形状、稳定性等。

四个横向页面和官方 Wear 列表已有正确方向；官方规范不要求加入手机四 Tab 栏。
也不能仅凭使用 Wear Material 3 就宣布整个项目合格。以下是本轮源码与资源检查的结果，未做模拟器/真机验收：

| 项目 | 当前观察与结论 |
| --- | --- |
| Wear 组件、列表、Pager | 已有 `TransformingLazyColumn`、`SurfaceTransformation`、`ScreenScaffold` 与四页 Pager；保留，不构成缺底部四 Tab 问题。 |
| 返回与状态（WO-V3 / WO-V5） | `WearManager.kt` 已有 swipe-to-dismiss 和可保存页面状态；手势冲突、跨分类返回及恢复效果需设备验证。 |
| 滚动指示（WO-V8） | 当前列表接入 `ScreenScaffold` 的 scroll state；长列表、日志及后续新页面仍需验证。 |
| 初始 Loading | 当前 `WearStartupStatus` / `WearList` 的 Loading 参数没有绘制品牌屏和进度反馈，未满足第 6 节。 |
| 系统 Splash（WO-V15） | 当前启动主题未显式配置 Wear 纯黑 Splash；不能确认启动底色与图标尺寸合格，需实现并冷启动检查。 |
| 文本与信息 | 四页标题仍带图标；首页缺少手机 SELinux / seccomp 等信息，且末尾重复数量；Superuser 仍有 Root 卡片。未满足本次产品要求。 |
| 字体、触摸、黑底与形状（WO-V1 / V2 / V13 / V14 / V16） | 有响应式布局及 Wear Theme，但本轮未实测 192dp 圆屏、大字体和完整点击区域，不能判为全部通过。 |
| 设置与文件 | 现有设置还是单页分类段落，缺显示分类及多项手机能力；安装仍依赖系统 picker，缺内置选择器。未满足本文。 |
| SDK / ABI / 稳定性（WO-P1 / WO-P2） | 配置 targetSdk 为 37，列出 arm64-v8a、x86_64、armeabi-v7a；本轮未构建、检查 APK 原生库或设备安装，不以配置代替运行证明。 |
| 发行、配套与其他质量项 | 若准备 Play 发行，再核验当时目标 SDK、64 位、Wear manifest 与商品信息。表盘、Tiles 等未提供的功能不强行列为本应用任务。 |

**当前结论：部分基础 Wear 模式已采用，但本次产品需求尚未完成，官方质量也未经过完整设备验证，不能宣称项目整体合格。**
这是文档修订时的观察，不是修复后的验收结果；后续代码变更后应重新判定适用项，不沿用本表作为完成证明。

### 14.2 实现后的相关验证

文档修改只需核对结构、需求一致性与链接/路径；不为纯文档修改要求构建或生成额外审查报告。
真正修改 UI / 设置后按 AGENTS.md 从 `manager/` 运行：

```powershell
.\gradlew.bat assembleRelease --console=plain
.\gradlew.bat :app:lintRelease --console=plain
```

Unix 环境对应 `./gradlew assembleRelease`。使用现有 `:lint-rules`，确保自定义 lint 检查通过；若构建/检查失败，报告真实错误与影响，不压制新增问题。
不执行不存在的 `:wear:assembleDebug`；单元测试若为 `NO-SOURCE`，不能报告为实际测试覆盖。

设备验证优先 Pixel Watch 2，并覆盖至少 192dp 圆屏、矩形布局和较大系统字体；按本次改动检查：

- 黑色冷启动 Splash → 品牌 Loading → 内容/失败状态，品牌资源与 Launcher 一致。
- 四页标题无图标且居中；横向 Pager、系统返回、Rotary 与列表无方向冲突，末尾 `EdgeButton` 行为正确。
- Home 信息与手机对应字段一致，无重复数量；首页及设置不会触发刷新。
- Superuser / Modules 短上拉只刷新、持续约 2 秒只进入面板；下拉返回、震动与阻尼符合要求，失败保留数据。
- 搜索、SU Log、授权背景、模组排序、按能力显示的 WebUI/执行/更新与原业务一致。
- 四类设置覆盖手机非主题功能；色彩、图片/遮罩与应用内 DPI 按示例生效，Root 能力条件不被绕过。
- 无系统 picker 时可内置选择并完成真实模块安装/日志导出；网页/WebUI 三段调用按实际环境验证。
- 手机日志导出仅在实现该可选项时验收传输、授权与实际落盘结果。

交付只说明改动、实际验证和剩余限制。构建、lint、静态源码检查不能代替手表交互或手机联动验收；不自动提交、推送、生成 APK 交付、建立长期流程或写入记忆。
