@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.test)
    alias(libs.plugins.androidx.baselineprofile)
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion = rootProject.extra["androidTargetSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
val managerPackageName = rootProject.extra["managerPackageName"] as String

android {
    namespace = "org.bakasu.bakasu.baselineprofile"
    compileSdk = androidCompileSdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "TARGET_PACKAGE", "\"$managerPackageName\"")
        buildConfigField("String", "TARGET_DEVICE", "\"wear\"")
    }

    buildFeatures { buildConfig = true }
    targetProjectPath = ":wear"
    sourceSets["main"].kotlin.srcDir("../src/main/java")

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.runner)
}
