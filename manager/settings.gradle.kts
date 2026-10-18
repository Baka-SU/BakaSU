@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    // Resolves the vendored cnwearoverlay plugin by id.
    includeBuild("cnwearoverlay")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// Crown haptics on Xiaomi/OPPO watches: the vendored cnwearoverlay build (LGPL-3.0, see
// cnwearoverlay/LICENSE) patches Wear Compose rotary haptics at build time. It is a plain
// directory, not a submodule, so a checkout builds without fetching anything.

rootProject.name = "BakaSU"
include(":app")
include(":baselineprofile")
include(":lint-rules")
