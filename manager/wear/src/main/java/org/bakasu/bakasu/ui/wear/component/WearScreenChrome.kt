/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bakasu.bakasu.ui.wear.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.curvedBox
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.weight
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PageIndicatorDefaults
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.ScrollIndicatorDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.material3.timeTextCurvedText
import kotlin.math.abs
import kotlin.math.floor
import org.bakasu.bakasu.R

data class WearTimeOptions(val mode: String = "time", val immersive: Boolean = false)
val LocalWearTimeOptions = staticCompositionLocalOf { WearTimeOptions() }

/** Shared by the scaffold's time title and the current page's refresh indicator. */
internal val LocalWearTimeTitleBottom = compositionLocalOf<MutableState<Float?>?> { null }

@Composable
internal fun WearTimeTitleLayout(pageKey: Any?, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val mode = LocalWearTimeOptions.current.mode
    val bottom = remember(pageKey, configuration, density, mode) { mutableStateOf<Float?>(null) }
    CompositionLocalProvider(LocalWearTimeTitleBottom provides bottom, content = content)
}

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** M3 chrome is curved; rectangular screens use the same scaffold slots and time source. */
@Composable
fun WearTimeText(title: String = stringResource(R.string.home), forceTime: Boolean = false) {
    val options = LocalWearTimeOptions.current.let { if (forceTime) it.copy(mode = "time") else it }
    val titleBottom = LocalWearTimeTitleBottom.current
    DisposableEffect(titleBottom, title, options.mode) {
        onDispose { titleBottom?.value = null }
    }
    if (options.mode == "hidden") return
    val background = if (options.immersive) Color.Transparent else TimeTextDefaults.backgroundColor()
    val curvedStyle = TimeTextDefaults.timeTextStyle()
    if (LocalConfiguration.current.isScreenRound) {
        TimeText(backgroundColor = background) { time ->
            if (options.mode == "title") {
                curvedBox(
                    modifier = CurvedModifier.weight(1f),
                    radialAlignment = CurvedAlignment.Radial.Inner,
                    angularAlignment = CurvedAlignment.Angular.End,
                ) {
                    curvedText(title, style = curvedStyle, overflow = TextOverflow.Ellipsis)
                    // A zero-size child at the measured arc's inner end marks its lowest point.
                    // It takes no space; the text determines both the sweep and radial thickness.
                    curvedComposable {
                        Box(Modifier.size(0.dp).onGloballyPositioned { titleBottom?.value = it.positionInRoot().y })
                    }
                }
            } else {
                timeTextCurvedText(time)
            }
        }
    } else {
        val time = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()).currentTime()
        Box(Modifier.fillMaxWidth().padding(TimeTextDefaults.ContentPadding), contentAlignment = Alignment.TopCenter) {
            Text(
                if (options.mode == "title") title else time,
                style = MaterialTheme.typography.labelSmall,
                color = curvedStyle.color,
                fontSize = curvedStyle.fontSize,
                fontFamily = curvedStyle.fontFamily,
                fontWeight = curvedStyle.fontWeight,
                letterSpacing = curvedStyle.letterSpacing,
                lineHeight = curvedStyle.lineHeight,
                modifier = Modifier.background(background, CircleShape)
                    // A round TimeText background extends half its thickness past each text end.
                    .padding(horizontal = with(LocalDensity.current) { curvedStyle.lineHeight.toDp() / 2 }).then(
                        if (options.mode == "title") {
                            Modifier.onGloballyPositioned { titleBottom?.value = it.boundsInRoot().bottom }.semantics { heading() }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun WearHorizontalPageIndicator(state: PagerState) {
    val pageCount = state.pageCount
    if (pageCount <= 1) return
    if (LocalConfiguration.current.isScreenRound) {
        HorizontalPageIndicator(state)
    } else {
        val selectedColor = PageIndicatorDefaults.selectedColor
        val unselectedColor = PageIndicatorDefaults.unselectedColor
        val backgroundColor = PageIndicatorDefaults.backgroundColor
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Canvas(
            Modifier.padding(bottom = WearChromeEdgePadding).size(width = (pageCount * 10 + 2).dp, height = 12.dp)
                .clearAndSetSemantics {},
        ) {
            // Wear M3 uses 6dp dots, 4dp gaps and 3dp background padding.
            val radius = 3.dp.toPx()
            val step = 10.dp.toPx()
            fun pageX(page: Float): Float {
                val x = 6.dp.toPx() + page * step
                return if (rtl) size.width - x else x
            }
            drawRoundRect(backgroundColor, cornerRadius = CornerRadius(size.height / 2f))
            // Read during drawing so the selection stretches with the swipe without recomposition.
            val position = (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, pageCount - 1f)
            val page = floor(position).toInt().coerceAtMost(pageCount - 2)
            val offset = position - page
            // M3 shrinks, fades and moves the two dots as the selected segment reaches them.
            // For 6dp dots and 4dp gaps the shrink interval is 0.1..0.4 of a page.
            val shrinkPrevious = ((0.9f - offset) / 0.3f).coerceIn(0f, 1f)
            val shrinkNext = ((offset - 0.1f) / 0.3f).coerceIn(0f, 1f)
            repeat(pageCount) { index ->
                val shrink = when (index) {
                    page -> shrinkPrevious
                    page + 1 -> shrinkNext
                    else -> 0f
                }
                val shift = when (index) {
                    page -> -shrinkPrevious / 3f
                    page + 1 -> shrinkNext / 3f
                    else -> 0f
                }
                val scale = 1f - shrink
                drawCircle(
                    color = unselectedColor.copy(alpha = unselectedColor.alpha * (scale * 2f).coerceAtMost(1f)),
                    radius = radius * scale,
                    center = Offset(pageX(index + shift), center.y),
                )
            }
            val start = pageX(page + (offset * 2f - 1f).coerceIn(0f, 1f))
            val end = pageX(page + (offset * 2f).coerceIn(0f, 1f))
            drawRoundRect(
                color = selectedColor,
                topLeft = Offset(minOf(start, end) - radius, center.y - radius),
                size = Size(abs(end - start) + radius * 2f, radius * 2f),
                cornerRadius = CornerRadius(radius),
            )
        }
    }
}

/** Visible item fractions follow the AndroidX TransformingLazyColumn indicator adapter. */
@Composable
fun WearScrollIndicator(state: TransformingLazyColumnState) {
    if (LocalConfiguration.current.isScreenRound) {
        ScrollIndicator(state)
    } else {
        val colors = ScrollIndicatorDefaults.colors()
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val strokeWidth = if (configuration.screenWidthDp >= 225) 6.dp else 5.dp
        val metrics by remember(state) {
            derivedStateOf {
                val info = state.layoutInfo
                val first = info.visibleItems.firstOrNull()
                val last = info.visibleItems.lastOrNull()
                if ((!state.canScrollBackward && !state.canScrollForward) || first == null || last == null || info.viewportSize.height <= 0) {
                    null
                } else {
                    val startPadding = if (first.index == 0) info.beforeContentPadding else 0
                    val endPadding = if (last.index == info.totalItemsCount - 1) info.afterContentPadding else 0
                    val start = first.index - (first.offset - startPadding).coerceAtMost(0).toFloat() /
                        (first.transformedHeight + startPadding).coerceAtLeast(1)
                    val end = last.index + (info.viewportSize.height - last.offset)
                        .coerceIn(0, last.transformedHeight + endPadding).toFloat() /
                        (last.transformedHeight + endPadding).coerceAtLeast(1)
                    val remaining = start + info.totalItemsCount - end
                    val position = when {
                        !state.canScrollBackward -> 0f
                        !state.canScrollForward -> 1f
                        remaining <= 0f -> 0f
                        else -> (start / remaining).coerceIn(0f, 1f)
                    }
                    position to ((end - start) / info.totalItemsCount.coerceAtLeast(1)).coerceIn(0.3f, 0.7f)
                }
            }
        }
        val currentMetrics = metrics ?: return
        // Like the M3 adapter, keep the thumb size stable while scrolling through items of
        // different heights. Recalculate after a data or viewport change, not at every edge.
        val sizeFraction = remember(state, state.layoutInfo.totalItemsCount, state.layoutInfo.viewportSize, density) { currentMetrics.second }
        val reverseLayout = state.layoutInfo.reverseLayout
        val position by animateFloatAsState(currentMetrics.first, ScrollIndicatorDefaults.PositionAnimationSpec, label = "squareScrollPosition")
        val fraction by animateFloatAsState(sizeFraction, ScrollIndicatorDefaults.PositionAnimationSpec, label = "squareScrollSize")
        Canvas(Modifier.padding(end = WearChromeEdgePadding).size(strokeWidth, 50.dp + strokeWidth).clearAndSetSemantics {}) {
            val stroke = strokeWidth.toPx()
            // Unroll M3's three arc segments onto a straight track. Each segment gives up
            // half a gap at both ends, leaving a 3dp visible gap after the round caps.
            val gap = 3.dp.toPx() + stroke
            val trackLength = 50.dp.toPx() + gap
            val top = (size.height - trackLength) / 2f
            val visualPosition = if (reverseLayout) 1f - position else position
            val before = visualPosition * (1f - fraction) * trackLength
            val thumb = fraction * trackLength
            drawWearScrollSegment(top, before, colors.trackColor, stroke, gap)
            drawWearScrollSegment(top + before, thumb, colors.indicatorColor, stroke, gap)
            drawWearScrollSegment(top + before + thumb, trackLength - before - thumb, colors.trackColor, stroke, gap)
        }
    }
}

/** M3 turns a short segment into a shrinking, fading dot instead of removing a full-width cap. */
private fun DrawScope.drawWearScrollSegment(start: Float, length: Float, color: Color, stroke: Float, gap: Float) {
    if (length <= gap) {
        val fraction = (length / gap).coerceIn(0f, 1f)
        drawCircle(
            color = color.copy(alpha = color.alpha * fraction),
            radius = stroke / 2f * fraction,
            center = Offset(center.x, start + length / 2f),
        )
    } else {
        drawLine(
            color = color,
            start = Offset(center.x, start + gap / 2f),
            end = Offset(center.x, start + length - gap / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// AndroidX Wear Compose M3 1.7.1's PaddingDefaults.edgePadding is internal.
private val WearChromeEdgePadding = 2.dp
