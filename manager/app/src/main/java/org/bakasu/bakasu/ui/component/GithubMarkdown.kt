package org.bakasu.bakasu.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import org.bakasu.bakasu.data.module.registry.RegistryUrls
import org.bakasu.bakasu.data.network.WebResourceRepository
import org.bakasu.bakasu.ui.activity.util.adjustLightnessArgb
import org.bakasu.bakasu.ui.activity.util.cssColorFromArgb
import org.bakasu.bakasu.ui.activity.util.ensureVisibleByMix
import org.bakasu.bakasu.ui.activity.util.relativeLuminance
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.isInDarkTheme
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer
import org.koin.compose.koinInject

private fun renderMarkdownDocument(content: String, restricted: Boolean): String {
    val extensions = listOf(TablesExtension.create())
    val parser = Parser.builder().extensions(extensions).build()
    val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(restricted)
    // CommonMark 0.13 lacks sanitizeUrls.
    if (restricted) renderer.attributeProviderFactory {
        AttributeProvider { _, _, attributes ->
            for (name in listOf("href", "src")) {
                val value = attributes[name] ?: continue
                val scheme = value.toUri().scheme
                if (scheme != null && scheme.lowercase() !in setOf("https", "http")) attributes.remove(name)
            }
        }
    }
    return renderer.build().render(parser.parse(content))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GithubMarkdown(
    content: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    loading: MutableState<Boolean> = remember { mutableStateOf(true) },
    callerProvideLoadingIndicator: Boolean = false,
    renderMarkdown: Boolean = false,
    baseUrl: String = "https://appassets.androidplatform.net",
    restrictedBaseUrl: String? = null,
    readmeLinks: Boolean = false,
) {
    val themeConfig: ThemeConfig = koinInject()
    val isDark = isInDarkTheme(themeConfig.forceDarkMode)
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "rtl" else "ltr"

    val bgArgb = backgroundColor.toArgb()
    val bgLuminance = relativeLuminance(bgArgb)

    fun makeVariant(delta: Float): Int {
        val candidate = adjustLightnessArgb(bgArgb, delta)
        val madeLighter = delta > 0f
        return ensureVisibleByMix(bgArgb, candidate, 1.15, madeLighter)
    }

    val bgDefault = cssColorFromArgb(bgArgb)
    val bgMuted = cssColorFromArgb(makeVariant(if (bgLuminance > 0.6) -0.06f else 0.06f))
    val bgNeutralMuted = cssColorFromArgb(makeVariant(if (bgLuminance > 0.6) -0.12f else 0.12f))
    val bgAttentionMuted = cssColorFromArgb(makeVariant(-0.12f))
    // Match upstream's blue light/dark link palette only for the README surface.
    val fgLink = if (readmeLinks) {
        if (isDark) "#85beff" else "#0366d6"
    } else cssColorFromArgb(MaterialTheme.colorScheme.primary.toArgb())
    val linkStyles = if (readmeLinks) {
        """
        .markdown-body a[href] {
          color: $fgLink;
          text-decoration: underline;
          -webkit-tap-highlight-color: ${if (isDark) "rgba(46, 143, 255, 0.08)" else "rgba(3, 102, 214, 0.08)"};
          word-break: break-word;
        }
        """.trimIndent()
    } else ""

    val cssHref = "https://appassets.androidplatform.net/assets/github-markdown.css"
    val rendered = remember(content, renderMarkdown, restrictedBaseUrl) {
        if (renderMarkdown) renderMarkdownDocument(content, restrictedBaseUrl != null) else content
    }
    val contentPolicy = if (renderMarkdown || restrictedBaseUrl != null) {
        """<meta http-equiv="Content-Security-Policy" content="script-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; img-src https: data:" />"""
    } else {
        ""
    }
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='utf-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1'/>
          $contentPolicy
          <link rel="stylesheet" href="$cssHref" />
          <style>
            html, body { margin:0; padding:0; }
            img, video { max-width:100%; height:auto; }
            .markdown-body {
              padding: 16px;
              --bgColor-default: $bgDefault;
              --bgColor-muted: $bgMuted;
              --bgColor-neutral-muted: $bgNeutralMuted;
              --bgColor-attention-muted: $bgAttentionMuted;
              --fgColor-accent: $fgLink;
            }
            $linkStyles
          </style>
        </head>
        <body dir='$dir'>
          <article class='markdown-body' data-theme='${if (isDark) "dark" else "light"}'>$rendered</article>
        </body>
        </html>
    """.trimIndent()

    key(html, baseUrl, renderMarkdown, restrictedBaseUrl) {
        GithubMarkdownWebView(loading, html, baseUrl, renderMarkdown, restrictedBaseUrl)
    }

    if (loading.value && !callerProvideLoadingIndicator) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            LoadingIndicator()
        }
    }
}

@SuppressLint("ClickableViewAccessibility", "JavascriptInterface", "SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
private fun GithubMarkdownWebView(
    loading: MutableState<Boolean>,
    html: String,
    baseUrl: String,
    renderMarkdown: Boolean,
    restrictedBaseUrl: String?,
) {
    val scriptsOff = renderMarkdown || restrictedBaseUrl != null
    val scrollInterface = remember { MarkdownScrollInterface() }
    val resourceRepository = koinInject<WebResourceRepository>()

    AndroidView(
        factory = { context ->
            val frameLayout = FrameLayout(context)
            val webView = (if (restrictedBaseUrl != null) EdgeAwareWebView(context) else WebView(context)).apply {
                try {
                    setBackgroundColor(Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.apply {
                        offscreenPreRaster = true
                        javaScriptEnabled = !scriptsOff
                        domStorageEnabled = !scriptsOff
                        mixedContentMode = if (scriptsOff) WebSettings.MIXED_CONTENT_NEVER_ALLOW else WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowFileAccess = false
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        textZoom = 90
                        setSupportZoom(false)
                        setGeolocationEnabled(false)
                    }
                    if (!scriptsOff) addJavascriptInterface(scrollInterface, "AndroidScroll")
                    webViewClient = object : WebViewClient() {
                        private val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                            .build()

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            if (scriptsOff) return

                            val js = """
                                (function() {
                                    if (window.androidScrollInjected) return;
                                    window.androidScrollInjected = true;
                                
                                    function checkScroll(target) {
                                        if (!target || target === document.body || target === document.documentElement) return {l: false, r: false};
                                        var style = window.getComputedStyle(target);
                                        if (style.overflowX !== 'auto' && style.overflowX !== 'scroll') return {l: false, r: false};
                                        if (target.scrollWidth <= target.clientWidth) return {l: false, r: false};
                                        
                                        var atLeft = target.scrollLeft <= 0;
                                        var atRight = Math.ceil(target.scrollLeft + target.clientWidth) >= target.scrollWidth;
                                        
                                        return {l: !atLeft, r: !atRight};
                                    }
                                
                                    var lastTarget = null;
                                    var lastState = {l: false, r: false};
                                    
                                    function update(l, r) {
                                        if (lastState.l !== l || lastState.r !== r) {
                                            lastState = {l: l, r: r};
                                            AndroidScroll.updateScrollState(l, r);
                                        }
                                    }
                                
                                    document.addEventListener('touchstart', function(e) {
                                        var t = e.target;
                                        var found = false;
                                        while(t && t !== document.body) {
                                            var s = checkScroll(t);
                                            if (s.l || s.r) { 
                                                 lastTarget = t;
                                                 update(s.l, s.r);
                                                 found = true;
                                                 break;
                                            }
                                            t = t.parentElement;
                                        }
                                        if (!found) {
                                            lastTarget = null;
                                            update(false, false);
                                        }
                                    }, {passive: true});
                                
                                    document.addEventListener('touchmove', function(e) {
                                        if (lastTarget) {
                                             var s = checkScroll(lastTarget);
                                             update(s.l, s.r);
                                        }
                                    }, {passive: true});
                                    
                                    document.addEventListener('scroll', function(e) {
                                        if (lastTarget && (e.target === lastTarget || e.target.contains(lastTarget))) {
                                              var s = checkScroll(lastTarget);
                                              update(s.l, s.r);
                                        }
                                    }, {passive: true, capture: true});
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            if (scriptsOff) {
                                if (restrictedBaseUrl != null && RegistryUrls.isInPageAnchor(url, restrictedBaseUrl)) return true
                                if (request.url.scheme !in (if (restrictedBaseUrl != null) setOf("https", "http") else setOf("https", "http", "mailto"))) return true
                                if (!request.hasGesture() || !request.isForMainFrame) return true
                                if (request.url.fragment != null && url.substringBefore('#') == baseUrl.substringBefore('#')) return false
                            }
                            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            if (scriptsOff) runCatching { context.startActivity(intent) } else context.startActivity(intent)
                            return true
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                            if (restrictedBaseUrl != null) {
                                if (request.isForMainFrame && request.url.scheme == "data") return null
                                if (request.method != "GET" || !RegistryUrls.isAllowedRestrictedResource(request.url.toString(), restrictedBaseUrl)) return blockedDocumentResource()
                            }
                            val scheme = request.url.scheme ?: return null
                            if (scriptsOff && scheme !in setOf("https", "data")) {
                                return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                            }
                            if (!scheme.startsWith("http")) return null
                            return resourceRepository.load(
                                url = request.url.toString(),
                                method = request.method,
                                requestHeaders = request.requestHeaders,
                                restrictedOrigin = restrictedBaseUrl,
                            )?.let { resource ->
                                WebResourceResponse(
                                    resource.mimeType,
                                    resource.encoding,
                                    resource.body,
                                )
                            } ?: if (restrictedBaseUrl != null) blockedDocumentResource() else null
                        }

                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            loading.value = false
                        }
                    }
                    if (restrictedBaseUrl != null) setOnTouchListener(RestrictedTouchHandler(this as EdgeAwareWebView))
                    else setOnTouchListener(object : View.OnTouchListener {
                        private var isHorizontalScrollLocked = false
                        private var initialDownX = 0f
                        private var initialDownY = 0f

                        override fun onTouch(v: View, event: MotionEvent): Boolean {
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    initialDownX = event.x
                                    initialDownY = event.y
                                    isHorizontalScrollLocked = false
                                    v.parent.requestDisallowInterceptTouchEvent(true)
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    if (isHorizontalScrollLocked) {
                                        v.parent.requestDisallowInterceptTouchEvent(true)
                                    } else {
                                        val dx = event.x - initialDownX
                                        val dy = event.y - initialDownY
                                        if (abs(dx) > abs(dy)) {
                                            // Untrusted documents keep horizontal scrolling without a JavaScript bridge.
                                            val canScroll = scriptsOff || if (dx < 0) scrollInterface.canScrollRight else scrollInterface.canScrollLeft
                                            if (canScroll) {
                                                isHorizontalScrollLocked = true
                                                v.parent.requestDisallowInterceptTouchEvent(true)
                                            } else {
                                                v.parent.requestDisallowInterceptTouchEvent(false)
                                            }
                                        } else {
                                            v.parent.requestDisallowInterceptTouchEvent(false)
                                            return true
                                        }
                                    }
                                }

                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    v.parent.requestDisallowInterceptTouchEvent(false)
                                    isHorizontalScrollLocked = false
                                }
                            }
                            return false
                        }
                    })
                    loadDataWithBaseURL(
                        baseUrl,
                        html,
                        "text/html",
                        StandardCharsets.UTF_8.name(),
                        null,
                    )
                } catch (e: Throwable) {
                    Log.e("GithubMarkdown", "WebView setup failed", e)
                }
            }
            frameLayout.addView(webView)
            frameLayout
        },
        onRelease = { frameLayout ->
            val webView = frameLayout.getChildAt(0) as? WebView
            frameLayout.removeAllViews()
            webView?.apply {
                stopLoading()
                destroy()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clipToBounds(),
    )
}

private fun blockedDocumentResource() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), "".byteInputStream())

private class EdgeAwareWebView(context: android.content.Context) : WebView(context) {
    var horizontalEdgeReached = false

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
        if (clampedX) horizontalEdgeReached = true
    }
}

private class RestrictedTouchHandler(private val webView: EdgeAwareWebView) : View.OnTouchListener {
    private val touchSlop = android.view.ViewConfiguration.get(webView.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var direction = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                direction = 0
                webView.horizontalEdgeReached = false
                v.parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (direction == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) direction = if (abs(dx) > abs(dy)) 1 else 2
                when (direction) {
                    1 -> if (webView.horizontalEdgeReached) v.parent.requestDisallowInterceptTouchEvent(false)
                    2 -> { v.parent.requestDisallowInterceptTouchEvent(false); return true }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.parent.requestDisallowInterceptTouchEvent(false)
                direction = 0
            }
        }
        return false
    }
}

class MarkdownScrollInterface {
    @Volatile
    var canScrollLeft = false

    @Volatile
    var canScrollRight = false

    @JavascriptInterface
    @Suppress("unused")
    fun updateScrollState(left: Boolean, right: Boolean) {
        canScrollLeft = left
        canScrollRight = right
    }
}
