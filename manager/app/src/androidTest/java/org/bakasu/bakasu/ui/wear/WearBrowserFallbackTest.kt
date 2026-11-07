package org.bakasu.bakasu.ui.wear

import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearBrowserFallbackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
