@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    // Crown haptics on Xiaomi/OPPO watches: cnwearoverlay patches Wear Compose rotary haptics at
    // build time. Its plugin marker is not on a public repository, so the id is mapped to the
    // JitPack module coordinates published from Hanhanawa/cnwearoverlay.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "cc.star0.wear.lib.cnwearoverlay") {
                useModule("com.github.Hanhanawa.cnwearoverlay:plugin:1.0.2")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "BakaSU"
include(":app")
include(":baselineprofile")
include(":lint-rules")
