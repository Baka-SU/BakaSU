package org.bakasu.bakasu.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.util.LruCache
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.webkit.WebViewAssetLoader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.network.WebResourceRepository
import org.bakasu.bakasu.domain.model.DocumentFormat
import org.bakasu.bakasu.domain.model.DocumentNavigation
import org.bakasu.bakasu.domain.model.ModuleReadme
import org.bakasu.bakasu.domain.model.navigation
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
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope

private val documentScrollScript = """
    (function() {
        var target = null;
        var lastLeft = null;
        var lastRight = null;

        function update() {
            var left = false;
            var right = false;
            if (target) {
                var maximum = target.scrollWidth - target.clientWidth;
                var offset = getComputedStyle(target).direction === 'rtl'
                    ? maximum + target.scrollLeft : target.scrollLeft;
                left = offset > 1;
                right = offset < maximum - 1;
            }
            if (left !== lastLeft || right !== lastRight) {
                AndroidScroll.updateScrollState(left, right);
                lastLeft = left;
                lastRight = right;
            }
        }

        document.addEventListener('touchstart', function(event) {
            target = null;
            lastLeft = lastRight = null;
            var element = event.target;
            while (element && element !== document.documentElement) {
                if (element.nodeType === 1) {
                    var overflow = getComputedStyle(element).overflowX;
                    if ((overflow === 'auto' || overflow === 'scroll') && element.scrollWidth > element.clientWidth) {
                        target = element;
                        break;
                    }
                }
                element = element.parentElement;
            }
            update();
        }, {passive: true, capture: true});
        document.addEventListener('touchmove', update, {passive: true, capture: true});
        document.addEventListener('scroll', update, {passive: true, capture: true});
    })();
""".trimIndent()

// Permit this exact app-owned script, without allowing publisher scripts or event handlers.
private val documentScrollHash = Base64.getEncoder().encodeToString(
    MessageDigest.getInstance("SHA-256").digest(documentScrollScript.toByteArray(StandardCharsets.UTF_8)),
)

