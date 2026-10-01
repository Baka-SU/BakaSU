package com.resukisu.resukisu.ui.component.wear

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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

/**
 * At the list end, a short upward pull refreshes; holding past 48dp for two seconds opens the panel.
 * A panel closes after the same hold while pulling down at its top. The pull begins when scrolling
 * reaches the boundary, including when that happens during an existing touch gesture.
 *
 * Apply to the entire scaffold, including its edge button. Only the list follows [displacement],
 * keeping pointer coordinates stable. [pullProgress] is positive at the top, negative at the end.
 * Rotary input continues to scroll the list without triggering these touch actions.
 */
@Composable
internal fun Modifier.wearRefreshGesture(
    listState: TransformingLazyColumnState,
    displacement: MutableFloatState,
    pullProgress: MutableFloatState,
    enabled: Boolean,
    onRefresh: (() -> Unit)?,
    onOpenPanel: (() -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null,
    panelLabel: String? = null,
): Modifier {
    if (onRefresh == null && onOpenPanel == null && onClosePanel == null) return this
    val active by rememberUpdatedState(enabled)
    val refresh by rememberUpdatedState(onRefresh)
    val open by rememberUpdatedState(onOpenPanel)
    val close by rememberUpdatedState(onClosePanel)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val refreshLabel = stringResource(R.string.wear_refresh)
    val closeLabel = stringResource(R.string.wear_close_panel)
    return this.pointerInput(listState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var anchorY: Float? = null
            var anchorX = down.position.x
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
                        // The hold ran its full time without being canceled or released.
                        if (active && !canceled && holdStarted != null) {
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
                        displacement.floatValue = 0f
                        continue
                    }
                    if (anchorY == null) {
                        anchorY = pointer.previousPosition.y
                        anchorX = pointer.previousPosition.x
                    }
                    pull = direction * (pointer.position.y - anchorY)
                    // Fingers drift sideways on a round screen; only a mostly horizontal drag from the
                    // boundary cancels, measured from where the boundary was reached.
                    if (abs(pointer.position.x - anchorX) > maxOf(threshold, pull)) canceled = true
                    // Small reverse jitter while holding still is tolerated; a real reverse drag cancels.
                    if (canceled || pull < -threshold / 4) {
                        canceled = true
                        holdStarted = null
                        displacement.floatValue = 0f
                        continue
                    }
                    // Once the hold has started, it survives the finger easing back slightly.
                    val holding = pull >= threshold || (holdStarted != null && pull >= threshold * 0.75f)
                    if (holding && (open != null || returning)) {
                        if (holdStarted == null) {
                            holdStarted = SystemClock.uptimeMillis()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        displacement.floatValue = direction * threshold * 0.25f
                    } else {
                        holdStarted = null
                        displacement.floatValue = 0f
                    }
                    if (pull > threshold / 2) pointer.consume()
                }
            } finally {
                displacement.floatValue = 0f
            }
        }
    }.semantics {
        if (enabled) customActions = buildList {
            if (onRefresh != null) add(CustomAccessibilityAction(refreshLabel) { onRefresh(); true })
            if (onOpenPanel != null && panelLabel != null) add(CustomAccessibilityAction(panelLabel) { onOpenPanel(); true })
            if (onClosePanel != null) add(CustomAccessibilityAction(closeLabel) { onClosePanel(); true })
        }
    }
}
