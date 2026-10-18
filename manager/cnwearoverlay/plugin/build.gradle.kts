plugins {
    `java-gradle-plugin`
}

group = "cc.star0.wear.lib"
version = "1.0.0"

java {
    // 与 xposed 构建统一使用 Java 17。
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    compileOnly(gradleApi())
    // 与 xposed 构建使用同一 AGP 基线；运行时由使用方 AGP 提供。
    compileOnly("com.android.tools.build:gradle-api:9.4.1")
    // ASM 由 AGP 的 instrumentation 运行时提供
    compileOnly("org.ow2.asm:asm:9.10.1")
    // 仅用于 transformClassesWith 的 Function1 lambda 返回 Unit
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.4.20")
}

gradlePlugin {
    plugins {
        create("cnWearOverlay") {
            id = "cc.star0.wear.lib.cnwearoverlay"
            implementationClass = "cc.star0.wear.lib.cnwearoverlay.gradle.CnWearOverlayPlugin"
            displayName = "CN Wear Overlay Gradle Plugin"
            description = "Make androidx wear-compose rotary haptics work on Xiaomi/OPPO watches (build-time bytecode instrumentation)."
        }
    }
}

// ---------------------------------------------------------------------------
// 内嵌运行时 jar：注入到使用方 app 中的辅助类与 com.google.wear.input 桩类。
// 运行时源码针对极简 android 桩类编译（不随 jar 发布），产物打进插件资源。
// ---------------------------------------------------------------------------

val runtimeClassesDir = layout.buildDirectory.dir("cnwear/runtime-classes")
val embeddedDir = layout.buildDirectory.dir("cnwear/embedded")

val compileCnWearOverlayRuntime = tasks.register<JavaCompile>("compileCnWearOverlayRuntime") {
    description = "Compile CN Wear Overlay Runtime"
    source(fileTree("../runtime/src/main/java"), fileTree("../runtime/src/stubs"))
    classpath = files()
    destinationDirectory.set(runtimeClassesDir)
    options.release.set(17)
    options.encoding = "UTF-8"
}

fun embeddedRuntimeJar(taskName: String, fileName: String, vararg includes: String) =
    tasks.register<Jar>(taskName) {
        description = "Embedded CN Wear Overlay Runtime JAR"
        dependsOn(compileCnWearOverlayRuntime)
        archiveFileName.set(fileName)
        destinationDirectory.set(embeddedDir)
        from(runtimeClassesDir)
        include(*includes)
    }

val runtimeHelperJar = embeddedRuntimeJar("cnWearRuntimeHelperJar", "cnwearoverlay-runtime.jar", "cc/**")
runtimeHelperJar.configure {
    from("../runtime/src/main/resources")
    include("META-INF/proguard/**")
}
val runtimeStubJar = embeddedRuntimeJar("cnWearRuntimeStubJar", "cnwearoverlay-stub.jar", "com/**")

// 两个 jar 输出到同一目录，srcDir 只需挂一次
sourceSets.main {
    resources.srcDir(embeddedDir)
}

tasks.named("processResources") {
    dependsOn(runtimeHelperJar, runtimeStubJar)
}
