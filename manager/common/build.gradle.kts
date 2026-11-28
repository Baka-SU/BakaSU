@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

val baseCFlags = listOf(
    "-Wall", "-Qunused-arguments", "-fvisibility=hidden", "-fvisibility-inlines-hidden",
    "-fno-exceptions", "-fno-stack-protector", "-fomit-frame-pointer",
    "-Wno-builtin-macro-redefined", "-Wno-unused-value", "-D__FILE__=__FILE_NAME__",
)
val baseCppFlags = baseCFlags + "-fno-rtti"

android {
    namespace = "org.bakasu.bakasu"
    compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
    ndkVersion = rootProject.extra["androidCompileNdkVersion"] as String
    buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String

    defaultConfig {
        minSdk = rootProject.extra["androidMinSdkVersion"] as Int
        buildConfigField("String", "APPLICATION_ID", "\"${rootProject.extra["managerPackageName"]}\"")
        buildConfigField("String", "VERSION_NAME", "\"${rootProject.extra["managerVersionName"]}\"")
        buildConfigField("int", "VERSION_CODE", rootProject.extra["managerVersionCode"].toString())
        buildConfigField("boolean", "IS_PR_BUILD", rootProject.extra["isPrBuild"].toString())
        resValue("string", "app_name", rootProject.extra["managerName"] as String)
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
                cFlags += baseCFlags + "-std=c2x"
                cppFlags += baseCppFlags + "-std=c++2b"
            }
        }
        ndk { abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a", "riscv64") }
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
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    compileOptions {
        sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
        targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    lintChecks(project(":lint-rules"))
    api(platform(libs.koin.bom))
    api(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    api(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.gson)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    api(libs.okhttp)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    api(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)
    api(libs.material.kolor)
    implementation(libs.monet.compat)
    implementation(libs.material.components)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.dev.rikka.rikkax.parcelablelist)
    api(libs.io.coil.kt.coil.compose)
    implementation(libs.me.zhanghai.android.appiconloader)
    implementation(libs.me.zhanghai.android.appiconloader.coil)
    implementation(libs.org.lsposed.hiddenapibypass)
    implementation(libs.markdown)
    implementation(libs.markwon.tables)
    implementation(libs.androidx.webkit)
    implementation(libs.miuix.nav)
    implementation(libs.lsposed.cxx)
}
