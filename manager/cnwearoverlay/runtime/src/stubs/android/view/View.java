package android.view;

/**
 * 仅用于编译内嵌运行时 jar 的最小 android 桩（compile-only，不随 jar 发布）。
 */
public class View {

    public boolean performHapticFeedback(int feedbackConstant) {
        throw new IllegalStateException("stub");
    }

    public boolean performHapticFeedback(int feedbackConstant, int flags) {
        throw new IllegalStateException("stub");
    }
}
