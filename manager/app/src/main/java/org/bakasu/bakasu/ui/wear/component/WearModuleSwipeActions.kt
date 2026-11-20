package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.GestureInclusion
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RevealDirection
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val RevealHoldMillis = 400L

/**
 * A left swipe or a 0.4 s hold reveals actions without executing them.
 * Taps, vertical scrolling and right swipes on covered rows retain their ordinary meaning.
 */
@Composable
fun TransformingLazyColumnItemScope.WearModuleSwipeActions(
    transformationSpec: TransformationSpec,
    scrolling: Boolean,
    primary: WearIconAction,
    secondary: WearIconAction? = null,
    content: @Composable () -> Unit,
) {
    val state = rememberRevealState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val active = LocalScreenIsActive.current
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val actionHeight = (with(density) { rowSize.height.toDp() } - 8.dp).coerceAtLeast(0.dp)
    val iconSize = (actionHeight * 0.7f).coerceAtMost(ButtonDefaults.LargeIconSize)
    val actionShapes = IconButtonDefaults.shapes(RoundedCornerShape(50))
    LaunchedEffect(scrolling, active) {
        if (!active) {
            state.snapTo(RevealValue.Covered)
        } else if (scrolling) {
            state.animateTo(RevealValue.Covered)
        }
    }
    // The reveal component never drags the row itself; the gesture below opens and closes it.
    val gestureInclusion = remember {
        object : GestureInclusion {
            override fun ignoreGestureStart(offset: Offset, layoutCoordinates: LayoutCoordinates) = true
        }
    }
    fun run(action: WearIconAction) {
        scope.launch {
            // Finish covering the row before navigation can dispose it or retain it offscreen.
            state.animateTo(RevealValue.Covered)
            action.onClick()
        }
    }
    SwipeToReveal(
        revealState = state,
        revealDirection = RevealDirection.RightToLeft,
        gestureInclusion = gestureInclusion,
        primaryAction = {
            FilledIconButton(
                onClick = { run(primary) },
                enabled = primary.enabled,
                modifier = Modifier.fillMaxWidth().height(actionHeight),
                shapes = actionShapes,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (primary.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (primary.destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Icon(primary.icon, contentDescription = primary.label, modifier = Modifier.requiredSize(iconSize)) }
        },
        secondaryAction = secondary?.let { action ->
            {
                FilledTonalIconButton(
                    onClick = { run(action) },
                    enabled = action.enabled,
                    modifier = Modifier.fillMaxWidth().height(actionHeight),
                    shapes = actionShapes,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (action.destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) { Icon(action.icon, contentDescription = action.label, modifier = Modifier.requiredSize(iconSize)) }
            }
        },
        // The row is never dragged by the component, so a full swipe cannot happen.
        onSwipePrimaryAction = {},
        // Match the Wear 1.7 SwipeToReveal list sample: keep the row transformation, but avoid
        // an offscreen buffer that clips translated content to the original row's bounds.
        modifier = Modifier.wearRefreshOffset().transformedHeight(this, transformationSpec).graphicsLayer {
            with(transformationSpec) { applyContainerTransformation(scrollProgress) }
            compositingStrategy = CompositingStrategy.ModulateAlpha
            clip = false
        }.minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding).pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                if (state.currentValue != RevealValue.Covered) {
                    // An open row closes with a left-to-right swipe.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val delta = (event.changes.firstOrNull { it.id == down.id } ?: break).position - down.position
                        if (abs(delta.x) > slop / 2 || abs(delta.y) > slop / 2) {
                            if (delta.x > 0 && abs(delta.x) > abs(delta.y)) {
                                scope.launch { state.animateTo(RevealValue.Covered) }
                                event.changes.forEach { it.consume() }
                                do {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                } while (currentEvent.changes.any { it.pressed })
                            }
                            break
                        }
                    } while (event.changes.any { it.pressed })
                    return@awaitEachGesture
                }
                val interrupted = withTimeoutOrNull(RevealHoldMillis) {
                    var cancelled = true
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || change.isConsumed) break
                        val delta = change.position - down.position
                        // Claim a left swipe before the pager; right swipes and vertical scrolls pass through.
                        if (delta.x < -slop / 2 && abs(delta.x) > abs(delta.y)) {
                            change.consume()
                            cancelled = false
                            break
                        }
                        if (delta.getDistance() > slop) break
                    }
                    cancelled
                }
                if (interrupted == true) return@awaitEachGesture
                if (interrupted == null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                // Revealing never enters the full-swipe state or invokes the action.
                scope.launch { state.animateTo(RevealValue.RightRevealing) }
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }.semantics {
            customActions = listOfNotNull(primary, secondary).map { action ->
                CustomAccessibilityAction(action.label) {
                    if (action.enabled) run(action)
                    action.enabled
                }
            }
        },
        content = {
            // The module's own content determines its height; actions never stretch short rows.
            Box(Modifier.onSizeChanged { rowSize = it }) { content() }
        },
    )
}
