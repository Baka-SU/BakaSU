# Wear Manager 更新日志

## 2026-09-25

- Wear 页面统一使用 `androidx.wear.compose.material3` 组件；检查并确认 Wear 专属代码没有引用手机 Material 3 的 `ListItem` 等组件。
- SU 日志页保留固定位置的开关入口：关闭时显示“开启 SU 日志”，开启后显示“关闭 SU 日志”，复用现有日志配置与刷新流程。
- 首页 Root 状态显示为“运作中 / Working”，并在可识别时显示 LKM 或 GKI 模式；下方显示超级用户和模块数量。
- 应用列表、模块列表、日志文件及普通操作按钮使用同一套 Wear 按钮颜色。
- 移除加载和刷新动画组件；保留页面加载状态与上拉刷新手势。

验证：`assembleDebug`、`lintRelease` 和 `assembleRelease` 通过。按本次要求未运行模拟器测试；SU 日志实际切换需在具有 Root 功能的设备上确认。
