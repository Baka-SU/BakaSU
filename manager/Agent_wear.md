> > # ReSukiSU Wear Manager UI 重构规范
> >
> > > 本文是 ReSukiSU Wear Manager 的完整 UI/UX 重构规范，供 Codex / Agent 执行。  
> > > 目标：构建真正面向 Wear OS 的 Material 3 Manager，而不是把手机 Manager 缩小到手表上。
> >
> > ---
> >
> > ## 1. 项目目标
> >
> > 对 ReSukiSU Manager 的 Wear UI 进行完整重构。
> >
> > 核心目标：
> >
> > - 使用 Jetpack Compose
> > - 使用 **Wear OS Material 3**
> > - 针对圆形与直屏 Wear OS 设备进行响应式布局
> > - 保留现有业务逻辑、Repository、ViewModel、ksud 通信和功能
> > - 保留原手机 Manager 的主要图标资源与品牌识别
> > - 重新设计 Wear 专属的 Typography、Spacing、Card、List、Navigation、Gesture
> > - 使用 Wear OS 的 Transforming / Scaling 列表体验
> > - 保留一级页面左右滑动切换
> > - 保留四个一级入口：**首页 / 超级用户 / 模组 / 设定**
> > - 支持 Touch、上下滚动、左右滑动、Rotary、Back
> > - 优先保证可读性、可操作性与 glanceability
> >
> > 本次任务的本质是：
> >
> > > **共享业务层，重新实现一套原生 Wear OS Material 3 UI。**
> >
> > ---
> >
> > # 2. 设计依据优先级
> >
> > 实现时按以下优先级处理冲突：
> >
> > 1. Google 官方 Wear OS Design Guidelines
> > 2. Android Developers 官方 Wear OS / Compose 文档
> > 3. 当前项目实际使用的 Wear Material 3 API
> > 4. Material / Accessibility Guidelines
> > 5. ReSukiSU 现有业务与品牌要求
> > 6. 一般手机 Material 3 设计习惯
> >
> > 禁止为了复用手机端 UI 而牺牲 Wear OS 的交互模型。
> >
> > ---
> >
> > # 3. Material 3 技术栈要求
> >
> > ## 3.1 必须使用 Wear Material 3
> >
> > Wear UI 必须基于 Wear Compose Material 3。
> >
> > Kotlin import 应使用当前项目依赖提供的 Material 3 包，例如：
> >
> > ```kotlin
> > import androidx.wear.compose.material3.*
> > ```
> >
> > ### 重要说明
> >
> > 不要使用旧的 Wear Material 2：
> >
> > ```kotlin
> > import androidx.wear.compose.material.*
> > ```
> >
> > 也不要为了“看起来像 Material 3”而混用手机端：
> >
> > ```kotlin
> > import androidx.compose.material3.*
> > ```
> >
> > 作为主要 Wear UI 组件来源。
> >
> > > 注意：`androidx.wear.compose` 本身是 Wear Compose 的命名空间；真正需要避免的是 `androidx.wear.compose.material` 这套 Material 2 API。根据 Google Wear Compose Material 3 API 的包结构，Material 3 Compose Kotlin 包名应是 `androidx.wear.compose.material3`。依赖 artifact 与 Kotlin package name 不要混淆。
> >
> > ---
> >
> > # 4. 禁止把手机 Material 3 缩小成 Wear UI
> >
> > 禁止采用：
> >
> > ```text
> > Phone Material 3
> >         ↓
> > 缩小尺寸
> >         ↓
> > Wear UI
> > ```
> >
> > 应该采用：
> >
> > ```text
> > Shared Business / Core
> >         │
> >         ├── ksud
> >         ├── Repository
> >         ├── ViewModel
> >         ├── State / Model
> >         │
> >         ├──────────────┐
> >         ↓              ↓
> >    Phone UI         Wear UI
> >  Material 3       Wear Material 3
> >                   Transforming UI
> >                   Rotary
> >                   Pager
> >                   Responsive Layout
> > ```
> >
> > 共享业务逻辑，UI 不强制共享。
> >
> > ---
> >
> > # 5. 业务层不可随意重写
> >
> > 本次重点是 UI / UX 重构。
> >
> > 除非为 Wear UI 适配而确有必要，不要随意修改：
> >
> > - ksud
> > - Root 管理
> > - Superuser 核心逻辑
> > - Module 管理
> > - IPC
> > - 权限逻辑
> > - 安装逻辑
> > - KernelSU 核心逻辑
> > - 现有数据模型
> > - Repository 接口
> >
> > 推荐结构：
> >
> > ```text
> > UI
> >  ↓
> > ViewModel
> >  ↓
> > Repository
> >  ↓
> > ksud / System
> > ```
> >
> > Composable 不应直接执行阻塞 IO、Shell 或底层 IPC。
> >
> > ---
> >
> > # 6. Wear UI 的核心原则
> >
> > 整个 UI 必须同时满足：
> >
> > ```text
> > 高可读性
> > +
> > 高信息密度
> > +
> > 紧凑布局
> > +
> > 大而明确的主要操作
> > +
> > 自然滚动
> > +
> > Wear OS 原生交互
> > ```
> >
> > 目标不是“塞最多信息”，而是：
> >
> > > **用户抬腕后可以迅速理解当前状态。**
> >
> > ---
> >
> > # 7. Scale / Transforming UI —— 强制要求
> >
> > 这是本项目的重要硬性要求。
> >
> > Wear UI 中的列表不能简单使用手机式：
> >
> > ```kotlin
> > LazyColumn
> > ```
> >
> > 然后让每一个 Item 永远保持完全一样的尺寸与视觉权重。
> >
> > 应优先使用当前项目 Wear Material 3 版本提供的：
> >
> > ```text
> > TransformingLazyColumn
> > ```
> >
> > 或同版本对应的官方 transforming / scaling list API。
> >
> > 以当前项目真实 API 为准，不要盲目复制旧教程。
> >
> > ---
> >
> > # 8. Transforming / Scaling 的目的
> >
> > Scale 不是单纯的装饰动画。
> >
> > 它应表达列表中的视觉焦点：
> >
> > ```text
> > 屏幕中心
> >    ↓
> > 主要内容
> >    ↓
> > 较大 / 最高视觉权重
> > 
> > 接近屏幕边缘
> >    ↓
> > 次要内容
> >    ↓
> > 自然缩小 / 变形 / 降低视觉权重
> > ```
> >
> > 视觉效果应接近：
> >
> > ```text
> >            Module A
> >         ┌──────────────┐
> >         │   Module A   │
> >         └──────────────┘
> > 
> >      ┌────────────────────┐
> >      │      Module B      │
> >      │       Active       │
> >      └────────────────────┘
> > 
> >         ┌──────────────┐
> >         │   Module C   │
> >         └──────────────┘
> > ```
> >
> > 中间项目自然成为视觉焦点。
> >
> > ---
> >
> > # 9. 不要自行重造 Scale System
> >
> > 不要无理由自己实现完整的：
> >
> > ```kotlin
> > animateFloatAsState()
> > scaleX
> > scaleY
> > graphicsLayer
> > ```
> >
> > 来模拟官方 Wear list transformation。
> >
> > 优先使用 Wear Material 3 官方列表组件及其 transformation / scaling 能力。
> >
> > 只有当前项目实际 API 无法满足需求时，才进行最小范围的自定义补充。
> >
> > ---
> >
> > # 10. Scale 与触摸区域必须分离
> >
> > 视觉缩放：
> >
> > ```text
> > Visual Scale
> > ```
> >
> > 不等于：
> >
> > ```text
> > Touch Target Scale
> > ```
> >
> > 例如项目视觉缩小到边缘时，仍然需要保留合理的点击区域。
> >
> > 禁止为了 Scale 把实际 Touch Target 一起缩得过小。
> >
> > ---
> >
> > # 11. Scale 与 Rotary
> >
> > Rotary 输入必须自然驱动当前页面的纵向列表：
> >
> > ```text
> > Rotary
> >   ↓
> > 列表滚动
> >   ↓
> > Item Position 改变
> >   ↓
> > Transform / Scale 自动变化
> > ```
> >
> > 用户应该感受到列表“流动”，而不是突然跳动。
> >
> > 禁止 Rotary 用来代替一级页面横向切换。
> >
> > ---
> >
> > # 12. 横向 Pager 与纵向 Transforming List 必须分工
> >
> > 整个 Wear Manager 的手势模型固定为：
> >
> > ```text
> > ←────────→
> > 横向手势
> > 一级页面切换
> > 
> >       ↕
> > 上下滚动
> > 当前页面内容
> > 
> >       ↕
> > Rotary
> > 当前页面内容滚动
> > 
> > Back
> > ↓
> > 返回上一层
> > ```
> >
> > 不得让不同手势互相抢夺。
> >
> > ---
> >
> > # 13. 一级页面 —— 只能保留四个 Tab
> >
> > 一级导航固定为：
> >
> > 1. **首页**
> > 2. **超级用户**
> > 3. **模组**
> > 4. **设定**
> >
> > 对应：
> >
> > ```text
> > Home
> > Superuser
> > Modules
> > Settings
> > ```
> >
> > 禁止新增第五个一级 Tab。
> >
> > 以下功能不作为一级 Tab：
> >
> > - Logs
> > - About
> > - Device Detail
> > - Module Detail
> > - Superuser Detail
> >
> > 它们应从首页、设定或相应 Detail 页面进入。
> >
> > ---
> >
> > # 14. 四 Tab 必须同时支持点击与左右滑动
> >
> > 底部四个入口必须保留。
> >
> > 同时必须保留：
> >
> > > **左右滑动切换一级页面。**
> >
> > 最终交互：
> >
> > ```text
> > Home
> >   ↔
> > Superuser
> >   ↔
> > Modules
> >   ↔
> > Settings
> > ```
> >
> > 底部 Tab：
> >
> > ```text
> > [Home] [Superuser] [Modules] [Settings]
> > ```
> >
> > 二者必须访问同一个 Pager 状态。
> >
> > ---
> >
> > # 15. Pager 状态统一
> >
> > 不要维护两个互相独立的状态：
> >
> > ```text
> > selectedTab
> > currentPagerPage
> > ```
> >
> > 推荐：
> >
> > ```text
> > PagerState
> >    │
> >    ├── Home
> >    ├── Superuser
> >    ├── Modules
> >    └── Settings
> > ```
> >
> > 点击 Tab：
> >
> > ```text
> > Tab click
> >    ↓
> > PagerState.animateScrollToPage()
> > ```
> >
> > 滑动 Pager：
> >
> > ```text
> > Horizontal Pager
> >    ↓
> > currentPage
> >    ↓
> > Tab selected state
> > ```
> >
> > 确保两个方向始终同步。
> >
> > ---
> >
> > # 16. 横向滑动是硬性需求
> >
> > 重构过程中不得因为：
> >
> > - 更换 Material 3
> > - 更换 Navigation
> > - 增加底部 Tab
> > - 使用官方 Wear API
> >
> > 而删除横向滑动切页。
> >
> > 禁止用“底部 Tab 点击”完全替代滑动。
> >
> > 两套入口都必须保留。
> >
> > ---
> >
> > # 17. 四 Tab 的排列
> >
> > 固定顺序：
> >
> > ```text
> > 首页 → 超级用户 → 模组 → 设定
> > ```
> >
> > 其信息层级是：
> >
> > ```text
> > 概览
> >  ↓
> > 权限
> >  ↓
> > 功能扩展
> >  ↓
> > 配置
> > ```
> >
> > 该顺序应保持稳定，不要随意调整。
> >
> > ---
> >
> > # 18. Tab 的视觉设计
> >
> > 四个 Tab 必须：
> >
> > - 图标清晰
> > - 文字尽量单行
> > - 图标与文字形成紧凑单元
> > - 当前选择状态清晰
> > - 与 Pager 状态同步
> >
> > 推荐视觉语义：
> >
> > ```text
> > [Home Icon] 首页
> > [Shield Icon] 超级用户
> > [Module Icon] 模组
> > [Settings Icon] 设定
> > ```
> >
> > 具体图标优先使用项目已有图标。
> >
> > 如果原有资源没有，再使用当前 Wear Material 3 可用的官方图标。
> >
> > ---
> >
> > # 19. 文字 + 图标紧凑布局
> >
> > 所有文字与图标元素必须尽量形成一个整体：
> >
> > ```text
> > [Icon] Text
> > ```
> >
> > 或者：
> >
> > ```text
> > [Icon] Title
> >        Subtitle
> > ```
> >
> > 不要使用：
> >
> > ```text
> > Icon                 Text
> > ```
> >
> > 这样的超大水平间隔。
> >
> > 也不要无意义地写成：
> >
> > ```text
> > [Icon]
> > 
> > Text
> > ```
> >
> > 除非视觉设计确实需要。
> >
> > ---
> >
> > # 20. 一行优先
> >
> > 以下内容默认必须保持一行：
> >
> > - Tab 文案
> > - Button 文案
> > - Chip 文案
> > - 简短状态
> > - 设备名称
> > - 模组名称
> > - 权限状态
> > - 页面内的短标题
> >
> > 例如：
> >
> > ```text
> > [Shield] 超级用户
> > ```
> >
> > 优先于：
> >
> > ```text
> > [Shield]
> > 超级用户
> > ```
> >
> > 更禁止：
> >
> > ```text
> > 超
> > 级
> > 用
> > 户
> > ```
> >
> > 当一行确实无法容纳：
> >
> > 1. 优先缩短文案
> > 2. 优先省略低价值信息
> > 3. 再使用省略号
> > 4. 只有确实必要时才换行
> >
> > ---
> >
> > # 21. 主信息 / 辅助信息层级
> >
> > 核心原则：
> >
> > > **大字号告诉用户“是什么”，小字号告诉用户“具体是什么”。**
> >
> > 例如：
> >
> > ```text
> > 📱 Pixel Watch 2
> >    Android 17 · API 37
> > ```
> >
> > 其中：
> >
> > ```text
> > Pixel Watch 2
> > ```
> >
> > 是主要信息，使用较大的 Typography。
> >
> > ```text
> > Android 17 · API 37
> > ```
> >
> > 是辅助信息，使用较小 Typography。
> >
> > ---
> >
> > # 22. 推荐的信息层级
> >
> > 每个信息单元优先采用：
> >
> > ```text
> > [Icon] Primary
> >        Secondary
> > ```
> >
> > 例如：
> >
> > ```text
> > [Shield] Root
> >          已获取
> > [Puzzle] 模组
> >          6 个已启用
> > [Device] Pixel Watch 2
> >          Android 17 · API 37
> > ```
> >
> > 避免把所有底层字段全部堆到一级页面。
> >
> > ---
> >
> > # 23. 原手机图标资源必须优先复用
> >
> > Wear UI 重新设计布局，但应尽量保留 ReSukiSU 既有品牌识别。
> >
> > 如果手机 Manager 已经有：
> >
> > ```text
> > drawable/
> > drawable-anydpi/
> > mipmap/
> > vector drawable
> > ```
> >
> > 中的相关图标，应优先复用。
> >
> > 例如：
> >
> > ```text
> > Home
> > Superuser
> > Modules
> > Settings
> > Logs
> > Device
> > ```
> >
> > 使用原有资源。
> >
> > 只有缺少合适资源时才使用 Wear Material 3 / AndroidX 官方图标。
> >
> > ---
> >
> > # 24. 图标语义必须一致
> >
> > 同一个功能在不同页面尽量使用同一个图标。
> >
> > 例如：
> >
> > ```text
> > 🛡 = Superuser / Root
> > 🧩 = Module
> > ⚙ = Settings
> > 📱 = Device
> > ```
> >
> > 不要：
> >
> > - 同一功能在不同页面换不同图标
> > - 使用语义不相关的图标
> > - 用图标代替必须存在的文字状态
> >
> > ---
> >
> > # 25. 首页设计
> >
> > 首页要首先回答：
> >
> > > **设备上的 ReSukiSU 当前是否正常？**
> >
> > 建议结构：
> >
> > ```text
> > ReSukiSU
> > Root Active
> > 
> > [Device] Pixel Watch 2
> >          Android 17
> > 
> > [Root] Root
> >        已获取
> > 
> > [Module] 模组
> >          6 个已启用
> > ```
> >
> > 核心状态放在页面前部。
> >
> > 低价值底层信息，例如完整 Build ID、完整 Kernel 字符串、内部编译信息等，不要默认放满首页，应放到 About / Device Detail。
> >
> > ---
> >
> > # 26. 首页设备信息
> >
> > 设备信息推荐：
> >
> > ```text
> > [Device] Pixel Watch 2
> >          Android 17 · API 37
> > ```
> >
> > 主标题：
> >
> > ```text
> > Pixel Watch 2
> > ```
> >
> > 大字号。
> >
> > 辅助信息：
> >
> > ```text
> > Android 17 · API 37
> > ```
> >
> > 小字号。
> >
> > 不要把这些信息拆成很多高大的 Card。
> >
> > ---
> >
> > # 27. 首页状态信息
> >
> > Root / ksud / Modules 等状态采用：
> >
> > ```text
> > [Icon] Status Name
> >        Current State
> > ```
> >
> > 例如：
> >
> > ```text
> > [Shield] Root
> >          已获取
> > [Terminal] ksud
> >            Running
> > [Puzzle] 模组
> >          6 个已启用
> > ```
> >
> > 颜色只能作为辅助语义，不能让用户必须依靠颜色才能理解状态。
> >
> > ---
> >
> > # 28. Superuser 页面
> >
> > Superuser 使用 Wear Material 3 transforming list pattern。
> >
> > 推荐：
> >
> > ```text
> > [App Icon] App A
> >            Allowed
> > 
> > [App Icon] App B
> >            Denied
> > 
> > [App Icon] App C
> >            Allowed
> > ```
> >
> > 项目随列表位置自然发生 transformation / scale。
> >
> > 点击整个项目进入详情，而不是强迫用户点击很小的箭头。
> >
> > ---
> >
> > # 29. Modules 页面
> >
> > Modules 必须使用适合 Wear 的 transforming/scaling list。
> >
> > 例如：
> >
> > ```text
> > [Module] Module A
> >          Enabled
> > 
> > [Module] Module B
> >          Disabled
> > 
> > [Module] Module C
> >          Enabled
> > ```
> >
> > 列表只展示快速判断需要的信息。
> >
> > 详细信息进入：
> >
> > ```text
> > Module Detail
> > ```
> >
> > 详情可以展示：
> >
> > - Package
> > - Version
> > - Author
> > - Description
> > - Status
> > - 操作
> >
> > ---
> >
> > # 30. Settings 页面
> >
> > Settings 也应使用 Wear Material 3 的列表与 transformation pattern。
> >
> > 推荐：
> >
> > ```text
> > [General] 一般
> >           Manager 行为
> > 
> > [Shield] Root
> >          权限相关设置
> > 
> > [Palette] 外观
> >           主题与显示
> > 
> > [Info] 关于
> >        ReSukiSU 信息
> > ```
> >
> > 设置列表信息保持紧凑。
> >
> > ---
> >
> > # 31. Logs 页面
> >
> > Logs 不作为一级 Tab。
> >
> > 可以从 Settings / Home 进入。
> >
> > 日志属于高信息密度场景，不建议“每一行一个大 Card”。
> >
> > 推荐：
> >
> > ```text
> > Scroll / Transforming container
> >         ↓
> > 紧凑日志行
> > ```
> >
> > 支持：
> >
> > - Touch scrolling
> > - Rotary scrolling
> > - Position indicator
> > - Loading
> > - Empty
> > - Error
> > - Refresh
> >
> > 长日志不要一次性创建大量复杂 Compose 节点。
> >
> > ---
> >
> > # 32. Detail 页面
> >
> > 二级页面采用层级导航：
> >
> > ```text
> > Modules
> >   ↓
> > Module Detail
> >   ↓ Back
> > Modules
> > Superuser
> >   ↓
> > Superuser Detail
> >   ↓ Back
> > Superuser
> > ```
> >
> > 详情页无需复制一级 Pager。
> >
> > ---
> >
> > # 33. Back / 返回行为
> >
> > 必须正确支持：
> >
> > - Wear OS Back
> > - 系统返回手势
> > - Detail → List
> > - List → 一级页面
> > - Dialog / Sheet → 当前页面
> > - 一级页面 → 退出 Manager
> >
> > 不要粗暴拦截系统返回手势。
> >
> > ---
> >
> > # 34. Rotary 输入
> >
> > Pixel Watch 等 Wear OS 设备的 Rotary 输入必须得到支持。
> >
> > Rotary 的职责：
> >
> > ```text
> > Rotary
> >   ↓
> > 当前页面纵向列表滚动
> > ```
> >
> > Rotary 不用于替代：
> >
> > ```text
> > 横向 Pager
> > ```
> >
> > 也不要让 Rotary 随意触发按钮或页面切换。
> >
> > 当页面使用 TransformingLazyColumn 时，Rotary 应自然驱动其滚动状态。
> >
> > ---
> >
> > # 35. 圆形屏幕适配
> >
> > 必须充分适配圆形屏幕，例如 Pixel Watch：
> >
> > ```text
> >        ╭────────╮
> >     ╭──────────────╮
> >    │                │
> >    │    Content     │
> >    │                │
> >     ╰──────────────╯
> >        ╰────────╯
> > ```
> >
> > 重点考虑：
> >
> > - 圆形安全区
> > - 左右 Padding
> > - 顶部 / 底部 Padding
> > - Card Width
> > - Tab 宽度
> > - 标题
> > - Button
> > - Chip
> > - 长数字
> > - Font Scale
> >
> > 主要内容不能靠近圆形边缘到被裁切。
> >
> > ---
> >
> > # 36. 直屏适配
> >
> > 除了圆屏，必须适配传统矩形 / 直屏 Wear OS 设备：
> >
> > ```text
> > ┌────────────────────┐
> > │                    │
> > │      Content       │
> > │                    │
> > └────────────────────┘
> > ```
> >
> > 不要把圆屏的大额左右 Padding 永久硬编码到所有设备。
> >
> > 直屏应合理利用额外的矩形宽度。
> >
> > ---
> >
> > # 37. 响应式布局
> >
> > 不要只依据：
> >
> > ```kotlin
> > isScreenRound
> > ```
> >
> > 然后硬编码两套 UI。
> >
> > 布局应综合考虑：
> >
> > - Window Width
> > - Window Height
> > - Screen Shape
> > - Density
> > - Font Scale
> > - 实际可用内容区域
> >
> > 推荐思路：
> >
> > ```text
> > Window Metrics
> >       ↓
> > Screen Shape
> >       ↓
> > Width / Height
> >       ↓
> > Responsive Layout Params
> >       ↓
> > Wear UI
> > ```
> >
> > 可以建立：
> >
> > ```text
> > Compact
> > Medium
> > Expanded
> > ```
> >
> > 等布局等级，但不要为每一种设备写完全独立 UI。
> >
> > ---
> >
> > # 38. 圆屏 / 直屏仅改变布局参数，不改变信息结构
> >
> > 相同数据：
> >
> > ```text
> > Module A
> > Module B
> > Module C
> > Module D
> > ```
> >
> > 圆屏：
> >
> > ```text
> >        Module A
> >      Module B
> >    [ Module C ]
> >      Module D
> > ```
> >
> > 直屏：
> >
> > ```text
> > Module A
> > Module B
> > [Module C]
> > Module D
> > ```
> >
> > 必须保持：
> >
> > - 数据结构一致
> > - 导航一致
> > - 操作一致
> > - 信息层级一致
> >
> > 只调整：
> >
> > - Width
> > - Padding
> > - Spacing
> > - Transformation
> > - Item size
> > - Safe area
> >
> > ---
> >
> > # 39. Wear Material 3 列表与屏幕形态协同
> >
> > Transforming / Scaling 不是只针对圆屏。
> >
> > 直屏也应保留合理的列表 transformation，只是由于可用空间不同，其视觉效果可以更宽松。
> >
> > 例如：
> >
> > ```text
> > 圆屏
> > → 更明显的圆边缘空间变化
> > → 更明显的中央视觉焦点
> > 
> > 直屏
> > → 可利用更大的矩形区域
> > → Transformation 仍然存在
> > ```
> >
> > 不要在 `isRound == false` 时简单退化成手机 LazyColumn。
> >
> > ---
> >
> > # 40. 信息密度
> >
> > Wear UI 应避免无意义的大面积空白，但也不能把所有信息压在一起。
> >
> > 核心目标：
> >
> > ```text
> > 紧凑 ≠ 拥挤
> > 
> > 高密度 ≠ 小字体
> > ```
> >
> > 主要信息要大而明确。
> >
> > 次要信息才使用小字号。
> >
> > Touch Target 不能因为视觉紧凑而过小。
> >
> > ---
> >
> > # 41. Typography
> >
> > 建议建立三级信息层级：
> >
> > ### Primary
> >
> > 用于：
> >
> > - 页面标题
> > - 设备型号
> > - 核心状态
> > - 重要数字
> >
> > ### Secondary
> >
> > 用于：
> >
> > - 当前状态
> > - 模组名称
> > - 操作描述
> >
> > ### Supporting
> >
> > 用于：
> >
> > - 版本
> > - 时间
> > - 辅助信息
> > - 次要状态
> >
> > 优先使用 Wear Material 3 Typography，而不是整个项目手动固定字号。
> >
> > ---
> >
> > # 42. Font Scale
> >
> > 必须考虑 Accessibility Font Scale。
> >
> > 当系统字体变大时：
> >
> > - 不得裁切
> > - 不得出现不可操作布局
> > - 短文本尽量仍保持单行
> > - 长文本允许合理换行
> > - 不得通过关闭辅助功能来“解决”布局问题
> >
> > ---
> >
> > # 43. Dynamic Color
> >
> > 所有 Wear 页面使用统一 Theme / ColorScheme。
> >
> > 不要大量硬编码：
> >
> > ```kotlin
> > Color(0xFF......)
> > ```
> >
> > 应形成：
> >
> > ```text
> > System / Dynamic Color
> >        ↓
> > Wear Material 3 Theme
> >        ↓
> > Home / Superuser / Modules / Settings
> > ```
> >
> > 各页面不要各自创建一套完全不同的颜色系统。
> >
> > ---
> >
> > # 44. Dark Mode
> >
> > 支持系统 Dark Mode。
> >
> > 不要简单地：
> >
> > ```text
> > background = Black
> > ```
> >
> > 然后全部组件固定黑色。
> >
> > 应使用 Wear Material 3 ColorScheme，保持：
> >
> > - 层级
> > - 对比度
> > - 低视觉噪声
> > - 深色环境下的可读性
> >
> > ---
> >
> > # 45. Accessibility
> >
> > 必须考虑：
> >
> > - TalkBack
> > - Content Description
> > - Touch Target
> > - Font Scale
> > - Contrast
> > - 不依赖颜色理解状态
> >
> > 图标按钮必须提供语义描述。
> >
> > 状态表达应类似：
> >
> > ```text
> > [Icon] Root
> >        已获取
> > ```
> >
> > 而不能只有一个颜色圆点。
> >
> > ---
> >
> > # 46. Loading / Empty / Error
> >
> > 重要页面必须实现：
> >
> > ```text
> > Loading
> >    ↓
> > Success / Empty
> > ```
> >
> > 或者：
> >
> > ```text
> > Loading
> >    ↓
> > Error + Retry
> > ```
> >
> > 必须处理：
> >
> > - Root 状态读取失败
> > - Superuser 加载失败
> > - Modules 为空
> > - Modules 读取失败
> > - Logs 为空
> > - Logs 读取失败
> > - ksud 异常
> > - 数据加载中
> >
> > 不能只实现成功路径。
> >
> > ---
> >
> > # 47. Card / Button / Chip 的使用原则
> >
> > 优先使用当前 Wear Material 3 版本提供的官方组件。
> >
> > 包括但不限于当前版本实际可用的：
> >
> > - Card
> > - Button
> > - IconButton
> > - Compact Button / Compact 变体
> > - Chip / Compact Chip
> > - List Header
> > - Dialog
> > - AlertDialog
> > - Toggle / Switch / Slider
> > - ScreenScaffold
> > - Position Indicator
> > - TransformingLazyColumn
> > - 其他官方 Wear Material 3 组件
> >
> > 具体 API 以当前依赖版本为准。
> >
> > 不要因为网上旧教程存在就强行使用过时 API。
> >
> > ---
> >
> > # 48. 不要混用 Material 2
> >
> > 禁止以下组合：
> >
> > ```text
> > Wear Material 2 Card
> > +
> > Wear Material 3 Theme
> > +
> > Phone Material 3 NavigationBar
> > ```
> >
> > 必须让 Wear UI 的主要组件属于同一 Material 3 设计系统。
> >
> > ---
> >
> > # 49. 不要自行大量重建官方组件
> >
> > 如果当前 Wear Material 3 已经提供：
> >
> > - 列表
> > - Button
> > - Card
> > - Navigation
> > - Dialog
> > - Indicator
> >
> > 优先使用官方组件。
> >
> > 只有官方组件无法满足 ReSukiSU 特定业务时，才建立最小自定义组件。
> >
> > ---
> >
> > # 50. 点击区域
> >
> > 视觉布局可以紧凑，但实际点击区域必须足够大。
> >
> > 例如：
> >
> > ```text
> > Icon 20dp
> > ```
> >
> > 并不代表：
> >
> > ```text
> > Touch Target = 20dp
> > ```
> >
> > 应通过组件自身合理的可点击区域保证操作体验。
> >
> > ---
> >
> > # 51. 整个信息单元优先可点击
> >
> > 例如：
> >
> > ```text
> > ┌────────────────────┐
> > │ [Module] Modules   │
> > │          6 enabled │
> > └────────────────────┘
> > ```
> >
> > 用户点击整个信息单元即可进入。
> >
> > 不要只把一个很小的 `>` 做成唯一点击区域。
> >
> > ---
> >
> > # 52. 动画
> >
> > 页面切换和列表 transformation 应：
> >
> > - 连贯
> > - 快速
> > - 低延迟
> > - 不阻塞操作
> > - 不浪费 GPU / CPU
> >
> > 禁止过度：
> >
> > - 3D 动画
> > - 巨幅缩放
> > - 大量 Blur
> > - 长时间过渡
> > - 高耗能持续动画
> >
> > Horizontal Pager 应有自然的页面过渡。
> >
> > Transforming list 应由官方组件自然驱动。
> >
> > ---
> >
> > # 53. 性能
> >
> > 重点关注：
> >
> > - Compose recomposition
> > - Pager 重组
> > - TransformingLazyColumn
> > - Logs 长列表
> > - 图片解码
> > - Flow collect
> > - ksud IPC
> > - 动画
> >
> > 不要在 Composable 中执行阻塞 IO。
> >
> > 长列表必须使用 Lazy / Transforming Lazy 结构，而不是一次性创建全部 UI。
> >
> > ---
> >
> > # 54. 状态保存
> >
> > Pager 和列表状态应合理保存。
> >
> > 例如：
> >
> > 用户：
> >
> > ```text
> > Modules
> >  ↓
> > 向下滚动
> >  ↓
> > 切换 Home
> >  ↓
> > 返回 Modules
> > ```
> >
> > 应该尽可能恢复 Modules 的滚动位置，前提是数据仍有效。
> >
> > 数据发生重大变化时，应以最新数据为准。
> >
> > ---
> >
> > # 55. 页面架构建议
> >
> > 推荐：
> >
> > ```text
> > WearApp
> >  │
> >  ├── WearTheme
> >  │
> >  ├── WearPager / Navigation
> >  │      │
> >  │      ├── HomeScreen
> >  │      ├── SuperuserScreen
> >  │      ├── ModulesScreen
> >  │      └── SettingsScreen
> >  │
> >  └── Detail Navigation
> >         ├── SuperuserDetail
> >         ├── ModuleDetail
> >         ├── Logs
> >         └── About
> > ```
> >
> > 业务层：
> >
> > ```text
> > ViewModel
> >  ↓
> > Repository
> >  ↓
> > ksud / Android System
> > ```
> >
> > ---
> >
> > # 56. Home 推荐内容
> >
> > 首页优先显示：
> >
> > 1. ReSukiSU 当前运行状态
> > 2. Root 状态
> > 3. 设备名称
> > 4. Android / Wear OS 版本
> > 5. ksud 状态
> > 6. 模组启用数量
> > 7. 少量高频操作
> >
> > 完整底层技术信息放到 About / Device Detail。
> >
> > ---
> >
> > # 57. 快速操作
> >
> > 首页可以加入少量高频操作：
> >
> > ```text
> > [Refresh]
> > [Modules]
> > [Logs]
> > ```
> >
> > 但不能把首页变成大型控制面板。
> >
> > Wear OS 应保持 glanceable。
> >
> > ---
> >
> > # 58. Settings 建议分类
> >
> > 可采用：
> >
> > ```text
> > 一般
> > Root
> > 外观
> > 高级
> > 关于
> > ```
> >
> > 每个项目：
> >
> > ```text
> > [Icon] Title
> >        Subtitle
> > ```
> >
> > 长设置进入 Detail 页面。
> >
> > ---
> >
> > # 59. About / Device Detail
> >
> > 这些内容不进入一级 Tab。
> >
> > 可以展示：
> >
> > - ReSukiSU Manager 版本
> > - ksud 版本
> > - Kernel 信息
> > - Android 版本
> > - Wear OS 版本
> > - ABI
> > - Build 信息
> > - 开源信息
> >
> > 但默认不堆在首页。
> >
> > ---
> >
> > # 60. 手势冲突处理
> >
> > 必须正确处理：
> >
> > ```text
> > Horizontal Pager
> >         ↔
> > Vertical list
> >         ↕
> > Rotary
> >         ↕
> > System Back
> > ```
> >
> > 禁止通过“拦截所有 Pointer Input”方式粗暴处理。
> >
> > 横向手势应由 Pager 判断。
> >
> > 纵向手势应由列表滚动判断。
> >
> > Rotary 应由滚动容器处理。
> >
> > 系统 Back 不应被页面抢走。
> >
> > ---
> >
> > # 61. Navigation 最终模型
> >
> > 最终结构：
> >
> > ```text
> >                          ←→
> >                  Horizontal Pager
> >                          │
> >           ┌──────────────┼──────────────┐
> >           │              │              │
> >         Home        Superuser        Modules
> >           │              │              │
> >           └──────────────┼──────────────┘
> >                          │
> >                       Settings
> > 
> > 当前页面内部：
> > 
> >                     ↕ Touch
> >                     ↕ Rotary
> >                        │
> >                        ↓
> >             Transforming / Scaling List
> > 
> > 进入详情：
> > 
> > Modules
> >    ↓
> > Module Detail
> >    ↓ Back
> > Modules
> > ```
> >
> > ---
> >
> > # 62. 最终一级导航视觉模型
> >
> > ```text
> > ┌────────────────────────────────────┐
> > │                                    │
> > │            PAGE CONTENT            │
> > │                                    │
> > │      ↕ Vertical / Rotary           │
> > │                                    │
> > ├────────────────────────────────────┤
> > │ 🏠 首页 │ 🛡 超级 │ 🧩 模组 │ ⚙ 设定 │
> > └────────────────────────────────────┘
> > ```
> >
> > 左右滑动：
> >
> > ```text
> > Home ↔ Superuser ↔ Modules ↔ Settings
> > ```
> >
> > 底部 Tab：
> >
> > ```text
> > 点击 Tab = 切换 Pager 页面
> > ```
> >
> > ---
> >
> > # 63. 参考 Google Wear OS 列表设计
> >
> > 实现时重点参考用户提供的 Google 官方 Wear OS 列表文档与 API：
> >
> > - Wear OS Compose Lists
> > - Transforming / Scaling list pattern
> > - 当前项目实际版本的 Wear Material 3 API
> > - `androidx.wear.compose.*` API reference
> >
> > 不要只参考手机 Compose `LazyColumn` 教程。
> >
> > 特别注意：用户要求的 Scale 是 Wear 列表的视觉行为，不是简单的 `Modifier.scale()`。
> >
> > ---
> >
> > # 64. API 版本原则
> >
> > 实现之前必须检查项目实际依赖版本。
> >
> > 如果当前版本提供：
> >
> > ```text
> > TransformingLazyColumn
> > ```
> >
> > 优先使用。
> >
> > 如果 API 名称或签名不同：
> >
> > - 使用当前版本真实存在的官方 API
> > - 不为了匹配旧教程而强行降级依赖
> > - 不复制已经废弃的 Material 2 示例
> >
> > 出现 API 不匹配时，应根据当前项目依赖源码 / 官方参考文档调整，而不是改变整个设计目标。
> >
> > ---
> >
> > # 65. Build 要求
> >
> > 至少验证：
> >
> > ```bash
> > ./gradlew :wear:assembleDebug
> > ```
> >
> > 以及项目存在对应 Manager 模块时：
> >
> > ```bash
> > ./gradlew :app:assembleDebug
> > ```
> >
> > 具体模块名以 `settings.gradle.kts` 为准。
> >
> > 必须检查：
> >
> > - Kotlin 编译
> > - Compose 编译
> > - Wear Material 3 API
> > - Resource merge
> > - APK 生成
> > - 无新增 lint / compile 致命错误
> >
> > ---
> >
> > # 66. 真机测试
> >
> > 优先在：
> >
> > > Google Pixel Watch 2
> >
> > 测试。
> >
> > 至少覆盖：
> >
> > ### 触摸
> >
> > - 点击 Tab
> > - 左滑
> > - 右滑
> > - 上下滚动
> > - 点击列表项目
> > - Back
> >
> > ### Rotary
> >
> > - 向前
> > - 向后
> > - 长列表
> > - Transforming list
> >
> > ### 显示
> >
> > - 圆屏
> > - 直屏/模拟测试环境
> > - Dark Mode
> > - Dynamic Color
> > - Font Scale
> >
> > ### 数据
> >
> > - Root 成功
> > - Root 失败
> > - Modules 有数据
> > - Modules 空
> > - Logs 有数据
> > - Logs 空
> > - ksud 正常
> > - ksud 异常
> > - Loading
> > - Error
> >
> > ---
> >
> > # 67. 验收标准：Material 3
> >
> > - [ ] Wear UI 使用 Wear Material 3
> > - [ ] 不使用 `androidx.wear.compose.material` Material 2
> > - [ ] 不以手机 `androidx.compose.material3` 作为主要 Wear 组件来源
> > - [ ] Material 3 Theme / ColorScheme 统一
> >
> > ---
> >
> > # 68. 验收标准：Scale / Transform
> >
> > - [ ] 长列表使用当前 Wear Material 3 对应的 Transforming / Scaling pattern
> > - [ ] 不简单使用普通手机 LazyColumn 作为最终视觉
> > - [ ] 列表滚动时项目视觉层级自然变化
> > - [ ] 中心项目视觉权重最高
> > - [ ] 边缘项目自然弱化
> > - [ ] Scale 与 Rotary 配合正常
> > - [ ] Scale 不破坏 Touch Target
> > - [ ] 不重复实现官方 transformation system
> >
> > ---
> >
> > # 69. 验收标准：一级导航
> >
> > - [ ] 只有 Home / Superuser / Modules / Settings
> > - [ ] 左右滑动切换一级页面
> > - [ ] 底部 Tab 点击切换一级页面
> > - [ ] Pager 与 Tab 完全同步
> > - [ ] Back 正常
> > - [ ] 二级页面返回层级正确
> >
> > ---
> >
> > # 70. 验收标准：视觉
> >
> > - [ ] 原手机核心图标资源得到复用
> > - [ ] 图标与文字紧凑
> > - [ ] 短文字尽量一行
> > - [ ] 主标题较大
> > - [ ] 辅助数据较小
> > - [ ] 信息密度适合手表
> > - [ ] 没有大面积无意义空白
> > - [ ] 没有手机式巨大列表项目
> >
> > ---
> >
> > # 71. 验收标准：设备形态
> >
> > - [ ] 圆屏适配
> > - [ ] 直屏适配
> > - [ ] 根据窗口宽高响应式调整
> > - [ ] 不依赖单一 `isScreenRound`
> > - [ ] Font Scale 正常
> > - [ ] 圆屏边缘不裁切
> > - [ ] 直屏能合理利用横向空间
> >
> > ---
> >
> > # 72. 验收标准：输入
> >
> > - [ ] Touch
> > - [ ] Horizontal swipe
> > - [ ] Vertical scrolling
> > - [ ] Rotary
> > - [ ] Back / system gesture
> >
> > 且输入之间无明显冲突。
> >
> > ---
> >
> > # 73. 验收标准：功能
> >
> > - [ ] Root 状态正常
> > - [ ] Superuser 正常
> > - [ ] Modules 正常
> > - [ ] Settings 正常
> > - [ ] Logs 正常
> > - [ ] About 正常
> > - [ ] ksud 通信正常
> > - [ ] 原有业务逻辑没有被无关重写
> >
> > ---
> >
> > # 74. 可选优化建议
> >
> > 以下优化可以加入，但不能破坏前述核心结构。
> >
> > ## 74.1 首页核心状态摘要
> >
> > 把最重要的状态集中到顶部：
> >
> > ```text
> > ReSukiSU
> > Root Active
> > 
> > ksud
> > Running
> > 
> > Modules
> > 6 enabled
> > ```
> >
> > ---
> >
> > ## 74.2 最近事件
> >
> > 首页可以显示 1～3 条最近状态：
> >
> > ```text
> > Recent
> > 
> > Module enabled
> > 2 min ago
> > ```
> >
> > 不要变成完整日志页。
> >
> > ---
> >
> > ## 74.3 状态变化反馈
> >
> > Root、Module、ksud 状态改变时，可以提供短促的视觉反馈。
> >
> > 动画应该简短，不要阻塞用户操作。
> >
> > ---
> >
> > ## 74.4 空间利用
> >
> > 如果设备尺寸足够，可以合理增加：
> >
> > - Secondary information
> > - Quick actions
> > - Position indicator
> >
> > 但不能因为“大屏设备有更多空间”就把所有底层技术信息都塞入首页。
> >
> > ---
> >
> > # 75. Git 要求
> >
> > Wear UI 所有修改必须在：
> >
> > ```text
> > wear_manager
> > ```
> >
> > 分支完成。
> >
> > 不要直接修改 `main`。
> >
> > 提交建议使用 Conventional Commits：
> >
> > ```text
> > feat(wear): redesign manager with Wear Material 3
> > refactor(wear): rebuild pager and transforming lists
> > ```
> >
> > ---
> >
> > # 76. 推荐实施顺序
> >
> > 建议逐阶段完成，避免一次性大规模重写后难以排错：
> >
> > ```text
> > 1. 检查当前 Wear Material 3 依赖
> >        ↓
> > 2. 建立 Wear Theme
> >        ↓
> > 3. 建立统一 Pager / 四 Tab 状态
> >        ↓
> > 4. 实现横向滑动
> >        ↓
> > 5. 实现圆屏 / 直屏 Responsive Layout
> >        ↓
> > 6. 实现 Transforming / Scaling List
> >        ↓
> > 7. 重构 Home
> >        ↓
> > 8. 重构 Superuser
> >        ↓
> > 9. 重构 Modules
> >        ↓
> > 10. 重构 Settings
> >        ↓
> > 11. Detail 页面
> >        ↓
> > 12. Rotary
> >        ↓
> > 13. Accessibility
> >        ↓
> > 14. Loading / Empty / Error
> >        ↓
> > 15. Pixel Watch 2 真机测试
> > ```
> >
> > 每个阶段结束后都应尽可能保持项目可编译。
> >
> > ---
> >
> > # 77. Codex 执行要求
> >
> > 不要把这次任务理解成：
> >
> > > “给现有 Manager 增加一个 Wear UI。”
> >
> > 应该理解成：
> >
> > > **“为 ReSukiSU 重新实现一套真正原生的 Wear OS Material 3 Manager UI，同时完整保留现有业务能力、四个一级 Tab、左右滑动页面切换，以及 Wear OS 的 Transforming / Scaling 列表交互。”**
> >
> > 必须同时做到：
> >
> > ```text
> > Wear Material 3
> > +
> > Transforming / Scaling List
> > +
> > Horizontal Pager
> > +
> > 四个固定一级 Tab
> > +
> > Touch / Rotary / Back
> > +
> > 圆屏 / 直屏 Responsive Layout
> > +
> > 紧凑 Icon + Text 信息设计
> > +
> > 原手机图标资源复用
> > +
> > Dynamic Color
> > +
> > Accessibility
> > +
> > 原有业务逻辑复用
> > ```
> >
> > ---
> >
> > # 78. 最终设计理念
> >
> > 最终 Wear Manager 应该达到：
> >
> > ```text
> >                     ReSukiSU
> >                         │
> >                 Wear Material 3
> >                         │
> >           ┌─────────────┴─────────────┐
> >           │                           │
> >  Transforming / Scaling         Horizontal Pager
> >           │                           │
> >           │                  ┌────────┴────────┐
> >           │                  │                 │
> >        Rotary             四个一级页面        Tab 点击
> >           │                  │                 │
> >           └──────────────────┼─────────────────┘
> >                              │
> >                     Responsive Layout
> >                              │
> >                   ┌──────────┴──────────┐
> >                   │                     │
> >                  圆屏                   直屏
> >                   │                     │
> >                   └──────────┬──────────┘
> >                              │
> >                      Touch / Back
> > ```
> >
> > 最终用户体验：
> >
> > ```text
> > 打开应用
> >    ↓
> > 看到 Root / ksud / Device 核心状态
> >    ↓
> > 左右滑动切换：首页 / 超级用户 / 模组 / 设定
> >    ↓
> > 底部 Tab 与 Pager 自动同步
> >    ↓
> > 上下滑动浏览列表
> >    ↓
> > 列表按 Wear Transforming / Scaling 规则自然变化
> >    ↓
> > Rotary 浏览长列表
> >    ↓
> > 点击完整信息单元进入详情
> >    ↓
> > Back 返回上一层
> > ```
> >
> > 最终目标：
> >
> > > **看起来像 Wear OS。**
> > >
> > > **操作起来像 Wear OS。**
> > >
> > > **列表滚动表现也像 Wear OS。**
> > >
> > > **同时保留 ReSukiSU 原有的功能与品牌识别。**
> >
> > ---
> >
> > # 79. 关键代码结构与 API 约束
> >
> > 本章节是 **Codex / Agent 的实现约束**，优先级高于一般代码风格建议。
> >
> > 目标不是规定每一行代码必须怎样写，而是规定：
> >
> > - UI 分层必须怎样组织
> > - 哪些 AndroidX API 可以使用
> > - 哪些 API 明确禁止使用
> > - Pager、Transforming List、Rotary、Responsive Layout 应如何组合
> > - UI 与业务逻辑之间必须保持怎样的边界
> >
> > ---
> >
> > ## 79.1 推荐 Wear 模块结构
> >
> > 如果当前项目已经存在独立 Wear module，应尽量保持独立 UI 层结构，例如：
> >
> > ```text
> > wear/
> > └── src/main/java/<package>/
> >     ├── MainActivity.kt
> >     ├── WearApp.kt
> >     │
> >     ├── ui/
> >     │   ├── theme/
> >     │   │   ├── Theme.kt
> >     │   │   ├── Color.kt
> >     │   │   └── Type.kt
> >     │   │
> >     │   ├── navigation/
> >     │   │   ├── WearPager.kt
> >     │   │   ├── WearDestination.kt
> >     │   │   └── WearNavigationState.kt
> >     │   │
> >     │   ├── components/
> >     │   │   ├── WearListItem.kt
> >     │   │   ├── WearStatusItem.kt
> >     │   │   ├── WearSection.kt
> >     │   │   └── WearResponsiveContainer.kt
> >     │   │
> >     │   └── screen/
> >     │       ├── HomeScreen.kt
> >     │       ├── SuperuserScreen.kt
> >     │       ├── ModulesScreen.kt
> >     │       ├── SettingsScreen.kt
> >     │       ├── ModuleDetailScreen.kt
> >     │       └── SuperuserDetailScreen.kt
> >     │
> >     └── viewmodel/
> >         ├── HomeViewModel.kt
> >         ├── SuperuserViewModel.kt
> >         ├── ModulesViewModel.kt
> >         └── SettingsViewModel.kt
> > ```
> >
> > 实际目录可以根据现有项目调整，但必须保持：
> >
> > ```text
> > Screen
> >   ↓
> > ViewModel
> >   ↓
> > Repository
> >   ↓
> > ksud / System
> > ```
> >
> > UI 不得反向依赖具体底层实现。
> >
> > ---
> >
> > # 80. App 入口结构
> >
> > 推荐保持单一 Wear Activity 作为 Compose Host：
> >
> > ```kotlin
> > class MainActivity : ComponentActivity() {
> >     override fun onCreate(savedInstanceState: Bundle?) {
> >         super.onCreate(savedInstanceState)
> >         setContent {
> >             ReSukiSUWearTheme {
> >                 WearApp()
> >             }
> >         }
> >     }
> > }
> > ```
> >
> > Activity 的职责仅包括：
> >
> > - Window / Activity 生命周期
> > - Compose Host
> > - 必要的 Wear OS 系统行为接入
> >
> > 不要把：
> >
> > - ksud IPC
> > - Root 操作
> > - Module 安装
> > - Repository 查询
> > - UI 状态机
> >
> > 全部塞进 Activity。
> >
> > ---
> >
> > # 81. Wear Theme 结构
> >
> > Theme 必须成为整个 Wear UI 的唯一颜色、Typography 和基础 Material 语义入口。
> >
> > 推荐结构：
> >
> > ```kotlin
> > @Composable
> > fun ReSukiSUWearTheme(
> >     content: @Composable () -> Unit,
> > ) {
> >     MaterialTheme(
> >         // 使用当前 Wear Material 3 版本支持的 colorScheme / typography 配置
> >     ) {
> >         content()
> >     }
> > }
> > ```
> >
> > 实际参数必须以当前 `androidx.wear.compose.material3` 版本为准。
> >
> > 禁止在每个 Screen 中重新创建一套独立 Theme。
> >
> > 禁止每个 Card 自行定义一组完全独立的颜色。
> >
> > ---
> >
> > # 82. Material 3 API 边界
> >
> > ## 82.1 允许的主要命名空间
> >
> > Wear UI 的核心组件优先来自：
> >
> > ```kotlin
> > androidx.wear.compose.material3.*
> > ```
> >
> > Wear-specific Pager / Foundation 能力优先来自当前项目版本对应的 Wear Compose Foundation API，例如：
> >
> > ```kotlin
> > androidx.wear.compose.foundation.*
> > androidx.wear.compose.foundation.pager.*
> > ```
> >
> > 具体 import 必须通过当前工程 IDE / Gradle 实际解析结果确认。
> >
> > ---
> >
> > ## 82.2 明确禁止 Material 2
> >
> > 禁止：
> >
> > ```kotlin
> > import androidx.wear.compose.material.*
> > ```
> >
> > 尤其不要因为某个旧教程中的 API 更熟悉就退回 Material 2。
> >
> > ---
> >
> > ## 82.3 手机 Material 3 不是 Wear 组件来源
> >
> > 禁止将以下包作为 Wear UI 的主要组件来源：
> >
> > ```kotlin
> > import androidx.compose.material3.*
> > ```
> >
> > 手机 Material 3 可以在确有需要且与 Wear UI 不冲突时作为基础 Compose API 的补充，但：
> >
> > > Wear 页面中的 Button / Card / Navigation / List / Scaffold 等 Wear-specific UI 不得使用手机 Material 3 替代 Wear Material 3。
> >
> > ---
> >
> > # 83. TransformingLazyColumn API 约束
> >
> > 这是本项目最重要的 API 约束之一。
> >
> > 对于需要滚动的 Wear 列表，优先检查当前 Wear Material 3 依赖是否提供：
> >
> > ```kotlin
> > TransformingLazyColumn
> > ```
> >
> > 如果提供，则优先使用它实现：
> >
> > - Modules
> > - Superuser
> > - Settings
> > - Device information
> > - 其他适合 Wear transforming list pattern 的列表
> >
> > 示意结构：
> >
> > ```kotlin
> > @Composable
> > fun ModulesScreen(
> >     items: List<ModuleUiModel>,
> >     onItemClick: (ModuleUiModel) -> Unit,
> > ) {
> >     TransformingLazyColumn {
> >         // header / item / transformed content
> >     }
> > }
> > ```
> >
> > ### 重要
> >
> > 这里的示例只表达架构，不要求照抄旧版本 API 签名。
> >
> > `TransformingLazyColumn`、Item Scope、Transformation Modifier、Scaling/Transform 参数都必须以**当前项目实际 Material 3 版本**提供的 API 为准。
> >
> > 如果当前版本的 `TransformingLazyColumn` API 与示例不同：
> >
> > > 以当前工程编译器和 AndroidX API 为准，不得为了匹配本文件而强行使用不存在的旧 API。
> >
> > ---
> >
> > # 84. 不得用普通 LazyColumn 替代 Transforming List
> >
> > 以下实现不符合本项目默认要求：
> >
> > ```kotlin
> > LazyColumn {
> >     items(items) {
> >         Card { ... }
> >     }
> > }
> > ```
> >
> > 如果这个页面本身属于 Wear transforming list 场景，则应改为当前 Wear Material 3 提供的 transforming list API。
> >
> > 普通 `LazyColumn` 仅在以下情况使用：
> >
> > - 页面内容本身不需要 Wear transforming behavior
> > - 当前 Material 3 API 对特殊内容没有合适的 transforming 实现
> > - 日志等特殊高密度内容经评估不适合将每一行进行 Card-style transformation
> >
> > 即使使用普通列表，也必须保持 Wear OS 的：
> >
> > - Rotary
> > - scrolling
> > - content padding
> > - position indication
> > - accessibility
> >
> > ---
> >
> > # 85. Transforming List 的代码组织
> >
> > 建议将列表 Item 单独抽取：
> >
> > ```kotlin
> > @Composable
> > fun WearModuleItem(
> >     module: ModuleUiModel,
> >     onClick: () -> Unit,
> > ) {
> >     // Wear Material 3 item / card / transformation content
> > }
> > ```
> >
> > 然后由 Screen 负责：
> >
> > ```text
> > Screen
> >  ↓
> > TransformingLazyColumn
> >  ↓
> > WearModuleItem
> >  ↓
> > ViewModel action
> > ```
> >
> > 不要让每个 Item 自己查询 Repository。
> >
> > ---
> >
> > # 86. Transforming 与视觉层级
> >
> > 不要自己计算一个固定：
> >
> > ```kotlin
> > val scale = ...
> > ```
> >
> > 然后把它硬编码给所有列表项目。
> >
> > 应该优先让官方 Wear transformation 机制根据列表位置处理视觉变化。
> >
> > 如果当前 API 要求提供 transformation / transformed height / transformed width 等 Modifier，则严格按照当前版本 API 实现。
> >
> > 原则：
> >
> > ```text
> > List position
> >     ↓
> > Wear transformation
> >     ↓
> > Scale / Transform
> >     ↓
> > Visual focus
> > ```
> >
> > 而不是：
> >
> > ```text
> > Scroll offset
> >     ↓
> > 自己写一套 scale 数学公式
> >     ↓
> > graphicsLayer
> > ```
> >
> > ---
> >
> > # 87. Horizontal Pager API 约束
> >
> > 一级页面必须由一个统一的 Horizontal Pager 状态驱动。
> >
> > 优先检查当前 Wear Compose Foundation 版本提供的 Pager API，例如：
> >
> > ```kotlin
> > androidx.wear.compose.foundation.pager.HorizontalPager
> > androidx.wear.compose.foundation.pager.PagerState
> > androidx.wear.compose.foundation.pager.rememberPagerState
> > ```
> >
> > 具体包名、构造参数与状态 API 必须以当前项目依赖为准。
> >
> > ---
> >
> > # 88. 四 Tab 页面模型
> >
> > 一级页面应该在代码中拥有稳定的枚举 / sealed model，而不是到处使用魔法数字。
> >
> > 推荐：
> >
> > ```kotlin
> > enum class WearTopLevelPage {
> >     HOME,
> >     SUPERUSER,
> >     MODULES,
> >     SETTINGS,
> > }
> > ```
> >
> > 或者使用当前项目更适合的 sealed interface。
> >
> > 可以提供：
> >
> > ```kotlin
> > val WearTopLevelPage.title: String
> > val WearTopLevelPage.icon: ImageVector
> > ```
> >
> > 但图标如果来自项目 drawable/vector resource，应优先保留 Resource ID，而不是为了统一 API 强行转换成 ImageVector。
> >
> > ---
> >
> > # 89. Pager State 必须是唯一真源
> >
> > 推荐结构：
> >
> > ```kotlin
> > @Composable
> > fun WearTopLevelPager() {
> >     val pagerState = rememberPagerState(
> >         initialPage = WearTopLevelPage.HOME.ordinal,
> >         pageCount = { WearTopLevelPage.entries.size },
> >     )
> > 
> >     HorizontalPager(
> >         state = pagerState,
> >     ) { page ->
> >         when (WearTopLevelPage.entries[page]) {
> >             WearTopLevelPage.HOME -> HomeScreen()
> >             WearTopLevelPage.SUPERUSER -> SuperuserScreen()
> >             WearTopLevelPage.MODULES -> ModulesScreen()
> >             WearTopLevelPage.SETTINGS -> SettingsScreen()
> >         }
> >     }
> > }
> > ```
> >
> > 上面仅为结构示例；`rememberPagerState` 和 `HorizontalPager` 的实际参数必须以当前 Wear Foundation API 为准。
> >
> > 底部 Tab 的 selected state 必须由：
> >
> > ```text
> > pagerState.currentPage
> > ```
> >
> > 派生。
> >
> > 不要长期保存第二份独立的：
> >
> > ```text
> > selectedTab
> > ```
> >
> > 作为另一个状态源。
> >
> > ---
> >
> > # 90. Tab 点击切换
> >
> > 底部四 Tab 点击后，应驱动同一个 Pager：
> >
> > ```kotlin
> > scope.launch {
> >     pagerState.animateScrollToPage(targetPage)
> > }
> > ```
> >
> > 实际实现可以根据 API 和交互需求决定使用平滑动画还是直接切换。
> >
> > 但必须保证：
> >
> > ```text
> > Tab click
> >  ↓
> > Pager
> >  ↓
> > currentPage
> >  ↓
> > Tab selection
> > ```
> >
> > 形成闭环。
> >
> > ---
> >
> > # 91. 横向滑动必须保留
> >
> > 禁止以下“简化”方案：
> >
> > ```text
> > 四个 Tab
> > ↓
> > 点击切换
> > ↓
> > 没有 Horizontal Pager
> > ```
> >
> > 也禁止：
> >
> > ```text
> > NavigationHost
> > ↓
> > 每个 Tab 只能点击
> > ↓
> > 删除左右滑动
> > ```
> >
> > 本项目明确要求：
> >
> > > **左右滑动必须是一级页面切换的一等公民。**
> >
> > ---
> >
> > # 92. 二级 Detail Navigation
> >
> > 一级页面使用：
> >
> > ```text
> > Horizontal Pager
> > ```
> >
> > 进入二级详情后，可以使用传统 Compose Navigation / 层级状态导航，或者当前项目已经存在的合适导航方案。
> >
> > 结构：
> >
> > ```text
> > Top Level Pager
> >    │
> >    ├── Home
> >    ├── Superuser
> >    ├── Modules
> >    └── Settings
> >           │
> >           └── Detail / Sub-page
> > ```
> >
> > 不要在每一个一级页面内部重新创建一个独立的顶级 Pager。
> >
> > ---
> >
> > # 93. Rotary API 约束
> >
> > Rotary 必须服务于：
> >
> > > 当前页面的纵向滚动。
> >
> > 优先使用当前 Wear Compose Foundation / Wear UI 提供的 Rotary scrolling API，例如：
> >
> > ```kotlin
> > Modifier.rotaryScrollable(...)
> > ```
> >
> > 或项目当前版本对应的官方 Rotary API。
> >
> > 不要自行监听原始旋转事件后手动修改列表 offset，除非官方 API 无法满足当前需求。
> >
> > ---
> >
> > # 94. Rotary 不得切换一级页面
> >
> > 禁止：
> >
> > ```text
> > 旋转表冠
> >     ↓
> > Home → Superuser → Modules
> > ```
> >
> > 正确：
> >
> > ```text
> > 旋转表冠
> >     ↓
> > 当前页面 Transforming List
> >     ↓
> > 纵向滚动
> > ```
> >
> > 横向 Pager 只由横向手势 / Tab 点击控制。
> >
> > ---
> >
> > # 95. FocusRequester 与 Rotary
> >
> > 如果当前 Rotary API / Wear UI 版本要求 FocusRequester：
> >
> > 必须确保 FocusRequester 在真正使用前完成初始化，并附着到正确的 Compose 节点。
> >
> > 禁止再次出现：
> >
> > ```text
> > FocusRequester is not initialized
> > ```
> >
> > 这意味着：
> >
> > ```text
> > remember
> >  ↓
> > FocusRequester
> >  ↓
> > Modifier.focusRequester(...)
> >  ↓
> > 实际使用 rotary / focus
> > ```
> >
> > 的生命周期必须正确。
> >
> > 不要在 Composable 尚未建立节点前直接调用：
> >
> > ```kotlin
> > requestFocus()
> > ```
> >
> > 除非已经通过正确的 coroutine / LaunchedEffect 生命周期保证节点已建立。
> >
> > ---
> >
> > # 96. Responsive Layout API 约束
> >
> > 不要把圆屏适配写成大量设备型号判断：
> >
> > ```kotlin
> > if (device == "Pixel Watch 2") { ... }
> > ```
> >
> > 必须基于窗口与屏幕形态。
> >
> > 优先考虑：
> >
> > - `WindowSizeClass`（如果当前 Wear 项目已正确接入并适合）
> > - 当前 Compose / Wear API 提供的 Window Metrics
> > - `LocalConfiguration`
> > - Screen Shape / roundness 信息
> > - Density
> > - Font Scale
> >
> > 具体 API 按当前项目依赖与 Wear 文档实现。
> >
> > ---
> >
> > # 97. Screen Shape 与布局参数分离
> >
> > 建议定义：
> >
> > ```kotlin
> > data class WearLayoutSpec(
> >     val horizontalPadding: Dp,
> >     val contentSpacing: Dp,
> >     val itemSpacing: Dp,
> >     val compact: Boolean,
> > )
> > ```
> >
> > 然后：
> >
> > ```text
> > Window Metrics
> >       ↓
> > WearLayoutSpec
> >       ↓
> > Screen / List / Item
> > ```
> >
> > 这样可以避免每个页面各自判断圆屏/直屏。
> >
> > ---
> >
> > # 98. 响应式布局不得复制整套 Screen
> >
> > 不要：
> >
> > ```text
> > RoundHomeScreen.kt
> > RectHomeScreen.kt
> > RoundModulesScreen.kt
> > RectModulesScreen.kt
> > ...
> > ```
> >
> > 除非存在极端必要的结构差异。
> >
> > 优先：
> >
> > ```text
> > HomeScreen
> >     ↓
> > WearLayoutSpec
> >     ↓
> > responsive layout
> > ```
> >
> > 这样圆屏和直屏共用业务与大部分 UI 结构。
> >
> > ---
> >
> > # 99. Icon 资源 API 约束
> >
> > 原手机资源图标优先使用 Android Resource：
> >
> > ```text
> > R.drawable.*
> > R.mipmap.*
> > ```
> >
> > 如果已有 XML Vector Drawable，应优先复用。
> >
> > 如果使用 Material/Wear 内置图标，则采用当前依赖实际提供的 `ImageVector`。
> >
> > 不要因为一个图标无法直接传入某个 API，就把整个项目的图标资源全部重绘。
> >
> > ---
> >
> > # 100. Icon + Text 组件建议
> >
> > 建议创建一个统一的小型语义组件，例如：
> >
> > ```kotlin
> > @Composable
> > fun WearIconText(
> >     icon: Painter,
> >     text: String,
> >     modifier: Modifier = Modifier,
> > ) {
> >     Row(
> >         modifier = modifier,
> >         verticalAlignment = Alignment.CenterVertically,
> >     ) {
> >         Icon(
> >             painter = icon,
> >             contentDescription = null,
> >         )
> >         Spacer(...)
> >         Text(
> >             text = text,
> >             maxLines = 1,
> >         )
> >     }
> > }
> > ```
> >
> > 但不得把所有 UI 都强制做成这一种组件。
> >
> > 对于：
> >
> > ```text
> > Device Name
> > Android Version
> > ```
> >
> > 可以使用：
> >
> > ```text
> > Large Primary Text
> > Small Secondary Text
> > ```
> >
> > 而不是把所有内容挤成一条纯文本。
> >
> > ---
> >
> > # 101. 单行文本 API 约束
> >
> > 短标题、状态、Tab、Button 文案优先：
> >
> > ```kotlin
> > Text(
> >     text = label,
> >     maxLines = 1,
> >     overflow = TextOverflow.Ellipsis,
> > )
> > ```
> >
> > 但是不要对长文本详情页面机械使用 `maxLines = 1`。
> >
> > 原则：
> >
> > ```text
> > 导航 / 状态 / 标签
> > → 单行优先
> > 
> > Description / Log / Detail
> > → 根据内容允许多行
> > ```
> >
> > ---
> >
> > # 102. Visual Scale 与 Touch Target 分离
> >
> > 不得根据 Scale 后的视觉尺寸直接计算点击区域。
> >
> > 错误：
> >
> > ```text
> > 视觉缩小
> >  ↓
> > 点击区域一起缩小
> > ```
> >
> > 正确：
> >
> > ```text
> > Transformation
> >  ↓
> > Visual presentation
> > 
> > Touch Target
> >  ↓
> > 仍保持可操作尺寸
> > ```
> >
> > 如果官方 Transforming List 已处理这部分，优先使用官方实现，不要额外修改其 pointer input 行为。
> >
> > ---
> >
> > # 103. Scroll Indicator / Position Indicator
> >
> > 对于长列表，应根据当前 Wear Material 3 API 使用适合的 position indicator / scroll indicator。
> >
> > 不要自己绘制一套长期固定的滚动条来模拟手机 UI。
> >
> > 目标是让用户知道：
> >
> > ```text
> > 当前位置
> > +
> > 还有多少内容
> > ```
> >
> > 但不要占用大量中心屏幕空间。
> >
> > ---
> >
> > # 104. ScreenScaffold / AppScaffold 等 Scaffold API
> >
> > 如果当前 Wear Material 3 版本提供对应的：
> >
> > ```text
> > ScreenScaffold
> > AppScaffold
> > ```
> >
> > 等 Wear-specific Scaffold，应优先评估使用，而不是直接使用手机：
> >
> > ```kotlin
> > androidx.compose.material3.Scaffold
> > ```
> >
> > 具体 API 名称和参数必须以当前项目实际 AndroidX 版本为准。
> >
> > 不要为了旧教程的 API 名称强行更换依赖版本。
> >
> > ---
> >
> > # 105. UI 状态 API 约束
> >
> > Screen 不应自行维护业务状态副本。
> >
> > 推荐：
> >
> > ```kotlin
> > data class ModulesUiState(
> >     val isLoading: Boolean,
> >     val modules: List<ModuleUiModel>,
> >     val error: String?,
> > )
> > ```
> >
> > ViewModel：
> >
> > ```kotlin
> > val uiState: StateFlow<ModulesUiState>
> > ```
> >
> > Compose：
> >
> > ```kotlin
> > val uiState by viewModel.uiState.collectAsStateWithLifecycle()
> > ```
> >
> > 具体生命周期收集 API 按当前项目依赖使用。
> >
> > ---
> >
> > # 106. Compose State 约束
> >
> > 不要在 Composable 中创建大量业务状态：
> >
> > ```kotlin
> > var modules by remember { mutableStateOf(...) }
> > ```
> >
> > 如果状态来自 Repository / ksud，则应由 ViewModel 持有。
> >
> > Composable 中允许保存纯 UI 状态，例如：
> >
> > - 临时展开状态
> > - 当前 Pager 状态
> > - Dialog 是否打开
> > - 一次性的动画状态
> >
> > 业务数据必须由 ViewModel / StateFlow 管理。
> >
> > ---
> >
> > # 107. Coroutine 约束
> >
> > 禁止在 Composable 内直接做：
> >
> > ```kotlin
> > runBlocking
> > ```
> >
> > 以及阻塞 IO：
> >
> > ```text
> > 文件读取
> > Shell
> > IPC
> > 网络请求
> > 数据库操作
> > ```
> >
> > 使用：
> >
> > ```text
> > ViewModel
> >  ↓
> > viewModelScope
> >  ↓
> > Repository
> >  ↓
> > IO / IPC
> > ```
> >
> > UI 通过 StateFlow 接收结果。
> >
> > ---
> >
> > # 108. Preview / UI 验证
> >
> > 关键组件应尽可能提供 Compose Preview，尤其是：
> >
> > - WearIconText
> > - StatusItem
> > - ModuleItem
> > - SuperuserItem
> > - SettingsItem
> > - Home status card
> >
> > 同时至少验证：
> >
> > ```text
> > Round screen
> > Rect screen
> > Large font
> > Dark theme
> > Long text
> > Empty state
> > Error state
> > ```
> >
> > 如果 Preview 不支持完整 Wear 硬件行为，仍必须通过真机验证 Rotary / Pager / Touch。
> >
> > ---
> >
> > # 109. API 版本检查流程
> >
> > 在修改 Wear UI 前，Codex 必须先检查：
> >
> > ```text
> > 1. settings.gradle.kts
> > 2. wear/build.gradle(.kts)
> > 3. libs.versions.toml（如果存在）
> > 4. 当前 androidx.wear.compose.material3 版本
> > 5. 当前 Wear Compose Foundation 版本
> > 6. 当前 Compose / Kotlin / AGP 版本
> > ```
> >
> > 然后再决定实际 API。
> >
> > 禁止：
> >
> > > 从网上旧文章直接复制 API → 编译报错 → 再通过降级依赖解决。
> >
> > 正确顺序是：
> >
> > ```text
> > 当前项目版本
> >       ↓
> > 当前 API
> >       ↓
> > 实现
> >       ↓
> > 编译验证
> > ```
> >
> > ---
> >
> > # 110. API 禁止清单
> >
> > 以下内容除非当前项目已经存在且具有明确兼容原因，否则禁止作为新的 Wear UI 核心实现：
> >
> > ```text
> > androidx.wear.compose.material.*       ← Material 2
> > androidx.compose.material3.*           ← 手机 Material 3 作为主要 Wear UI
> > 普通手机 NavigationBar                   ← 代替 Wear 四 Tab
> > 普通 LazyColumn                          ← 代替可用的 Wear Transforming List
> > 自制完整 Scale 系统                     ← 代替官方 transformation
> > 原始 Rotary 事件手工修改列表 offset        ← 代替官方 rotaryScrollable
> > 设备型号 if/else                         ← 代替响应式布局
> > 大量绝对 dp                             ← 代替 Window-aware layout
> > ```
> >
> > 这不是说这些 API 在整个项目中永远不可出现，而是：
> >
> > > **不得用它们破坏本项目的 Wear Material 3 核心架构。**
> >
> > ---
> >
> > # 111. 推荐核心代码关系
> >
> > 最终代码关系建议接近：
> >
> > ```text
> > MainActivity
> >     │
> >     ↓
> > ReSukiSUWearTheme
> >     │
> >     ↓
> > WearApp
> >     │
> >     ├── WearPager / TopLevelPager
> >     │       │
> >     │       ├── HomeScreen
> >     │       │      └── Transforming / Wear layout
> >     │       │
> >     │       ├── SuperuserScreen
> >     │       │      └── Transforming List
> >     │       │
> >     │       ├── ModulesScreen
> >     │       │      └── Transforming List
> >     │       │
> >     │       └── SettingsScreen
> >     │              └── Transforming List
> >     │
> >     └── Detail navigation
> >             ├── ModuleDetail
> >             └── SuperuserDetail
> > 
> > Screen
> >   ↓
> > ViewModel
> >   ↓
> > StateFlow
> >   ↓
> > Repository
> >   ↓
> > ksud / System
> > ```
> >
> > ---
> >
> > # 112. 关键 API 组合关系
> >
> > 整个 Wear UI 的核心 API 关系应该是：
> >
> > ```text
> >                 Wear Material 3
> >                        │
> >         ┌──────────────┼───────────────┐
> >         │              │               │
> >  Transforming List   Theme       Wear UI Components
> >         │              │               │
> >         │         ColorScheme       Card/Button
> >         │         Typography        Icon/Text
> >         │
> >         └──────────────┐
> >                        │
> >                 Horizontal Pager
> >                        │
> >              ┌─────────┼─────────┐
> >              │         │         │
> >             Home   Superuser  Modules
> >                        │         │
> >                     Settings    │
> >              │                   │
> >              └───────┬───────────┘
> >                      │
> >               Touch / Rotary
> >                      │
> >                    Back
> > ```
> >
> > 这部分是本项目的核心架构约束。
> >
> > ---
> >
> > # 113. 最终实现判断规则
> >
> > 遇到任何 UI 设计选择时，按照下面的决策顺序：
> >
> > ```text
> > 这个 API 是不是 Wear Material 3？
> >         ↓ 是
> > 是不是当前项目依赖版本实际存在？
> >         ↓ 是
> > 是不是符合 Wear OS 的圆/直屏交互？
> >         ↓ 是
> > 是不是保留四 Tab + 横向 Pager？
> >         ↓ 是
> > 是不是保留 Transforming / Scaling list？
> >         ↓ 是
> > 是不是支持 Rotary / Back / Accessibility？
> >         ↓ 是
> > 是不是复用已有业务层与原图标？
> >         ↓ 是
> > 采用
> > ```
> >
> > 任意一步为否，都应该重新评估实现，而不是直接把手机端方案搬过来。
> >
> > ---
> >
> > # 114. Codex 最终 API 原则
> >
> > > **API 以当前项目实际依赖为准，不以旧教程为准。**
> >
> > > **Wear Material 3 以 `androidx.wear.compose.material3` 为核心 UI 包。**
> >
> > > **TransformingLazyColumn / 官方 Transforming List 是 Wear 列表的优先方案。**
> >
> > > **Horizontal Pager 是四个一级页面左右滑动切换的核心。**
> >
> > > **底部四 Tab 与 Pager 必须共享状态。**
> >
> > > **Rotary 负责当前页面的纵向滚动，不负责一级页面切换。**
> >
> > > **Scale 是 Wear 列表 transformation 的一部分，不是简单 `Modifier.scale()` 动画。**
> >
> > > **圆屏和直屏必须共享 UI 结构，通过响应式参数适配。**
> >
> > > **禁止使用 Material 2 组件解决 Material 3 UI 问题。**
> >
> > > **不要为了消除编译错误而降低 AndroidX / Compose / AGP 版本；先以当前项目版本实际 API 为准修改实现。**
> >
> > > **最终代码必须以真实 Pixel Watch 2 触摸、旋转表冠、左右滑动和返回行为验证为准。**
> >
> > ---
> >
> > # 115. 新增需求：加载状态与刷新交互
> >
> > ## 115.1 取消独立“刷新”按钮
> >
> > 当前 UI 中如果存在：
> >
> > ```text
> > [刷新]
> > ```
> >
> > 或单独的刷新 IconButton，必须移除。
> >
> > 刷新应该成为列表/页面本身的一部分，而不是占用一个额外的可视操作入口。
> >
> > 禁止为了保留旧功能而在页面顶部、底部或悬浮区域继续放置独立刷新按钮。
> >
> > ---
> >
> > ## 115.2 改为手势触发加载
> >
> > 刷新逻辑修改为：
> >
> > ```text
> > 用户上拉
> >    ↓
> > 达到触发阈值
> >    ↓
> > 进入 Refreshing 状态
> >    ↓
> > 显示加载指示器
> >    ↓
> > 执行现有刷新数据逻辑
> >    ↓
> > 完成 / 失败
> >    ↓
> > 恢复正常页面
> > ```
> >
> > 这里的“上拉”严格按照产品要求实现为**向上拖拽/上滑触发刷新**，不得擅自改成按钮点击。
> >
> > 如果当前页面已经处于列表滚动状态，必须正确处理：
> >
> > ```text
> > 普通上滑
> > → 列表滚动
> > 
> > 达到刷新手势条件
> > → 触发 Refresh
> > ```
> >
> > 不能让任何普通滚动都误触发刷新。
> >
> > ---
> >
> > # 116. Refresh Indicator 的位置 —— 必须居中
> >
> > 加载动画必须显示在当前 Wear UI 的**视觉中心区域**。
> >
> > 不能继续出现当前 UI 中类似：
> >
> > ```text
> > 屏幕顶部 / 偏左 / 列表某一个 Item 的位置
> >         ↓
> >       Spinner
> > ```
> >
> > 而应该接近：
> >
> > ```text
> > ╭────────────────────╮
> > │                    │
> > │                    │
> > │         ◉          │
> > │      Loading       │
> > │                    │
> > │                    │
> > ╰────────────────────╯
> > ```
> >
> > 更准确地说：
> >
> > > **加载指示器应该以当前可用内容区域为基准居中，而不是简单以屏幕像素边界居中。**
> >
> > 如果页面包含固定的系统/页面结构，例如 TimeText、导航区域等，应根据实际 Content Area 计算视觉中心。
> >
> > ---
> >
> > ## 117. Refresh Indicator 不得破坏原布局
> >
> > Refreshing 状态不能简单向列表中插入一个巨大的：
> >
> > ```text
> > item {
> >     CircularProgressIndicator(...)
> > }
> > ```
> >
> > 并把原来的全部内容向下推。
> >
> > 优先考虑覆盖式/独立 loading layer：
> >
> > ```text
> > Page Container
> > ├── Content
> > │   └── TransformingLazyColumn
> > │
> > └── Refresh Indicator Layer
> >     └── Centered Loading Indicator
> > ```
> >
> > 加载动画出现时：
> >
> > - 不应导致整个页面发生明显位移
> > - 不应破坏 Pager 状态
> > - 不应改变底部四 Tab 的位置
> > - 不应破坏当前列表状态
> >
> > ---
> >
> > # 118. Google 官方 Loading / Refresh API —— 不得凭记忆硬编码
> >
> > 用户要求：
> >
> > > 加载转圈动画采用 Google 给出的预设模板；收到示例视觉后，联网检索对应 API 并采用官方实现。
> >
> > 因此 Codex 在真正修改 Loading UI 前，必须：
> >
> > 1. 查看用户提供的加载动画示例图
> > 2. 根据示例判断对应的 Wear OS Material 3 / Wear Foundation interaction pattern
> > 3. 查询 Google 官方 Android Developers 文档
> > 4. 查询当前项目实际 AndroidX 版本对应 API
> > 5. 优先使用官方组件/State/Modifier
> > 6. 不要自行复制一个“看起来类似”的 Spinner
> >
> > 用户提供的 Google API 参考入口：
> >
> > ```text
> > https://developer.android.com/reference/kotlin/androidx/wear/compose/foundation/package-summary.html
> > ```
> >
> > 以及用户之前提供的 Wear Compose API 参考入口：
> >
> > ```text
> > https://developer.android.com/reference/kotlin/androidx/wear/compose/
> > ```
> >
> > ### 重要
> >
> > **在收到最终加载动画示例图并确认 API 前，不要擅自锁定最终 Loading 动画的具体视觉参数。**
> >
> > 可以先采用当前项目已有的 Material 3 Loading API 作为临时实现，但最终视觉与 API 必须经过 Google 官方文档核对。
> >
> > ---
> >
> > # 119. 当前环境的联网验证要求
> >
> > 如果执行 Agent 时能够访问互联网，必须以 Google 官方 Android Developers 文档作为第一来源检查：
> >
> > - Wear Foundation 的刷新/手势 API
> > - Wear Material 3 Loading API
> > - Transforming List API
> > - Rotary API
> > - Pager API
> >
> > 不要使用博客、搜索引擎摘要或第三方教程作为最终 API 依据。
> >
> > 如果当前开发环境无法联网：
> >
> > > **不得声称已经验证了最新 Google API。**
> >
> > 应以项目 Gradle 实际解析到的 AndroidX API 为准，并在代码中尽量减少版本敏感的 API 假设。
> >
> > ---
> >
> > # 120. Loading 状态模型
> >
> > 刷新和普通初始化加载必须在状态层明确区分。
> >
> > 推荐至少区分：
> >
> > ```kotlin
> > sealed interface UiLoadState {
> >     data object Idle : UiLoadState
> >     data object Loading : UiLoadState
> >     data object Refreshing : UiLoadState
> >     data class Error(val message: String) : UiLoadState
> >     data object Success : UiLoadState
> > }
> > ```
> >
> > 如果现有项目已经有对应状态模型，不要为了这个需求重新创建重复模型，应复用现有状态。
> >
> > 重点：
> >
> > ```text
> > Loading
> > ≠
> > Refreshing
> > ```
> >
> > 初次打开页面：
> >
> > ```text
> > Loading
> > ```
> >
> > 用户主动执行上拉刷新：
> >
> > ```text
> > Refreshing
> > ```
> >
> > 二者的 UI 行为可以不同。
> >
> > ---
> >
> > # 121. 刷新必须复用现有业务逻辑
> >
> > 上拉触发的刷新操作必须继续调用已有：
> >
> > ```text
> > ViewModel
> >  ↓
> > Repository
> >  ↓
> > ksud / System / Data Source
> > ```
> >
> > 禁止在 Composable 中直接：
> >
> > ```text
> > 网络请求
> > Shell
> > 阻塞 IO
> > ksud IPC
> > 文件读取
> > ```
> >
> > 推荐：
> >
> > ```kotlin
> > fun refresh() {
> >     viewModelScope.launch {
> >         // 调用现有 repository refresh
> >     }
> > }
> > ```
> >
> > 具体实现必须服从当前项目已有架构。
> >
> > ---
> >
> > # 122. Refresh 防抖与重复触发
> >
> > Refreshing 过程中再次执行上拉不得重复启动任务。
> >
> > 例如：
> >
> > ```text
> > Refreshing = true
> >         ↓
> > 忽略新的 Refresh Trigger
> >         ↓
> > 任务完成
> >         ↓
> > Refreshing = false
> > ```
> >
> > 不得产生：
> >
> > ```text
> > Refresh #1
> > Refresh #2
> > Refresh #3
> > Refresh #4
> > ```
> >
> > 同时运行的情况。
> >
> > ---
> >
> > # 123. Refresh 失败状态
> >
> > 刷新失败时：
> >
> > - 保留已有数据
> > - 不要清空整个页面
> > - 显示紧凑错误反馈
> > - 允许再次上拉刷新
> >
> > 不应出现：
> >
> > ```text
> > Refreshing
> >  ↓
> > Error
> >  ↓
> > 整个页面变成空白
> > ```
> >
> > 推荐：
> >
> > ```text
> > Existing Content
> >       +
> > Error Feedback
> > ```
> >
> > 而不是摧毁现有内容。
> >
> > ---
> >
> > # 124. About 页面重新设计
> >
> > “关于”页面必须完全重做，不再只是一个简单文本页面。
> >
> > 用户提供的预览图作为视觉参考，其主要结构包括：
> >
> > ```text
> > 系统时间
> > 
> > 关于软件
> > 
> >       [ App Icon ]
> > 
> >        App Name
> > 
> >        Version
> > 
> >       GitHub / Link
> > ```
> >
> > 目标是做成：
> >
> > > **一个真正适合 Wear OS 的 App About 页面。**
> >
> > 不是把手机端 About 页面缩小。
> >
> > ---
> >
> > # 125. About 页面核心内容
> >
> > 至少显示：
> >
> > 1. App Icon
> > 2. App Name
> > 3. App Version
> > 4. GitHub / Project URL
> > 5. 开源/项目相关信息
> >
> > 根据现有项目实际信息，可以继续包含：
> >
> > - Build Version
> > - KernelSU / ReSukiSU 版本信息
> > - License
> > - Open Source 信息
> > - Developer / Organization 信息
> >
> > 但低价值信息不应全部堆在首屏。
> >
> > ---
> >
> > # 126. About 页面布局参考
> >
> > 推荐：
> >
> > ```text
> > ┌──────────────────────┐
> > │        9:11          │
> > │                      │
> > │       关于软件       │
> > │                      │
> > │        ◯             │
> > │      App Icon        │
> > │                      │
> > │      App Name        │
> > │       1.0.0          │
> > │                      │
> > │   GitHub / Project   │
> > │                      │
> > └──────────────────────┘
> > ```
> >
> > 实际视觉密度按照 Wear Material 3 调整。
> >
> > 重点：
> >
> > - App Icon 位于明显视觉中心
> > - 名称使用较大 Typography
> > - 版本使用较小 Typography
> > - GitHub 链接作为可操作元素
> > - 信息上下关系自然
> > - 避免巨大空白
> >
> > ---
> >
> > # 127. App Icon
> >
> > About 页面必须显示应用实际 App Icon。
> >
> > 优先使用项目已经存在的：
> >
> > ```text
> > mipmap
> > adaptive icon
> > launcher icon
> > 现有 drawable/vector
> > ```
> >
> > 禁止为了 About 页面重新生成一个完全不同的图标。
> >
> > 如果 Android Launcher 使用 Adaptive Icon：
> >
> > > Wear About 页面可以根据实际显示效果使用合适的 Foreground / Background 组合，但必须保持与应用 Launcher Icon 的品牌一致。
> >
> > ---
> >
> > # 128. App Name 与 Version
> >
> > App Name：
> >
> > - 大字号
> > - 居中
> > - 单行优先
> >
> > Version：
> >
> > - 使用较小字号
> > - 位于名称下方
> > - 不抢主标题视觉权重
> >
> > 例如：
> >
> > ```text
> > ReSukiSU
> > Manager
> > vX.Y.Z
> > ```
> >
> > 具体显示名称和版本号必须从项目 BuildConfig / Manifest / Package metadata 中读取，不应硬编码当前版本字符串。
> >
> > ---
> >
> > # 129. GitHub 链接
> >
> > GitHub 链接必须可操作。
> >
> > 推荐形式：
> >
> > ```text
> > [GitHub Icon] GitHub
> > ```
> >
> > 或者：
> >
> > ```text
> > [GitHub] hanhanawa/ReSukiSU
> > ```
> >
> > 点击后调用 Wear OS 当前可用的标准 Intent / URI 打开方式。
> >
> > 不要在 UI 中显示一整行超长 URL：
> >
> > ```text
> > https://github.com/Hanhanawa/ReSukiSU/xxxxxxxx...
> > ```
> >
> > 优先显示简短、可识别的项目名称。
> >
> > 实际 URI 应从项目配置/常量来源读取。
> >
> > ---
> >
> > # 130. About 页面与参考截图的对应关系
> >
> > 用户提供的 About 预览图应理解为：
> >
> > > **布局与信息层级参考，而非要求逐像素复制。**
> >
> > 需要保留的设计语言：
> >
> > ```text
> > Top Time
> >     ↓
> > Centered Title
> >     ↓
> > App Icon
> >     ↓
> > App Name
> >     ↓
> > Version
> >     ↓
> > Project / GitHub
> > ```
> >
> > 同时需要按照 ReSukiSU 自己的：
> >
> > - Material 3 Theme
> > - Dynamic Color
> > - Typography
> > - Icon
> > - 圆/直屏响应式布局
> >
> > 进行重新实现。
> >
> > 不能直接复制参考应用的品牌元素。
> >
> > ---
> >
> > # 131. About 页面滚动策略
> >
> > 如果 About 页面信息很少：
> >
> > > 尽量保持完整内容在首屏可见。
> >
> > 如果信息明显超过可视区域：
> >
> > > 使用 Wear Material 3 Transforming/Scrolling list pattern。
> >
> > 不要为了强制使用 TransformingLazyColumn 而把一个本来只有数项的信息页面拆得过于零散。
> >
> > 原则：
> >
> > ```text
> > 少量信息
> > → 静态居中布局
> > 
> > 较多信息
> > → Wear transforming/scaling list
> > ```
> >
> > 最终以实际内容量决定。
> >
> > ---
> >
> > # 132. About 页面交互
> >
> > GitHub 等链接属于明确的可点击元素。
> >
> > 可使用：
> >
> > ```text
> > Icon + Text
> > ```
> >
> > 形成完整操作单元。
> >
> > 点击后：
> >
> > ```text
> > About
> >  ↓
> > Open GitHub
> > ```
> >
> > 不需要增加额外的复杂设置层级。
> >
> > ---
> >
> > # 133. Loading / About 的代码分层
> >
> > 推荐结构：
> >
> > ```text
> > wear/
> > ├── ui/
> > │   ├── screen/
> > │   │   ├── HomeScreen.kt
> > │   │   ├── SuperuserScreen.kt
> > │   │   ├── ModulesScreen.kt
> > │   │   ├── SettingsScreen.kt
> > │   │   └── AboutScreen.kt
> > │   │
> > │   ├── components/
> > │   │   ├── WearLoadingIndicator.kt
> > │   │   ├── WearStatusItem.kt
> > │   │   ├── WearAppHeader.kt
> > │   │   └── WearNavigation.kt
> > │   │
> > │   └── navigation/
> > │       └── WearPagerNavigation.kt
> > │
> > ├── viewmodel/
> > │   └── ...
> > │
> > └── data/
> >     └── ...
> > ```
> >
> > 如果项目已经有不同目录结构，不要为了形式统一而进行无关的大规模移动；保持现有工程结构，只需遵守职责边界。
> >
> > ---
> >
> > # 134. WearLoadingIndicator API 约束
> >
> > 建议封装成：
> >
> > ```kotlin
> > @Composable
> > fun WearLoadingIndicator(
> >     modifier: Modifier = Modifier,
> > ) {
> >     // Material 3 / official Wear API
> > }
> > ```
> >
> > 这样最终从 Google 官方文档确认具体 API 后，只需要修改一个组件，而不需要在 Home / Modules / Superuser / Settings 中重复修改。
> >
> > 禁止各页面自行实现不同 Spinner。
> >
> > 所有页面应该共享：
> >
> > ```text
> > WearLoadingIndicator
> > ```
> >
> > 或者项目当前版本对应的统一官方加载组件。
> >
> > ---
> >
> > # 135. Center Loading 的推荐代码结构
> >
> > 最终代码结构可以接近：
> >
> > ```kotlin
> > @Composable
> > fun WearPageContent(
> >     isRefreshing: Boolean,
> >     content: @Composable () -> Unit,
> > ) {
> >     Box(
> >         modifier = Modifier.fillMaxSize()
> >     ) {
> >         content()
> > 
> >         if (isRefreshing) {
> >             WearLoadingIndicator(
> >                 modifier = Modifier.align(Alignment.Center)
> >             )
> >         }
> >     }
> > }
> > ```
> >
> > 这只是**结构约束示例**。
> >
> > 最终 Loading API、手势 API 和具体 Material 3 组件必须根据当前项目实际 AndroidX 版本以及 Google 官方文档确定。
> >
> > 禁止在此阶段把这个示例中的实现细节视为最终 Google API。
> >
> > ---
> >
> > # 136. 新增 API 验收规则
> >
> > Codex 完成修改后必须检查：
> >
> > ```text
> > 是否仍然使用 androidx.wear.compose.material3？
> >         ↓
> > 是否错误引入 androidx.wear.compose.material？
> >         ↓
> > Transforming List 是否使用当前版本官方 API？
> >         ↓
> > Refresh 是否使用当前版本官方 Wear API？
> >         ↓
> > Loading 是否居中？
> >         ↓
> > 是否仍有旧刷新按钮？
> >         ↓
> > 四 Tab 是否仍然存在？
> >         ↓
> > 左右滑动是否仍然存在？
> >         ↓
> > Rotary 是否仍然可用？
> >         ↓
> > About 是否包含 Icon / Name / Version / GitHub？
> >         ↓
> > 圆屏 / 直屏是否都能正常布局？
> > ```
> >
> > ---
> >
> > # 137. 禁止事项补充
> >
> > 禁止：
> >
> > 1. 保留独立刷新按钮作为主要刷新入口
> > 2. 把加载 Spinner 放在屏幕角落
> > 3. 把 Spinner 当成普通列表 Item 导致页面整体位移
> > 4. 未查看当前 API 就随意复制旧 Wear 教程代码
> > 5. 将 Material 2 API 当作 Material 3 API 使用
> > 6. 使用手机 Pull-to-Refresh API 直接替代 Wear 专用交互
> > 7. 用自定义动画完全模拟 Google 官方 Wear Loading pattern
> > 8. About 页面继续使用手机式密集信息堆叠
> > 9. About 页面硬编码版本号
> > 10. About 页面硬编码一个与 Launcher 不一致的 App Icon
> > 11. 为了 About 页面加入新的一级 Tab
> > 12. 删除左右滑动切换页面
> >
> > ---
> >
> > # 138. 最终页面结构更新
> >
> > 最终一级页面仍然严格为：
> >
> > ```text
> > Home
> >   ↔
> > Superuser
> >   ↔
> > Modules
> >   ↔
> > Settings
> > ```
> >
> > 其中：
> >
> > ```text
> > Home
> > ├── Device Information
> > ├── Root Status
> > ├── ksud Status
> > └── Quick Information
> > 
> > Superuser
> > └── Superuser List / Detail
> > 
> > Modules
> > └── Module List / Detail
> > 
> > Settings
> > ├── General
> > ├── Appearance
> > ├── Security
> > ├── Logs
> > └── About
> >         ├── App Icon
> >         ├── App Name
> >         ├── Version
> >         ├── GitHub
> >         └── Open Source Information
> > ```
> >
> > Logs 与 About 均不能成为新的一级 Tab。
> >
> > ---
> >
> > # 139. 最终首页交互示意
> >
> > ```text
> >               Home
> >                 │
> >        ┌────────┴────────┐
> >        │                 │
> >   Device Info         Root Status
> >        │                 │
> >        └────────┬────────┘
> >                 │
> >           Transforming List
> >                 │
> >              ↑   ↓
> >            Rotary
> >                 │
> >         上拉达到 Refresh 阈值
> >                 │
> >                 ↓
> >         ┌────────────────┐
> >         │                │
> >         │       ◉        │
> >         │    Loading     │
> >         │                │
> >         └────────────────┘
> >                 │
> >                 ↓
> >            Refresh Done
> > ```
> >
> > 页面底部始终保持：
> >
> > ```text
> > [首页] [超级用户] [模组] [设定]
> > ```
> >
> > 左右滑动仍然切换四个一级页面。
> >
> > ---
> >
> > # 140. 最新总验收标准
> >
> > ## Loading / Refresh
> >
> > - [ ] 独立刷新按钮已删除
> > - [ ] 上拉手势可以触发刷新
> > - [ ] 普通纵向滚动不会误触发刷新
> > - [ ] Refresh 状态与初次 Loading 状态区分
> > - [ ] 加载动画位于视觉中心
> > - [ ] Loading 不会把整个页面布局向下推
> > - [ ] 不会重复触发 Refresh
> > - [ ] 刷新完成后正确隐藏 Indicator
> > - [ ] 刷新失败保留已有数据
> > - [ ] 最终 Loading/Refresh API 已根据 Google 官方 Wear 文档核验
> >
> > ## About
> >
> > - [ ] 使用实际 App Icon
> > - [ ] 显示 App Name
> > - [ ] 显示 Version
> > - [ ] 显示 GitHub / Project Link
> > - [ ] GitHub 入口可点击
> > - [ ] 版本号来自 BuildConfig / Manifest / 项目 metadata
> > - [ ] 不创建新的一级 Tab
> > - [ ] 布局与用户提供的参考图保持一致的视觉层级
> > - [ ] 同时适配圆屏与直屏
> >
> > ## Wear Material 3
> >
> > - [ ] 使用 `androidx.wear.compose.material3`
> > - [ ] 无 Wear Material 2 组件
> > - [ ] 无手机 Material 3 组件作为主要 Wear UI
> > - [ ] Transforming / Scaling List 使用当前官方 API
> > - [ ] Rotary 与列表滚动正确结合
> > - [ ] Horizontal Pager 与 Transforming List 不发生方向冲突
> >
> > ## Navigation
> >
> > - [ ] Home
> > - [ ] Superuser
> > - [ ] Modules
> > - [ ] Settings
> > - [ ] 四 Tab 顺序正确
> > - [ ] 左右滑动切换正常
> > - [ ] 底部 Tab 点击切换正常
> > - [ ] Pager / Tab 状态同步
> >
> > ---
> >
> > # 141. Codex 最新执行原则
> >
> > 本文件后续涉及 Wear UI 的所有修改，都必须遵守：
> >
> > > **先确定当前项目实际 AndroidX Wear Material 3 版本，再选择 API；不要根据记忆或旧教程编写 API。**
> >
> > > **列表优先采用官方 Wear Material 3 Transforming / Scaling pattern。**
> >
> > > **一级页面固定为 Home / Superuser / Modules / Settings。**
> >
> > > **左右滑动切换页面为硬性需求，不能删除。**
> >
> > > **底部四 Tab 与 Horizontal Pager 必须同步。**
> >
> > > **Rotary 负责当前页面纵向内容。**
> >
> > > **刷新按钮必须取消，刷新改为产品要求的上拉手势。**
> >
> > > **Loading Indicator 必须位于当前页面视觉中心。**
> >
> > > **最终 Loading 动画样式以用户后续提供的示例图为视觉依据，并优先采用 Google 官方 Wear API。**
> >
> > > **About 页面必须具备 App Icon、App Name、Version、GitHub / Project Link 等核心信息，并采用用户提供预览图的信息层级重新设计。**
> >
> > > **圆屏与直屏共享组件和状态逻辑，通过响应式布局参数适配，而不是为设备型号分别硬编码页面。**
> >
> > > **不能为了实现新 UI 而破坏现有 ksud、Repository、ViewModel、权限及其他业务逻辑。**