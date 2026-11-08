package org.bakasu.bakasu.ui.wear.component

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.ScreenScaffold
import kotlin.math.abs
import kotlin.math.hypot
import org.bakasu.bakasu.R
import org.json.JSONObject

/** Uses the full display; diagonal drags bring clipped round-screen corners within reach. */
@Composable
internal fun WearWebView(webView: WebView, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val round = LocalConfiguration.current.isScreenRound
    val screenActive = LocalScreenIsActive.current
    var scalePercent by remember(webView) { mutableIntStateOf(100) }
    var resetPosition by remember(webView) { mutableIntStateOf(0) }
    var toolsOpen by remember(webView) { mutableStateOf(false) }
    val scale = scalePercent / 100f
    val toolsLabel = stringResource(R.string.wear_web_tools)
    WearOperationsBackHandler(toolsOpen) { toolsOpen = false }
    ScreenScaffold(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(0.dp),
        timeText = {},
        overscrollEffect = null,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).clipToBounds()) {
            WearOperationsHost(
                menuKey = "web-tools",
                menuOpen = toolsOpen,
                menuActive = toolsOpen,
                menuContent = { onClosing ->
                    WearList(
                        onClosePanel = { toolsOpen = false },
                        onPanelClosing = onClosing,
                        onConfirm = { toolsOpen = false },
                        confirmLabel = stringResource(R.string.close),
                    ) { spec ->
                        item { WearPageHeader(spec, toolsLabel) }
                        item {
                            WearActionButton(spec, Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), {
                                toolsOpen = false
                                onBack()
                            })
                        }
                        item {
                            WearActionButton(spec, Icons.Rounded.CenterFocusStrong, stringResource(R.string.wear_web_reset), {
                                resetPosition++
                                scalePercent = 100
                                toolsOpen = false
                            })
                        }
                        item {
                            WearActionButton(spec, Icons.Rounded.ZoomIn, stringResource(R.string.wear_web_scale, scalePercent), {
                                scalePercent = if (scalePercent >= 100) 60 else scalePercent + 20
                            })
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
    private var pullingTools = false
    private var gestureDecided = false
    private var gesture = 0
    private var atTop: Boolean? = null
    private var diagonalCandidate = false
    private var pull = 0f
    private var interactionEnabled = true
    private var haptic: (HapticFeedbackType) -> Unit = {}
    private var onStart: () -> Float = { 0f }
    private var onPull: (Float) -> Unit = {}
    private var onEnd: (Boolean) -> Unit = {}
    private var crown = 0f
    private var lastCrown = 0L
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
        onStart: () -> Float,
        onPull: (Float) -> Unit,
        onEnd: (Boolean) -> Unit,
    ) {
        if (interactionEnabled && !enabled) {
            gesture++
            crown = 0f
        }
        interactionEnabled = enabled
        this.haptic = haptic
        this.onStart = onStart
        this.onPull = onPull
        this.onEnd = onEnd
        if (pageScale != scale || round != isRound || resetPosition != reset) {
            if (resetPosition != reset) {
                offsetX = 0f
                offsetY = 0f
            }
            pageScale = scale
            round = isRound
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
                let e = document.elementFromPoint(x, y), top = true, scrollable = false;
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
        if (pixels >= 0f || now - lastCrown > 600) crown = 0f
        lastCrown = now
        queryScroll(point) { top, _ ->
            if (top && pixels < 0f) {
                if (crown == 0f) haptic(HapticFeedbackType.SegmentTick)
                crown += -pixels
                if (crown >= pullThreshold * 3) {
                    crown = 0f
                    haptic(HapticFeedbackType.Confirm)
                    onEnd(true)
                } else {
                    onPull(crown / 3)
                }
            } else {
                crown = 0f
                onPull(0f)
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
                panning = false
                pullingTools = false
                gestureDecided = false
                diagonalCandidate = false
                atTop = null
                crown = 0f
                val point = pagePoint(event.x, event.y)
                queryScroll(point) { top, scrollable ->
                    if (!gestureDecided) atTop = top
                    // Tapping a fixed footer does not replace the last scrollable target.
                    if (scrollable) rotaryTarget = point
                }
                parent.requestDisallowInterceptTouchEvent(true)
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
        val diagonal = abs(dx) > abs(dy) * 0.65f && abs(dy) > abs(dx) * 0.65f
        if (round && diagonal) {
            // Require both axes and two consistent movements, not a vertical swipe's first jitter.
            if (!diagonalCandidate) {
                diagonalCandidate = true
                lastX = event.x
                lastY = event.y
                return
            }
            panning = abs(dx) > touchSlop * 2 && abs(dy) > touchSlop * 2 &&
                (event.x - lastX) * dx > 0 && (event.y - lastY) * dy > 0
            if (!panning) return
        } else {
            pullingTools = dy > 0f && abs(dx) < abs(dy) * 0.5f && atTop == true
            if (pullingTools) pull = onStart()
        }
        gestureDecided = true
        // Unknown or non-top scroll chains stay with WebView for the rest of this gesture.
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = panning || pullingTools

    private fun moveHost(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1) {
            if (panning) {
                offsetX += event.x - lastX
                offsetY += event.y - lastY
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
        if (pullingTools && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)) {
            onEnd(event.actionMasked == MotionEvent.ACTION_UP && pull >= pullThreshold)
        }
        return true
    }
}
