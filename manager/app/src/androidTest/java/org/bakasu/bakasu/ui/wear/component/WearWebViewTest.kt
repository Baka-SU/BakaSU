package org.bakasu.bakasu.ui.wear.component

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
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
    fun pageUsesAvailableHeightAndScalingKeepsClicksAligned() {
        showPage()
        val viewport = compose.runOnIdle { webView.parent as View }
        val hostBounds = compose.onNodeWithTag("web-host").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertEquals(hostBounds.width, viewport.width.toFloat(), 1f)
            assertEquals(hostBounds.height, viewport.height.toFloat(), 1f)
            assertEquals(0.8f, webView.scaleX, 0.001f)
        }
        assertPageFitsViewport()
        listOf("tl", "tr", "bl", "br").forEach { clickPageButton(it) }
        js("clicks = []")
        val initialWidth = js("innerWidth").toInt()
        screenshot("default")
        clickPageButton("center")
        assertEquals("[\"center\"]", js("clicks"))

        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_scale, 80))
        closeTools()
        compose.waitUntil(10_000) { js("innerWidth").toInt() < initialWidth }
        assertPageFitsViewport()
        clickPageButton("center")
        assertEquals("[\"center\",\"center\"]", js("clicks"))
        compose.runOnIdle { assertEquals(1f, webView.scaleX, 0.001f) }

        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_scale, 100))
        closeTools()
        compose.waitUntil(10_000) { js("innerWidth").toInt() > initialWidth }
        assertPageFitsViewport()
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
        assertPageFitsViewport()
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
        assertPageFitsViewport()
        screenshot("navigation-dragged")
        clickPageButton("modules")
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(center, center + Offset(0f, height * 0.07f), 500)
        }
        assertEquals("A vertical drag must not click the navigation", 5, js("clicks.length").toInt())
        assertPageFitsViewport()
        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_reset))
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
        assertPageFitsViewport()
        screenshot("viewport-content")
    }

    @Test
    fun menuActionsScrollIntoViewAndDoNotClickThrough() {
        showPage()
        openTools()
        listOf(R.string.back, R.string.wear_web_reset, R.string.close).forEach { resource ->
            if (resource == R.string.close) {
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
            } else {
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(compose.activity.getString(resource)))
            }
            val label = compose.onNodeWithText(compose.activity.getString(resource), useUnmergedTree = true)
            label.assertIsDisplayed()
            val bounds = label.fetchSemanticsNode().boundsInRoot
            val origin = compose.onNodeWithTag("web-host").fetchSemanticsNode().boundsInRoot.topLeft
            assertInsideDisplay(bounds.center - origin)
        }
        screenshot("controls")
        compose.onNodeWithText(compose.activity.getString(R.string.wear_web_scale, 80)).performTouchInput { click() }
        assertEquals("[]", js("clicks"))
        closeTools()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_web_tools)).assertDoesNotExist()
    }

    @Test
    fun ordinaryScrollingAndBackRemainAvailable() {
        showPage()
        val initial = compose.runOnIdle { Offset(webView.translationX, webView.translationY) }
        compose.onNodeWithTag("web-host").performTouchInput {
            // Scroll within the native viewport, including on a 192dp watch.
            swipe(Offset(width * 0.25f, height * 0.55f), Offset(width * 0.25f, height * 0.25f), 500)
        }
        compose.waitUntil(10_000) { js("scrollY").toDouble() > 20 }
        compose.runOnIdle {
            assertEquals(initial.x, webView.translationX, 0.01f)
            assertEquals(initial.y, webView.translationY, 0.01f)
            assertEquals(0, backCount)
        }
        openTools()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        control(R.string.close).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, backCount) }
        openTools()
        control(R.string.back).performClick()
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun boundaryPullOpensToolsWithoutClickingThePage() {
        showPage("wear-webview-navigation.html")
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(Offset(centerX, height * 0.3f), Offset(centerX, height * 0.85f), 500)
        }
        control(R.string.wear_web_tools).assertExists()
        assertEquals("[]", js("clicks"))
        val sameWebView = webView
        closeTools()
        compose.runOnIdle { assertTrue(sameWebView === webView && webView.parent != null) }
        clickPageButton("modules")
        assertEquals("[\"modules\"]", js("clicks"))
    }

    @Test
    fun shortAndReversedPullsDoNotOpenTools() {
        showPage("wear-webview-navigation.html")
        compose.onNodeWithTag("web-host").performTouchInput {
            down(Offset(centerX, height * 0.3f))
            moveBy(Offset(0f, height * 0.08f), delayMillis = 200)
            up()
        }
        control(R.string.wear_web_tools).assertDoesNotExist()
        compose.onNodeWithTag("web-host").performTouchInput {
            down(Offset(centerX, height * 0.3f))
            moveTo(Offset(centerX, height * 0.8f), delayMillis = 300)
            moveTo(Offset(centerX, height * 0.35f), delayMillis = 300)
            up()
        }
        control(R.string.wear_web_tools).assertDoesNotExist()
        assertEquals("[]", js("clicks"))
    }

    @Test
    fun crownReturnsToTheSamePageAfterClosingTools() {
        showPage()
        rotateCrown(64f)
        compose.waitUntil(5_000) { js("scrollY").toDouble() > 20 }
        val before = js("scrollY").toDouble()
        openTools()
        rotateCrown(64f)
        assertEquals(before, js("scrollY").toDouble(), 1.0)
        closeTools()
        rotateCrown(64f)
        compose.waitUntil(5_000) { js("scrollY").toDouble() > before + 20 }
    }

    @Test
    fun twoFingerGestureDoesNotOpenToolsOrClickThrough() {
        showPage("wear-webview-navigation.html")
        compose.onNodeWithTag("web-host").performTouchInput {
            down(0, Offset(centerX - width * 0.1f, centerY))
            down(1, Offset(centerX + width * 0.1f, centerY))
            moveTo(0, Offset(centerX - width * 0.2f, height * 0.75f), delayMillis = 200)
            moveTo(1, Offset(centerX + width * 0.2f, height * 0.75f), delayMillis = 200)
            up(0)
            up(1)
        }
        control(R.string.wear_web_tools).assertDoesNotExist()
        assertEquals("[]", js("clicks"))
    }

    private fun rotateCrown(distanceDp: Float) {
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            val coordinates = MotionEvent.PointerCoords().apply {
                setAxisValue(MotionEvent.AXIS_SCROLL, -distanceDp * compose.activity.resources.displayMetrics.density / ViewConfiguration.get(compose.activity).scaledVerticalScrollFactor)
            }
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(MotionEvent.PointerProperties().apply { id = 0 }), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0)
            try {
                compose.activity.dispatchGenericMotionEvent(event)
            } finally {
                event.recycle()
            }
        }
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
                screenWidthDp = 192
                screenHeightDp = 192
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        // Exercise the touch-driven parent as well, including on API 36+ emulators.
                        SwipeToDismissBox(onDismissed = { backCount++ }) { background ->
                            if (!background) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(192.dp).clip(if (round) CircleShape else RectangleShape).testTag("web-host")) {
                                        WearWebView(webView, onBack = { backCount++ })
                                    }
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

    private fun control(resource: Int) = compose.onNodeWithText(compose.activity.getString(resource))

    private fun clickTool(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed().performClick()
    }

    private fun closeTools() {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        control(R.string.close).assertIsDisplayed().performClick()
    }

    private fun openTools() {
        val actions = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == compose.activity.getString(R.string.wear_web_tools) }.action() }
    }

    private fun assertPageFitsViewport() {
        compose.runOnIdle {
            val left = webView.translationX
            val top = webView.translationY
            val right = left + webView.width * webView.scaleX
            val bottom = top + webView.height * webView.scaleY
            val viewport = webView.parent as View
            val expectedHeight = if (round) viewport.height - minOf(viewport.width, viewport.height) * (1f - 1f / kotlin.math.sqrt(2f)) else viewport.height.toFloat()
            assertEquals("No space is reserved for tools", expectedHeight, bottom - top, 1f)
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
