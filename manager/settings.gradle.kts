@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Crown scroll haptics on watches without the Google Wear SDK, such as the Xiaomi Watch 5: the
// miwearhaptics submodule routes the Google haptic getters to a Google/Xiaomi compatibility layer.
// It provides both the plugin and its runtime library, so it is included at the top level.
includeBuild("miwearhaptics")

rootProject.name = "BakaSU"
include(":app")
include(":baselineprofile")
include(":lint-rules")
