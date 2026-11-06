package org.bakasu.bakasu.ui.wear.component

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenWith
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlin.math.ceil
import org.bakasu.bakasu.R

/** Full-window web content, with independent page density and explicit corner access on round screens. */
@Composable
internal fun WearWebView(webView: WebView, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val round = LocalConfiguration.current.isScreenRound
    var scalePercent by remember(webView) { mutableIntStateOf(80) }
    var offset by remember(webView, round) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var toolsOpen by remember(webView) { mutableStateOf(false) }
    var moving by remember(webView, round) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val scale = scalePercent / 100f

    LaunchedEffect(webView, toolsOpen, moving) {
        if (!toolsOpen && !moving) focus.requestFocus()
    }
    BackHandler(toolsOpen || moving) {
        toolsOpen = false
        moving = false
    }
    // Web content is a viewport, not a Wear list: no list padding or reserved EdgeButton height.
    ScreenScaffold(
        modifier = modifier.imePadding(),
        contentPadding = PaddingValues(0.dp),
        timeText = {},
        overscrollEffect = null,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).clipToBounds().onSizeChanged { size = it }) {
            AndroidView(
                factory = { WearWebViewViewport(it, webView) },
                update = { it.update(scale, offset) },
                onRelease = { it.removeView(webView) },
                modifier = Modifier.fillMaxSize()
                    .onRotaryScrollEvent {
                        if (toolsOpen || moving) return@onRotaryScrollEvent false
                        webView.scrollBy(0, (it.verticalScrollPixels / scale).toInt())
                        true
                    }
                    .focusRequester(focus).focusable(),
            )
            if (toolsOpen || moving) {
                val moveHint = stringResource(R.string.wear_web_move_hint)
                // Claim the whole gesture from DOWN to UP. Moving the viewport must never click
                // the page underneath or turn into the parent's swipe-to-dismiss gesture.
                Box(
                    Modifier.fillMaxSize()
                        .then(if (toolsOpen) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
                        .semantics { if (moving) contentDescription = moveHint }
                        .pointerInput(moving, size) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                down.consume()
                                var distance = Offset.Zero
                                var dragging = false
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (moving && change != null && change.pressed) {
                                        val delta = change.positionChange()
                                        distance += delta
                                        if (dragging || distance.getDistance() > viewConfiguration.touchSlop) {
                                            val movement = if (dragging) delta else distance
                                            offset = Offset(
                                                (offset.x + movement.x).coerceIn(-size.width / 4f, size.width / 4f),
                                                (offset.y + movement.y).coerceIn(-size.height / 4f, size.height / 4f),
                                            )
                                            dragging = true
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                )
            }
            if (toolsOpen) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalIconButton(onClick = {
                            toolsOpen = false
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                        }
                        if (round) {
                            FilledTonalIconButton(onClick = {
                                toolsOpen = false
                                moving = true
                            }) {
                                Icon(Icons.Rounded.OpenWith, stringResource(R.string.wear_web_move))
                            }
                        }
                        FilledTonalIconButton(onClick = {
                            offset = Offset.Zero
                            scalePercent = 80
                            toolsOpen = false
                        }) {
                            Icon(Icons.Rounded.CenterFocusStrong, stringResource(R.string.wear_web_reset))
                        }
                    }
                    FilledTonalButton(onClick = { scalePercent = if (scalePercent >= 100) 60 else scalePercent + 20 }) {
                        Text(stringResource(R.string.wear_web_scale, scalePercent))
                    }
                    FilledTonalIconButton(onClick = { toolsOpen = false }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.close))
                    }
                }
            } else {
                if (moving) {
                    Text(
                        stringResource(R.string.wear_web_move_hint),
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.7f).padding(top = 24.dp)
                            .background(MaterialTheme.colorScheme.background).padding(4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
                FilledTonalIconButton(
                    onClick = { if (moving) moving = false else toolsOpen = true },
                    onLongClick = if (round && !moving) ({ moving = true }) else null,
                    onLongClickLabel = if (round && !moving) stringResource(R.string.wear_web_move) else null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                ) {
                    Icon(
                        if (moving) Icons.Rounded.Check else Icons.Rounded.Tune,
                        stringResource(if (moving) R.string.wear_web_move_done else R.string.wear_web_tools),
                    )
                }
            }
        }
    }
}

/**
 * Measure a larger viewport before scaling it, so the result still fills the entire window. This
 * shrinks CSS pixels as well as text, including pages that disable browser zoom. Android's native
 * child transforms also map touch coordinates back to the WebView after scaling and panning.
 */
// Created only by AndroidView with an existing module/browser WebView; never inflated from XML.
@SuppressLint("ViewConstructor")
private class WearWebViewViewport(context: Context, private val webView: WebView) : ViewGroup(context) {
    private var pageScale = 0.8f

    init {
        // Configure only watch WebViews, before their first layout/load.
        webView.settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        addView(webView)
        webView.pivotX = 0f
        webView.pivotY = 0f
    }

    fun update(scale: Float, offset: Offset) {
        if (pageScale != scale) {
            pageScale = scale
            requestLayout()
        }
        webView.scaleX = scale
        webView.scaleY = scale
        webView.translationX = offset.x
        webView.translationY = offset.y
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        webView.measure(
            MeasureSpec.makeMeasureSpec(ceil(width / pageScale).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(ceil(height / pageScale).toInt(), MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
    }
}
