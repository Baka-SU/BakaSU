package com.resukisu.resukisu.ui.component.wear

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * At the list end, a short upward pull refreshes; holding past 48dp for two seconds opens the panel.
 * A panel closes after the same hold while pulling down at its top. A gesture commits one action,
 * and must start at the boundary so ordinary scrolling never opens or closes a panel.
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
    val openPanel by rememberUpdatedState(onOpenPanel)
    val closePanel by rememberUpdatedState(onClosePanel)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val refreshLabel = stringResource(R.string.wear_refresh)
    val closeLabel = stringResource(R.string.wear_close_panel)
    return this.pointerInput(listState, threshold) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val startedAtTop = !listState.canScrollBackward
                val startedAtEnd = !listState.canScrollForward
                var pullDown = 0f
                var pullUp = 0f
                var crossed = false
                var canceled = !active
                var panelCommitted = false
                var hold: Job? = null
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            if (!canceled && active && !panelCommitted && pullUp >= threshold) refresh?.invoke()
                            break
                        }
                        if (event.changes.count { it.pressed } != 1 || !active) canceled = true
                        val dy = pointer.position.y - pointer.previousPosition.y
                        pullDown = if (closePanel != null && startedAtTop && !listState.canScrollBackward)
                            (pullDown + dy).coerceAtLeast(0f) else 0f
                        pullUp = if ((openPanel != null || refresh != null) && startedAtEnd && !listState.canScrollForward)
                            (pullUp - dy).coerceAtLeast(0f) else 0f
                        val dx = abs(pointer.position.x - down.position.x)
                        if (dx > threshold && dx > maxOf(pullDown, pullUp)) canceled = true
                        if (canceled || panelCommitted) {
                            hold?.cancel()
                            hold = null
                            displacement.floatValue = 0f
                            pullProgress.floatValue = 0f
                            continue
                        }
                        val pull = maxOf(pullDown, pullUp)
                        if (pull >= threshold && !crossed) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        crossed = pull >= threshold
                        val sign = if (pullDown > 0f) 1f else -1f
                        pullProgress.floatValue = sign * pull / threshold
                        displacement.floatValue = sign * minOf(pull * 0.6f, threshold * 0.75f)
                        val panel = if (pullDown >= threshold) closePanel else if (pullUp >= threshold) openPanel else null
                        if (panel == null) {
                            hold?.cancel()
                            hold = null
                        } else if (hold == null) {
                            hold = launch {
                                delay(2_000)
                                if (!canceled && active && !panelCommitted) {
                                    panelCommitted = true
                                    displacement.floatValue = 0f
                                    pullProgress.floatValue = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    panel()
                                }
                            }
                        }
                        if (pull > threshold / 4) pointer.consume()
                    }
                } finally {
                    hold?.cancel()
                    displacement.floatValue = 0f
                    pullProgress.floatValue = 0f
                }
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
