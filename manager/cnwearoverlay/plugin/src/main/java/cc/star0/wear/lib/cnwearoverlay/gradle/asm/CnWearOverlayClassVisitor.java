package cc.star0.wear.lib.cnwearoverlay.gradle.asm;

import java.util.function.Predicate;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 核心字节码访问器，对每个被 instrument 的类做两类改写：
 *
 * A. HapticsKt 补丁（仅当类为 androidx.wear.compose.foundation.rotary.HapticsKt）：
 * 采用「重命名原方法 + 新增同名包装方法」的方式，避免对原方法做帧表手术：
 *
 * // 层 1：常量选择（分支仅一个跳转目标，手工补一帧 F_NEW）
 * public static HapticConstants getCustomRotaryConstants(View view) {
 *     Object c = CnWearOverlay.rotaryConstants(HapticConstants.class); // 小米/OPPO 时返回平台常量，否则 null
 *     if (c != null) return (HapticConstants) c;
 *     return HapticsKt.getCustomRotaryConstants$cnwear$orig(view);      // 原始逻辑
 * }
 * // 层 2：WearSDK 判定（无分支，无需 StackMapTable）
 * private static boolean hasWearSDK(Context context) {
 *     return CnWearOverlay.forceWearSdk() | HapticsKt.hasWearSDK$cnwear$orig(context);
 * }
 *
 * 调用方（rememberRotaryHapticFeedbackProvider）按名称绑定到新的包装方法，行为不变。
 *
 * B. 层 3 调用点重写（remap 开启时的所有类，含 owner 为 View 子类的调用）：
 * invokevirtual android/view/View.performHapticFeedback(I)Z
 *     -> invokestatic cc/star0/wear/lib/cnwearoverlay/runtime/CnWearOverlay.performHapticFeedback(Landroid/view/View;I)Z
 * invokevirtual android/view/View.performHapticFeedback(II)Z
 *     -> invokestatic cc/star0/wear/lib/cnwearoverlay/runtime/CnWearOverlay.performHapticFeedback(Landroid/view/View;II)Z
 *
 * 栈形状完全一致（receiver+args -> Z），不引入分支，不影响帧表与 maxStack/maxLocals。
 */
final class CnWearOverlayClassVisitor extends ClassVisitor {

    private static final String HAPTICS_KT = "androidx/wear/compose/foundation/rotary/HapticsKt";
    private static final String HAPTIC_CONSTANTS =
            "androidx/wear/compose/foundation/rotary/HapticConstants";
    private static final String HELPER = "cc/star0/wear/lib/cnwearoverlay/runtime/CnWearOverlay";
    private static final String ORIG_SUFFIX = "$cnwear$orig";

    private static final String DESC_GET_CUSTOM_ROTARY_CONSTANTS =
            "(Landroid/view/View;)Landroidx/wear/compose/foundation/rotary/HapticConstants;";
    private static final String DESC_HAS_WEAR_SDK = "(Landroid/content/Context;)Z";

    private final boolean patchHaptics;
    private final boolean patchHasWearSdk;
    private final boolean remap;
    private final Predicate<String> isViewSubclass;

    private boolean isHapticsKt = false;

    private boolean renamedGcrc = false;
    private int gcrcAccess;
    private String gcrcName;
    private String gcrcDesc;

    private boolean renamedHws = false;
    private int hwsAccess;

