package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.io.File
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRefreshSurfaceTest(private val round: Boolean, private val fontScale: Float, private val kind: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshKeepsSurfacesVisibleAndOutsideTheIndicatorRegion() {
        var refreshing by mutableStateOf(false)
        var clicks = 0
        var pixelsPerDp = 1f
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            pixelsPerDp = LocalDensity.current.density
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(pixelsPerDp, fontScale),
                LocalWearTimeOptions provides WearTimeOptions("hidden"),
            ) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surfaceContainer = Color.Red)) {
                    AppScaffold(timeText = {}) {
                        if (kind == "radio") {
                            WearChoicePage(
                                title = stringResource(R.string.about),
                                choices = listOf("choice" to stringResource(R.string.readme)),
                                selected = "",
                                onBack = {},
                                isRefreshing = refreshing,
                                onChoose = { clicks++ },
                            )
                        } else {
                            WearList(pageTitle = stringResource(R.string.about), isRefreshing = refreshing) { spec ->
                                item {
                                    when (kind) {
                                        "card" -> WearModuleInfoCard(spec) { Text(stringResource(R.string.readme)) }
                                        "button" -> WearChip(spec, stringResource(R.string.readme), onClick = { clicks++ })
                                        "switch" -> WearSettingsSwitchWidget(spec, stringResource(R.string.readme), false, { clicks++ })
                                        "jump" -> WearSettingsJumpPageWidget(spec, stringResource(R.string.readme), { clicks++ })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun paintedBounds(stage: String): IntRect {
            val image = compose.onRoot().captureToImage()
            val pixels = image.toPixelMap()
            var top = pixels.height
            var bottom = -1
            var left = pixels.width
            var right = -1
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red > 0.5f && color.green < 0.1f && color.blue < 0.1f) {
                        top = minOf(top, y)
                        bottom = maxOf(bottom, y)
                        left = minOf(left, x)
                        right = maxOf(right, x)
                    }
                }
            }
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "refresh-surface-$kind-$round-$fontScale-$stage.png").outputStream().use {
                image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue("The $kind surface must be painted during $stage", bottom >= top)
            return IntRect(left, top, right + 1, bottom + 1)
        }

        val before = paintedBounds("idle")
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { refreshing = true }
            compose.mainClock.advanceTimeBy(80)
            val entering = paintedBounds("entering")
            val enteringIndicator = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).fetchSemanticsNode().boundsInRoot
            compose.mainClock.advanceTimeBy(300)
            val settled = paintedBounds("refresh")
            val settledIndicator = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).fetchSemanticsNode().boundsInRoot
            for ((stage, bounds, indicator) in listOf(Triple("entering", entering, enteringIndicator), Triple("refresh", settled, settledIndicator))) {
                if (round) {
                    // The smaller viewport changes the official round-screen transformation. Both
                    // axes must follow it, rather than cutting off only the top/bottom of a surface.
                    assertEquals("The $kind surface must transform without clipping during $stage", bounds.width.toFloat() / before.width, bounds.height.toFloat() / before.height, 0.04f)
                } else {
                    assertEquals("The $kind surface must retain its full painted height during $stage", before.height.toFloat(), bounds.height.toFloat(), 2f)
                    assertEquals("The $kind surface must retain its full painted width during $stage", before.width.toFloat(), bounds.width.toFloat(), 2f)
                }
                assertTrue("The $kind surface must not paint over the indicator during $stage", bounds.top >= indicator.bottom - 2f)
            }
            assertTrue("The entire surface must move down over multiple frames", before.top < entering.top && entering.top < settled.top)
            if (kind != "card") {
                compose.onRoot().performTouchInput { click(Offset(settled.center.x.toFloat(), settled.center.y.toFloat())) }
                compose.runOnIdle { assertEquals("The translated surface must remain tappable", 1, clicks) }
            }
            compose.runOnIdle { refreshing = false }
            compose.mainClock.advanceTimeBy(80)
            val leaving = paintedBounds("leaving")
            if (!round) {
                assertEquals("The full surface must remain painted while returning", before.height.toFloat(), leaving.height.toFloat(), 2f)
                assertEquals("The surface must keep its width while returning", before.width.toFloat(), leaving.width.toFloat(), 2f)
            }
            compose.mainClock.advanceTimeBy(300)
            val after = paintedBounds("completion")
            assertEquals(before.top.toFloat(), after.top.toFloat(), 2f)
            assertEquals(before.bottom.toFloat(), after.bottom.toFloat(), 2f)
        } finally {
            compose.runOnIdle { refreshing = false }
            compose.mainClock.autoAdvance = true
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}, kind={2}")
        fun configurations() = listOf(true, false).flatMap { round ->
            listOf(1f, 1.3f).flatMap { scale -> listOf("card", "button", "switch", "jump", "radio").map { arrayOf<Any>(round, scale, it) } }
        }
    }
}
