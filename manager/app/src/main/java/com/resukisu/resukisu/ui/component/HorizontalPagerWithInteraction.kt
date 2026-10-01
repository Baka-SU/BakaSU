package com.resukisu.resukisu.ui.component

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.foundation.gestures.Orientation
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.horizontalPagerSwipeOverride
import androidx.wear.compose.foundation.pager.HorizontalPager as WearHorizontalPager
import androidx.wear.compose.foundation.pager.PagerState as WearPagerState
import androidx.wear.compose.foundation.pager.PagerScope as WearPagerScope
import androidx.wear.compose.foundation.pager.PagerDefaults as WearPagerDefaults

/**
 * The single pager entry point for the app. It keeps gesture arbitration consistent between
 * pages while still allowing screens with custom pager interception to provide their connection.
 */
@Composable
fun HorizontalPagerWithInteraction(
    state: PagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    beyondViewportPageCount: Int = 0,
    pageNestedScrollConnection: NestedScrollConnection? = null,
    flingBehavior: TargetedFlingBehavior? = null,
    enableGestureOverride: Boolean = true,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
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
        beyondViewportPageCount = beyondViewportPageCount,
        pageNestedScrollConnection = if (enableGestureOverride) {
            pageNestedScrollConnection ?: PagerGestureNestedScrollConnection
        } else {
            nestedConnection
        },
        flingBehavior = flingBehavior ?: PagerDefaults.flingBehavior(state),
        pageContent = pageContent,
    )
}

/** Wear's pager arbitrates edge-dismiss gestures itself and leaves rotary input to each list. */
@Composable
fun HorizontalPagerWithInteraction(
    state: WearPagerState,
    modifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true,
    flingBehavior: TargetedFlingBehavior = WearPagerDefaults.snapFlingBehavior(state),
    key: ((Int) -> Any)? = null,
    pageContent: @Composable WearPagerScope.(Int) -> Unit,
) {
    WearHorizontalPager(state = state, modifier = modifier, userScrollEnabled = userScrollEnabled,
        flingBehavior = flingBehavior, key = key, content = pageContent)
}
