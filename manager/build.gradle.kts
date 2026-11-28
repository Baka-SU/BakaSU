import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.library) apply false
    alias(libs.plugins.agp.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
}

spotless {
    lineEndings = LineEnding.UNIX

    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.compose.rules.get()}"))
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "ktlint_compose_modifier-missing-check" to "disabled",
                    "ktlint_compose_compositionlocal-allowlist" to "disabled",
                    "ktlint_compose_mutable-state-param-check" to "disabled",
                    "ktlint_compose_parameter-naming" to "disabled",
                    "ktlint_compose_modifier-naming" to "disabled",
                ),
            )
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}

extra["androidMinSdkVersion"] = 26
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidBuildToolsVersion"] = "36.1.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = 30000 + getGitCommitCount() + 700
extra["managerVersionName"] = getGitDescribe()
extra["isPrBuild"] = project.findProperty("IS_PR_BUILD")?.toString()?.toBoolean() ?: false
extra["defaultManagerPackageName"] = "com.resukisu.resukisu"
extra["managerPackageName"] = project.findProperty("KSU_PACKAGE_NAME")?.toString() ?: extra["defaultManagerPackageName"]
extra["defaultManagerAppName"] = if (extra["isPrBuild"] == true) "BakaSU PR" else "BakaSU"
extra["managerName"] = project.findProperty("KSU_NAME")?.toString() ?: extra["defaultManagerAppName"]

val isSpoofedBuild = project.findProperty("IS_SPOOFED_BUILD")?.toString()?.toBoolean() ?: false

fun getGitCommitCount(): Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

fun getGitDescribe(): String {
    val desc = providers.exec {
        commandLine("git", "describe", "--tags", "--always", "--abbrev=0")
    }.standardOutput.asText.get().trim()
    if (isSpoofedBuild) {
        return "$desc-spoofed"
    }
    return desc
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        val isReleaseTask = project.gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
        val managerVersionName = rootProject.extra["managerVersionName"] as String
        val managerVersionCode = rootProject.extra["managerVersionCode"] as Int
        val device = project.name

        configure<ApplicationExtension> {
            namespace = "org.bakasu.bakasu.$device"
            compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
            ndkVersion = rootProject.extra["androidCompileNdkVersion"] as String
            buildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
            defaultConfig {
                applicationId = rootProject.extra["managerPackageName"] as String
                minSdk = rootProject.extra["androidMinSdkVersion"] as Int
                targetSdk = rootProject.extra["androidTargetSdkVersion"] as Int
                versionCode = managerVersionCode
                versionName = managerVersionName
                ndk { abiFilters += listOf("arm64-v8a", "x86_64", "armeabi-v7a", "riscv64") }
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    vcsInfo.include = false
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), rootProject.file("proguard-rules.pro"))
                }
            }
            buildFeatures { compose = true }
            packaging {
                jniLibs { useLegacyPackaging = true }
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
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
            androidResources { generateLocaleConfig = true }
            splits {
                abi {
                    isEnable = isReleaseTask
                    reset()
                    include("arm64-v8a", "x86_64", "armeabi-v7a", "riscv64")
                    isUniversalApk = true
                }
            }
            compileOptions {
                sourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
                targetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
            }
            lint {
                abortOnError = true
                checkReleaseBuilds = false
            }
        }

        configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                variant.outputs.forEach { output ->
                    val abi = output.filters.firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
                    val abiSegment = when {
                        abi != null -> "-$abi"
                        variant.outputs.size > 1 -> "-universal"
                        else -> ""
                    }
                    // Installed Mobile Managers still match the flavorless filename.
                    val deviceSegment = if (device == "wear") "-wear" else ""
                    output.outputFileName.set("BakaSU_${managerVersionName}_$managerVersionCode$deviceSegment$abiSegment-${variant.buildType}.apk")
                }
            }
        }
    }
}
