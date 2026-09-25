package com.resukisu.resukisu.ui.component.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec

/**
 * A Wear screen whose crown and vertical touch gestures scroll only its own content.
 *
 * Loading states are layered over the list so neither moves the content or the pager.
 */
@Composable
fun WearList(
    isLoading: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val isRound = LocalConfiguration.current.isScreenRound
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = (maxWidth * if (isRound) 0.073f else 0.04f).coerceAtLeast(6.dp)
        ScreenScaffold(scrollState = listState) { contentPadding ->
            Box(Modifier.fillMaxSize()) {
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize().wearRefreshGesture(
                        listState,
                        enabled = !isLoading && !isRefreshing,
                        onRefresh = onRefresh,
                    ),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) { content(transformationSpec) }
                if (isLoading) {
                    Box(
                        Modifier.fillMaxSize().padding(contentPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        WearLoadingIndicator()
                    }
                }
                WearRefreshIndicator(
                    visible = isRefreshing,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
