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
 * Only touch drags beyond a list boundary participate; rotary and ordinary scrolling do not.
 *
 * Apply it to the whole screen (list and edge button), so a pull that starts on the edge button at
 * the end of the list still counts. [displacement] receives the damping offset; draw it on the list
 * only, so it does not shift the coordinates this gesture measures.
 */
@Composable
internal fun Modifier.wearRefreshGesture(
    listState: TransformingLazyColumnState,
    displacement: MutableFloatState,
    enabled: Boolean,
    onRefresh: (() -> Unit)?,
    onOpenPanel: (() -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null,
    panelLabel: String? = null,
): Modifier {
    // Lists without any boundary action keep their gestures untouched.
    if (onRefresh == null && onOpenPanel == null && onClosePanel == null) return this
    val active by rememberUpdatedState(enabled)
    val refresh by rememberUpdatedState(onRefresh)
    val open by rememberUpdatedState(onOpenPanel)
    val close by rememberUpdatedState(onClosePanel)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val refreshLabel = stringResource(R.string.wear_refresh)
    val backLabel = stringResource(R.string.wear_back)
    return this.pointerInput(listState, threshold) {
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
                        displacement.floatValue = 0f
                        continue
                    }
                    if (anchorY == null) anchorY = pointer.previousPosition.y
                    pull = direction * (pointer.position.y - anchorY)
                    if (abs(pointer.position.x - down.position.x) > maxOf(threshold / 2, pull)) canceled = true
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
            if (onClosePanel != null) add(CustomAccessibilityAction(backLabel) { onClosePanel(); true })
        }
    }
}
