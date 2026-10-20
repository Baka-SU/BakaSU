package org.bakasu.bakasu.ui.component

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import org.bakasu.bakasu.ui.theme.LocalBackgroundRenderState
import org.bakasu.bakasu.ui.util.LocalPagerPage
import org.bakasu.bakasu.ui.util.LocalPagerState
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.horizontalPagerSwipeOverride

@Composable
fun HorizontalPagerWithInteraction(
    state: PagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    pageNestedScrollConnection: NestedScrollConnection? = null,
    flingBehavior: TargetedFlingBehavior? = null,
    enableGestureOverride: Boolean = true,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
    val backgroundRenderState = LocalBackgroundRenderState.current
    val scrollOwner = remember { Any() }
    LaunchedEffect(state, backgroundRenderState) {
        try {
            snapshotFlow { state.isScrollInProgress }.collect {
                backgroundRenderState.updatePagerScroll(scrollOwner, it)
            }
        } finally {
            backgroundRenderState.updatePagerScroll(scrollOwner, false)
        }
    }
    val transitionRunning = LocalNavTransitionScope.current.isRunning
    var contentReady by remember { mutableStateOf(false) }
    LaunchedEffect(transitionRunning) {
        if (!transitionRunning && !contentReady) {
            withFrameNanos { }
            contentReady = true
        }
    }
    val nestedConnection = pageNestedScrollConnection ?: PagerDefaults.pageNestedScrollConnection(
        state = state,
        orientation = Orientation.Horizontal,
    )
    HorizontalPager(
        state = state,
        modifier = if (enableGestureOverride) {
            modifier.horizontalPagerSwipeOverride(state, enabled = userScrollEnabled)
        } else {
            modifier
        },
        userScrollEnabled = if (enableGestureOverride) false else userScrollEnabled,
        beyondViewportPageCount = if (contentReady) state.pageCount else 0,
        pageNestedScrollConnection = if (enableGestureOverride) {
            pageNestedScrollConnection ?: PagerGestureNestedScrollConnection
        } else {
            nestedConnection
        },
        flingBehavior = flingBehavior ?: PagerDefaults.flingBehavior(state),
        pageContent = { pageIndex ->
            if (!contentReady && state.currentPage != pageIndex) return@HorizontalPager

            CompositionLocalProvider(
                LocalPagerState provides state,
                LocalPagerPage provides pageIndex,
            ) {
                pageContent(pageIndex)
            }
        },
    )
}
