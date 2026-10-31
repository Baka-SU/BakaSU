package org.bakasu.bakasu.ui.wear.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.ResponsiveTransformationSpec
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.TransformationVariableSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.theme.CardConfig
import org.koin.compose.koinInject

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/**
 * How many times a settled page panel has closed or left the page. A page paints the panel preview
 * while the panel is dragged and keeps it under the panel's own destination; each count means the
 * destination is done with it, so the preview steps aside and the page shows again.
 */
val LocalWearPanelDismissals = compositionLocalOf { 0 }

/**
 * Whether a pulled panel's own destination covers the page. The page stops being active while the
 * destination is on top, but the preview it holds must stay: the destination draws the same content
 * over it, so the page and the destination swap without the page showing in between.
 */
val LocalWearPanelHosted = compositionLocalOf { false }

/**
 * How far the panel of the page on screen has been pulled, from 0 (closed) to 1 (landed). The page
 * publishes it so the surfaces drawn above the page, such as the pager indicator, can step aside
 * with the pull instead of sitting on top of the panel.
 */
val LocalWearPanelPull = compositionLocalOf { mutableFloatStateOf(0f) }

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
    isBusy: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    confirmLabel: String? = null,
    confirmEnabled: Boolean = true,
    backToTop: Boolean = false,
    onOpenPanel: (() -> Unit)? = null,
    panelContent: @Composable (() -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null,
    onPanelClosing: (() -> Unit)? = null,
    panelLabel: String? = null,
    snap: Boolean = false,
    itemSpacing: Dp = WearItemSpacing,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Round screens scale and fade items at the edges; square screens (including the screen shape
    // setting) keep items at full size, as nothing is cut off there.
    val transformationSpec = if (LocalConfiguration.current.isScreenRound) {
        rememberTransformationSpec()
    } else {
        rememberTransformationSpec(SquareSmallScreenSpec, SquareLargeScreenSpec)
    }
    val pullOffset = remember { mutableFloatStateOf(0f) }
    val pullProgress = remember { mutableFloatStateOf(0f) }
    val motion = MaterialTheme.motionScheme
    val reduceMotion = LocalReduceMotion.current
    val screenActive = LocalScreenIsActive.current
    // The panel's destination is a window of its own: while it covers the page, the page is no longer
    // active even though the panel is exactly where the pull left it.
    val panelHosted = LocalWearPanelHosted.current
    var viewportHeight by remember { mutableFloatStateOf(1f) }
    var openingPanel by remember { mutableStateOf(false) }
    var closingPanel by remember { mutableStateOf(false) }
    val closeProgress by animateFloatAsState(
        if (closingPanel) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else motion.defaultSpatialSpec(),
        label = "wear-panel-close",
        finishedListener = { if (closingPanel && it == 1f) onClosePanel?.invoke() },
    )
    val threshold = with(LocalDensity.current) { WearPullThreshold.toPx() }
    // The pull the panel and the page behind it both follow.
    val panelTarget = if (openingPanel) 1f else (pullProgress.floatValue * threshold / viewportHeight).coerceIn(0f, 1f)
    // While the finger is on the list the panel follows it exactly; a release springs it into place.
    val following = reduceMotion || (pullProgress.floatValue > 0f && !openingPanel)
    val panelProgress by animateFloatAsState(
        targetValue = panelTarget,
        animationSpec = if (following) snap() else spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "wear-panel-reveal",
        // The fully revealed preview stays put: the panel's destination draws the same content over
        // it, so the page and the destination swap without the page showing in between.
        finishedListener = { progress -> if (openingPanel && progress == 1f && screenActive) onOpenPanel?.invoke() },
    )
    // As on the Wear OS lock screen, the page follows the finger rather than the sheet: it starts
    // darkening and stepping back as soon as the pull begins, and is as far back as it goes when the
    // pull completes. A released pull keeps that position until the sheet has landed.
    val recessTarget = if (openingPanel) 1f else pullProgress.floatValue.coerceIn(0f, 1f)
    val recessProgress by animateFloatAsState(
        targetValue = recessTarget,
        animationSpec = if (following) snap() else spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "wear-page-recess",
    )
    // The panel's own spring passes its resting place a little, so the edge that slides in bounces as
    // it lands.
    val revealProgress = panelProgress.coerceAtLeast(0f)
    val pageRecess = recessProgress.coerceIn(0f, WearPageRecessLimit)
    val pageScale = 1f - WearPanelPageScale * pageRecess
    // The page is fully dark by the time the sheet's first row shows, so that row comes up on the
    // sheet's own background and the two read as one surface. A chosen background is the page's own
    // backdrop, and the user asked to see it, so the panel leaves it undimmed.
    val customBackground = koinInject<CardConfig>().isCustomBackgroundEnabled
    val pageDim = if (customBackground) 0f else (pageRecess / WearPanelDimReach).coerceIn(0f, 1f)
    // The destination has started closing or has left; the held preview goes away at once, so the
    // closing panel uncovers the page rather than a copy of itself.
    val panelDismissals = LocalWearPanelDismissals.current
    LaunchedEffect(panelDismissals) {
        if (panelDismissals > 0) {
            openingPanel = false
            pullOffset.floatValue = 0f
            pullProgress.floatValue = 0f
        }
    }
    // The panel's own page reports the pull to the surfaces drawn over it; the preview and the
    // panel's destination hold no panel of their own and stay quiet.
    val panelPull = LocalWearPanelPull.current
    SideEffect {
        if (panelContent != null && screenActive) {
            panelPull.floatValue = pageRecess.coerceIn(0f, 1f)
        }
    }
    // A page that leaves the screen gives up a pull in progress, unless the panel it handed over to
    // is the reason the page is not active: that panel is still showing the same content.
    LaunchedEffect(screenActive, panelHosted) {
        if (!screenActive && !panelHosted) {
            openingPanel = false
            pullOffset.floatValue = 0f
            pullProgress.floatValue = 0f
        }
    }
    // The list follows a boundary pull closely and springs back with a small bounce when it ends.
    val animatedOffset by animateFloatAsState(
        pullOffset.floatValue,
        animationSpec = if (reduceMotion) {
            snap()
        } else if (pullOffset.floatValue == 0f) {
            motion.defaultSpatialSpec()
        } else {
            motion.fastSpatialSpec()
        },
        label = "wear-boundary-pull",
    )
    // Keep the gesture on the stationary viewport while its pages move underneath it.
    val gestureModifier = Modifier.wearRefreshGesture(
        listState,
        pullOffset,
        pullProgress,
        enabled = screenActive && !isLoading && !isRefreshing && !isBusy,
        onRefresh = onRefresh,
        onOpenPanel = onOpenPanel?.let { open ->
            {
                if (panelContent == null) {
                    open()
                } else {
                    // The preview finishes the reveal itself, then hands the panel to its destination.
                    openingPanel = true
                }
            }
        },
        onClosePanel = if (onClosePanel != null) {
            {
                onPanelClosing?.invoke()
                closingPanel = true
            }
        } else {
            null
        },
        panelLabel = panelLabel,
    )
    // Loading shows the indeterminate indicator at the screen center instead of the content, as the
    // phone does; the branded loading screen is only for the manager's first load.
    val loadingDescription = stringResource(R.string.wear_loading)
    val listContent: @Composable (PaddingValues) -> Unit = { contentPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = loadingDescription })
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationY = if (panelContent == null || pullProgress.floatValue < 0f) animatedOffset else 0f
                    },
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.Top),
                    flingBehavior = if (snap) {
                        TransformingLazyColumnDefaults.snapFlingBehavior(listState)
                    } else {
                        ScrollableDefaults.flingBehavior()
                    },
                    rotaryScrollableBehavior = if (snap) {
                        RotaryScrollableDefaults.snapBehavior(listState)
                    } else {
                        RotaryScrollableDefaults.behavior(listState)
                    },
                ) {
                    content(transformationSpec)
                }
                // Pulling down at the top shows the panel indicator in the room the list makes for it.
                if (onOpenPanel != null && panelContent == null) {
                    WearPullIndicator(
                        pullProgress.floatValue,
                        closing = onClosePanel != null,
                        Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
                    )
                }
            }
        }
    }
    // The main page stays stationary; the menu is a separate surface above it. Behind an opening
    // panel the page also scales down, so the panel appears to land on a receding page.
    val pageModifier = Modifier.graphicsLayer {
        translationY = if (onClosePanel != null) -closeProgress * viewportHeight else 0f
        if (panelContent != null) {
            scaleX = pageScale
            scaleY = pageScale
        }
    }.then(if (onClosePanel != null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
    Box(Modifier.fillMaxSize().then(gestureModifier).onSizeChanged { viewportHeight = it.height.toFloat().coerceAtLeast(1f) }) {
        // An opaque base behind the page keeps the margin its shrink leaves free of the window
        // background while the page springs back. With a chosen background the margin belongs to
        // that background, so nothing is drawn over it.
        if (panelContent != null && pageRecess > 0f && !customBackground) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        // The edge button and the room it takes stay hidden until loading ends.
        if (!isLoading && (onBack != null || onConfirm != null || backToTop)) {
            ScreenScaffold(
                scrollState = listState,
                modifier = pageModifier,
                scrollIndicator = { if (!LocalScrollCaptureInProgress.current) WearScrollIndicator(listState) },
                edgeButton = {
                    EdgeButton(
                        enabled = onConfirm == null || confirmEnabled,
                        onClick = {
                            // While refreshing, the edge button only shows the progress of the pull refresh.
                            if (isRefreshing) {
                                Unit
                            } else if (onClosePanel != null) {
                                onPanelClosing?.invoke()
                                onClosePanel()
                            } else if (onConfirm != null) {
                                onConfirm()
                            } else if (backToTop) {
                                scope.launch { listState.animateScrollToItem(0) }
                            } else {
                                onBack?.invoke()
                            }
                        },
                        // Dragging on the edge button keeps scrolling the list, as in the M3 list guidance.
                        modifier = Modifier.scrollable(
                            listState,
                            orientation = Orientation.Vertical,
                            reverseDirection = true,
                            overscrollEffect = rememberOverscrollEffect(),
                        ),
                    ) {
                        // The pull refresh starts at the end of the list, so its progress shows here, where
                        // the finger is: a ring that fills with the pull, then a spinner while refreshing.
                        if (onClosePanel != null && pullProgress.floatValue < 0f) {
                            WearPullIndicator(-pullProgress.floatValue, closing = true)
                        } else if (pullProgress.floatValue < 0f) {
                            CircularProgressIndicator(
                                progress = { (-pullProgress.floatValue).coerceIn(0f, 1f) },
                                modifier = Modifier.size(24.dp),
                                colors = ProgressIndicatorDefaults.colors(indicatorColor = LocalContentColor.current),
                                // The pull ring keeps the thin stroke of the spinner that follows it.
                                strokeWidth = CircularProgressIndicatorDefaults.IndeterminateStrokeWidth,
                            )
                        } else if (isRefreshing) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp),
                                colors = ProgressIndicatorDefaults.colors(indicatorColor = LocalContentColor.current),
                            )
                        } else if (onConfirm != null && confirmLabel != null) {
                            Text(confirmLabel)
                        } else {
                            Icon(
                                if (onClosePanel != null) {
                                    Icons.Rounded.KeyboardArrowDown
                                } else if (onConfirm != null) {
                                    Icons.Rounded.Check
                                } else if (backToTop) {
                                    Icons.Rounded.VerticalAlignTop
                                } else {
                                    Icons.Rounded.ArrowBack
                                },
                                contentDescription = stringResource(
                                    if (onClosePanel != null) {
                                        R.string.wear_close_panel
                                    } else if (onConfirm != null) {
                                        R.string.confirm
                                    } else if (backToTop) {
                                        R.string.scroll_to_top
                                    } else {
                                        R.string.back
                                    },
                                ),
                            )
                        }
                    }
                },
            ) { listContent(it) }
        } else {
            ScreenScaffold(
                scrollState = listState,
                modifier = pageModifier,
                scrollIndicator = { if (!LocalScrollCaptureInProgress.current) WearScrollIndicator(listState) },
            ) { listContent(it) }
        }
        // The page itself darkens as the panel covers it.
        if (panelContent != null && pageDim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = pageDim)).clearAndSetSemantics {})
        }
        // A panel preview is drawn while it is pulled or opening, and stays under the panel's own
        // destination afterwards; a dismissal hands the page back. The destination covers it, so the
        // two swap without the page showing in between.
        if (panelContent != null && (pullProgress.floatValue > 0f || openingPanel) && revealProgress > 0f && (screenActive || panelHosted)) {
            // On a round screen the surface is the screen's own circle, so the edge that slides in
            // is an arc rather than a straight cut across the display. The surface casts no shadow:
            // it rises over a page that is already black, and it has to read as one with it.
            val panelShape = if (LocalConfiguration.current.isScreenRound) CircleShape else RectangleShape
            Box(
                Modifier.fillMaxSize().graphicsLayer { translationY = (revealProgress - 1f) * viewportHeight }
                    .clip(panelShape).background(MaterialTheme.colorScheme.background)
                    .clearAndSetSemantics {},
            ) {
                CompositionLocalProvider(LocalScreenIsActive provides false) { panelContent() }
            }
        }
    }
}

/** How far the page shrinks behind a pulled panel. */
private const val WearPanelPageScale = 0.12f

/** The share of the pull by which the page behind the panel is fully dark. */
private const val WearPanelDimReach = 0.8f

/** A bound on the page overshoot, so a long pull cannot shrink it far out of shape. */
private const val WearPageRecessLimit = 1.2f

/** The gap between list items, the TransformingLazyColumn default. */
val WearItemSpacing = 4.dp

/** A slightly tighter gap for pages made of full-width rows, such as the module details. */
val WearCompactItemSpacing = 2.dp

private val SquareItemSpec = TransformationVariableSpec(1f)
private val SquareSmallScreenSpec = ResponsiveTransformationSpec.smallScreen(containerAlpha = SquareItemSpec, contentAlpha = SquareItemSpec, scale = SquareItemSpec)
private val SquareLargeScreenSpec = ResponsiveTransformationSpec.largeScreen(containerAlpha = SquareItemSpec, contentAlpha = SquareItemSpec, scale = SquareItemSpec)
