package cc.star0.wear.lib.cnwearoverlay.gradle.asm;

import com.android.build.api.instrumentation.InstrumentationParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

/**
 * 传给 AGP 字节码 instrumentation worker 的参数（在配置期从扩展惰性映射）。
 *
 * 注意：按 AGP 的 artifact transform 参数校验要求，每个属性必须带 {@code @Input} 注解。
 */
public interface CnWearInstrumentationParams extends InstrumentationParameters {

    @Input
    Property<Boolean> getEnabled();

    @Input
    Property<Boolean> getPatchHapticsKt();

    /** hasWearSDK 包装依赖桩类存在，由插件侧绑定 patchHapticsKt 与 generateStub。 */
    @Input
    Property<Boolean> getInjectHasWearSdk();

    @Input
    Property<Boolean> getRemapXiaomiConstants();
}
