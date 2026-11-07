package org.bakasu.bakasu.ui.wear

import android.annotation.SuppressLint
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.bakasu.bakasu.ui.wear.component.WearWebView

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WearBrowserPage(url: String, onBack: () -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    var renderGone by remember(url) { mutableStateOf(false) }
    val webView = remember(url) {
        runCatching {
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                webViewClient = WearBrowserClient { renderGone = true }
            }
        }.getOrNull()
    }
    if (webView == null) {
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

private class WearBrowserClient(private val onRendererGone: () -> Unit) : WebViewClient() {
    // A killed renderer leaves this WebView unusable; leave the page instead of crashing the app.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }
}
