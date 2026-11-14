package org.bakasu.bakasu.ui.wear.component

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.delay
import org.bakasu.bakasu.R
import org.json.JSONObject

/** Uses the full display; inward drags from the round edge reveal one fixed page corner. */
@Composable
internal fun WearWebView(webView: WebView, modifier: Modifier = Modifier) {
    val round = LocalConfiguration.current.isScreenRound
    val screenActive = LocalScreenIsActive.current
    val colors = MaterialTheme.colorScheme
    var lightBackground by remember(webView) { mutableStateOf(false) }
    LaunchedEffect(webView, screenActive) {
        while (screenActive) {
            webView.evaluateJavascript(
                """(() => {
                    const nodes = [document.documentElement, document.body].filter(Boolean);
                    const luminance = nodes.map(node => {
                        const rgba = getComputedStyle(node).backgroundColor.match(/[\d.]+/g)?.map(Number);
                        if (!rgba || (rgba[3] ?? 1) === 0) return null;
                        return (rgba[0] * .2126 + rgba[1] * .7152 + rgba[2] * .0722) / 255;
                    }).filter(value => value !== null);
                    return luminance.length ? luminance.reduce((a,b) => a+b) / luminance.length : 0;
                })()
                """.trimIndent(),
            ) { lightBackground = (it.toFloatOrNull() ?: 0f) > 0.5f }
            delay(1000)
        }
    }
    val hintColor by animateColorAsState(
        if (lightBackground) colors.background else colors.onSurface,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(200),
        label = "web-menu-hint-color",
    )
    var scaleIndex by remember(webView) { mutableIntStateOf(0) }
    var textPercent by remember(webView) { mutableIntStateOf(webView.settings.textZoom) }
    var resetPosition by remember(webView) { mutableIntStateOf(0) }
    var toolsOpen by remember(webView) { mutableStateOf(false) }
    val scale = listOf(0.6f, 0.8f, 1f)[scaleIndex]
    val scalePercent = listOf(100, 133, 167)[scaleIndex]
    SideEffect { webView.settings.textZoom = textPercent }
    val toolsLabel = stringResource(R.string.wear_web_tools)
    WearOperationsBackHandler(toolsOpen && screenActive) { toolsOpen = false }
    ScreenScaffold(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(0.dp),
        timeText = {},
        overscrollEffect = null,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).clipToBounds()) {
            WearOperationsHost(
                menuKey = "web-tools",
                hintColor = hintColor,
                showHint = true,
                revealFromBottom = true,
                menuOpen = toolsOpen,
                menuActive = toolsOpen,
                menuContent = { onClosing ->
                    WearList(onClosePanel = { toolsOpen = false }, onPanelClosing = onClosing, closePanelOnPullDown = true) { spec ->
                        item { WearPageHeader(spec, toolsLabel) }
                        item {
                            WearActionButton(spec, Icons.Rounded.CenterFocusStrong, stringResource(R.string.wear_web_reset), {
                                resetPosition++
                                scaleIndex = 0
                                toolsOpen = false
                            }, colors = ButtonDefaults.filledTonalButtonColors())
                        }
                        item {
                            WearActionButton(spec, Icons.Rounded.ZoomIn, stringResource(R.string.wear_web_scale, scalePercent), {
                                scaleIndex = (scaleIndex + 1) % 3
                            }, colors = ButtonDefaults.filledTonalButtonColors())
                        }
                        item {
                            WearActionButton(spec, Icons.Rounded.TextFields, stringResource(R.string.wear_web_text_scale, textPercent), {
                                textPercent = if (textPercent >= 150) 100 else textPercent + 25
                            }, colors = ButtonDefaults.filledTonalButtonColors())
                        }
                    }
                },
            ) {
                WearWebViewContent(webView, scale, round, resetPosition, screenActive, toolsOpen, toolsLabel) { toolsOpen = true }
            }
        }
    }
}

