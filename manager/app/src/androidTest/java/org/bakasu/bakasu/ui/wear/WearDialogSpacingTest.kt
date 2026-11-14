package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.HomeDashboardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearDialogSpacingTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun longInstallConfirmationUsesTheHomeWarningsIconInset() {
        val showInstall = mutableStateOf(false)
        val pending = mutableStateOf<String?>(null)
        val installer = WearModuleInstaller(WearNavigator(NavBackStack<NavKey>(WearRoute.Main), {}, {}, {}), pending, {}, {})
        val zip = File(compose.activity.cacheDir, "dialog-spacing-module.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("module.prop"))
            it.write(("id=spacing\nname=Dialog spacing module\nversion=1\nversionCode=1\nauthor=Test\ndescription=" + "A long module description that scrolls. ".repeat(30)).toByteArray())
            it.closeEntry()
        }
        try {
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current).apply {
                    screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                        if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                }
                CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Color.Magenta)) {
                        AppScaffold(timeText = {}) {
                            if (showInstall.value) {
                                WearModuleInstallConfirmDialog(installer)
                            } else {
                                WearHomePage(HomeDashboardState(isInitialDataLoaded = true), null, {}, {})
                            }
                        }
                    }
                }
            }
            val warningTitle = compose.activity.getString(R.string.wear_warning_debug)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(warningTitle))
            compose.onNodeWithText(warningTitle).performClick()
            fun iconTop(name: String): Pair<Int, Int> {
                val image = compose.onNode(isDialog()).captureToImage()
                File(compose.activity.getExternalFilesDir(null), "dialog-$name-$round-$fontScale.png").outputStream().use {
                    image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                val pixels = image.toPixelMap()
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) {
                        val color = pixels[x, y]
                        if (color.red > 0.4f && color.blue > 0.4f && color.green < 0.2f) return y to pixels.height
                    }
                }
                error("The dialog icon must be painted")
            }
            val warning = iconTop("warning")
            compose.runOnIdle {
                showInstall.value = true
                pending.value = zip.toURI().toString()
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(compose.activity.getString(R.string.confirm_installation)).fetchSemanticsNodes().isNotEmpty()
            }
            val install = iconTop("install")
            assertTrue("The warning icon must leave a safe top inset", warning.first >= warning.second * 0.08f)
            assertTrue("A long confirmation must keep the same safe top inset", install.first >= install.second * 0.08f)
            assertEquals("Warning and installation icons must start at the same inset", warning.first.toFloat(), install.first.toFloat(), compose.activity.resources.displayMetrics.density * 4)
        } finally {
            zip.delete()
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
