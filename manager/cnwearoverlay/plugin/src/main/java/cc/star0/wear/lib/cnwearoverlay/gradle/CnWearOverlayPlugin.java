package cc.star0.wear.lib.cnwearoverlay.gradle;

import cc.star0.wear.lib.cnwearoverlay.gradle.asm.CnWearOverlayClassVisitorFactory;
import com.android.build.api.instrumentation.InstrumentationScope;
import com.android.build.api.variant.ApplicationAndroidComponentsExtension;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Unit;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

/**
 * CN Wear Overlay Gradle 插件（主方案）。
 * 让 androidx.wear.compose.foundation.rotary（Haptics.kt，依赖库不可修改）的表冠触觉
 * 在小米 / OPPO 手表上正常工作，无需修改任何业务代码：
 *   - 层 1：包装 {@code HapticsKt.getCustomRotaryConstants(View)} ——
 *       小米返回 (19,18,20)（SCROLL_*，经小米注入映射为 210 表冠旋转），
 *       OPPO 返回 (12,12,12)（GESTURE_START，系统映射为波形 302 滚动触觉）。
 *   - 层 2：包装 {@code HapticsKt.hasWearSDK(Context)}（OPPO API 30 上恒为 false，
 *       需强制为 true 走 WearSDK 常量路径）+ 注入
 *       {@code com.google.wear.input.WearHapticFeedbackConstants} 桩类
 *       （小米/OPPO 系统无此类，缺失时 wear-compose 会 NoClassDefFoundError；
 *       在真 Wear OS 上 boot classpath 中的真实类优先，桩类不生效）。
 *   - 层 3：重写 app 内所有 {@code View.performHapticFeedback(I)/(II)} 调用点为
 *       运行时代理，仅在小米系统上把 CLOCK_TICK(4)/SEGMENT_FREQUENT_TICK(27)
 *       映射为 SEGMENT_TICK(26)，修复震感异常（小米系统将 4/27 映射到 TEXTURE_TICK(21)，
 *       而 26 走 EFFECT_TICK -> 210 表冠旋转）。其余设备与常量全部原样透传。
 * 使用方式：将本仓库放到目标项目中，{@code settings.gradle.kts} 加
 * {@code includeBuild("cnwearoverlay")}（指向仓库根目录），app 模块 {@code plugins { id("cc.star0.wear.lib.cnwearoverlay") }}。
 */
