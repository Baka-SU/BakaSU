package org.bakasu.bakasu.ui.wear.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.hierarchicalFocusGroup

/** The list and native WebView use the same finger-following reveal and release animation. */
@Composable
internal fun wearPanelReveal(
    pullPixels: Float,
    height: Float,
    opening: Boolean,
    dragging: Boolean,
    hosted: Boolean,
    dismissed: Boolean,
    active: Boolean,
    onOpen: () -> Unit,
): Float {
    val progress by animateFloatAsState(
        targetValue = when {
            dismissed -> 0f
            opening -> 1f
            else -> (pullPixels / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        },
        animationSpec = if (LocalReduceMotion.current || dragging || hosted || dismissed || (pullPixels > 0f && !opening)) snap() else tween(180),
        label = "wear-panel-reveal",
        finishedListener = { if (opening && it == 1f && active) onOpen() },
    )
    return progress
}

internal fun wearPanelPageScale(progress: Float): Float = 1f - 0.12f * progress.coerceIn(0f, 1.2f)

/** Keeps a menu in its owning navigation entry, including while that entry animates away. */
@Composable
internal fun WearOperationsHost(
    menuKey: Any?,
    menuOpen: Boolean,
    menuActive: Boolean,
    menuContent: @Composable (onClosing: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val pull = remember { mutableFloatStateOf(if (menuOpen) 1f else 0f) }
    var dismissals by remember { mutableIntStateOf(0) }
    var wasOpen by remember { mutableStateOf(menuOpen) }
    val menuClosed = wasOpen && !menuOpen
    val currentDismissals = if (menuClosed) dismissals + 1 else dismissals
    LaunchedEffect(menuOpen) {
        if (wasOpen && !menuOpen) {
            pull.floatValue = 0f
            dismissals++
        }
        wasOpen = menuOpen
    }
    var height by remember { mutableFloatStateOf(1f) }
    val screenActive = LocalScreenIsActive.current
    CompositionLocalProvider(
        LocalWearPanelDismissals provides currentDismissals,
        LocalWearPanelHosted provides menuOpen,
        LocalWearPanelPull provides pull,
    ) {
        Box(Modifier.fillMaxSize()) {
            // A preview must leave crown focus on its page until the menu finishes opening.
            Box(Modifier.fillMaxSize().hierarchicalFocusGroup(active = screenActive && !menuOpen)) {
                content()
            }
            if (!menuClosed && menuKey != null && (menuOpen || pull.floatValue > 0f)) {
                // Keep position while a child page covers the menu, but discard it when the reveal ends.
                val stateHolder = rememberSaveableStateHolder()
                val dim = WearPanelMaxDim * (pull.floatValue / WearPanelDimReach).coerceIn(0f, 1f)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)).clearAndSetSemantics {})
                Box(
                    Modifier.fillMaxSize().hierarchicalFocusGroup(active = menuActive && screenActive)
                        // Keep horizontal back and its haptics local to the active menu.
                        .then(
                            if (menuActive && screenActive) {
                                Modifier.systemGestureExclusion().pointerInput(Unit) {
                                    detectHorizontalDragGestures { change, _ -> change.consume() }
                                }
                            } else {
                                Modifier
                            },
                        )
                        .onSizeChanged { height = it.height.toFloat() }
                        .graphicsLayer { translationY = if (menuOpen) 0f else (pull.floatValue - 1f) * height }
                        .clipToBounds().then(if (menuActive) Modifier else Modifier.clearAndSetSemantics {}),
                ) {
                    CompositionLocalProvider(LocalScreenIsActive provides (menuActive && screenActive)) {
                        stateHolder.SaveableStateProvider(menuKey) {
                            menuContent { dismissals++ }
                        }
                    }
                }
            }
        }
    }
}

/** Menus ignore system back gestures while keeping button and accessibility Back available. */
@Composable
internal fun WearOperationsBackHandler(enabled: Boolean, onBack: () -> Unit) {
    PredictiveBackHandler(enabled) { progress ->
        var gestured = false
        progress.collect { gestured = true }
        if (!gestured) onBack()
    }
}
