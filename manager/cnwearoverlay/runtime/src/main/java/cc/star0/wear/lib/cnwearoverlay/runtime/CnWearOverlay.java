package cc.star0.wear.lib.cnwearoverlay.runtime;

import android.view.View;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * cnwearoverlay 运行时辅助类（由 Gradle 插件以 jar 依赖注入 app，勿手工引用）。
 *
 * 职责（与 libref 逆向报告中的链路一一对应）：
 *   - 设备识别：探测 boot classpath 中的小米 framework / SDK 类或 OPPO 线性马达类；存在真实的
 *       com.google.wear.input.WearHapticFeedbackConstants（真 Wear OS）时一律不介入。
 * 
 *   - 层 1 {@link #forceWearSdk()} 与
 *       {@link com.google.wear.input.WearHapticFeedbackConstants} 桩类取值
 *       （{@link #getScrollItemFocus()} 等）。
 * 
 *   - 层 2 {@link #rotaryConstants(Class)}：小米 (19,18,20)=SCROLL_ITEM_FOCUS/SCROLL_TICK/
 *       SCROLL_LIMIT（小米注入将 18/20 映射为 210 表冠旋转、19 映射为 211 键盘反馈）；
 *       OPPO (12,12,12)=GESTURE_START（WearPhoneWindowManager 将 12 映射为波形 302 滚动触觉，
 *       且是唯一绕过睡眠/勿扰白名单的 ID）。
 
 *   - 层 3 {@link #performHapticFeedback(View, int)} /
 *       {@link #performHapticFeedback(View, int, int)}：小米上把
 *       CLOCK_TICK(4)/SEGMENT_FREQUENT_TICK(27) 重映射为 SEGMENT_TICK(26)
 *       （修复小米部分常量的震感异常：系统把 4/27 映射到 TEXTURE_TICK(21)；
 *       26 走 EFFECT_TICK(2) -> 210 表冠旋转）；其余设备与常量原样透传。
 */
public final class CnWearOverlay {

    private static final String GOOGLE_WEAR_CONSTANTS_CLASS =
            "com.google.wear.input.WearHapticFeedbackConstants";
    private static final String XIAOMI_WEAR_CONSTANTS_CLASS =
            "com.xiaomi.miwear.input.WearHapticFeedbackConstants";
    // framework.jar 中的类；wear-sdk.jar 不一定对应用的 boot classloader 可见。
    private static final String XIAOMI_FRAMEWORK_CLASS = "miwear.os.VibrationEffectId";
    // OPPO framework.jar 中的震动服务客户端，WearPhoneWindowManager 通过它播放波形 302。
    private static final String OPPO_VIBRATOR_CLASS =
            "android.os.linearmotorvibrator.LinearmotorVibrator";

    // android.view.HapticFeedbackConstants（AOSP 公开 SDK 与小米/OPPO 设备端编号一致，已实证）
    private static final int CLOCK_TICK = 4;
    private static final int SEGMENT_FREQUENT_TICK = 27;
    private static final int SEGMENT_TICK = 26;

    // 小米映射：SCROLL_ITEM_FOCUS / SCROLL_TICK / SCROLL_LIMIT
    private static final int XIAOMI_SCROLL_ITEM_FOCUS = 19;
    private static final int XIAOMI_SCROLL_TICK = 18;
    private static final int XIAOMI_SCROLL_LIMIT = 20;

    // OPPO 映射：GESTURE_START（tick/limit/官方 snap 反馈同用 12）
    private static final int OPPO_SCROLL_ITEM_FOCUS = 12;
    private static final int OPPO_SCROLL_TICK = 12;
    private static final int OPPO_SCROLL_LIMIT = 12;

    private static final int STATE_UNKNOWN = 0;
    private static final int STATE_INACTIVE = 1;
    private static final int STATE_XIAOMI = 2;
    private static final int STATE_OPPO = 3;

    private static volatile int activeState = STATE_UNKNOWN;

    private CnWearOverlay() {}

    // ------------------------------------------------------------------
    // 设备识别
    // ------------------------------------------------------------------

    public static boolean isXiaomi() {
        return resolveState() == STATE_XIAOMI;
    }

    public static boolean isOppo() {
        return resolveState() == STATE_OPPO;
    }

    public static boolean isActive() {
        int s = resolveState();
        return s == STATE_XIAOMI || s == STATE_OPPO;
    }

    private static int resolveState() {
        int s = activeState;
        if (s != STATE_UNKNOWN) {
            return s;
        }
        synchronized (CnWearOverlay.class) {
            if (activeState == STATE_UNKNOWN) {
                activeState = computeState();
            }
            return activeState;
        }
    }

    private static int computeState() {
        boolean google = hasSystemClass(GOOGLE_WEAR_CONSTANTS_CLASS);
        boolean xiaomiFramework = hasSystemClass(XIAOMI_FRAMEWORK_CLASS);
        boolean xiaomiSdk = hasSystemClass(XIAOMI_WEAR_CONSTANTS_CLASS);
        boolean oppo = hasSystemClass(OPPO_VIBRATOR_CLASS);
        // 真 Wear OS：boot classloader 里存在 Google 常量类，桩类永远不会被加载，
        // 这里直接退出，保持原生行为（含三星 Galaxy 等）。
        if (google) {
            return STATE_INACTIVE;
        }
        if (xiaomiFramework || xiaomiSdk) {
            return STATE_XIAOMI;
        }
        if (oppo) {
            return STATE_OPPO;
        }
        return STATE_INACTIVE;
    }

    /** 只探测系统类且不初始化，避免命中 app 内的 Google 桩类或厂商 SDK。 */
    private static boolean hasSystemClass(String name) {
        try {
            ClassLoader boot = CnWearOverlay.class.getClassLoader();
            while (boot != null && boot.getParent() != null) {
                boot = boot.getParent();
            }
            Class.forName(name, false, boot);
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 层 1：HapticsKt.hasWearSDK 包装 与 桩类取值
    // ------------------------------------------------------------------

    /** 目标设备上强制走 WearSDK 常量路径（配合注入的桩类）。 */
    public static boolean forceWearSdk() {
        return isActive();
    }

    /** 桩类 getScrollItemFocus()：OPPO 用 12，其余默认 19（真 Wear OS 上桩类不会生效）。 */
    public static int getScrollItemFocus() {
        return isOppo() ? OPPO_SCROLL_ITEM_FOCUS : XIAOMI_SCROLL_ITEM_FOCUS;
    }

    public static int getScrollTick() {
        return isOppo() ? OPPO_SCROLL_TICK : XIAOMI_SCROLL_TICK;
    }

    public static int getScrollLimit() {
        return isOppo() ? OPPO_SCROLL_LIMIT : XIAOMI_SCROLL_LIMIT;
    }

    // ------------------------------------------------------------------
    // 层 2：HapticsKt.getCustomRotaryConstants 包装
    // ------------------------------------------------------------------

    /**
     * @param hapticConstantsClass androidx...HapticConstants 类（由补丁代码以 LDC 传入，R8 混淆下依旧有效）
     * @return 目标平台的常量实例；非目标设备返回 null（回落原始逻辑）
     */
    public static Object rotaryConstants(Class<?> hapticConstantsClass) {
        try {
            if (isXiaomi()) {
                return hapticConstants(hapticConstantsClass,
                        XIAOMI_SCROLL_ITEM_FOCUS, XIAOMI_SCROLL_TICK, XIAOMI_SCROLL_LIMIT);
            }
            if (isOppo()) {
                return hapticConstants(hapticConstantsClass,
                        OPPO_SCROLL_ITEM_FOCUS, OPPO_SCROLL_TICK, OPPO_SCROLL_LIMIT);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported wear-compose HapticConstants structure", e);
        }
        return null;
    }

    private static Object hapticConstants(Class<?> cls, int focus, int tick, int limit)
            throws ReflectiveOperationException {
        // 1.6.2 的基类是 abstract；创建具体子类的新实例，不修改 Kotlin 全局单例。
        Class<?> concrete = Class.forName(
                cls.getName() + "$Wear4RotaryHapticConstants", true, cls.getClassLoader());
        Constructor<?> constructor = concrete.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object constants = constructor.newInstance();
        setConstant(cls, constants, "scrollFocus", focus);
        setConstant(cls, constants, "scrollTick", tick);
        setConstant(cls, constants, "scrollLimit", limit);
        return constants;
    }

    private static void setConstant(Class<?> cls, Object instance, String name, int value)
            throws ReflectiveOperationException {
        Field field = cls.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    // ------------------------------------------------------------------
    // 层 3：performHapticFeedback 调用点代理
    // ------------------------------------------------------------------

    public static boolean performHapticFeedback(View view, int feedbackConstant) {
        return view.performHapticFeedback(remap(feedbackConstant));
    }

    public static boolean performHapticFeedback(View view, int feedbackConstant, int flags) {
        return view.performHapticFeedback(remap(feedbackConstant), flags);
    }

    private static int remap(int feedbackConstant) {
        if (feedbackConstant == CLOCK_TICK || feedbackConstant == SEGMENT_FREQUENT_TICK) {
            if (isXiaomi()) {
                return SEGMENT_TICK;
            }
        }
        return feedbackConstant;
    }
}
