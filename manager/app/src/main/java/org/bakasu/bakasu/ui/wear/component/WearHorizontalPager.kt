package org.bakasu.bakasu.ui.wear.component

import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerDefaults
import androidx.wear.compose.foundation.pager.PagerScope
import androidx.wear.compose.foundation.pager.PagerState

/** The Wear pager entry point; it keeps the library's edge gesture arbitration and active-page handling. */
@Composable
fun WearHorizontalPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    key: ((Int) -> Any)? = null,
    flingBehavior: TargetedFlingBehavior = PagerDefaults.snapFlingBehavior(state),
    pageContent: @Composable PagerScope.(Int) -> Unit,
) {
    HorizontalPager(
        state = state,
        modifier = modifier,
        key = key,
        flingBehavior = flingBehavior,
        content = pageContent,
    )
}
