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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.MaterialTheme
import kotlin.math.absoluteValue

/** M3 1.7.1 clips AnimatedPage to a circle; square pages retain its motion with rectangular bounds. */
@Composable
internal fun WearAnimatedPage(
    pageIndex: Int,
    pagerState: PagerState,
    contentScrimColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    if (configuration.isScreenRound) {
        AnimatedPage(pageIndex, pagerState, contentScrimColor, content)
        return
    }
    val reduceMotion = LocalReduceMotion.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val orientation = remember(pagerState) { pagerState.layoutInfo.orientation }
    val intervals = ((if (orientation == Orientation.Horizontal) configuration.screenWidthDp else configuration.screenHeightDp) / 2).coerceAtLeast(1)
    val offsetFraction by remember(pagerState, intervals) {
        derivedStateOf { (pagerState.currentPageOffsetFraction * intervals).toInt() / intervals.toFloat() }
    }
    fun transitionFraction(): Float = if (pageIndex == pagerState.currentPage) offsetFraction.absoluteValue else 1f - offsetFraction.absoluteValue

    val motionModifier = if (reduceMotion) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            val direction = if (isRtl) -1 else 1
            val offset = offsetFraction
            val isCurrentPage = pageIndex == pagerState.currentPage
            val anchorRight = (direction * offset > 0 && isCurrentPage) || (direction * offset < 0 && !isCurrentPage)
            val pivot = if (anchorRight) 1f else 0f
            transformOrigin = if (orientation == Orientation.Horizontal) TransformOrigin(pivot, 0.5f) else TransformOrigin(0.5f, pivot)
            val scale = lerp(1f, 0.55f, transitionFraction())
            scaleX = scale
            scaleY = scale
        }
    }
    Box(
        motionModifier.drawWithContent {
            drawContent()
            if (contentScrimColor.isSpecified) {
                drawRect(contentScrimColor.copy(alpha = lerp(0f, 0.5f, transitionFraction())))
            }
        }.clipToBounds(),
    ) {
        content()
    }
}
