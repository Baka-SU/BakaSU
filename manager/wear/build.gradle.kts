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
    id("cc.star0.wear.lib.cnwearoverlay") version "1.2.2" apply false
}

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

baselineProfile {
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

aboutLibraries {
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

// Scope overlay runtime and instrumentation to this application.
val wearOverlayRuntime = tasks.register<ExtractRuntimeJars>("extractWearOverlayRuntime") {
    outputDir.set(layout.buildDirectory.dir("cnwearoverlay/wear"))
    resources.set(listOf("/cnwearoverlay-runtime.jar", "/cnwearoverlay-stub.jar"))
}

androidComponents {
    onVariants { variant ->
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

dependencies {
    implementation(project(":common"))
    lintChecks(project(":lint-rules"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime.tracing)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.aboutlibraries.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.accompanist.drawablepainter)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    baselineProfile(project(":baselineprofile:wear"))
    implementation(libs.com.github.topjohnwu.libsu.io)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.wear.input)
    implementation(files(wearOverlayRuntime.map { it.outputDir.file("cnwearoverlay-runtime.jar") }).builtBy(wearOverlayRuntime))
    implementation(files(wearOverlayRuntime.map { it.outputDir.file("cnwearoverlay-stub.jar") }).builtBy(wearOverlayRuntime))
}
