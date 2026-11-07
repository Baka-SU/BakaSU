package org.bakasu.bakasu.ui.wear

import android.annotation.SuppressLint
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.bakasu.bakasu.ui.wear.component.WearWebView

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WearBrowserPage(url: String, onBack: () -> Unit, onOpened: () -> Unit = {}, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    var renderGone by remember(url) { mutableStateOf(false) }
    var unavailable by remember(url) { mutableStateOf(false) }
    val currentOnOpened by rememberUpdatedState(onOpened)
    val webView = remember(url) {
        runCatching {
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                webViewClient = WearBrowserClient(
                    onOpened = { currentOnOpened() },
                    onUnavailable = { unavailable = true },
                    onRendererGone = { renderGone = true },
                )
            }
        }.getOrNull()
    }
    if (webView == null || unavailable) {
        LaunchedEffect(url) { onUnavailable() }
        return
    }
    DisposableEffect(webView) {
        var loaded = false
        val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (!loaded && view.width > 0 && view.height > 0) {
                loaded = true
                webView.loadUrl(url)
            }
        }
        webView.addOnLayoutChangeListener(listener)
        onDispose {
            webView.removeOnLayoutChangeListener(listener)
            webView.stopLoading()
            webView.destroy()
        }
    }
    if (renderGone) {
        LaunchedEffect(url) { onBack() }
        return
    }
    WearWebView(webView, onBack)
}

internal class WearBrowserClient(private val onOpened: () -> Unit, private val onUnavailable: () -> Unit, private val onRendererGone: () -> Unit) : WebViewClient() {
    private var opened = false
    private var failed = false

    override fun onPageCommitVisible(view: WebView, url: String) {
        if (!opened && !failed) {
            opened = true
            onOpened()
        }
    }

    private fun failInitialLoad() {
        if (!opened && !failed) {
            failed = true
            onUnavailable()
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) failInitialLoad()
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame) failInitialLoad()
    }

    // Only an initial renderer failure continues the handoff; later failures leave the opened page.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (opened) onRendererGone() else failInitialLoad()
        return true
    }
}
