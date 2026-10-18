package cc.star0.wear.lib.cnwearoverlay.gradle.asm;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.provider.Property;
import org.objectweb.asm.ClassVisitor;

/**
 * AGP instrumentation 工厂：Scope 为 ALL（依赖里的 HapticsKt 也必须被处理）。
 * 按 AGP 规范：必须声明为 abstract class，parameters / instrumentationContext
 * 留空由框架注入；需具备无参构造（隐式）。
 * 通过 isInstrumentable 做廉价过滤：
 *   - 排除运行时类自身（否则代理方法会递归重写自身调用，导致无限递归）；
 *   - 排除 com.google.wear.input 桩类；
 *   - patchHapticsKt 开启时命中 HapticsKt；
 *   - remapXiaomiConstants 开启时接受全部其余类（业务代码的 CLOCK_TICK 调用点可能在任何类里）。
 */
public abstract class CnWearOverlayClassVisitorFactory
        implements AsmClassVisitorFactory<CnWearInstrumentationParams> {

    private static final String HAPTICS_KT = "androidx.wear.compose.foundation.rotary.HapticsKt";
    private static final String STUB_CLASS = "com.google.wear.input.WearHapticFeedbackConstants";

    @Override
    public ClassVisitor createClassVisitor(ClassContext classContext, ClassVisitor next) {
        CnWearInstrumentationParams p = getParameters().getOrNull();
        boolean patchHaptics = flag(p, CnWearInstrumentationParams::getPatchHapticsKt);
        boolean patchHasWearSdk = flag(p, CnWearInstrumentationParams::getInjectHasWearSdk);
        boolean remap = flag(p, CnWearInstrumentationParams::getRemapXiaomiConstants);
        // 缓存仅属于本次 visitor，避免跨 worker / variant 共享类路径信息。
        Map<String, Boolean> viewSubclasses = new HashMap<>();
        return new CnWearOverlayClassVisitor(next, patchHaptics, patchHasWearSdk, remap,
                owner -> viewSubclasses.computeIfAbsent(owner, name -> {
                    // ASM 使用内部名；AGP 使用全限定名，superClasses 包含全部祖先。
                    String className = name.replace('/', '.');
                    ClassData current = classContext.getCurrentClassData();
                    ClassData data = className.equals(current.getClassName())
                            ? current : classContext.loadClassData(className);
                    return data != null && data.getSuperClasses().contains("android.view.View");
                }));
    }

    @Override
    public boolean isInstrumentable(ClassData classData) {
        String name = classData.getClassName();
        // 绝不能改写运行时自身：代理方法内部调用的 view.performHapticFeedback 会被再次重写 → 无限递归
        if (name.startsWith("cc.star0.wear.lib.cnwearoverlay.runtime.")) {
            return false;
        }
        if (STUB_CLASS.equals(name)) {
            return false;
        }
        CnWearInstrumentationParams p = getParameters().getOrNull();
        if (p == null || !Boolean.TRUE.equals(p.getEnabled().getOrNull())) {
            return false;
        }
        if (Boolean.TRUE.equals(p.getPatchHapticsKt().getOrNull()) && HAPTICS_KT.equals(name)) {
            return true;
        }
        return Boolean.TRUE.equals(p.getRemapXiaomiConstants().getOrNull());
    }

    private interface FlagGetter {
        Property<Boolean> get(CnWearInstrumentationParams params);
    }

    private static boolean flag(CnWearInstrumentationParams p, FlagGetter getter) {
        return p != null && Boolean.TRUE.equals(getter.get(p).getOrNull());
    }
}
