package cc.star0.wear.lib.cnwearoverlay.gradle;

import org.gradle.api.provider.Property;

/**
 * cnWearOverlay 扩展配置。
 * cnWearOverlay {
 *     enabled = true                    // 总开关
 *     patchHapticsKt = true             // 字节码补丁：HapticsKt.getCustomRotaryConstants / hasWearSDK
 *     remapXiaomiConstants = true         // 调用点重写：View.performHapticFeedback(CLOCK_TICK/SEGMENT_FREQUENT_TICK) -> SEGMENT_TICK（仅小米生效）
 *     generateStub = true               // 生成 com.google.wear.input.WearHapticFeedbackConstants 桩类
 * }
 */
public interface CnWearOverlayExtension {

    /** 总开关；false 时不注入任何类、不做任何字节码修改。 */
    Property<Boolean> getEnabled();

    /**
     * 是否给 androidx.wear.compose.foundation.rotary.HapticsKt 打补丁：
     * 1) getCustomRotaryConstants(View) 包装：小米返回 (19,18,20)、OPPO 返回 (12,12,12)；
     * 2) hasWearSDK(Context) 包装（仅在同时启用 generateStub 时注入）。
     */
    Property<Boolean> getPatchHapticsKt();

    /**
     * 是否把应用内所有 android.view.View.performHapticFeedback(int)/(int,int) 调用点
     * 重写为运行时代理；代理仅在小米设备上把 CLOCK_TICK(4)/SEGMENT_FREQUENT_TICK(27)
     * 重映射为 SEGMENT_TICK(26)，其余设备与常量原样透传。
     */
    Property<Boolean> getRemapXiaomiConstants();

    /** 是否向 app 注入 com.google.wear.input.WearHapticFeedbackConstants 桩类。 */
    Property<Boolean> getGenerateStub();

}
