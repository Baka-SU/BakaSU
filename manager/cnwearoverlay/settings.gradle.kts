pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cnwearoverlay"

// Gradle 插件（主）：includeBuild("cnwearoverlay") 后在 app 模块应用 id "cc.star0.wear.lib.cnwearoverlay"
include(":plugin")

// xposed/ 是独立的 Android 构建（libxposed 102 模块），不参与本构建。
