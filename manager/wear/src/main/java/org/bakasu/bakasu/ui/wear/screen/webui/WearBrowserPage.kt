package org.bakasu.bakasu.ui.wear.screen.webui

import android.annotation.SuppressLint
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.usecase.OpenWearExternalLinkUseCase
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearBackground
import org.bakasu.bakasu.ui.wear.component.WearEdgeProgressIndicator
import org.bakasu.bakasu.ui.wear.component.WearLoadFailureScreen
import org.bakasu.bakasu.ui.wear.component.WearLoadingIndicator
import org.bakasu.bakasu.ui.wear.component.WearViewportWebView
import org.bakasu.bakasu.ui.wear.component.WearWebView
import org.bakasu.bakasu.wear.R as WearR
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WearBrowserPage(url: String, onBack: () -> Unit, onOpened: () -> Unit = {}, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    var loadProgress by remember(url, attempt) { mutableIntStateOf(0) }
    var opened by remember(url, attempt) { mutableStateOf(false) }
    var pageFailed by remember(url, attempt) { mutableStateOf(false) }
    val openExternal = koinInject<OpenWearExternalLinkUseCase>()
    val toast = LocalWearToast.current
    val failedMessage = stringResource(R.string.wear_link_open_failed)
    var renderGone by remember(url, attempt) { mutableStateOf(false) }
    var unavailable by remember(url, attempt) { mutableStateOf(false) }
    val currentOnOpened by rememberUpdatedState(onOpened)
    val webView = remember(url, attempt) {
        runCatching {
            WearViewportWebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        loadProgress = newProgress
                    }
                }
                webViewClient = WearBrowserClient(
                    onOpened = {
                        opened = true
                        currentOnOpened()
                    },
                    onUnavailable = { unavailable = true },
                    onRendererGone = { renderGone = true },
                    onFailed = { pageFailed = true },
                    onExternal = { openExternal(it).onFailure { toast.show(failedMessage) } },
                )
            }
        }.getOrNull()
    }
    if (webView == null || unavailable) {
        if (attempt == 0) {
            LaunchedEffect(url) { onUnavailable() }
            return
        }
        WearBackground {
            WearLoadFailureScreen(stringResource(WearR.string.wear_browser_load_failed), onBack, { attempt++ }, retryInEdgeButton = true)
        }
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
    Box(Modifier.fillMaxSize()) {
        if (!renderGone) WearWebView(webView)
        if (renderGone || pageFailed) {
            WearBackground {
                WearLoadFailureScreen(stringResource(WearR.string.wear_browser_load_failed), onBack, { attempt++ }, retryInEdgeButton = true)
            }
        } else if (loadProgress < 100) {
            if (opened) {
                val fraction = loadProgress / 100f
                WearEdgeProgressIndicator({ fraction }, Modifier.progressSemantics(fraction))
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { WearLoadingIndicator() }
            }
        }
    }
}

internal class WearBrowserClient(
    private val onOpened: () -> Unit,
    private val onUnavailable: () -> Unit,
    private val onRendererGone: () -> Unit,
    private val onFailed: () -> Unit = {},
    private val onExternal: (String) -> Unit = {},
) : WebViewClient() {
    private var opened = false
    private var failed = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.url.scheme in setOf("http", "https", "about", "data")) return false
        if (request.isForMainFrame) onExternal(request.url.toString())
        return true
    }

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
        if (request.isForMainFrame) {
            if (opened) onFailed() else failInitialLoad()
        }
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame) {
            if (opened) onFailed() else failInitialLoad()
        }
    }

    // Initial renderer failures continue the handoff; later ones show the browser retry surface.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (opened) onRendererGone() else failInitialLoad()
        return true
    }
}