@Composable
private fun WearWebViewContent(
    webView: WebView,
    scale: Float,
    round: Boolean,
    reset: Int,
    active: Boolean,
    toolsOpen: Boolean,
    toolsLabel: String,
    onOpen: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var cornerFeedback by remember(webView) { mutableIntStateOf(0) }
    var feedbackCorner by remember(webView) { mutableStateOf(WebCorner.Default) }
    val edgeAlpha = remember(webView) { Animatable(0f) }
    LaunchedEffect(cornerFeedback) {
        if (cornerFeedback > 0) {
            edgeAlpha.snapTo(1f)
            edgeAlpha.animateTo(0f, tween(350))
        }
    }
    var viewport by remember(webView) { mutableStateOf<WearWebViewViewport?>(null) }
    var height by remember { mutableFloatStateOf(1f) }
    var pull by remember { mutableFloatStateOf(0f) }
    var opening by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val dismissals = LocalWearPanelDismissals.current
    var lastDismissal by remember { mutableIntStateOf(dismissals) }
    val dismissed = lastDismissal != dismissals
    val panelPull = LocalWearPanelPull.current
    val progress = wearPanelReveal(pull, height, opening, dragging, toolsOpen, dismissed, active, onOpen)
    val reveal = if (toolsOpen) {
        panelPull.floatValue
    } else if (dismissed) {
        0f
    } else {
        progress
    }
    SideEffect { if (!toolsOpen && active) panelPull.floatValue = reveal }
    LaunchedEffect(dismissals, active) {
        if (dismissed || !active) {
            lastDismissal = dismissals
            opening = false
            dragging = false
            pull = 0f
        }
    }
    AndroidView(
        factory = { WearWebViewViewport(it, webView).also { view -> viewport = view } },
        update = {
            it.update(
                scale,
                round,
                reset,
                active && !toolsOpen,
                haptic = { feedback -> haptic.performHapticFeedback(feedback) },
                onCornerLimit = {
                    feedbackCorner = it
                    cornerFeedback++
                },
                onStart = {
                    val held = progress * height
                    opening = false
                    dragging = true
                    held
                },
                onPull = { distance -> pull = distance },
                onEnd = { commit ->
                    dragging = false
                    opening = commit
                    pull = 0f
                },
            )
        },
        onRelease = { it.removeView(webView) },
        modifier = Modifier.fillMaxSize()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                scaleX = wearPanelPageScale(reveal)
                scaleY = scaleX
            }
            .semantics {
                if (active && !toolsOpen) {
                    customActions = listOf(
                        CustomAccessibilityAction(toolsLabel) {
                            opening = true
                            true
                        },
                    )
                }
            }
            .onRotaryScrollEvent {
                if (toolsOpen || !active) return@onRotaryScrollEvent false
                viewport?.rotate(it.verticalScrollPixels)
                true
            }
            .requestFocusOnHierarchyActive().focusable(active && !toolsOpen),
    )
    val edgeColor = MaterialTheme.colorScheme.primary
    if (active && !toolsOpen && edgeAlpha.value > 0f) {
        Canvas(Modifier.fillMaxSize().testTag("web-corner-feedback")) {
            val inset = 3.dp.toPx()
            listOf(if (feedbackCorner.x > 0) 150f else -30f, if (feedbackCorner.y > 0) 240f else 60f).forEach { start ->
                drawArc(
                    edgeColor.copy(alpha = edgeAlpha.value),
                    start,
                    60f,
                    false,
                    Offset(inset, inset),
                    Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
    }
}

/** A full, exactly measured responsive viewport with native child touch-coordinate transforms. */
@SuppressLint("ViewConstructor")
private class WearWebViewViewport(context: Context, private val webView: WebView) : ViewGroup(context) {
    private var pageScale = 1f
    private var round = false
    private var resetPosition = 0
    private var offsetX = 0f
    private var offsetY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var panning = false
    private var corner = WebCorner.Default
    private var cornerTarget = WebCorner.Default
    private var cornerCandidate: WebCorner? = null
    private var cornerProgress = 0f
    private var restoring: ValueAnimator? = null
    private var hostCanceled = false
    private var pullingTools = false
    private var gestureDecided = false
    private var gesture = 0
    private var atTop: Boolean? = null
    private var pull = 0f
    private var interactionEnabled = true
    private var haptic: (HapticFeedbackType) -> Unit = {}
    private var onStart: () -> Float = { 0f }
    private var onPull: (Float) -> Unit = {}
    private var onEnd: (Boolean) -> Unit = {}
    private var onCornerLimit: (WebCorner) -> Unit = {}
    private var cornerAtLimit = false
    private var rotaryTarget: Pair<Float, Float>? = null
    private val configuration = ViewConfiguration.get(context)
    private val touchSlop = configuration.scaledTouchSlop
    private val pullThreshold = WearPullThreshold.value * resources.displayMetrics.density

    init {
        webView.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = true
            displayZoomControls = false
        }
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        webView.pivotX = 0f
        webView.pivotY = 0f
    }

    fun update(
        scale: Float,
        isRound: Boolean,
        reset: Int,
        enabled: Boolean,
        haptic: (HapticFeedbackType) -> Unit,
        onCornerLimit: (WebCorner) -> Unit,
        onStart: () -> Float,
        onPull: (Float) -> Unit,
        onEnd: (Boolean) -> Unit,
    ) {
        if (interactionEnabled && !enabled) {
            gesture++
            finishCorner(false)
        }
        interactionEnabled = enabled
        this.haptic = haptic
        this.onCornerLimit = onCornerLimit
        this.onStart = onStart
        this.onPull = onPull
        this.onEnd = onEnd
        if (pageScale != scale || round != isRound || resetPosition != reset) {
            if (resetPosition != reset) {
                restoring?.cancel()
                restoring = null
                corner = WebCorner.Default
                offsetX = 0f
                offsetY = 0f
            }
            pageScale = scale
            round = isRound
            if (!round) corner = WebCorner.Default
            offsetX = corner.x * width / 4f
            offsetY = corner.y * height / 4f
            resetPosition = reset
            rotaryTarget = null
            gesture++
            requestLayout()
        }
        webView.scaleX = scale
        webView.scaleY = scale
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        webView.measure(
            MeasureSpec.makeMeasureSpec((width / pageScale).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((height / pageScale).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
        if (!panning && restoring == null) {
            offsetX = corner.x * width / 4f
            offsetY = corner.y * height / 4f
        }
        positionPage()
    }

    private fun positionPage() {
        offsetX = if (round) offsetX.coerceIn(-width / 4f, width / 4f) else 0f
        offsetY = if (round) offsetY.coerceIn(-height / 4f, height / 4f) else 0f
        webView.translationX = (width - webView.width * pageScale) / 2f + offsetX
        webView.translationY = (height - webView.height * pageScale) / 2f + offsetY
    }

    private fun pagePoint(x: Float, y: Float) = Pair(
        ((x - webView.translationX) / pageScale / webView.width).coerceIn(0f, 1f),
        ((y - webView.translationY) / pageScale / webView.height).coerceIn(0f, 1f),
    )

    /** Reads the hit element's complete scroll chain without changing the module's DOM or handlers. */
    private fun queryScroll(point: Pair<Float, Float>, result: (atTop: Boolean, scrollable: Boolean) -> Unit) {
        val requestGesture = gesture
        val url = webView.url
        if (!webView.settings.javaScriptEnabled) {
            result(!webView.canScrollVertically(-1), webView.canScrollVertically(-1) || webView.canScrollVertically(1))
            return
        }
        webView.evaluateJavascript(
            """(() => {
                const v = window.visualViewport;
                const x = ${point.first} * (v ? v.width : innerWidth) + (v ? v.offsetLeft : 0);
                const y = ${point.second} * (v ? v.height : innerHeight) + (v ? v.offsetTop : 0);
                let e = document.elementFromPoint(x, y), top = !v || v.offsetTop <= 1;
                let scrollable = !!v && v.height < innerHeight - 1;
                if (!e) return null;
                while (e) {
                    const s = getComputedStyle(e);
                    if (e === document.scrollingElement || /auto|scroll|overlay/.test(s.overflowY)) {
                        if (e.scrollHeight > e.clientHeight + 1) {
                            scrollable = true;
                            if (e.scrollTop > 1) top = false;
                        }
                    }
                    e = e.parentElement;
                }
                const root = document.scrollingElement;
                if (root && root.scrollTop > 1) top = false;
                return {top, scrollable};
            })()
            """.trimIndent(),
        ) { value ->
            if (requestGesture == gesture && interactionEnabled && webView.parent === this && webView.url == url) {
                val state = runCatching { JSONObject(value) }.getOrNull()
                result(state?.optBoolean("top") == true, state?.optBoolean("scrollable") == true)
            }
        }
    }

    fun rotate(pixels: Float) {
        val point = rotaryTarget ?: pagePoint(width / 2f, height / 2f)
        val now = SystemClock.uptimeMillis()
        val coords = MotionEvent.PointerCoords().apply {
            x = point.first * webView.width
            y = point.second * webView.height
            setAxisValue(MotionEvent.AXIS_VSCROLL, -pixels / pageScale / configuration.scaledVerticalScrollFactor)
        }
        val props = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        try {
            webView.dispatchGenericMotionEvent(event)
        } finally {
            event.recycle()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture++
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                // A new finger starts from the last committed position, even when the native
                // settle animation has not delivered its final frame yet.
                restoring?.cancel()
                restoring = null
                offsetX = corner.x * width / 4f
                offsetY = corner.y * height / 4f
                positionPage()
                panning = false
                cornerAtLimit = false
                pullingTools = false
                gestureDecided = false
                hostCanceled = false
                cornerCandidate = if (round && restoring == null) cornerCandidate(event.x, event.y) else null
                atTop = null
                onPull(0f)
                val point = pagePoint(event.x, event.y)
                queryScroll(point) { top, scrollable ->
                    if (!gestureDecided) atTop = top
                    // Tapping a fixed footer does not replace the last scrollable target.
                    if (scrollable) rotaryTarget = point
                }
                // Keep the platform/outer page's left-edge back gesture available. Interior
                // drags still belong to the webpage and its nested scrolling elements.
                parent.requestDisallowInterceptTouchEvent(event.x > configuration.scaledEdgeSlop)
            }

            MotionEvent.ACTION_MOVE -> {
                classify(event)
                // Only an explicitly recognized host gesture may cancel a child's interception veto.
                if (panning || pullingTools) {
                    super.requestDisallowInterceptTouchEvent(false)
                    moveHost(event)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureDecided = true
                hostCanceled = true
                finishCorner(false)
                if (pullingTools) {
                    pull = 0f
                    onEnd(false)
                }
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            gesture++
            parent.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    private fun classify(event: MotionEvent) {
        if (event.pointerCount != 1 || gestureDecided || event.actionMasked != MotionEvent.ACTION_MOVE) return
        val dx = event.x - downX
        val dy = event.y - downY
        if (hypot(dx, dy) <= touchSlop) return
        if (downX <= configuration.scaledEdgeSlop && dx > touchSlop && abs(dx) > abs(dy) * 1.5f) {
            gestureDecided = true
            parent.requestDisallowInterceptTouchEvent(false)
            return
        }
        val diagonal = abs(dx) > abs(dy) * 0.65f && abs(dy) > abs(dx) * 0.65f
        val candidate = cornerCandidate
        if (diagonal && candidate != null && dx * (candidate.x - corner.x) > 0f && dy * (candidate.y - corner.y) > 0f) {
            if (abs(dx) <= touchSlop * 2 || abs(dy) <= touchSlop * 2) return
            cornerTarget = candidate
            cornerProgress = 0f
            panning = true
        } else {
            pullingTools = dy > 0f && abs(dx) < abs(dy) * 0.5f && atTop == true
            if (pullingTools) pull = onStart()
        }
        gestureDecided = true
        // Unknown or non-top scroll chains stay with WebView for the rest of this gesture.
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = panning || pullingTools

    private fun moveHost(event: MotionEvent) {
        if (!hostCanceled && event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1) {
            if (panning) {
                val targetX = (cornerTarget.x - corner.x) * width / 4f
                val targetY = (cornerTarget.y - corner.y) * height / 4f
                cornerProgress = (minOf((event.x - downX) / targetX, (event.y - downY) / targetY) * 0.6f).coerceIn(0f, 1f)
                if (cornerProgress == 1f && !cornerAtLimit) {
                    haptic(HapticFeedbackType.GestureThresholdActivate)
                    onCornerLimit(if (cornerTarget == WebCorner.Default) corner else cornerTarget)
                }
                cornerAtLimit = cornerProgress == 1f
                offsetX = corner.x * width / 4f + targetX * cornerProgress
                offsetY = corner.y * height / 4f + targetY * cornerProgress
                positionPage()
            } else if (pullingTools) {
                val before = pull
                pull = (pull + event.y - lastY).coerceAtLeast(0f)
                if (before < pullThreshold && pull >= pullThreshold) haptic(HapticFeedbackType.GestureThresholdActivate)
                onPull(pull)
            }
            lastX = event.x
            lastY = event.y
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            val commit = event.actionMasked == MotionEvent.ACTION_UP && !hostCanceled
            if (pullingTools) onEnd(commit && pull >= pullThreshold)
            finishCorner(commit && cornerProgress >= 0.5f)
        }
        return true
    }

    private fun cornerCandidate(x: Float, y: Float): WebCorner? {
        val nx = x / width - 0.5f
        val ny = y / height - 0.5f
        val expansion = minOf(0.03f, configuration.scaledEdgeSlop.toFloat() / minOf(width, height).coerceAtLeast(1) / 2)
        val atCornerEdge = abs(nx) in (0.2f - expansion)..(0.43f + expansion) && abs(ny) in (0.2f - expansion)..(0.43f + expansion) && hypot(nx, ny) <= 0.5f
        if (corner == WebCorner.Default) {
            return if (atCornerEdge) WebCorner.entries.firstOrNull { it.x * nx < 0f && it.y * ny < 0f } else null
        }
        val atCenter = abs(nx) <= 0.23f && abs(ny) <= 0.23f
        val atOppositeEdge = atCornerEdge && nx * corner.x > 0f && ny * corner.y > 0f
        return if (atCenter || atOppositeEdge) WebCorner.Default else null
    }

    /** A release always settles on one of five positions; another corner requires returning first. */
    private fun finishCorner(commit: Boolean) {
        if (!panning) return
        panning = false
        if (commit) corner = cornerTarget
        rotaryTarget = null
        val startX = offsetX
        val startY = offsetY
        restoring?.cancel()
        restoring = null
        if (startX == corner.x * width / 4f && startY == corner.y * height / 4f) return
        restoring = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160
            addUpdateListener {
                val fraction = it.animatedValue as Float
                offsetX = startX + (corner.x * width / 4f - startX) * fraction
                offsetY = startY + (corner.y * height / 4f - startY) * fraction
                positionPage()
                if (fraction == 1f) {
                    restoring = null
                    if (commit && !cornerAtLimit) {
                        cornerAtLimit = true
                        haptic(HapticFeedbackType.GestureThresholdActivate)
                        onCornerLimit(if (cornerTarget == WebCorner.Default) corner else cornerTarget)
                    }
                }
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        restoring?.cancel()
        restoring = null
        super.onDetachedFromWindow()
    }
}

private enum class WebCorner(val x: Int, val y: Int) {
    Default(0, 0),
    TopLeft(1, 1),
    TopRight(-1, 1),
    BottomLeft(1, -1),
    BottomRight(-1, -1),
}
