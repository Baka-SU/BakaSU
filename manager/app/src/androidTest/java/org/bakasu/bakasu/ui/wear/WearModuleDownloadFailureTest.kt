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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import java.util.Locale
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DownloadFailure
import org.bakasu.bakasu.domain.model.DownloadFailureReason
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadPhase
import org.bakasu.bakasu.ui.viewmodel.ModuleDownloadUiState
import org.bakasu.bakasu.ui.wear.component.LocalWearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearTimeOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearModuleDownloadFailureTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var chinese = false
    private val name = mutableStateOf("A long module name")
    private val clockMode = mutableStateOf("time")
    private val download = mutableStateOf(ModuleDownloadUiState(phase = ModuleDownloadPhase.DOWNLOADING, downloadedBytes = 25, totalBytes = 100))

    @Test
    fun failureKeepsIdentityFillsRedRingAndHasNoRetryButton() {
        show()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(500)
        val iconBefore = compose.onNodeWithTag("module-download-icon").fetchSemanticsNode().boundsInRoot
        val nameBefore = compose.onNodeWithText("A long module name").fetchSemanticsNode().boundsInRoot
        save("downloading")
        compose.runOnIdle {
            download.value = download.value.copy(phase = ModuleDownloadPhase.FAILED, failure = DownloadFailure(DownloadFailureReason.TIMEOUT))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        assertEquals(iconBefore, compose.onNodeWithTag("module-download-icon").fetchSemanticsNode().boundsInRoot)
        assertEquals(nameBefore, compose.onNodeWithText("A long module name").fetchSemanticsNode().boundsInRoot)
        val pixels = compose.onNodeWithTag("download-page").captureToImage().toPixelMap()
        var red = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.red > 0.8f && pixel.green < 0.2f && pixel.blue < 0.2f) red++
            }
        }
        assertTrue("The failed progress ring must fill the whole arc, not retain the old percentage: $red", red > pixels.width * pixels.height * 0.055f)
        save("failed")
        compose.onNodeWithTag("download-page").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(iconBefore, compose.onNodeWithTag("module-download-icon").fetchSemanticsNode().boundsInRoot)
        assertEquals(nameBefore, compose.onNodeWithText("A long module name").fetchSemanticsNode().boundsInRoot)
        compose.mainClock.autoAdvance = true
        val reason = compose.activity.getString(R.string.wear_download_error_timeout)
        compose.onNode(hasScrollToIndexAction()).assertDoesNotExist()
        compose.onNodeWithText(reason).assertIsDisplayed()
        val retry = compose.activity.getString(R.string.network_retry)

        compose.onNodeWithText(retry).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).assertDoesNotExist()
        compose.onNodeWithTag("module-download-icon").assertIsDisplayed()
        compose.onNodeWithText("v1").assertDoesNotExist()
        compose.onNodeWithTag("module-download-clock").assertExists()
    }

    @Test
    fun demoOrderKeepsClockForHiddenAndTitlePreferencesAndAllProgressStates() {
        name.value = "microG"
        clockMode.value = "hidden"
        show()
        compose.onNodeWithTag("module-download-clock").assertExists()
        val icon = compose.onNodeWithTag("module-download-icon").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithText(name.value).fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithText(compose.activity.getString(R.string.wear_downloading)).fetchSemanticsNode().boundsInRoot
        val percent = compose.onNodeWithText(compose.activity.getString(R.string.wear_download_percent, 25)).fetchSemanticsNode().boundsInRoot
        assertTrue(icon.bottom < label.top && label.bottom < status.top && status.bottom <= percent.top)
        save("demo")
        compose.runOnIdle {
            clockMode.value = "title"
            download.value = download.value.copy(totalBytes = 0)
        }
        compose.onNodeWithTag("module-download-clock").assertExists()
        save("unknown")
        compose.runOnIdle { download.value = download.value.copy(phase = ModuleDownloadPhase.VERIFYING) }
        compose.onNodeWithText(compose.activity.getString(R.string.wear_verifying_download)).assertIsDisplayed()
        compose.onNodeWithTag("module-download-clock").assertExists()
        save("verifying")
    }

    @Test
    fun chineseDefaultDpiPercentageAndFailureGlyphsAreNotClipped() {
        chinese = true
        name.value = "Hybrid Mount"
        download.value = ModuleDownloadUiState(phase = ModuleDownloadPhase.DOWNLOADING, downloadedBytes = 89, totalBytes = 100)
        show()
        val resources = compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }).resources
        fun check(text: String) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach {
                assertTrue("Text must have its complete measured height: $text", !it.didOverflowHeight)
                assertTrue("Last line must fit: $text", it.getLineBottom(it.lineCount - 1) <= it.size.height)
            }
        }
        check(resources.getString(R.string.wear_downloading))
        check(resources.getString(R.string.wear_download_percent, 89))
        save("chinese-89")
        compose.runOnIdle { download.value = download.value.copy(phase = ModuleDownloadPhase.VERIFYING) }
        check(resources.getString(R.string.wear_verifying_download))
        compose.runOnIdle { download.value = download.value.copy(phase = ModuleDownloadPhase.FAILED, failure = DownloadFailure(DownloadFailureReason.TIMEOUT)) }
        check(resources.getString(R.string.wear_download_error_timeout))
        save("chinese-failure")
    }

    private fun show() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                if (chinese) setLocale(Locale.SIMPLIFIED_CHINESE)
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalResources provides compose.activity.createConfigurationContext(configuration).resources, LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale), LocalWearTimeOptions provides WearTimeOptions(clockMode.value)) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(error = Color.Red)) {
                    Box(Modifier.requiredSize(192.dp).background(MaterialTheme.colorScheme.background).testTag("download-page")) {
                        AppScaffold(timeText = {}) {
                            WearModuleDownloadScreen(name.value, download.value, { download.value.fraction ?: 0f })
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
