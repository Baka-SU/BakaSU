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
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
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
    private val hapticEvents = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            hapticEvents += hapticFeedbackType
        }
    }

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
            assertEquals(0.6f, webView.scaleX, 0.001f)
        }
        assertPageFitsViewport()
        listOf("tl", "tr", "bl", "br").forEach { id ->
            if (round) {
                openTools()
                clickTool(compose.activity.getString(R.string.wear_web_reset))
                val dx = if (id.endsWith("l")) 1f else -1f
                val dy = if (id.startsWith("t")) 1f else -1f
                peekCorner(dx, dy)
            }
            clickPageButton(id)
        }
        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_reset))
        js("clicks = []")
        val initialWidth = js("innerWidth").toInt()
        screenshot("default")
        clickPageButton("center")
        assertEquals("[\"center\"]", js("clicks"))

        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_scale, 100))
        closeTools()
        compose.waitUntil(10_000) { js("innerWidth").toInt() < initialWidth }
        assertPageFitsViewport()
        clickPageButton("center")
        assertEquals("[\"center\",\"center\"]", js("clicks"))
        compose.runOnIdle { assertEquals(0.8f, webView.scaleX, 0.001f) }

        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_scale, 133))
        closeTools()
        compose.waitUntil(10_000) { js("innerWidth").toInt() < initialWidth }
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
        listOf("status", "config", "modules", "info").forEach { id ->
            if (round && id in listOf("status", "info")) {
                openTools()
                clickTool(compose.activity.getString(R.string.wear_web_reset))
                val direction = if (id == "status") 1f else -1f
                peekCorner(direction, 1f)
            }
            clickPageButton(id)
        }
        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_reset))
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
        // Chromium may pan its visual viewport independently of document.scrollTop at the
        // smaller whole-page scale. Scroll it back normally before tapping the hidden navigation.
        compose.onNodeWithTag("web-host").performTouchInput { down(center) }
        awaitWebFrame()
        compose.onNodeWithTag("web-host").performTouchInput {
            moveTo(Offset(centerX, height * 0.85f), delayMillis = 500)
            up()
        }
        compose.waitUntil(5_000) { js("visualViewport.offsetTop").toDouble() <= 1 }
        control(R.string.wear_web_tools).assertDoesNotExist()
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
        if (round) peekCorner(1f, 1f)
        clickPageButton("status")
        if (round) {
            openTools()
            clickTool(compose.activity.getString(R.string.wear_web_reset))
            peekCorner(1f, -1f)
        }
        clickPageButton("config")
        assertEquals("[\"status\",\"config\"]", js("clicks"))
        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_reset))
        assertPageFitsViewport()
        screenshot("viewport-content")
    }

    @Test
    fun menuActionsScrollIntoViewAndDoNotClickThrough() {
        showPage()
        openTools()
        control(R.string.back).assertDoesNotExist()
        control(R.string.close).assertDoesNotExist()
        listOf(compose.activity.getString(R.string.wear_web_reset), compose.activity.getString(R.string.wear_web_scale, 100), compose.activity.getString(R.string.wear_web_text_scale, 100)).forEach { text ->
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
            val label = compose.onNodeWithText(text, useUnmergedTree = true)
            label.assertIsDisplayed()
            val bounds = label.fetchSemanticsNode().boundsInRoot
            val origin = compose.onNodeWithTag("web-host").fetchSemanticsNode().boundsInRoot.topLeft
            assertInsideDisplay(bounds.center - origin)
        }
        screenshot("controls")
        compose.onNodeWithText(compose.activity.getString(R.string.wear_web_scale, 100)).performTouchInput { click() }
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
        closeTools()
        compose.runOnIdle { assertEquals(0, backCount) }
    }

    @Test
    fun boundaryPullOpensToolsWithoutClickingThePage() {
        showPage("wear-webview-navigation.html")
        compose.onNodeWithTag("web-host").performTouchInput { down(Offset(centerX, height * 0.3f)) }
        awaitWebFrame()
        compose.onNodeWithTag("web-host").performTouchInput {
            moveTo(Offset(centerX, height * 0.85f), delayMillis = 500)
            up()
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
        awaitStableScroll()
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

    @Test
    fun nestedTouchScrollProbe() {
        showOverflowPage()
        val before = overflowState()
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(Offset(width * 0.4f, height * 0.55f), Offset(width * 0.4f, height * 0.25f), 500)
        }
        compose.waitUntil(5_000) { js("document.querySelector('main').scrollTop").toDouble() > 20 }
        awaitWebFrame()
        screenshot("probe-nested-touch")
        val after = overflowState()
        assertTrue("Nested touch must scroll: before=$before after=$after", js("document.querySelector('main').scrollTop").toDouble() > 20)
    }

    @Test
    fun nestedCrownScrollProbe() {
        showOverflowPage()
        val before = overflowState()
        rotateCrown(80f)
        awaitWebFrame()
        val after = overflowState()
        assertTrue("Nested crown must scroll: before=$before after=$after", js("document.querySelector('main').scrollTop").toDouble() > 20)
    }

    @Test
    fun mainWebpageSystemBackCancelsOrExitsThePage() {
        showPage("wear-webview-navigation.html")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, 0.6f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.runOnIdle { assertEquals(0, backCount) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 100f, 1f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, backCount) }
    }

    @Test
    fun mainWebpageEdgeSwipeReturnsWithoutClickingItsContent() {
        showPage("wear-webview-navigation.html")
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(Offset(1f, centerY), Offset(width * 0.94f, centerY), 500)
        }
        compose.runOnIdle { assertEquals(1, backCount) }
        assertEquals("[]", js("clicks"))
    }

    @Test
    fun diagonalDragIsDampedAndEachLimitCrossingGivesOneFeedback() {
        if (!round) return
        showPage("wear-webview-navigation.html")
        val viewport = compose.runOnIdle { webView.parent as View }
        for ((dx, dy) in listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f)) {
            openTools()
            clickTool(compose.activity.getString(R.string.wear_web_reset))
            compose.runOnIdle { hapticEvents.clear() }
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithTag("web-host").performTouchInput {
                    down(center - Offset(width * 0.32f * dx, height * 0.32f * dy))
                    moveTo(center - Offset(width * 0.07f * dx, height * 0.07f * dy), delayMillis = 300)
                }
                compose.runOnIdle {
                    assertEquals(viewport.width * 0.15f * dx, webView.translationX, 2f)
                    assertEquals(viewport.height * 0.15f * dy, webView.translationY, 2f)
                    assertTrue(hapticEvents.isEmpty())
                }
                compose.onNodeWithTag("web-host").performTouchInput {
                    moveTo(center + Offset(width * 0.14f * dx, height * 0.14f * dy), delayMillis = 300)
                    moveTo(center + Offset(width * 0.16f * dx, height * 0.16f * dy), delayMillis = 100)
                }
                compose.mainClock.advanceTimeBy(32)
                compose.runOnIdle { assertEquals(1, hapticEvents.size) }
                screenshot("corner-limit-$dx-$dy")
                compose.onNodeWithTag("web-host").performTouchInput { up() }
            } finally {
                compose.mainClock.autoAdvance = true
            }
            awaitPosition(viewport.width / 4f * dx, viewport.height / 4f * dy)
            compose.runOnIdle { assertEquals(1, hapticEvents.size) }
        }
    }

    @Test
    fun menuIgnoresPredictiveBackAndUpwardClosePreservesThePage() {
        showPage()
        rotateCrown(64f)
        awaitStableScroll()
        val scroll = js("scrollY").toDouble()
        compose.onNodeWithTag("wear-menu-hint").assertExists()
        openTools()
        compose.onNodeWithTag("wear-menu-hint").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, 0.6f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        control(R.string.wear_web_tools).assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 100f, 1f, BackEventCompat.EDGE_LEFT)) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        control(R.string.wear_web_tools).assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.15f), 500)
        }
        control(R.string.wear_web_tools).assertDoesNotExist()
        compose.onNodeWithTag("wear-menu-hint").assertExists()
        assertEquals(scroll, js("scrollY").toDouble(), 1.0)
        assertEquals(0, backCount)
    }

    @Test
    fun fontSizeRemainsIndependentOfTheNewPageScaleAndReset() {
        showPage()
        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_text_scale, 100))
        clickTool(compose.activity.getString(R.string.wear_web_scale, 100))
        compose.runOnIdle {
            assertEquals(125, webView.settings.textZoom)
            assertEquals(0.8f, webView.scaleX, 0.001f)
        }
        clickTool(compose.activity.getString(R.string.wear_web_reset))
        compose.runOnIdle {
            assertEquals(125, webView.settings.textZoom)
            assertEquals(0.6f, webView.scaleX, 0.001f)
        }
    }

    @Test
    fun crownAtTopDoesNotOpenTools() {
        showPage("wear-webview-navigation.html")
        repeat(3) { rotateCrown(-200f) }
        compose.waitForIdle()
        control(R.string.wear_web_tools).assertDoesNotExist()
    }

    @Test
    fun menuPreviewDuringDragProbe() {
        showPage("wear-webview-navigation.html")
        val before = compose.onNodeWithTag("web-host").captureToImage().toPixelMap()
        compose.onNodeWithTag("web-host").performTouchInput {
            down(Offset(centerX, height * 0.30f))
        }
        awaitWebFrame()
        compose.onNodeWithTag("web-host").performTouchInput {
            moveTo(Offset(centerX, height * 0.65f), delayMillis = 300)
        }
        try {
            screenshot("probe-menu-mid-drag")
            val during = compose.onNodeWithTag("web-host").captureToImage().toPixelMap()
            var changed = 0
            for (y in 0 until before.height) {
                for (x in 0 until before.width) {
                    if (kotlin.math.abs(before[x, y].red - during[x, y].red) > 0.1f) changed++
                }
            }
            assertTrue("The menu and scrim must be drawn before release", changed > before.width * before.height / 5)
        } finally {
            compose.onNodeWithTag("web-host").performTouchInput { up() }
        }
    }

    @Test
    fun nativeWheelNestedScrollControl() {
        showOverflowPage()
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            val coordinates = MotionEvent.PointerCoords().apply {
                x = webView.width * 0.4f
                y = webView.height * 0.4f
                setAxisValue(MotionEvent.AXIS_VSCROLL, -3f)
            }
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            try {
                webView.dispatchGenericMotionEvent(event)
            } finally {
                event.recycle()
            }
        }
        compose.waitUntil(5_000) { js("document.querySelector('main').scrollTop").toDouble() > 20 }
        println("NATIVE_WHEEL_CONTROL ${overflowState()}")
    }

    @Test
    fun directWebViewTouchControl() {
        showOverflowPage()
        val now = SystemClock.uptimeMillis()
        for (step in 0..12) {
            compose.runOnIdle {
                val action = when (step) {
                    0 -> MotionEvent.ACTION_DOWN
                    12 -> MotionEvent.ACTION_UP
                    else -> MotionEvent.ACTION_MOVE
                }
                val event = MotionEvent.obtain(now, now + step * 40L, action, webView.width * 0.4f, webView.height * (0.65f - step * 0.03f), 0)
                try {
                    webView.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
        }
        compose.waitUntil(5_000) { js("document.querySelector('main').scrollTop").toDouble() > 20 }
        println("DIRECT_TOUCH_CONTROL ${overflowState()}")
    }

    private fun showOverflowPage() {
        showPage("wear-webview-viewport.html")
        js("document.querySelector('main').innerHTML = Array.from({length:30}, (_,i) => '<p style=\"height:40px;margin:0\">Row '+i+'</p>').join('')")
        awaitWebFrame()
        assertTrue(js("document.querySelector('main').scrollHeight > document.querySelector('main').clientHeight").toBoolean())
        compose.runOnIdle {
            assertFalse("Fixture root must not scroll", webView.canScrollVertically(1))
            assertFalse(webView.canScrollVertically(-1))
        }
    }

    private fun overflowState(): String = js("JSON.stringify({root:scrollY, inner:document.querySelector('main').scrollTop, height:document.querySelector('main').clientHeight, content:document.querySelector('main').scrollHeight})")

    @Test
    fun pannedAndScaledOverflowContentKeepsScrollingAndMenuDoesNotResetIt() {
        showOverflowPage()
        if (round) {
            peekCorner(1f, 1f)
        }
        val pan = compose.runOnIdle { Offset(webView.translationX, webView.translationY) }
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(Offset(width * 0.55f, height * 0.65f), Offset(width * 0.57f, height * 0.4f), 500)
        }
        compose.waitUntil(5_000) { js("document.querySelector('main').scrollTop").toDouble() > 20 }
        compose.runOnIdle {
            assertEquals(pan.x, webView.translationX, 1f)
            assertEquals(pan.y, webView.translationY, 1f)
        }
        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_scale, 100))
        closeTools()
        awaitWebFrame()
        compose.runOnIdle {
            assertEquals(pan.x, webView.translationX, 1f)
            assertEquals(pan.y, webView.translationY, 1f)
        }
        val before = js("document.querySelector('main').scrollTop").toDouble()
        // Resizing may leave Chromium at the bottom; turn towards available content.
        val direction = if (before > 40) -1f else 1f
        rotateCrown(64f * direction)
        compose.waitUntil(5_000) { (js("document.querySelector('main').scrollTop").toDouble() - before) * direction > 20 }
        openTools()
        clickTool(compose.activity.getString(R.string.wear_web_reset))
        compose.runOnIdle {
            assertEquals(0f, webView.translationX, 1f)
            assertEquals(0f, webView.translationY, 1f)
            assertEquals(0.6f, webView.scaleX, 0.001f)
        }
    }

    @Test
    fun cornersSnapAndReverseToDefaultWithoutFreeDragging() {
        showPage("wear-webview-navigation.html")
        listOf(1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f).forEach { (dx, dy) ->
            peekCorner(dx, dy)
            val viewport = compose.runOnIdle { webView.parent as View }
            val expectedX = if (round) viewport.width / 4f * dx else 0f
            val expectedY = if (round) viewport.height / 4f * dy else 0f
            compose.runOnIdle {
                assertEquals(expectedX, webView.translationX, 1f)
                assertEquals(expectedY, webView.translationY, 1f)
            }
            openTools()
            closeTools()
            compose.runOnIdle {
                assertEquals(expectedX, webView.translationX, 1f)
                assertEquals(expectedY, webView.translationY, 1f)
            }
            compose.onNodeWithTag("web-host").performTouchInput {
                swipe(center, center - Offset(width * 0.3f * dx, height * 0.3f * dy), 600)
            }
            awaitPosition(0f, 0f)
        }
        // A diagonal starting in the middle, horizontal drag, and short corner pull stay on-page.
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(center, center + Offset(width * 0.25f, height * 0.25f), 500)
            swipe(center, center + Offset(width * 0.3f, 0f), 500)
            swipe(Offset(width * 0.18f, height * 0.18f), Offset(width * 0.24f, height * 0.24f), 500)
        }
        awaitPosition(0f, 0f)
        control(R.string.wear_web_tools).assertDoesNotExist()
    }

    @Test
    fun nestedDownwardAndDiagonalScrollingDoesNotOpenTools() {
        showOverflowPage()
        js("document.querySelector('main').scrollTop = 400")
        awaitWebFrame()
        compose.onNodeWithTag("web-host").performTouchInput { down(Offset(width * 0.4f, height * 0.3f)) }
        awaitWebFrame()
        compose.onNodeWithTag("web-host").performTouchInput {
            moveTo(Offset(width * 0.4f, height * 0.7f), delayMillis = 500)
            up()
        }
        control(R.string.wear_web_tools).assertDoesNotExist()
        compose.waitUntil(5_000) { js("document.querySelector('main').scrollTop").toDouble() < 400 }
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(center, center - Offset(width * 0.22f, height * 0.22f), 500)
        }
        awaitPosition(0f, 0f)
        control(R.string.wear_web_tools).assertDoesNotExist()
    }

    private fun peekCorner(dx: Float, dy: Float) {
        compose.onNodeWithTag("web-host").performTouchInput {
            swipe(center - Offset(width * 0.32f * dx, height * 0.32f * dy), center, 600)
        }
        val viewport = compose.runOnIdle { webView.parent as View }
        awaitPosition(if (round) viewport.width / 4f * dx else 0f, if (round) viewport.height / 4f * dy else 0f)
    }

    private fun awaitPosition(x: Float, y: Float) {
        compose.waitUntil(5_000) {
            compose.runOnIdle { kotlin.math.abs(webView.translationX - x) < 1f && kotlin.math.abs(webView.translationY - y) < 1f }
        }
    }

    private fun awaitStableScroll() {
        var previous = Double.NaN
        var stable = 0
        compose.waitUntil(5_000) {
            awaitWebFrame()
            val current = js("scrollY").toDouble()
            stable = if (current == previous) stable + 1 else 0
            previous = current
            stable >= 2
        }
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
            compose.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            compose.activity.setTurnScreenOn(true)
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
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalHapticFeedback provides haptics) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        PredictiveBackHandler { progress ->
                            progress.collect { }
                            backCount++
                        }
                        // Exercise the touch-driven parent as well, including on API 36+ emulators.
                        SwipeToDismissBox(onDismissed = { backCount++ }) { background ->
                            if (!background) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(192.dp).clip(if (round) CircleShape else RectangleShape).testTag("web-host")) {
                                        WearWebView(webView)
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
        compose.waitUntil(15_000) { compose.runOnIdle { webView.hasWindowFocus() } }
        compose.waitUntil(10_000) { compose.runOnIdle { asset != "wear-webview.html" || webView.canScrollVertically(1) } }
    }

    private fun control(resource: Int) = compose.onNodeWithText(compose.activity.getString(resource))

    private fun clickTool(text: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed().performClick()
    }

    private fun closeTools() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        control(R.string.wear_web_tools).assertDoesNotExist()
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
            assertEquals("Full-width viewport", viewport.width.toFloat(), right - left, 1f)
            assertEquals("Full-height viewport", viewport.height.toFloat(), bottom - top, 1f)
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
