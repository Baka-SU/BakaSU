package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearBrowserFallbackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun roundBrowserPageLoadsFromItsFirstLayout() = browserPageLoadsFromItsFirstLayout(round = true)

    @Test
    fun squareBrowserPageLoadsFromItsFirstLayout() = browserPageLoadsFromItsFirstLayout(round = false)

    private fun browserPageLoadsFromItsFirstLayout(round: Boolean) {
        val html = InstrumentationRegistry.getInstrumentation().context.assets.open("wear-webview-viewport.html").bufferedReader().use { it.readText() }
        val url = "data:text/html;charset=utf-8,${Uri.encode(html)}"
        var opened = 0
        var unavailable = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                WearManagerTheme {
                    AppScaffold(timeText = {}) {
                        WearBrowserPage(url, onBack = {}, onOpened = { opened++ }, onUnavailable = { unavailable++ })
                    }
                }
            }
        }
        compose.waitUntil(15_000) { opened > 0 || unavailable > 0 }
        compose.runOnIdle {
            assertEquals(1, opened)
            assertEquals(0, unavailable)
        }
        val view = compose.runOnIdle { findWebView(compose.activity.window.decorView)!! }
        var contentVisible: String? = null
        compose.runOnIdle {
            view.evaluateJavascript("document.querySelector('main').getBoundingClientRect().height > 40 && document.body.innerText.includes('Module status')") { contentVisible = it }
        }
        compose.waitUntil { contentVisible != null }
        assertEquals("true", contentVisible)
        val drawn = CountDownLatch(1)
        compose.runOnIdle {
            view.postVisualStateCallback(
                0,
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        view.postOnAnimation { view.postOnAnimation { drawn.countDown() } }
                    }
                },
            )
        }
        assertTrue(drawn.await(5, TimeUnit.SECONDS))
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val bitmap = automation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "wear-browser-${if (round) "round" else "square"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test
    fun initialMainDocumentFailureFallsBackOnceAndIgnoresSubresources() {
        compose.runOnIdle {
            val view = WebView(compose.activity)
            var opened = 0
            var fallbacks = 0
            val client = WearBrowserClient({ opened++ }, { fallbacks++ }, { error("Not opened yet") })
            try {
                client.onReceivedHttpError(view, request(false), httpError())
                assertEquals(0, fallbacks)
                client.onReceivedHttpError(view, request(true), httpError())
                client.onRenderProcessGone(view, rendererGone)
                client.onPageCommitVisible(view, "https://example.org")
                assertEquals(1, fallbacks)
                assertEquals(0, opened)
            } finally {
                view.destroy()
            }
        }
    }

    @Test
    fun initialHttpAndRendererFailuresEachContinueTheAttempt() {
        compose.runOnIdle {
            val view = WebView(compose.activity)
            try {
                var fallbacks = 0
                WearBrowserClient({}, { fallbacks++ }, {}).onReceivedHttpError(view, request(true), httpError())
                WearBrowserClient({}, { fallbacks++ }, {}).onRenderProcessGone(view, rendererGone)
                assertEquals(2, fallbacks)
            } finally {
                view.destroy()
            }
        }
    }

    @Test
    fun errorsAfterOpeningDoNotRestartTheHandoff() {
        compose.runOnIdle {
            val view = WebView(compose.activity)
            var opened = 0
            var closed = 0
            val client = WearBrowserClient({ opened++ }, { error("Must not restart handoff") }, { closed++ })
            try {
                client.onPageCommitVisible(view, "https://example.org")
                client.onPageCommitVisible(view, "https://example.org/next")
                client.onReceivedHttpError(view, request(true), httpError())
                client.onRenderProcessGone(view, rendererGone)
                assertEquals(1, opened)
                assertEquals(1, closed)
            } finally {
                view.destroy()
            }
        }
    }

    private fun request(mainFrame: Boolean) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse("https://example.org")
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }

    private fun httpError() = WebResourceResponse("text/html", "UTF-8", 503, "Unavailable", emptyMap(), null)

    @Test
    fun actualMainDocumentNetworkErrorContinuesTheAttempt() {
        lateinit var view: WebView
        var fallbacks = 0
        compose.runOnIdle {
            view = WebView(compose.activity)
            view.webViewClient = WearBrowserClient({ error("Unavailable local endpoint must fail") }, { fallbacks++ }, {})
            view.loadUrl("http://127.0.0.1:1/")
        }
        try {
            compose.waitUntil(15_000) { fallbacks == 1 }
            compose.runOnIdle { assertEquals(1, fallbacks) }
        } finally {
            compose.runOnIdle { view.destroy() }
        }
    }

    private val rendererGone = object : RenderProcessGoneDetail() {
        override fun didCrash() = true
        override fun rendererPriorityAtExit() = WebView.RENDERER_PRIORITY_IMPORTANT
    }
}
