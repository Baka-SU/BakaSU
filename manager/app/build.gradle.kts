@file:Suppress("UnstableApiUsage")

import cc.star0.wear.lib.cnwearoverlay.gradle.CnWearOverlayPlugin.ExtractRuntimeJars
import cc.star0.wear.lib.cnwearoverlay.gradle.asm.CnWearOverlayClassVisitorFactory
import com.android.build.api.instrumentation.InstrumentationScope

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.aboutLibraries)
    id("kotlin-parcelize")
    id("cc.star0.wear.lib.cnwearoverlay") version "1.2.2" apply false
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidCompileNdkVersion = rootProject.extra["androidCompileNdkVersion"] as String
val androidBuildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion = rootProject.extra["androidTargetSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
val managerVersionCode = rootProject.extra["managerVersionCode"] as Int
val managerVersionName = rootProject.extra["managerVersionName"] as String
val managerPackageName = rootProject.extra["managerPackageName"] as String
val managerName = rootProject.extra["managerName"] as String

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

val baseCFlags = listOf(
    "-Wall", "-Qunused-arguments", "-fvisibility=hidden", "-fvisibility-inlines-hidden",
    "-fno-exceptions", "-fno-stack-protector", "-fomit-frame-pointer",
    "-Wno-builtin-macro-redefined", "-Wno-unused-value", "-D__FILE__=__FILE_NAME__",
)
val baseCppFlags = baseCFlags + "-fno-rtti"

val isReleaseTask =
    project.gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

android {
    namespace = "org.bakasu.bakasu"

    flavorDimensions += "device"
    productFlavors {
        create("normal") { dimension = "device" }
        create("wear") { dimension = "device" }
    }

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DCMAKE_CXX_FLAGS_DEBUG=-Og", "-DCMAKE_C_FLAGS_DEBUG=-Og")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            externalNativeBuild {
                cmake {
                    arguments += "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.get().asFile.absolutePath}/symbols"
                    arguments += "-DCMAKE_BUILD_TYPE=Release"

                    val releaseFlags = listOf(
                        "-flto",
                        "-ffunction-sections",
                        "-fdata-sections",
                        "-Wl,--gc-sections",
                        "-fno-unwind-tables",
                        "-fno-asynchronous-unwind-tables",
                        "-Wl,--exclude-libs,ALL",
                    )
                    val configFlags = listOf("-Oz", "-DNDEBUG").joinToString(" ")

                    cppFlags += releaseFlags
                    cFlags += releaseFlags

                    arguments += listOf(
                        "-DCMAKE_CXX_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_C_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--icf=all -s -Wl,--hash-style=sysv -Wl,-z,norelro",
                    )
                }
            }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        resValues = true
        compose = true
        prefab = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            if (isReleaseTask) {
                // https://stackoverflow.com/a/58956288
                // It will break Layout Inspector, but it's unused for release build.
                excludes += "META-INF/*.version"
            }
            // https://github.com/Kotlin/kotlinx.coroutines?tab=readme-ov-file#avoiding-including-the-debug-infrastructure-in-the-resulting-apk
            excludes += "DebugProbesKt.bin"
            // https://issueantenna.com/repo/kotlin/kotlinx.coroutines/issues/3158
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileSdk = androidCompileSdkVersion
    ndkVersion = androidCompileNdkVersion
    buildToolsVersion = androidBuildToolsVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = managerVersionCode
        versionName = managerVersionName
        applicationId = managerPackageName

        val isPrBuild = rootProject.extra["isPrBuild"] as Boolean
        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())
        resValue("string", "app_name", managerName)

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
                cFlags += baseCFlags + "-std=c2x"
                cppFlags += baseCppFlags + "-std=c++2b"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a", "riscv64")
        }
    }

    splits {
        abi {
            isEnable = isReleaseTask
            reset()
            include("arm64-v8a", "x86_64", "armeabi-v7a", "riscv64")
            isUniversalApk = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false

    variants {
        create("wear") { mergeIntoMain = false }
    }
}

// Plugin 1.2.2 applies to every variant. Reuse its task and visitor only for Wear.
val wearOverlayRuntime = tasks.register<ExtractRuntimeJars>("extractWearOverlayRuntime") {
    outputDir.set(layout.buildDirectory.dir("cnwearoverlay/wear"))
    resources.set(listOf("/cnwearoverlay-runtime.jar", "/cnwearoverlay-stub.jar"))
}

androidComponents {
    onVariants(selector().withFlavor("device" to "wear")) { variant ->
        variant.instrumentation.transformClassesWith(
            CnWearOverlayClassVisitorFactory::class.java,
            InstrumentationScope.ALL,
        ) { parameters ->
            parameters.enabled.set(true)
            parameters.patchHapticsKt.set(true)
            parameters.injectHasWearSdk.set(true)
            parameters.remapXiaomiConstants.set(true)
        }
    }
}

base {
    archivesName.set(
        "BakaSU_${managerVersionName}_$managerVersionCode",
    )
}

aboutLibraries {
    library {
        // Enable the duplication mode, allows to merge, or link dependencies which relate
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        // Configure the duplication rule, to match "duplicates" with
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

dependencies {
    lintChecks(project(":lint-rules"))
    baselineProfile(project(":baselineprofile"))

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.gson)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.foundation)
    "wearImplementation"(libs.androidx.wear.compose.foundation)
    "wearImplementation"(libs.androidx.wear.compose.material3)
    "wearImplementation"(libs.androidx.wear.compose.navigation3)
    "wearImplementation"(libs.androidx.navigation3.runtime)
    "wearImplementation"(libs.androidx.navigation3.ui)
    "wearImplementation"(libs.androidx.lifecycle.viewmodel.navigation3)
    "wearImplementation"(
        files(wearOverlayRuntime.map { it.outputDir.file("cnwearoverlay-runtime.jar") }).builtBy(wearOverlayRuntime),
    )
    "wearImplementation"(
        files(wearOverlayRuntime.map { it.outputDir.file("cnwearoverlay-stub.jar") }).builtBy(wearOverlayRuntime),
    )

    implementation(libs.androidx.compose.runtime.tracing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.miuix.blur)
    implementation(libs.miuix.nav)

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.material.kolor)
    implementation(libs.monet.compat)
    implementation(libs.material.components)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.capsule)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.io.coil.kt.coil.compose)
    implementation(libs.ucrop)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.me.zhanghai.android.appiconloader)
    implementation(libs.me.zhanghai.android.appiconloader.coil)
    implementation(libs.org.lsposed.hiddenapibypass)

    implementation(libs.markdown)
    implementation(libs.markwon.tables)
    implementation(libs.androidx.webkit)
    "wearImplementation"(libs.androidx.wear.input)

    implementation(libs.lsposed.cxx)

    implementation(libs.accompanist.drawablepainter)
}
