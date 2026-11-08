package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadFailure
import org.bakasu.bakasu.domain.model.DownloadFailureReason
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadPhase
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearModuleDownloadFailureTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val download = mutableStateOf(ModuleDownloadUiState(phase = ModuleDownloadPhase.DOWNLOADING, downloadedBytes = 25, totalBytes = 100))
    private var retries = 0

    @Test
    fun failureKeepsIdentityFillsRedRingAndRetriesWithoutLoading() {
        show()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        val iconBefore = compose.onNodeWithTag("module-download-icon").fetchSemanticsNode().boundsInRoot
        val nameBefore = compose.onNodeWithText("Module").fetchSemanticsNode().boundsInRoot
        save("downloading")
        compose.runOnIdle {
            download.value = download.value.copy(phase = ModuleDownloadPhase.FAILED, failure = DownloadFailure(DownloadFailureReason.TIMEOUT))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(iconBefore, compose.onNodeWithTag("module-download-icon").fetchSemanticsNode().boundsInRoot)
        assertEquals(nameBefore, compose.onNodeWithText("Module").fetchSemanticsNode().boundsInRoot)
        val pixels = compose.onNodeWithTag("download-page").captureToImage().toPixelMap()
        var red = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.red > 0.8f && pixel.green < 0.2f && pixel.blue < 0.2f) red++
            }
        }
        assertTrue("The failed progress ring must fill the whole arc, not retain the old percentage: $red", red > pixels.width * pixels.height * 0.08f)
        save("failed")
        compose.mainClock.autoAdvance = true
        val reason = compose.activity.getString(R.string.wear_download_error_timeout)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(reason))
        compose.onNodeWithText(reason).assertIsDisplayed()
        val retry = compose.activity.getString(R.string.network_retry)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(retry))
        compose.onNodeWithText(retry).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithText(reason).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithTag("module-download-icon").assertIsDisplayed()
        save("retry")
    }

    private fun show() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(error = Color.Red)) {
                    Box(Modifier.requiredSize(240.dp).background(MaterialTheme.colorScheme.background).testTag("download-page")) {
                        AppScaffold(timeText = {}) {
                            WearModuleDownloadScreen("Module", "v1", download.value, { download.value.fraction ?: 0f }) {
                                retries++
                                download.value = ModuleDownloadUiState(phase = ModuleDownloadPhase.DOWNLOADING)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun save(stage: String) {
        val bitmap = compose.onNodeWithTag("download-page").captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "download-$stage-$round-$fontScale.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
