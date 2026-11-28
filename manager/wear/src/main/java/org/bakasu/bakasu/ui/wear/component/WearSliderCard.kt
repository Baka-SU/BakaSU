package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import kotlin.math.roundToInt

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * Supports track taps and drags, which Wear Material 3's button-stepped Slider does not.
 * [onValueChange] runs only when the selected step changes, limiting immediate setting writes.
 */
@Composable
fun TransformingLazyColumnItemScope.WearSliderCard(
    transformationSpec: TransformationSpec,
    icon: ImageVector,
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
) {
    WearInfoCard(
        transformationSpec,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        WearStepTrack(value, onValueChange, valueRange, steps, enabled, label)
    }
}

// Keep a 48dp touch target around the smaller track.
private val TrackTouchHeight = 48.dp
private val TrackHeight = 20.dp
private val ThumbWidth = 4.dp
private val ThumbHeight = 36.dp
private val ThumbGap = 6.dp
private val InnerCornerRadius = 4.dp
private val StopRadius = 1.5.dp

@Composable
private fun WearStepTrack(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    description: String,
) {
    val segments = steps + 1
    val start = valueRange.start
    val span = valueRange.endInclusive - start
    fun stepOf(target: Float) = if (span <= 0f) 0 else (((target - start) / span) * segments).roundToInt().coerceIn(0, segments)
    fun valueAt(step: Int) = start + span * step / segments

    // Show the dragged step immediately while waiting for the caller to update value.
    var dragStep by remember { mutableStateOf<Int?>(null) }
    val shownStep = dragStep ?: stepOf(value)
    val currentStep by rememberUpdatedState(stepOf(value))
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var lastSent by remember { mutableIntStateOf(-1) }
    fun select(step: Int) {
        if (step != lastSent) {
            lastSent = step
            if (step != currentStep) currentOnValueChange(valueAt(step))
        }
    }

    val active = MaterialTheme.colorScheme.primary
    val onActive = MaterialTheme.colorScheme.onPrimary
    val inactive = MaterialTheme.colorScheme.surfaceContainerHigh
    val onInactive = MaterialTheme.colorScheme.onSurfaceVariant

    Spacer(
        Modifier.fillMaxWidth().height(TrackTouchHeight)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(valueAt(shownStep), valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        lastSent = currentStep
                        select(stepOf(target))
                        true
                    }
                }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(segments, valueRange) {
                        detectTapGestures { offset ->
                            lastSent = currentStep
                            select(stepAt(offset.x, size.width.toFloat(), segments))
                        }
                    }.pointerInput(segments, valueRange) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                lastSent = currentStep
                                val step = stepAt(offset.x, size.width.toFloat(), segments)
                                dragStep = step
                                select(step)
                            },
                            onDragEnd = { dragStep = null },
                            onDragCancel = { dragStep = null },
                        ) { change, _ ->
                            change.consume()
                            val step = stepAt(change.position.x, size.width.toFloat(), segments)
                            dragStep = step
                            select(step)
                        }
                    }
                } else {
                    Modifier
                },
            )
            .drawBehind { drawStepTrack(shownStep, segments, active, onActive, inactive, onInactive) },
    )
}

/** The step under [x]: stops sit evenly between the rounded ends of the track. */
private fun androidx.compose.ui.unit.Density.stepAt(x: Float, width: Float, segments: Int): Int {
    val inset = TrackHeight.toPx() / 2
    val usable = (width - inset * 2).coerceAtLeast(1f)
    return (((x - inset) / usable) * segments).roundToInt().coerceIn(0, segments)
}

private fun DrawScope.drawStepTrack(step: Int, segments: Int, active: Color, onActive: Color, inactive: Color, onInactive: Color) {
    val trackHeight = TrackHeight.toPx()
    val inset = trackHeight / 2
    val usable = size.width - inset * 2
    val top = (size.height - trackHeight) / 2
    val bottom = top + trackHeight
    val gap = ThumbGap.toPx()
    val outer = CornerRadius(trackHeight / 2)
    val inner = CornerRadius(InnerCornerRadius.toPx())
    fun stopX(index: Int) = inset + usable * index / segments
    val thumbX = stopX(step)

    if (thumbX - gap > 0f) {
        drawPath(
            Path().apply {
                addRoundRect(RoundRect(0f, top, thumbX - gap, bottom, outer, inner, inner, outer))
            },
            active,
        )
    }
    if (thumbX + gap < size.width) {
        drawPath(
            Path().apply {
                addRoundRect(RoundRect(thumbX + gap, top, size.width, bottom, inner, outer, outer, inner))
            },
            inactive,
        )
    }
    val stopRadius = StopRadius.toPx()
    // Hide crowded intermediate stops without reducing the setting's precision.
    val visibleStops = if (usable / segments >= stopRadius * 4) 0..segments else listOf(0, segments)
    for (index in visibleStops) {
        val x = stopX(index)
        // Stops next to the thumb would sit in its gap.
        if (index == step || kotlin.math.abs(x - thumbX) < gap + stopRadius) continue
        drawCircle(if (index < step) onActive else onInactive, stopRadius, Offset(x, size.height / 2))
    }
    val thumbWidth = ThumbWidth.toPx()
    val thumbHeight = ThumbHeight.toPx()
    drawRoundRect(
        active,
        topLeft = Offset(thumbX - thumbWidth / 2, (size.height - thumbHeight) / 2),
        size = Size(thumbWidth, thumbHeight),
        cornerRadius = CornerRadius(thumbWidth / 2),
    )
}
