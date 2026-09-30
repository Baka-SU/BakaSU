package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.resukisu.resukisu.R
import kotlinx.coroutines.launch

/**
 * A Wear screen whose crown and vertical touch gestures scroll only its own content.
 *
 * The list uses the [ScreenScaffold] content padding (5.2% side and 10% vertical margins); items
 * raise the top and bottom padding with `minimumVerticalContentPadding` when they sit at an edge.
 * [snap] centers items after a fling or crown rotation, for lists of similarly sized items.
 */
@Composable
fun WearList(
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    backToTop: Boolean = false,
    onOpenPanel: (() -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null,
    panelLabel: String? = null,
    snap: Boolean = false,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val transformationSpec = rememberTransformationSpec()
    if (isLoading) {
        WearLoadingScreen()
        return
    }
    val listContent: @Composable (PaddingValues) -> Unit = { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize().wearRefreshGesture(
                listState,
                enabled = !isRefreshing,
                onRefresh = onRefresh,
                onOpenPanel = onOpenPanel,
                onClosePanel = onClosePanel,
                panelLabel = panelLabel,
            ),
            state = listState,
            contentPadding = contentPadding,
            flingBehavior = if (snap) TransformingLazyColumnDefaults.snapFlingBehavior(listState)
                else ScrollableDefaults.flingBehavior(),
            rotaryScrollableBehavior = if (snap) RotaryScrollableDefaults.snapBehavior(listState)
                else RotaryScrollableDefaults.behavior(listState),
        ) { content(transformationSpec) }
    }
    if (onBack != null || onConfirm != null || backToTop) {
        ScreenScaffold(scrollState = listState, edgeButton = {
            EdgeButton(
                onClick = {
                    if (onConfirm != null) onConfirm()
                    else if (backToTop) scope.launch { listState.animateScrollToItem(0) }
                    else onBack?.invoke()
                },
                // Dragging on the edge button keeps scrolling the list, as in the M3 list guidance.
                modifier = Modifier.scrollable(
                    listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true,
                    overscrollEffect = rememberOverscrollEffect(),
                ),
            ) {
                Icon(
                    if (onConfirm != null) Icons.Default.Check
                    else if (backToTop) Icons.Default.VerticalAlignTop else Icons.AutoMirrored.Default.ArrowBack,
                    contentDescription = stringResource(if (onConfirm != null) R.string.confirm else if (backToTop) R.string.scroll_to_top else R.string.wear_back),
                )
            }
        }) { listContent(it) }
    } else ScreenScaffold(scrollState = listState) { listContent(it) }
}
