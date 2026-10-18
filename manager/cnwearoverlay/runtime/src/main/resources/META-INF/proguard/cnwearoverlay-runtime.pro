# 运行时以基类名拼接子类名：保留基类名称，但允许裁剪未使用的类及优化实现。
-keep,allowshrinking,allowoptimization class androidx.wear.compose.foundation.rotary.HapticConstants {}

# 只保护反射写入的字段；禁止常量传播，确保小米 / OPPO 的赋值仍影响 getter。
-keepclassmembers class androidx.wear.compose.foundation.rotary.HapticConstants {
    java.lang.Integer scrollFocus;
    java.lang.Integer scrollTick;
    java.lang.Integer scrollLimit;
}

# 仅在反射入口被使用时保留具体子类及无参构造；单例、getter 等按实际引用处理。
-if class cc.star0.wear.lib.cnwearoverlay.runtime.CnWearOverlay {
    public static java.lang.Object rotaryConstants(java.lang.Class);
}
-keep,allowoptimization class androidx.wear.compose.foundation.rotary.HapticConstants$Wear4RotaryHapticConstants {
    <init>();
}
