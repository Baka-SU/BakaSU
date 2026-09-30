package com.resukisu.resukisu.ui.component.wear

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import com.resukisu.resukisu.R
import kotlin.math.abs

/** Only touch drags beyond a list boundary participate; rotary and ordinary scrolling do not. */
@Composable
internal fun Modifier.wearRefreshGesture(
    listState: TransformingLazyColumnState,
    enabled: Boolean,
    onRefresh: (() -> Unit)?,
    onOpenPanel: (() -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null,
    panelLabel: String? = null,
): Modifier {
    val active by rememberUpdatedState(enabled)
    val refresh by rememberUpdatedState(onRefresh)
    val open by rememberUpdatedState(onOpenPanel)
    val close by rememberUpdatedState(onClosePanel)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    var displacement by remember { mutableFloatStateOf(0f) }
    val animatedDisplacement by animateFloatAsState(displacement,
        animationSpec = tween(if (displacement == 0f) 150 else 2_000), label = "wear-panel-damping")
    val refreshLabel = stringResource(R.string.wear_refresh)
    val backLabel = stringResource(R.string.wear_back)
    return this.graphicsLayer { translationY = animatedDisplacement }.pointerInput(listState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var anchorY: Float? = null
            var pull = 0f
            var holdStarted: Long? = null
            var canceled = !active
            var submitted = false
            val returning = close != null
            val direction = if (returning) 1f else -1f
            try {
                while (true) {
                    val remaining = holdStarted?.let { 2_000L - (SystemClock.uptimeMillis() - it) }
                    val event = if (remaining != null) {
                        withTimeoutOrNull(remaining.coerceAtLeast(1L)) {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                    } else awaitPointerEvent(PointerEventPass.Initial)
                    if (event == null) {
                        if (active && !canceled && pull >= threshold) {
                            submitted = true
                            if (returning) close?.invoke() else open?.invoke()
                        }
                        break
                    }
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) {
                        if (!submitted && !canceled && active && !returning && pull >= threshold) refresh?.invoke()
                        break
                    }
                    if (event.changes.count { it.pressed } != 1 || !active) canceled = true
                    val atBoundary = if (returning) !listState.canScrollBackward else !listState.canScrollForward
                    if (!atBoundary) {
                        anchorY = null
                        holdStarted = null
                        pull = 0f
                        displacement = 0f
                        continue
                    }
                    if (anchorY == null) anchorY = pointer.previousPosition.y
                    pull = direction * (pointer.position.y - anchorY)
                    if (abs(pointer.position.x - down.position.x) > maxOf(threshold / 2, pull)) canceled = true
                    if (canceled || pull < 0) {
                        canceled = true
                        holdStarted = null
                        displacement = 0f
                        continue
                    }
                    if (pull >= threshold && (open != null || returning)) {
                        if (holdStarted == null) {
                            holdStarted = SystemClock.uptimeMillis()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        displacement = direction * threshold * 0.25f
                    } else {
                        holdStarted = null
                        displacement = 0f
                    }
                    if (pull > threshold / 2) pointer.consume()
                }
            } finally {
                displacement = 0f
            }
        }
    }.semantics {
        if (enabled) customActions = buildList {
            if (onRefresh != null) add(CustomAccessibilityAction(refreshLabel) { onRefresh(); true })
            if (onOpenPanel != null && panelLabel != null) add(CustomAccessibilityAction(panelLabel) { onOpenPanel(); true })
            if (onClosePanel != null) add(CustomAccessibilityAction(backLabel) { onClosePanel(); true })
        }
    }
}