    CnWearOverlayClassVisitor(
            ClassVisitor next, boolean patchHaptics, boolean patchHasWearSdk, boolean remap,
            Predicate<String> isViewSubclass) {
        super(Opcodes.ASM9, next);
        this.patchHaptics = patchHaptics;
        this.patchHasWearSdk = patchHasWearSdk;
        this.remap = remap;
        this.isViewSubclass = isViewSubclass;
    }

    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        isHapticsKt = HAPTICS_KT.equals(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
        // 层 1：仅匹配当前 getCustomRotaryConstants 签名。
        if (isHapticsKt
                && patchHaptics
                && "getCustomRotaryConstants".equals(name)
                && DESC_GET_CUSTOM_ROTARY_CONSTANTS.equals(descriptor)) {
            renamedGcrc = true;
            gcrcAccess = access;
            gcrcName = name;
            gcrcDesc = descriptor;
            return wrap(
                    super.visitMethod(access, name + ORIG_SUFFIX, descriptor, signature, exceptions));
        }
        // 层 2：重命名 hasWearSDK
        if (isHapticsKt
                && patchHasWearSdk
                && "hasWearSDK".equals(name)
                && DESC_HAS_WEAR_SDK.equals(descriptor)) {
            renamedHws = true;
            hwsAccess = access;
            return wrap(
                    super.visitMethod(access, name + ORIG_SUFFIX, descriptor, signature, exceptions));
        }
        return wrap(super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    @Override
    public void visitEnd() {
        if (renamedGcrc) {
            emitGetCustomRotaryConstantsWrapper(gcrcAccess, gcrcName, gcrcDesc);
        }
        if (renamedHws) {
            emitHasWearSdkWrapper(hwsAccess);
        }
        super.visitEnd();
    }

    /** remap 开启时，为方法体挂调用点重写器（含被重命名的原方法，保持一致性）。 */
    private MethodVisitor wrap(MethodVisitor mv) {
        return remap ? new PerformHapticFeedbackRewriter(api, mv, isViewSubclass) : mv;
    }

    /**
     * 生成分支包装方法；唯一分支目标处补一帧 F_NEW。
     * locals = [view]，stack = [helper 返回的 Object]。
     */
    private void emitGetCustomRotaryConstantsWrapper(int access, String name, String descriptor) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, null, null);
        Label lNull = new Label();
        mv.visitCode();
        // 传 HapticConstants.class（LDC 类型常量，R8 混淆下仍保持引用有效）
        mv.visitLdcInsn(Type.getObjectType(HAPTIC_CONSTANTS));
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                HELPER,
                "rotaryConstants",
                "(Ljava/lang/Class;)Ljava/lang/Object;",
                false);
        mv.visitInsn(Opcodes.DUP);
        mv.visitJumpInsn(Opcodes.IFNULL, lNull);
        mv.visitTypeInsn(Opcodes.CHECKCAST, HAPTIC_CONSTANTS);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(lNull);
        mv.visitFrame(
                Opcodes.F_NEW,
                1,
                new Object[] {"android/view/View"},
                1,
                new Object[] {"java/lang/Object"});
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HAPTICS_KT, name + ORIG_SUFFIX, descriptor, false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    /** 生成无分支包装方法：forceWearSdk() | hasWearSDK$cnwear$orig(context)。 */
    private void emitHasWearSdkWrapper(int access) {
        MethodVisitor mv =
                super.visitMethod(access, "hasWearSDK", DESC_HAS_WEAR_SDK, null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "forceWearSdk", "()Z", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                HAPTICS_KT,
                "hasWearSDK" + ORIG_SUFFIX,
                DESC_HAS_WEAR_SDK,
                false);
        mv.visitInsn(Opcodes.IOR);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
    }

    /** 把 View 及其子类的虚调用替换为运行时代理（栈形状不变）。 */
    private static final class PerformHapticFeedbackRewriter extends MethodVisitor {
        private final Predicate<String> isViewSubclass;

        PerformHapticFeedbackRewriter(
                int api, MethodVisitor mv, Predicate<String> isViewSubclass) {
            super(api, mv);
            this.isViewSubclass = isViewSubclass;
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // super 调用使用 INVOKESPECIAL，必须保留；代理的虚分派会重新进入子类并递归。
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "performHapticFeedback".equals(name)
                    && ("(I)Z".equals(descriptor) || "(II)Z".equals(descriptor))
                    && ("android/view/View".equals(owner) || isViewSubclass.test(owner))) {
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        "performHapticFeedback",
                        "(Landroid/view/View;" + descriptor.substring(1),
                        false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }
}
