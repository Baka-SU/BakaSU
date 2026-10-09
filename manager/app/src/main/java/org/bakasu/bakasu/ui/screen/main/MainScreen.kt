package org.bakasu.bakasu.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bakasu.bakasu.ui.activity.component.NavigationBar
import org.bakasu.bakasu.ui.component.HorizontalPagerWithInteraction
import org.bakasu.bakasu.ui.component.LocalTopBarSlot
import org.bakasu.bakasu.ui.component.TopBarScrim
import org.bakasu.bakasu.ui.component.TopBarSlot
import org.bakasu.bakasu.ui.rememberMaterial3BlurBackdrop
import org.bakasu.bakasu.ui.screen.BottomBarDestination
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.util.LocalBlurState
import org.bakasu.bakasu.ui.util.LocalHandlePageChange
import org.bakasu.bakasu.ui.util.LocalPagerPage
import org.bakasu.bakasu.ui.util.LocalPagerPages
import org.bakasu.bakasu.ui.util.LocalPagerState
import org.bakasu.bakasu.ui.util.LocalPortraitState
import org.bakasu.bakasu.ui.util.LocalSelectedPage
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.LocalTopBarOwner
import org.bakasu.bakasu.ui.util.LocalTopBarSwipeDelta
import org.bakasu.bakasu.ui.viewmodel.HomeViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

/** Offset below which the pager counts as at rest; it settles on an epsilon, not on zero. */
private const val SettledOffset = 0.001f

