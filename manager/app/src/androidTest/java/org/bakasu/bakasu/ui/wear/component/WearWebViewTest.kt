package org.bakasu.bakasu.ui.wear.component

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwipeToDismissBox
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.bakasu.bakasu.R
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearWebViewTest(private val round: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var webView: WebView
    private var backCount = 0

    @After
    fun dispose() {
        if (::webView.isInitialized) {
            compose.runOnIdle {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        }
    }

    @Test
    fun pageAndControlsStayVisibleAndScalingKeepsClicksAligned() {
        showPage()
        val viewport = compose.runOnIdle { webView.parent as View }
        val hostBounds = compose.onNodeWithTag("web-host").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertEquals(hostBounds.width, viewport.width.toFloat(), 1f)
            assertEquals(hostBounds.height, viewport.height.toFloat(), 1f)
            assertEquals(0.8f, webView.scaleX, 0.001f)
        }
        assertPageClearOfControls()
        listOf("tl", "tr", "bl", "br").forEach { clickPageButton(it) }
        js("clicks = []")
        val initialWidth = js("innerWidth").toInt()
        screenshot("default")
        clickPageButton("center")
        assertEquals("[\"center\"]", js("clicks"))

        control(R.string.wear_web_tools).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_web_scale, 80)).performClick()
        control(R.string.close).performClick()
        compose.waitUntil(10_000) { js("innerWidth").toInt() < initialWidth }
        assertPageClearOfControls()
        clickPageButton("center")
        assertEquals("[\"center\",\"center\"]", js("clicks"))
        compose.runOnIdle { assertEquals(1f, webView.scaleX, 0.001f) }

        control(R.string.wear_web_tools).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_web_scale, 100)).performClick()
        control(R.string.close).performClick()
        compose.waitUntil(10_000) { js("innerWidth").toInt() > initialWidth }
        assertPageClearOfControls()
        clickPageButton("center")
        assertEquals("[\"center\",\"center\",\"center\"]", js("clicks"))
        screenshot("small")
    }

    @Test
    fun navigationPageKeepsItsViewportAndDraggingDoesNotClickThrough() {
        showPage("wear-webview-navigation.html")
        val viewport = compose.runOnIdle { webView.parent as View }
        compose.runOnIdle {
            assertTrue(webView.height * webView.scaleY > viewport.height / 2f)
        }
        assertPageClearOfControls()
        screenshot("navigation-viewport")
        listOf("status", "config", "modules", "info").forEach { clickPageButton(it) }
        val initial = compose.runOnIdle { Offset(webView.translationX, webView.translationY) }
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(center, center - Offset(width * 0.07f, height * 0.07f), 500)
        }
        compose.runOnIdle {
            assertEquals(0, backCount)
        }
        assertEquals("A drag must not click the navigation", 4, js("clicks.length").toInt())
        assertPageClearOfControls()
        screenshot("navigation-dragged")
        clickPageButton("modules")
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(center, center + Offset(0f, height * 0.07f), 500)
        }
        assertEquals("A vertical drag must not click the navigation", 5, js("clicks.length").toInt())
        assertPageClearOfControls()
        control(R.string.wear_web_tools).performClick()
        control(R.string.wear_web_reset).performClick()
        compose.runOnIdle {
            assertEquals(initial.x, webView.translationX, 1f)
            assertEquals(initial.y, webView.translationY, 1f)
        }
    }

    @Test
    fun viewportHeightPageKeepsItsContentAboveNavigation() {
        showPage("wear-webview-viewport.html")
        assertTrue(js("document.querySelector('main').getBoundingClientRect().height").toDouble() > 40)
        clickPageButton("status")
        clickPageButton("config")
        assertEquals("[\"status\",\"config\"]", js("clicks"))
        assertPageClearOfControls()
        screenshot("viewport-content")
    }

    @Test
    fun controlsFitTheRoundDisplayAndDoNotClickThrough() {
        showPage()
        control(R.string.wear_web_tools).performClick()
        listOf(R.string.back, R.string.wear_web_reset, R.string.close).forEach { resource ->
            val bounds = control(resource).fetchSemanticsNode().boundsInRoot
            assertInsideDisplay(bounds.topLeft)
            assertInsideDisplay(bounds.topRight)
            assertInsideDisplay(bounds.bottomLeft)
            assertInsideDisplay(bounds.bottomRight)
        }
        screenshot("controls")
        compose.onNodeWithTag("web-host").performTouchInput { click(Offset(width / 2f, height * 0.1f)) }
        assertEquals("[]", js("clicks"))
        control(R.string.close).performTouchInput { click() }
        control(R.string.wear_web_tools).assertExists()
    }

    @Test
    fun ordinaryScrollingAndBackRemainAvailable() {
        showPage()
        val initial = compose.runOnIdle { Offset(webView.translationX, webView.translationY) }
        compose.onNodeWithTag("web-host").performTouchInput {
            // Stay on the page, clear of the bottom-center native controls even on a 192dp watch.
            swipe(Offset(width * 0.25f, height * 0.55f), Offset(width * 0.25f, height * 0.25f), 500)
        }
        compose.waitUntil(10_000) { js("scrollY").toDouble() > 20 }
        compose.runOnIdle {
            assertEquals(initial.x, webView.translationX, 0.01f)
            assertEquals(initial.y, webView.translationY, 0.01f)
            assertEquals(0, backCount)
        }
        control(R.string.wear_web_tools).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        control(R.string.close).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, backCount) }
        control(R.string.wear_web_tools).performClick()
        control(R.string.back).performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPage(asset: String = "wear-webview.html") {
        compose.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
            webView = WebView(compose.activity).apply { settings.javaScriptEnabled = true }
        }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        // Exercise the touch-driven parent as well, including on API 36+ emulators.
                        SwipeToDismissBox(onDismissed = { backCount++ }) { background ->
                            if (!background) {
                                Box(Modifier.fillMaxSize().clip(if (round) CircleShape else RectangleShape).testTag("web-host")) {
                                    WearWebView(webView, onBack = { backCount++ })
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.runOnIdle { webView.width > 0 } }
        val html = InstrumentationRegistry.getInstrumentation().context.assets.open(asset).bufferedReader().use { it.readText() }
        compose.runOnIdle { webView.loadDataWithBaseURL("https://mui.kernelsu.org/", html, "text/html", "UTF-8", null) }
        compose.waitUntil(15_000) { js("typeof clicks").contains("object") }
        awaitWebFrame()
        compose.waitUntil(10_000) { compose.runOnIdle { webView.hasWindowFocus() && (asset != "wear-webview.html" || webView.canScrollVertically(1)) } }
    }

    private fun control(resource: Int) = compose.onNodeWithContentDescription(compose.activity.getString(resource))

    private fun assertPageClearOfControls() {
        val tools = control(R.string.wear_web_tools).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val left = webView.translationX
            val top = webView.translationY
            val right = left + webView.width * webView.scaleX
            val bottom = top + webView.height * webView.scaleY
            assertTrue("Page content must not overlap the controls", bottom < tools.top)
            listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach { assertInsideDisplay(it) }
        }
    }

    private fun assertInsideDisplay(point: Offset) {
        val viewport = webView.parent as View
        if (round) {
            val center = Offset(viewport.width / 2f, viewport.height / 2f)
            assertTrue("$point must fit inside the round screen", (point - center).getDistance() <= minOf(viewport.width, viewport.height) / 2f + 1f)
        } else {
            assertTrue(point.x >= -1 && point.x <= viewport.width + 1 && point.y >= -1 && point.y <= viewport.height + 1)
        }
    }

    private fun js(script: String): String {
        val result = AtomicReference<String>()
        val ready = CountDownLatch(1)
        compose.runOnIdle {
            webView.evaluateJavascript(script) {
                result.set(it)
                ready.countDown()
            }
        }
        assertTrue("WebView JS did not respond", ready.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun clickPageButton(id: String) {
        awaitWebFrame()
        val previousClicks = js("clicks.length").toInt()
        val rect = JSONObject(js("(() => { const r = document.getElementById('$id').getBoundingClientRect(); return {x:r.x+r.width/2,y:r.y+r.height/2,d:devicePixelRatio}; })()"))
        val point = compose.runOnIdle {
            val factor = rect.getDouble("d").toFloat() * webView.scaleX
            Offset(rect.getDouble("x").toFloat() * factor + webView.translationX, rect.getDouble("y").toFloat() * factor + webView.translationY)
        }
        if (round) {
            val viewport = compose.runOnIdle { webView.parent as View }
            val center = Offset(viewport.width / 2f, viewport.height / 2f)
            assertTrue("The button must be inside the round display", (point - center).getDistance() < minOf(viewport.width, viewport.height) / 2f)
        }
        compose.onNodeWithTag("web-host").performTouchInput { click(point) }
        // Compose idling does not wait for Chromium to process the native touch event.
        compose.waitUntil(5_000) { js("clicks.length").toInt() == previousClicks + 1 }
    }

    private fun awaitWebFrame() {
        val ready = CountDownLatch(1)
        compose.runOnIdle {
            webView.postVisualStateCallback(
                0,
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        // The callback guarantees the next draw, not the frame currently displayed.
                        webView.postOnAnimation { webView.postOnAnimation { ready.countDown() } }
                    }
                },
            )
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        awaitWebFrame()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertFalse(bitmap == null)
        val file = File(compose.activity.getExternalFilesDir(null), "webview-${if (round) "round" else "square"}-$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun parameters() = listOf(arrayOf(true), arrayOf(false))
    }
}
