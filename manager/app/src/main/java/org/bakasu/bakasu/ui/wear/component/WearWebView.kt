package org.bakasu.bakasu.ui.wear.component

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import org.bakasu.bakasu.R

/** Centers web content clear of the round bezel and the page controls. */
@Composable
internal fun WearWebView(webView: WebView, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val round = LocalConfiguration.current.isScreenRound
    var scalePercent by remember(webView) { mutableIntStateOf(80) }
    var resetPosition by remember(webView) { mutableIntStateOf(0) }
    var toolsOpen by remember(webView) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scale = scalePercent / 100f
    val buttonSize = IconButtonDefaults.SmallButtonSize
    val controlsHeight = with(LocalDensity.current) { (buttonSize + 16.dp).toPx() }

    LaunchedEffect(webView, toolsOpen) {
        if (!toolsOpen) focus.requestFocus()
    }
    BackHandler(toolsOpen) { toolsOpen = false }
    ScreenScaffold(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(0.dp),
        timeText = {},
        overscrollEffect = null,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).clipToBounds()) {
            AndroidView(
                factory = { WearWebViewViewport(it, webView) },
                update = { it.update(scale, round, controlsHeight, resetPosition) },
                onRelease = { it.removeView(webView) },
                modifier = Modifier.fillMaxSize()
                    .onRotaryScrollEvent {
                        if (toolsOpen) return@onRotaryScrollEvent false
                        webView.scrollBy(0, (it.verticalScrollPixels / scale).toInt())
                        true
                    }
                    .focusRequester(focus).focusable(),
            )
            if (toolsOpen) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .pointerInput(Unit) {
                            // The panel must not pass gestures through to the web page.
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume()
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                )
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                toolsOpen = false
                                onBack()
                            },
                            modifier = Modifier.size(buttonSize),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                resetPosition++
                                scalePercent = 80
                                toolsOpen = false
                            },
                            modifier = Modifier.size(buttonSize),
                        ) {
                            Icon(Icons.Rounded.CenterFocusStrong, stringResource(R.string.wear_web_reset))
                        }
                    }
                    FilledTonalButton(onClick = { scalePercent = if (scalePercent >= 100) 60 else scalePercent + 20 }) {
                        Text(stringResource(R.string.wear_web_scale, scalePercent))
                    }
                    FilledTonalIconButton(onClick = { toolsOpen = false }, modifier = Modifier.size(buttonSize)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.close))
                    }
                }
            } else {
                FilledTonalIconButton(
                    onClick = { toolsOpen = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(buttonSize),
                ) {
                    Icon(Icons.Rounded.Tune, stringResource(R.string.wear_web_tools))
                }
            }
        }
    }
}

/**
 * Gives responsive pages a real viewport, including those whose content depends on 100vh or flex
 * height. Native child transforms preserve WebView touch mapping inside the available rectangle.
 */
@SuppressLint("ViewConstructor")
private class WearWebViewViewport(context: Context, private val webView: WebView) : ViewGroup(context) {
    private var pageScale = 0.8f
    private var round = false
    private var controlsHeight = 0f
    private var resetPosition = 0
    private var offsetX = 0f
    private var offsetY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var panning = false
    private var fromMargin = false
    private var gestureDecided = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        webView.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        webView.pivotX = 0f
        webView.pivotY = 0f
    }

    fun update(scale: Float, isRound: Boolean, controlsPx: Float, reset: Int) {
        if (pageScale != scale || round != isRound || controlsHeight != controlsPx || resetPosition != reset) {
            pageScale = scale
            round = isRound
            controlsHeight = controlsPx
            resetPosition = reset
            offsetX = 0f
            offsetY = 0f
            requestLayout()
        }
        webView.scaleX = scale
        webView.scaleY = scale
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        // A centered square fits inside the round bezel. Reserve the controls and their touch
        // target separately so they cannot cover a page's fixed bottom navigation.
        val inset = if (round) minOf(width, height) * (1f - 1f / sqrt(2f)) / 2f else 0f
        val pageWidth = (width - 2 * inset).coerceAtLeast(1f)
        val pageHeight = (height - controlsHeight - inset).coerceAtLeast(1f)
        webView.measure(
            MeasureSpec.makeMeasureSpec((pageWidth / pageScale).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((pageHeight / pageScale).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
        positionPage()
    }

    private fun positionPage() {
        val pageWidth = webView.width * pageScale
        val pageHeight = webView.height * pageScale
        val centeredLeft = (width - pageWidth) / 2f
        val centeredTop = minOf((height - pageHeight) / 2f, height - controlsHeight - pageHeight).coerceAtLeast(0f)
        var minTop = 0f
        var maxTop = (height - controlsHeight - pageHeight).coerceAtLeast(0f)
        var minLeft = 0f
        var maxLeft = (width - pageWidth).coerceAtLeast(0f)
        val radius = minOf(width, height) / 2f
        if (round) {
            val halfHeight = sqrt((radius * radius - pageWidth * pageWidth / 4f).coerceAtLeast(0f))
            minTop = (height / 2f - halfHeight).coerceAtMost(maxTop)
            maxTop = minOf(maxTop, height / 2f + halfHeight - pageHeight).coerceAtLeast(minTop)
        }
        val pageTop = (centeredTop + offsetY).coerceIn(minTop, maxTop)
        if (round) {
            val farthestY = maxOf(abs(pageTop - height / 2f), abs(pageTop + pageHeight - height / 2f))
            val halfWidth = sqrt((radius * radius - farthestY * farthestY).coerceAtLeast(0f))
            minLeft = width / 2f - halfWidth
            maxLeft = (width / 2f + halfWidth - pageWidth).coerceAtLeast(minLeft)
        }
        val pageLeft = (centeredLeft + offsetX).coerceIn(minLeft, maxLeft)
        offsetX = pageLeft - centeredLeft
        offsetY = pageTop - centeredTop
        webView.translationX = pageLeft
        webView.translationY = pageTop
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
            lastX = event.x
            lastY = event.y
            panning = false
            gestureDecided = false
            fromMargin = event.x < webView.translationX || event.x > webView.translationX + webView.width * pageScale ||
                event.y < webView.translationY || event.y > webView.translationY + webView.height * pageScale
            // A page drag belongs to the browser, not the enclosing swipe-to-dismiss surface.
            parent.requestDisallowInterceptTouchEvent(true)
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Keep observing until the gesture is classified. Scrolling and pinch gestures still
        // receive their original events unless a one-finger pan is claimed.
        parent.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount != 1) {
            gestureDecided = true
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !gestureDecided) {
            val dx = event.x - downX
            val dy = event.y - downY
            if (hypot(dx, dy) > touchSlop) {
                val diagonal = abs(dx) > abs(dy) * 0.5f && abs(dy) > abs(dx) * 0.5f
                val horizontal = abs(dx) > abs(dy) && !webView.canScrollHorizontally(if (dx > 0) -1 else 1)
                val shortPage = !webView.canScrollVertically(-1) && !webView.canScrollVertically(1)
                panning = fromMargin || diagonal || horizontal || shortPage
                gestureDecided = true
            }
        }
        // Interception sends ACTION_CANCEL to the WebView, preventing click-through after a pan.
        return panning
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1) {
            if (!gestureDecided) onInterceptTouchEvent(event)
            if (panning) {
                offsetX = (offsetX + event.x - lastX).coerceIn(-width / 4f, width / 4f)
                offsetY = (offsetY + event.y - lastY).coerceIn(-height / 4f, height / 4f)
                positionPage()
            }
            lastX = event.x
            lastY = event.y
        }
        return true
    }
}
