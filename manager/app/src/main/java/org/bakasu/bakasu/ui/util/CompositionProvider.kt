package org.bakasu.bakasu.ui.util

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.LayoutCoordinates
import org.bakasu.bakasu.ui.activity.PermissionRequestInterface
import org.bakasu.bakasu.ui.overscroll.StretchOverscrollCompensationState
import org.bakasu.bakasu.ui.screen.BottomBarDestination
import top.yukonga.miuix.kmp.blur.LayerBackdrop

val LocalSnackbarHost = compositionLocalOf<SnackbarHostState> {
    error("CompositionLocal LocalSnackbarController not present")
}

// Defaults to null so surfaces outside NavContainer (the crop activity, previews) simply skip blur.
val LocalBlurState = compositionLocalOf<LayerBackdrop?> { null }

val LocalPagerState = compositionLocalOf<PagerState> { error("No pager state") }
val LocalPortraitState = compositionLocalOf<Boolean> { error("No portrait state") }
val LocalPagerPage = staticCompositionLocalOf<Int?> { null }

/**
 * How far the pager has been dragged away from the page owning the shared top bar: 0 at rest,
 * +/-0.5 at the handover. Read as a lambda so draw code can sample it per frame.
 */
val LocalTopBarSwipeDelta = compositionLocalOf<() -> Float> { { 0f } }

/**
 * Page whose top bar the shared slot is showing.
 *
 * Not the selected page: a flick settles the selection as soon as its target is known, while the
 * bar's content is still half way through fading out. This crosses over at the halfway point
 * instead, where the fade and the search field's fold have both reached zero and the change is
 * invisible. A navigation tap keeps to the selection, so one transition plays across the jump.
 */
val LocalTopBarOwner = compositionLocalOf<Int?> { null }

/** The pager's destinations, so a page can tell what it is being swiped towards. */
val LocalPagerPages = compositionLocalOf<List<BottomBarDestination>> { emptyList() }
val LocalHandlePageChange = compositionLocalOf<(Int) -> Unit> { error("No handle page change") }
val LocalSelectedPage = compositionLocalOf<Int> { error("No selected page") }

val LocalBackgroundBlurAnchor = staticCompositionLocalOf<LayoutCoordinates?> { null }
val LocalStretchOverscrollCompensationState =
    staticCompositionLocalOf<StretchOverscrollCompensationState?> { null }

val LocalPermissionRequestInterface = compositionLocalOf<PermissionRequestInterface> {
    error("CompositionLocal LocalPermissionRequestInterface not present")
}