public final class CnWearOverlayPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "cnWearOverlay";

    static final String RUNTIME_HELPER_JAR = "/cnwearoverlay-runtime.jar";
    static final String RUNTIME_STUB_JAR = "/cnwearoverlay-stub.jar";

    private static final String EXTRACT_TASK_NAME = "cnWearOverlayExtractRuntimeJars";
    private static final String EXTRACT_OUTPUT_DIR = "cnwearoverlay";

    private final AtomicBoolean runtimeAdded = new AtomicBoolean(false);

    @Override
    public void apply(Project project) {
        CnWearOverlayExtension ext =
                project.getExtensions().create(EXTENSION_NAME, CnWearOverlayExtension.class);
        ext.getEnabled().convention(Boolean.TRUE);
        ext.getPatchHapticsKt().convention(Boolean.TRUE);
        ext.getRemapXiaomiConstants().convention(Boolean.TRUE);
        ext.getGenerateStub().convention(Boolean.TRUE);

        project.getPluginManager().withPlugin(
                "com.android.application", plugin -> register(project, ext));

        project.getPluginManager().withPlugin(
                "com.android.library",
                plugin -> {
                    if (!project.getPluginManager().hasPlugin("com.android.application")) {
                        project.getLogger()
                                .warn(
                                        "cnwearoverlay: applied to a library module; it is a no-op here."
                                                + " Apply the plugin to the application module so that"
                                                + " androidx.wear.compose HapticsKt (an app dependency)"
                                                + " can be instrumented.");
                    }
                });
    }

    private void register(Project project, CnWearOverlayExtension ext) {
        ApplicationAndroidComponentsExtension components =
                project.getExtensions()
                        .getByType(ApplicationAndroidComponentsExtension.class);

        components.onVariants(
                components.selector().all(),
                variant -> {
                    if (!ext.getEnabled().get()) {
                        project.getLogger()
                                .lifecycle(
                                        "cnwearoverlay: disabled, skipping variant {}",
                                        variant.getName());
                        return;
                    }
                    // 注入运行时 jar（每个 project 仅一次）
                    if (runtimeAdded.compareAndSet(false, true)) {
                        addRuntimeJars(project, ext);
                    }
                    variant.getInstrumentation()
                            .transformClassesWith(
                                    CnWearOverlayClassVisitorFactory.class,
                                    InstrumentationScope.ALL,
                                    params -> {
                                        params.getEnabled().set(ext.getEnabled());
                                        params.getPatchHapticsKt().set(ext.getPatchHapticsKt());
                                        // hasWearSDK 强制路径依赖 Google 常量桩类，二者绑定
                                        params.getInjectHasWearSdk()
                                                .set(
                                                        ext.getPatchHapticsKt()
                                                                .zip(
                                                                        ext.getGenerateStub(),
                                                                        (a, b) -> a && b));
                                        params.getRemapXiaomiConstants()
                                                .set(ext.getRemapXiaomiConstants());
                                        return Unit.INSTANCE;
                                    });
                });
    }

    /**
     * 把内嵌 jar 以「任务产物」的形式挂成 implementation 依赖。
     * 解压必须是任务而不是配置期副作用：产物位于 build/ 下，配置期解压会被同一次调用里的
     * {@code :app:clean} 删掉（随后 desugar / ASM transform 报文件不存在），配置缓存命中时
     * 也根本不会重跑。声明 {@code builtBy} 后 Gradle 会把解压排到 clean 之后、消费它的
     * transform 之前，产物缺失即重新生成。
     */
    private void addRuntimeJars(Project project, CnWearOverlayExtension ext) {
        List<String> resources =
                ext.getGenerateStub().get()
                        ? Arrays.asList(RUNTIME_HELPER_JAR, RUNTIME_STUB_JAR)
                        : List.of(RUNTIME_HELPER_JAR);
        Provider<Directory> outDir =
                project.getLayout().getBuildDirectory().dir(EXTRACT_OUTPUT_DIR);

        TaskProvider<ExtractRuntimeJars> extract =
                project.getTasks()
                        .register(
                                EXTRACT_TASK_NAME,
                                ExtractRuntimeJars.class,
                                task -> {
                                    task.setGroup(EXTENSION_NAME);
                                    task.setDescription(
                                            "Extract the embedded cnwearoverlay runtime jars.");
                                    task.getOutputDir().set(outDir);
                                    task.getResources().set(resources);
                                });

        List<Provider<File>> jars = new ArrayList<>(resources.size());
        for (String resource : resources) {
            String name = jarName(resource);
            jars.add(outDir.map(dir -> dir.file(name).getAsFile()));
        }
        project.getDependencies().add("implementation", project.files(jars).builtBy(extract));
    }

    private static String jarName(String resource) {
        return resource.substring(resource.lastIndexOf('/') + 1);
    }

    /** 从插件 jar 内嵌资源解压运行时辅助 jar 与 com.google.wear.input 桩 jar。 */
    public static abstract class ExtractRuntimeJars extends DefaultTask {

        @OutputDirectory
        public abstract DirectoryProperty getOutputDir();

        @Input
        public abstract ListProperty<String> getResources();

        @TaskAction
        public void extract() {
            File dir = getOutputDir().get().getAsFile();
            for (String resource : getResources().get()) {
                File out = new File(dir, jarName(resource));
                try (InputStream in = ExtractRuntimeJars.class.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new GradleException(
                                "cnwearoverlay: embedded runtime jar not found: " + resource);
                    }
                    Files.createDirectories(out.getParentFile().toPath());
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new GradleException("cnwearoverlay: failed to extract " + resource, e);
                }
            }
        }
    }
}