private val renderedMarkdownCache = object : LruCache<Pair<String, Boolean>, String>(2 * 1024 * 1024) {
    override fun sizeOf(key: Pair<String, Boolean>, value: String): Int = ((key.first.length.toLong() + value.length) * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun renderMarkdownDocument(content: String, restricted: Boolean): String {
    val key = content to restricted
    renderedMarkdownCache.get(key)?.let { return it }
    val extensions = listOf(TablesExtension.create())
    val parser = Parser.builder().extensions(extensions).build()
    val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(restricted)
    // CommonMark 0.13 lacks sanitizeUrls.
    if (restricted) {
        renderer.attributeProviderFactory {
            AttributeProvider { _, _, attributes ->
                for (name in listOf("href", "src")) {
                    val value = attributes[name] ?: continue
                    val scheme = value.toUri().scheme
                    if (scheme != null && scheme.lowercase() !in setOf("https", "http")) attributes.remove(name)
                }
            }
        }
    }
    return renderer.build().render(parser.parse(content)).also { renderedMarkdownCache.put(key, it) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GithubMarkdown(
    document: ModuleReadme,
    backgroundColor: androidx.compose.ui.graphics.Color,
    loading: MutableState<Boolean> = remember { mutableStateOf(true) },
    callerProvideLoadingIndicator: Boolean = false,
    readmeLinks: Boolean = false,
) {
    val content = document.content
    val renderMarkdown = document.format == DocumentFormat.MARKDOWN
    val baseUrl = document.baseUrl
    val policy = document.policy
    val escapeHtml = policy.escapeEmbeddedHtml
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
    } else {
        cssColorFromArgb(MaterialTheme.colorScheme.primary.toArgb())
    }
    val linkStyles = if (readmeLinks) {
        """
        .markdown-body a[href] {
          color: $fgLink;
          text-decoration: underline;
          -webkit-tap-highlight-color: ${if (isDark) "rgba(46, 143, 255, 0.08)" else "rgba(3, 102, 214, 0.08)"};
          word-break: break-word;
        }
        """.trimIndent()
    } else {
        ""
    }

    val cssHref = "https://appassets.androidplatform.net/assets/github-markdown.css"
    val renderedState = remember(content, renderMarkdown, escapeHtml) {
        mutableStateOf(if (renderMarkdown) renderedMarkdownCache.get(content to escapeHtml) else content)
    }
    LaunchedEffect(content, renderMarkdown, escapeHtml) {
        if (renderMarkdown && renderedState.value == null) {
            loading.value = true
            renderedState.value = withContext(Dispatchers.Default) {
                renderMarkdownDocument(content, escapeHtml)
            }
        }
    }
    val rendered = renderedState.value
    val html = remember(rendered, dir, isDark, bgDefault, bgMuted, bgNeutralMuted, bgAttentionMuted, fgLink, linkStyles) {
        if (rendered == null) {
            null
        } else {
            """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset='utf-8'/>
          <meta name='viewport' content='width=device-width, initial-scale=1'/>
          <meta http-equiv="Content-Security-Policy" content="script-src 'sha256-$documentScrollHash'; script-src-attr 'none'; connect-src 'none'; worker-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'; img-src https: data:" />
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
          <script>$documentScrollScript</script>
        </head>
        <body dir='$dir'>
          <article class='markdown-body' data-theme='${if (isDark) "dark" else "light"}'>$rendered</article>
        </body>
        </html>
            """.trim() // Preserve the script's exact bytes for its CSP hash.
        }
    }

    if (html != null) {
        key(html, baseUrl, policy) {
            GithubMarkdownWebView(loading, html, document)
        }
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

@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
@Suppress("DEPRECATION")
@Composable
private fun GithubMarkdownWebView(
    loading: MutableState<Boolean>,
    html: String,
    document: ModuleReadme,
) {
    val policy = document.policy
    val resourceRepository = koinInject<WebResourceRepository>()
    val transitionRunning = LocalNavTransitionScope.current.isRunning

    AndroidView(
        factory = { context ->
            val frameLayout = FrameLayout(context)
            val scrollBridge = DocumentScrollBridge()
            val webView = WebView(context).apply {
                try {
                    setBackgroundColor(Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.apply {
                        offscreenPreRaster = true
                        javaScriptEnabled = true
                        domStorageEnabled = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
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
                    addJavascriptInterface(scrollBridge, "AndroidScroll")
                    webViewClient = object : WebViewClient() {
                        private val assetLoader = WebViewAssetLoader.Builder()
                            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                            .build()

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = when (document.navigation(request.url.toString(), request.hasGesture(), request.isForMainFrame)) {
                            DocumentNavigation.ANCHOR -> false

                            DocumentNavigation.BLOCK -> true

                            DocumentNavigation.EXTERNAL -> {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                true
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                            // The initial loadDataWithBaseURL document and inline images are data URLs.
                            if (request.url.scheme == "data") return null
                            if (!policy.allowsRequest(request.url.toString(), request.method)) return blockedDocumentResource()
                            return resourceRepository.load(
                                url = request.url.toString(),
                                method = request.method,
                                requestHeaders = request.requestHeaders,
                                policy = policy,
                            )?.let { resource ->
                                WebResourceResponse(resource.mimeType, resource.encoding, resource.body)
                            } ?: blockedDocumentResource()
                        }

                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            loading.value = false
                        }
                    }
                    setOnTouchListener(DocumentTouchHandler(this, scrollBridge))
                    loadDataWithBaseURL(
                        document.baseUrl,
                        html,
                        "text/html",
                        StandardCharsets.UTF_8.name(),
                        null,
                    )
                } catch (e: Throwable) {
                    Log.e("GithubMarkdown", "WebView setup failed", e)
                }
            }
            frameLayout.addView(
                webView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT),
            )
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
            .graphicsLayer {
                // Keep Chromium hardware accelerated and composite its content with the
                // navigation transform instead of switching WebView to software drawing.
                compositingStrategy = if (transitionRunning) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .clipToBounds(),
    )
}

private fun blockedDocumentResource() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), "".byteInputStream())

private class DocumentScrollBridge {
    @Volatile
    private var directions = 0

    @JavascriptInterface
    @Suppress("unused")
    fun updateScrollState(left: Boolean, right: Boolean) {
        directions = (if (left) 1 else 0) or (if (right) 2 else 0)
    }

    fun reset() {
        // Keep the initial gesture until the document reports the touched element.
        directions = 3
    }

    fun canScroll(dx: Float): Boolean = (directions and (if (dx > 0) 1 else 2)) != 0
}

private class DocumentTouchHandler(view: View, private val scrollBridge: DocumentScrollBridge) : View.OnTouchListener {
    private val touchSlop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var direction = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                direction = 0
                scrollBridge.reset()
                v.parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (direction == 0 && (abs(dx) > touchSlop || abs(dy) > touchSlop)) direction = if (abs(dx) > abs(dy)) 1 else 2
                when (direction) {
                    1 -> {
                        val step = event.x - lastX
                        if (step != 0f) v.parent.requestDisallowInterceptTouchEvent(scrollBridge.canScroll(step))
                    }

                    2 -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        return true
                    }
                }
                lastX = event.x
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.parent.requestDisallowInterceptTouchEvent(false)
                direction = 0
            }
        }
        return false
    }
}
