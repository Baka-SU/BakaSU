package org.bakasu.bakasu.ui.wear.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalScrollCaptureInProgress
import androidx.compose.ui.res.stringResource
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

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Signals that a menu has closed so its page can release the held reveal. */
val LocalWearPanelDismissals = compositionLocalOf { 0 }

/** Whether the shared operations host currently owns the reveal. */
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
    hasOperationsPanel: Boolean = false,
    onClosePanel: (() -> Unit)? = null,
    onPanelClosing: (() -> Unit)? = null,
    panelLabel: String? = null,
    snap: Boolean = false,
    itemSpacing: Dp = WearItemSpacing,
    topContentPadding: Dp = 0.dp,
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
    val reduceMotion = LocalReduceMotion.current
    val screenActive = LocalScreenIsActive.current
    // The page yields gestures and reveal updates while the shared menu is active.
    val panelHosted = LocalWearPanelHosted.current
    var viewportHeight by remember { mutableFloatStateOf(1f) }
    var openingPanel by remember { mutableStateOf(false) }
    var closingPanel by remember { mutableStateOf(false) }
    var draggingPanel by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { WearPullThreshold.toPx() }
    val closeProgress by animateFloatAsState(
        if (closingPanel) 1f else (-pullProgress.floatValue * threshold / viewportHeight).coerceIn(0f, 1f),
        animationSpec = if (reduceMotion || draggingPanel || (pullProgress.floatValue < 0f && !closingPanel)) snap() else tween(180),
        label = "wear-panel-close",
        finishedListener = { if (closingPanel && it == 1f) onClosePanel?.invoke() },
    )
    val panelDismissals = LocalWearPanelDismissals.current
    var lastDismissal by remember { mutableIntStateOf(panelDismissals) }
    val panelWasDismissed = lastDismissal != panelDismissals
    // The pull the panel and the page behind it both follow.
    val panelTarget = if (panelWasDismissed) {
        0f
    } else if (openingPanel) {
        1f
    } else {
        (pullProgress.floatValue * threshold / viewportHeight).coerceIn(0f, 1f)
    }
    // Follow the finger directly; settle without resistance or spring overshoot.
    val following = reduceMotion || (pullProgress.floatValue > 0f && !openingPanel)
    val panelProgress by animateFloatAsState(
        targetValue = panelTarget,
        animationSpec = if (following || draggingPanel || panelHosted || panelWasDismissed) snap() else tween(180),
        label = "wear-panel-reveal",
        // Committing the logical destination keeps the same menu in the shared host.
        finishedListener = { progress -> if (openingPanel && progress == 1f && screenActive) onOpenPanel?.invoke() },
    )
    // Movement, scale and scrim share the reveal, including the destination's closing animation.
    val panelPull = LocalWearPanelPull.current
    val revealProgress = (if (panelHosted && hasOperationsPanel) panelPull.floatValue else panelProgress).coerceAtLeast(0f)
    val pageRecess = revealProgress.coerceIn(0f, WearPageRecessLimit)
    val pageScale = 1f - WearPanelPageScale * pageRecess
    // The destination has started closing or has left; the held preview goes away at once, so the
    // closing panel uncovers the page rather than a copy of itself.
    LaunchedEffect(panelDismissals) {
        if (panelWasDismissed) {
            lastDismissal = panelDismissals
            openingPanel = false
            pullOffset.floatValue = 0f
            pullProgress.floatValue = 0f
        }
    }
    // The opening page publishes its reveal; the hosted menu publishes the reverse on closing.
    SideEffect {
        if (hasOperationsPanel && screenActive && !panelHosted) {
            panelPull.floatValue = pageRecess.coerceIn(0f, 1f)
        } else if (onClosePanel != null && screenActive && panelHosted) {
            panelPull.floatValue = 1f - closeProgress
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
    // Boundary drags follow the finger without resistance; release returns without a spring.
    val animatedOffset by animateFloatAsState(
        pullOffset.floatValue,
        animationSpec = if (reduceMotion || pullOffset.floatValue != 0f) snap() else tween(180),
        label = "wear-boundary-pull",
    )
    // Keep the gesture on the stationary viewport while its pages move underneath it.
    val gestureModifier = Modifier.wearRefreshGesture(
        listState,
        pullOffset,
        pullProgress,
        enabled = screenActive && (!panelHosted || onClosePanel != null) && !isLoading && !isRefreshing && !isBusy,
        onRefresh = onRefresh,
        onOpenPanel = onOpenPanel?.let { open ->
            {
                if (!hasOperationsPanel) {
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
        onPullStart = {
            val currentPull = if (onClosePanel != null) -closeProgress * viewportHeight else panelProgress * viewportHeight
            openingPanel = false
            closingPanel = false
            draggingPanel = true
            currentPull
        },
        onPullEnd = { draggingPanel = false },
    )
    // Loading shows the indeterminate indicator at the screen center instead of the content, as the
    // phone does; the branded loading screen is only for the manager's first load.
    val loadingDescription = stringResource(R.string.wear_loading)
    val listContent: @Composable (PaddingValues) -> Unit = { scaffoldPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val timeSpace = if (LocalWearTimeOptions.current.mode == "hidden") {
            0.dp
        } else {
            with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() } +
                if (LocalConfiguration.current.isScreenRound) 20.dp else 12.dp
        }
        val contentPadding = PaddingValues(
            start = scaffoldPadding.calculateStartPadding(layoutDirection),
            end = scaffoldPadding.calculateEndPadding(layoutDirection),
            top = maxOf(scaffoldPadding.calculateTopPadding(), timeSpace) + topContentPadding,
            // Keep the final menu button inside the straight moving edge, including on round screens.
            bottom = maxOf(scaffoldPadding.calculateBottomPadding(), if (onClosePanel != null) 24.dp else 0.dp),
        )
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = loadingDescription })
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationY = if (panelHosted && onClosePanel != null) {
                            0f
                        } else if (!hasOperationsPanel || pullProgress.floatValue < 0f) {
                            animatedOffset
                        } else {
                            0f
                        }
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
                if (onOpenPanel != null && !hasOperationsPanel) {
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
        translationY = if (onClosePanel != null && !panelHosted) -closeProgress * viewportHeight else 0f
        if (hasOperationsPanel) {
            scaleX = pageScale
            scaleY = pageScale
        }
    }
    Box(Modifier.fillMaxSize().then(gestureModifier).onSizeChanged { viewportHeight = it.height.toFloat().coerceAtLeast(1f) }) {
        // The edge button and the room it takes stay hidden until loading ends.
        if (!isLoading && (onBack != null || onConfirm != null || backToTop)) {
            ScreenScaffold(
                scrollState = listState,
                modifier = pageModifier,
                scrollIndicator = { if (onClosePanel == null && !LocalScrollCaptureInProgress.current) WearScrollIndicator(listState) },
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
                scrollIndicator = { if (onClosePanel == null && !LocalScrollCaptureInProgress.current) WearScrollIndicator(listState) },
            ) { listContent(it) }
        }
    }
}

/** How far the page shrinks behind a pulled panel. */
private const val WearPanelPageScale = 0.12f

/** Dimming follows the complete reveal, so the page remains visible while it recedes. */
internal const val WearPanelDimReach = 1f

internal const val WearPanelMaxDim = 1f

/** A bound on the page overshoot, so a long pull cannot shrink it far out of shape. */
private const val WearPageRecessLimit = 1.2f

/** The gap between list items, the TransformingLazyColumn default. */
val WearItemSpacing = 4.dp

/** A slightly tighter gap for pages made of full-width rows, such as the module details. */
val WearCompactItemSpacing = 2.dp

private val SquareItemSpec = TransformationVariableSpec(1f)
private val SquareSmallScreenSpec = ResponsiveTransformationSpec.smallScreen(containerAlpha = SquareItemSpec, contentAlpha = SquareItemSpec, scale = SquareItemSpec)
private val SquareLargeScreenSpec = ResponsiveTransformationSpec.largeScreen(containerAlpha = SquareItemSpec, contentAlpha = SquareItemSpec, scale = SquareItemSpec)
