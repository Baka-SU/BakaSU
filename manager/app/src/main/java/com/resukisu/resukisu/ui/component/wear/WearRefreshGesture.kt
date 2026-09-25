package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import com.resukisu.resukisu.R
import kotlin.math.abs

/**
 * Refresh by pulling up once the list has nothing left to scroll.
 *
 * The pull is measured from the pointer itself, so scrolling never counts towards it: the distance
 * only starts accumulating once the list has reached its end and is dropped again if the drag turns
 * back into a scroll.
 */
@Composable
internal fun Modifier.wearRefreshGesture(
    listState: TransformingLazyColumnState,
    enabled: Boolean,
    onRefresh: (() -> Unit)?,
): Modifier {
    val canRefresh by rememberUpdatedState(enabled && onRefresh != null)
    val refresh by rememberUpdatedState(onRefresh)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val refreshLabel = stringResource(R.string.wear_refresh)
    return this.pointerInput(listState, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var tracking = canRefresh
            var pulling = false
            var pull = 0f
            var drift = 0f
            var anchorY = 0f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.firstOrNull { it.id == down.id }?.let { pointer ->
                    if (tracking && !listState.canScrollForward) {
                        if (!pulling) {
                            pulling = true
                            anchorY = pointer.position.y
                        }
                        pull = maxOf(pull, anchorY - pointer.position.y)
                        drift = maxOf(drift, abs(pointer.position.x - down.position.x))
                    } else {
                        pulling = false
                        pull = 0f
                    }
                }
                if (event.changes.count { it.pressed } > 1) tracking = false
            } while (event.changes.any { it.pressed })
            if (tracking && canRefresh && pull >= threshold && pull > drift) refresh?.invoke()
        }
    }.semantics {
        if (canRefresh) customActions = listOf(CustomAccessibilityAction(refreshLabel) {
            refresh?.invoke()
            true
        })
    }
}