@Composable
fun MainScreen(
    pagerInterceptionMode: Int = PagerInterceptionMode.CrossAxisInterceptor.ordinal,
) {
    val themeConfig: ThemeConfig = koinInject()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val pages = remember(homeState.systemStatus.isFullFeatured) {
        BottomBarDestination.getPages(homeState.systemStatus.isFullFeatured)
    }

    val coroutineScope = rememberCoroutineScope()
    var uiSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = uiSelectedPage,
        pageCount = { pages.size },
    )
    var userScrollEnabled by remember { mutableStateOf(true) }
    var animating by remember { mutableStateOf(false) }
    var animateJob by remember { mutableStateOf<Job?>(null) }
    var lastRequestedPage by remember { mutableIntStateOf(pagerState.currentPage) }
    // How many pages the current nav-bar jump spans, so the top bar transition can be played
    // over the whole journey instead of only the last half page of it.
    var navDistance by remember { mutableFloatStateOf(0f) }
    // The page that jump set off from; the bar belongs to it until the half way point.
    var navFromPage by remember { mutableIntStateOf(0) }

    val pagerMode = PagerInterceptionMode.entries.getOrElse(pagerInterceptionMode) {
        PagerInterceptionMode.Native
    }
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor

    // A tap has no gesture behind it, so it crosses the whole page on its own: it travels on
    // the theme's spatial spec rather than the spec a fling settles on, which is tuned for the
    // last fraction of a page and lands a tap in about a tenth of a second.
    val navSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val handlePageChange: (Int) -> Unit = remember(pagerState, coroutineScope, pages, navSpec) {
        { page ->
            if (page !in pages.indices) return@remember
            uiSelectedPage = page

            if (page == pagerState.currentPage) {
                if (animateJob != null && lastRequestedPage != page) {
                    animateJob?.cancel()
                    animateJob = null
                    animating = false
                    userScrollEnabled = true
                }
                lastRequestedPage = page
            } else if (animateJob == null || lastRequestedPage != page) {
                animateJob?.cancel()
                animating = true
                userScrollEnabled = false
                lastRequestedPage = page
                navFromPage = pagerState.currentPage
                navDistance = abs(page - pagerState.currentPage).toFloat()
                animateJob = coroutineScope.launch {
                    try {
                        // A held pager gesture owns the scroll mutation at UserInput
                        // priority. Stop it explicitly so a navigation tap always wins.
                        pagerState.scroll(MutatePriority.PreventUserInput) { }
                        pagerState.animateScrollToPage(page = page, animationSpec = navSpec)
                    } finally {
                        if (animateJob === this) {
                            animating = false
                            userScrollEnabled = true
                            animateJob = null
                            lastRequestedPage = pagerState.currentPage
                            navDistance = 0f
                            navFromPage = pagerState.currentPage
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!animating) uiSelectedPage = page
        }
    }

    BackHandler(pagerState.currentPage != 0) {
        handlePageChange(0)
    }

    // One bar for the whole pager: a per-page bar would translate on swipe and carry its own
    // blur backdrop, leaving a seam between two scrims mid-drag.
    val topBarSlot = remember { TopBarSlot() }
    val density = LocalDensity.current

    // Pages lay their content out below their own bar, so take each one's height while the pager
    // is at rest. Measuring mid swipe would catch the field half folded, or the next page's bar
    // already in place, and drag the content under it either way.
    LaunchedEffect(topBarSlot, pagerState) {
        snapshotFlow {
            Triple(
                abs(pagerState.currentPageOffsetFraction) < SettledOffset && !animating,
                topBarSlot.page,
                topBarSlot.height,
            )
        }.collect { (settled, page, height) ->
            if (settled) topBarSlot.recordHeight(page, height)
        }
    }

    val barOwner by remember(pagerState, pages) {
        derivedStateOf {
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val distance = navDistance
            if (animating && distance > 0f) {
                // A tap used to hand the bar over at the tap itself, so the page being left lost
                // its bar at once and only the arriving half of the cross-fade ever played. Hand
                // over where a drag does - half way - and both halves play.
                if (abs(position - navFromPage) / distance < 0.5f) navFromPage else uiSelectedPage
            } else {
                position.roundToInt().coerceIn(0, pages.lastIndex.coerceAtLeast(0))
            }
        }
    }

    CompositionLocalProvider(
        LocalPagerState provides pagerState,
        LocalTopBarOwner provides barOwner,
        LocalHandlePageChange provides handlePageChange,
        LocalSelectedPage provides uiSelectedPage,
        LocalPagerPages provides pages,
        LocalTopBarSwipeDelta provides {
            // Measured from the page that published the bar on screen rather than the selected
            // one. The two part company for the frames between the pager passing the halfway
            // point and the handover, and measuring from the selection there flips the delta's
            // sign - which reads as a swipe back towards the page the bar belongs to, snapping a
            // folded search field open for a frame.
            val owner = topBarSlot.page ?: uiSelectedPage
            val raw = pagerState.currentPage + pagerState.currentPageOffsetFraction - owner
            // A drag never exceeds half a page, so it maps straight through. A nav-bar tap
            // can span several, so scale by the jump: that puts the half way point at 0.5, where
            // the fade reaches zero and the bar changes hands, for either kind of move.
            val distance = navDistance
            if (distance > 0f) raw / distance else raw
        },
        LocalTopBarSlot provides topBarSlot
    ) {
        val content = @Composable { paddingBottom: Dp ->
            HorizontalPagerWithInteraction(
                enableGestureOverride = false,
                modifier = Modifier
                    .fillMaxSize()
                    .pagerGestureOverride(
                        pagerState = pagerState,
                        mode = pagerMode,
                        enabled = userScrollEnabled,
                    ),
                state = pagerState,
                userScrollEnabled = userScrollEnabled && !interceptPagerGestures,
                beyondViewportPageCount = if (homeState.isInitialDataLoaded) 1 else 0,
                pageNestedScrollConnection = if (interceptPagerGestures) {
                    PagerGestureNestedScrollConnection
                } else {
                    PagerDefaults.pageNestedScrollConnection(
                        state = pagerState,
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                    )
                },
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = PagerNavigationSpringSpec,
                ),
            ) { pageIndex ->
                if (pages.isEmpty()) return@HorizontalPagerWithInteraction

                val snackBarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(
                    LocalSnackbarHost provides snackBarHostState,
                    LocalPagerPage provides pageIndex,
                    LocalBlurState provides rememberMaterial3BlurBackdrop(
                        enableBlur = themeConfig.isEnableBlur,
                        pagerState = pagerState,
                        pagerPage = pageIndex,
                    ),
                ) {
                    val destination = pages[pageIndex]
                    destination.direction(paddingBottom)
                }
            }
        }

        if (LocalPortraitState.current) {
            Scaffold(
                // The outer scaffold reserves the measured bottom navigation bar height for the
                // pager content; the shared top bar is drawn over it rather than reserving space,
                // so pages keep scrolling underneath.
                contentWindowInsets = WindowInsets(),
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar(
                        destinations = pages,
                        isBottomBar = true,
                    )
                },
                containerColor = Color.Transparent,
            ) { innerPadding ->
                Box(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.blurSource()
                    ) {
                        content(innerPadding.calculateBottomPadding())
                    }
                    Box(
                        modifier = Modifier.onSizeChanged {
                            topBarSlot.height = with(density) { it.height.toDp() }
                        }
                    ) {
                        TopBarScrim {
                            topBarSlot.content?.invoke()
                        }
                    }
                }
            }
        } else {
            var navWidth by remember { mutableIntStateOf(0) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .blurSource(),
                ) {
                    Spacer(
                        modifier = Modifier.width(
                            with(density) { navWidth.toDp() },
                        ),
                    )

                    Box(Modifier.weight(1f)) {
                        content(0.dp)
                    }
                }

                // The shared bar is drawn here as well as in the portrait branch: the pages
                // publish it either way, and without a host for it landscape had no top bar at
                // all and left every page padding itself for a stale height.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(start = with(density) { navWidth.toDp() })
                        .onSizeChanged {
                            topBarSlot.height = with(density) { it.height.toDp() }
                        },
                ) {
                    TopBarScrim {
                        topBarSlot.content?.invoke()
                    }
                }

                NavigationBar(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .onSizeChanged {
                            navWidth = it.width
                        },
                    destinations = pages,
                    isBottomBar = false,
                )
            }
        }
    }
}
