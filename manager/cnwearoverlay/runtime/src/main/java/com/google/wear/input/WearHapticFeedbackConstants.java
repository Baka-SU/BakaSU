package com.google.wear.input;

/**
 * com.google.wear.input.WearHapticFeedbackConstants 桩类。
 *
 * Google Wear OS 的系统私有库，小米/OPPO 手表上不存在 wear-compose 的
 * WearSDKHapticConstants 初始化时会 NoClassDefFoundError 或静默禁用触觉。
 *
 * 类加载规则（parent-first）：真 Wear OS 上 boot classpath 中的真实类优先，本桩类不生效；
 * 仅在缺失该类的系统（小米/OPPO）上被加载，返回值由运行时按系统触觉库自适应：
 * 小米 19/18/20（SCROLL_*，表冠旋转链路），OPPO 12/12/12（GESTURE_START，滚动波形 302）。
 */
public final class WearHapticFeedbackConstants {

    private WearHapticFeedbackConstants() {}

    public static int getScrollItemFocus() {
        return cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay.getScrollItemFocus();
    }

    public static int getScrollTick() {
        return cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay.getScrollTick();
    }

    public static int getScrollLimit() {
        return cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay.getScrollLimit();
    }
}
