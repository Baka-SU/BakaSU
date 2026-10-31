package org.bakasu.bakasu.ui.wear.component

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import kotlin.math.abs
import org.bakasu.bakasu.R

internal val WearPullThreshold = 48.dp

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Pulls past the list boundaries. At the top, pulling down (or turning the crown up) opens the page's
 * panel; at the end, pulling up (or, in a panel, turning the crown down) closes the panel, and
 * pulling up refreshes the main list. Releasing past
 * the 48dp threshold commits; ordinary scrolling and shorter pulls do nothing.
 *
 * Apply it to the whole screen (list and edge button), so a pull that starts on the edge button at
 * the end of the list still counts. [displacement] receives the list offset that follows the pull;
 * draw it on the list only, so it does not shift the coordinates this gesture measures.
 * [pullProgress] is the pull relative to the threshold: positive at the top, negative at the end.
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
    currentPanelPull: () -> Float = { 0f },
    onPullStart: () -> Float = { 0f },
    onPullEnd: () -> Unit = {},
): Modifier {
    // Lists without any boundary action keep their gestures untouched.
    if (onRefresh == null && onOpenPanel == null && onClosePanel == null) return this
    val active by rememberUpdatedState(enabled)
    val refresh by rememberUpdatedState(onRefresh)
    val panel by rememberUpdatedState(onOpenPanel)
    val closePanel by rememberUpdatedState(onClosePanel)
    val heldPanelPull by rememberUpdatedState(currentPanelPull)
    val pullStart by rememberUpdatedState(onPullStart)
    val pullEnd by rememberUpdatedState(onPullEnd)
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { WearPullThreshold.toPx() }
    val refreshLabel = stringResource(R.string.wear_refresh)
    val backLabel = stringResource(R.string.back)
    // The crown turned past the boundary: three times the touch threshold of extra rotation, with a
    // pause of the crown resetting it.
    val crownTarget = threshold * 3
    val crown = remember { CrownPull() }
    return this.onPreRotaryScrollEvent { event ->
        // A panel closes past its end with the crown turned down; a page opens its panel past the
        // top with the crown turned up, mirroring the touch pulls.
        val closing = closePanel != null
        val action = closePanel ?: panel
        val now = SystemClock.uptimeMillis()
        val atBoundary = if (closing) !listState.canScrollForward else !listState.canScrollBackward
        val delta = if (closing) event.verticalScrollPixels else -event.verticalScrollPixels
        val sign = if (closing) -1f else 1f
        if (action == null || !active || !atBoundary || delta <= 0f || now - crown.lastEvent > 600) crown.accumulated = 0f
        crown.lastEvent = now
        if (action != null && active && atBoundary && delta > 0f) {
            if (crown.accumulated == 0f) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            crown.accumulated += delta
            if (crown.accumulated >= crownTarget) {
                crown.accumulated = 0f
                displacement.floatValue = 0f
                pullProgress.floatValue = 0f
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                action()
            } else {
                pullProgress.floatValue = sign * crown.accumulated / crownTarget
                displacement.floatValue = pullProgress.floatValue * threshold
            }
        } else {
            displacement.floatValue = 0f
            pullProgress.floatValue = 0f
        }
        // The list still receives the event; past its top it has nothing left to scroll.
        false
    }.pointerInput(listState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pullDown = 0f
            var pullUp = 0f
            var crossed = false
            var canceled = !active
            var dragging = false
            val slop = viewConfiguration.touchSlop
            // Only a gesture that starts at a boundary pulls past it, so a fling that scrolls back
            // to the top does not open the panel with the rest of its travel.
            val startedAtTop = !listState.canScrollBackward
            val startedAtEnd = !listState.canScrollForward
            val resumingPanel = heldPanelPull()
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) {
                        if (!canceled && active) {
                            if (pullDown >= threshold) {
                                panel?.invoke()
                            } else if (pullUp >= threshold) {
                                (closePanel ?: refresh)?.invoke()
                            }
                        }
                        break
                    }
                    if (event.changes.count { it.pressed } != 1 || !active) canceled = true
                    val total = pointer.position - down.position
                    var dy = pointer.position.y - pointer.previousPosition.y
                    if (!dragging && !canceled) {
                        if (abs(total.x) <= slop && abs(total.y) <= slop) continue
                        // A settling panel can be caught from either direction. An ordinary
                        // boundary pull still starts only towards its own action.
                        val canPull = when {
                            resumingPanel > 0f -> panel != null && startedAtTop && !listState.canScrollBackward
                            resumingPanel < 0f -> closePanel != null && startedAtEnd && !listState.canScrollForward
                            total.y > 0f -> panel != null && startedAtTop && !listState.canScrollBackward
                            else -> (closePanel != null || refresh != null) && startedAtEnd && !listState.canScrollForward
                        }
                        if (pointer.isConsumed || abs(total.x) >= abs(total.y) || !canPull) {
                            canceled = true
                        } else {
                            dragging = true
                            dy = total.y
                            // Resume a settling reveal at its current position before following
                            // this finger, including when it immediately reverses direction.
                            val initialPull = pullStart()
                            pullDown = initialPull.coerceAtLeast(0f)
                            pullUp = (-initialPull).coerceAtLeast(0f)
                        }
                    }
                    // A pull grows only while its boundary is held, and moving back shrinks it.
                    pullDown = if (panel != null && startedAtTop && !listState.canScrollBackward) (pullDown + dy).coerceAtLeast(0f) else 0f
                    pullUp = if ((closePanel != null || refresh != null) && startedAtEnd && !listState.canScrollForward) (pullUp - dy).coerceAtLeast(0f) else 0f
                    if (canceled) {
                        displacement.floatValue = 0f
                        pullProgress.floatValue = 0f
                        pullEnd()
                        continue
                    }
                    val pull = maxOf(pullDown, pullUp)
                    if (pull >= threshold && !crossed) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    crossed = pull >= threshold
                    val sign = if (pullDown > 0f) 1f else -1f
                    pullProgress.floatValue = sign * pull / threshold
                    // Match finger travel without resistance or a displacement cap.
                    displacement.floatValue = sign * pull
                    if (dragging) pointer.consume()
                }
            } finally {
                displacement.floatValue = 0f
                pullProgress.floatValue = 0f
                pullEnd()
            }
        }
    }.semantics {
        if (enabled) {
            customActions = buildList {
                if (onRefresh != null) {
                    add(
                        CustomAccessibilityAction(refreshLabel) {
                            onRefresh()
                            true
                        },
                    )
                }
                if (onOpenPanel != null && panelLabel != null) {
                    add(
                        CustomAccessibilityAction(panelLabel) {
                            onOpenPanel()
                            true
                        },
                    )
                }
                if (onClosePanel != null) {
                    add(
                        CustomAccessibilityAction(backLabel) {
                            onClosePanel()
                            true
                        },
                    )
                }
            }
        }
    }
}

/** Crown rotation collected past the list top, and when it last arrived. */
private class CrownPull {
    var accumulated = 0f
    var lastEvent = 0L
}
