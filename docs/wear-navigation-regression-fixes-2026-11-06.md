# Wear 菜单与返回动画修复记录

日期：2026-11-06。

## 二级操作菜单

最终约定：滚动到底部后继续上拉返回，保留左右滑动禁用，移除左右滑动触发的系统返回振动入口；不添加独立圆形进度提示，也不添加底部按钮。

- `WearOperationsPanel` 不再传入 `closePanelOnPullDown = true`，恢复现有底部上拉退出路径。菜单初次显示仍位于列表底部，达到 48 dp 阈值后松手关闭，短拉取消、顶部下拉及普通滚动不关闭菜单。
- `WearOperationsHost` 仅在菜单活动时使用 `systemGestureExclusion()`，并在菜单本身消费横向拖动。菜单关闭或被子页面覆盖后移除这些处理；原有返回事件拦截及其他触感保留。
- `WearManager` 不再将 `operationsOnTop` 传入全局导航策略的滑动开关。禁用仅对活动菜单有效，主界面翻页和普通子页面返回不继承菜单的禁用状态；原有用户滑动设置继续生效。
- 已撤销本轮曾新增的底部独立 `WearPullIndicator`。当前操作菜单只有 `onClosePanel`，不渲染 `EdgeButton`；它并非已由按钮提示替代。按最终确认，不补按钮。
- 普通排序/筛选页保持独立导航页面的行为。

## 历史原因

| 现象 | 提交 | 原因 |
| --- | --- | --- |
| API 36 及以上多数页面没有进入动画 | `dd68922a`，2026-11-05 | 为回避快速预测性返回与进入动画重叠，将全局进入过渡设为 `EnterTransition.None togetherWith ExitTransition.None`。 |
| 只有部分页面有进入动画 | `c2891b4d`、`5d39b836`，2026-11-06 | 动态管理器和应用详情相关路由分别添加局部进入过渡，全局默认仍为 `None`。 |
| 操作菜单变成顶部下拉退出 | `eecc4660`，2026-11-05 | 启用 `closePanelOnPullDown`，同时禁用横向退出。本轮恢复底部上拉，保留横向禁用。 |
| 排序/筛选页底部上拉和退出提示消失 | `33be1a21`，2026-11-05 | 移除对应页面的 `onClosePanel = onBack`，保留普通返回。 |
| 纵向拖动阻力及弹簧收尾消失 | `3120b8fc`，2026-10-31 | 位移由 `minOf(pull * 0.6f, threshold * 0.75f)` 改为直接跟手，收尾改为 `tween(180)`。这与系统预测性返回的阻尼不是同一路径；本轮不修改该阻尼。 |

## 公开资料

- [Wear Navigation 3 指南](https://developer.android.com/training/wearables/compose/navigation3) 使用 `NavDisplay` 和 `rememberSwipeDismissableSceneStrategy`。
- 已核对项目所用 Wear Compose Navigation 3 1.7.1 的官方源码：`PredictiveBackScene` 为 API 36 及以上提供进入、普通返回及预测性返回过渡，entry metadata 可覆盖默认值；官方默认并未关闭进入动画。
- 已核对 Compose Foundation 1.12.1 的 `systemGestureExclusion()` 实现；API 29 以下不生效，排除区域由系统执行。
- 未取得能确认 Google Messages Wear 具体动画实现的公开材料，没有获取或解包其 APK。

## 验证

菜单修复在 `d35b5936` 加本轮修改的状态下验证：

- `spotlessCheck`、`assembleRelease`、`lintRelease`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 均通过。
- API 37 模拟器 `emulator-5558` 运行 `WearPanelsTest`、`WearOperationsHostTest`、`WearMenuNavigationTest`，**64 项通过**，耗时 88.245 秒。测试覆盖圆屏/方屏配置、带/不带 pager、上拉阈值、左右手势无应用层振动、排除区域撤销、关闭菜单后主界面双向翻页，以及子页面返回与菜单手势隔离。
- Wear AVD 本轮因磁盘空间不足未能启动，因此上述复测使用已连接的 Android 模拟器。未修改其屏幕或系统配置。真实手表的系统振动仍需设备确认，模拟器验证不代表物理触感验收。
- `git diff --check` 通过。

菜单修复提交：`0471fd17`，`fix(wear): restore upward menu dismissal and scope gesture blocking`。

## 进入动画恢复

- API 36 及以上普通 Wear 页面恢复进入动画，复用应用详情页已使用的平移、缩放及背景淡出过渡，并与返回过渡保持相反端点。进入和返回均交由同一个 `NavDisplay`，不增加第二层动画容器。
- 移除仅应用详情页使用的重复过渡配置。动态管理器保留已有短动画，API 36 以下行为不变；普通返回与预测性返回的配置不变。
- `WearAppTransitionsTest` 扩展并更名为 `WearPageTransitionsTest`，新增模块、仓库、设置和首页详情路径，覆盖正常进入与按钮返回、进入中预测性返回取消、进入第一帧后立即预测性返回完成。
- 在独立的 API 37 Wear 临时模拟器上运行 `WearPageTransitionsTest`，**60 项通过**，耗时 85.324 秒；覆盖 10 个目标页面、圆屏/方屏和 3 类过渡场景。临时实例使用已有 Wear 系统镜像与 D 盘独立数据目录，未更改现有 AVD。
- 测试结束后已关闭临时模拟器。自动审批审查以 `blocked by policy` 拒绝清理 `D:\Temp\bakasu-entry-validation`，该临时目录保留，未改用其他方式删除。
- `spotlessCheck`、`assembleRelease`、`lintRelease`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 全部通过，`git diff --check` 通过。纵向退出阻尼保持现状。
