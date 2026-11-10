package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.foundation.LocalReduceMotion
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import org.bakasu.bakasu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRefreshLayoutTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshFollowsTheTitleButDoesNotAppearOverScrolledContent() {
        val resources = compose.activity.resources
        var refreshing by mutableStateOf(false)
        var titleInTime by mutableStateOf(false)
        var spacing by mutableStateOf(4.dp)
        lateinit var state: TransformingLazyColumnState
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 192
                screenHeightDp = 192
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalWearTimeOptions provides WearTimeOptions(if (titleInTime) "title" else "time"),
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            state = rememberTransformingLazyColumnState()
                            WearList(isRefreshing = refreshing, listState = state, itemSpacing = spacing) { spec ->
                                item { WearPageHeader(spec, stringResource(R.string.umount_path_manager)) }
                                items(20) { WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("row-$it")) }
                            }
                        }
                    }
                }
            }
        }
        val loading = resources.getString(R.string.wear_loading)
        val firstRowBefore = compose.onNodeWithTag("row-0").getUnclippedBoundsInRoot()
        val titleBefore = compose.onNodeWithText(resources.getString(R.string.umount_path_manager)).assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.runOnIdle { refreshing = true }
        compose.waitForIdle()
        val indicator = compose.onNodeWithContentDescription(loading).assertIsDisplayed().getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(resources.getString(R.string.umount_path_manager)).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals("The title stays in place during refresh", titleBefore.top.value, title.top.value, 1f)
        val row = compose.onNodeWithTag("row-0").getUnclippedBoundsInRoot()
        assertEquals("Indicator must leave 8dp below the title text", 8f, (indicator.top - title.bottom).value, 1f)
        assertEquals("Indicator must leave the same 8dp gap before the first row", 8f, (row.top - indicator.bottom).value, 1f)
        assertTrue("The first bar row must settle below the title during refresh: before=$firstRowBefore after=$row title=$title indicator=$indicator", row.top - firstRowBefore.top >= 48.dp)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "refresh-spacing-$round-$fontScale.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle { refreshing = false }
        compose.waitForIdle()
        compose.runOnIdle { spacing = 2.dp }
        compose.waitForIdle()
        compose.runOnIdle { refreshing = true }
        val compactIndicator = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
        val compactRow = compose.onNodeWithTag("row-0").getUnclippedBoundsInRoot()
        assertEquals("Compact item spacing must also leave an 8dp gap", 8f, (compactRow.top - compactIndicator.bottom).value, 1f)
        compose.runOnIdle { titleInTime = true }
        compose.onNodeWithText(resources.getString(R.string.umount_path_manager)).assertDoesNotExist()
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        val rowDuringRefresh = compose.onNodeWithTag("row-0").getUnclippedBoundsInRoot()
        compose.runOnIdle { refreshing = false }
        compose.waitForIdle()
        val rowAfterRefresh = compose.onNodeWithTag("row-0").getUnclippedBoundsInRoot()
        assertTrue("The first bar row must return when refresh ends", rowDuringRefresh.top - rowAfterRefresh.top >= 48.dp)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(15)
        val anchor = compose.runOnIdle { state.anchorItemIndex }
        val beforeScrolledRefresh = compose.runOnIdle { state.anchorItemScrollOffset }
        compose.runOnIdle { refreshing = true }
        compose.onNodeWithContentDescription(loading).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(anchor, state.anchorItemIndex)
            assertEquals(beforeScrolledRefresh, state.anchorItemScrollOffset)
        }
    }

    @Test
    fun refreshSpaceAnimatesDownAndBackAndHonorsReducedMotion() {
        var refreshing by mutableStateOf(false)
        var reduceMotion by mutableStateOf(false)
        lateinit var listState: TransformingLazyColumnState
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalReduceMotion provides reduceMotion,
            ) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        listState = rememberTransformingLazyColumnState()
                        WearList(isRefreshing = refreshing, onBack = {}, listState = listState) { spec ->
                            item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
                            item {
                                WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("first-row"))
                            }
                            item {
                                WearIconButtonGroup(spec, listOf(WearIconAction(Icons.Rounded.Info, stringResource(R.string.about), {})))
                            }
                        }
                    }
                }
            }
        }
        fun firstRowTop() = compose.onNodeWithTag("first-row").getUnclippedBoundsInRoot().top
        val original = firstRowTop()
        val titleNode = compose.onNodeWithText(compose.activity.getString(R.string.module_repo))
        val titleBefore = titleNode.getUnclippedBoundsInRoot()
        val action = compose.onNodeWithContentDescription(compose.activity.getString(R.string.about))
        val actionBefore = action.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeBy(80)
        val entering = firstRowTop()
        val indicatorBefore = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).getUnclippedBoundsInRoot()
        assertEquals(titleBefore.top.value, titleNode.getUnclippedBoundsInRoot().top.value, 1f)
        compose.mainClock.advanceTimeBy(300)
        val settled = firstRowTop()
        val indicatorAfter = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).getUnclippedBoundsInRoot()
        assertEquals(indicatorBefore.top.value, indicatorAfter.top.value, 1f)
        assertEquals(titleBefore.top.value, titleNode.getUnclippedBoundsInRoot().top.value, 1f)
        assertEquals(48f, (action.getUnclippedBoundsInRoot().top - actionBefore.top).value, 1f)
        assertEquals(actionBefore.left.value, action.getUnclippedBoundsInRoot().left.value, 1f)
        assertTrue("The bars must move down over multiple frames", original < entering && entering < settled)
        compose.runOnIdle { refreshing = false }
        compose.mainClock.advanceTimeBy(80)
        val leaving = firstRowTop()
        compose.mainClock.advanceTimeBy(300)
        val restored = firstRowTop()
        assertTrue("The bars must return over multiple frames", restored < leaving && leaving < settled)
        assertEquals(original.value, restored.value, 1f)
        compose.runOnIdle { reduceMotion = true }
        compose.mainClock.advanceTimeBy(32)
        val reducedBaseline = firstRowTop()
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeBy(32)
        assertEquals("Reduced motion must apply the full gap immediately: backward=${listState.canScrollBackward}, padding=${listState.layoutInfo.beforeContentPadding}, first=${listState.layoutInfo.visibleItems.firstOrNull()?.let { it.index to it.offset }}, anchor=${listState.anchorItemIndex}:${listState.anchorItemScrollOffset}", (reducedBaseline + 48.dp).value, firstRowTop().value, 1f)
        compose.runOnIdle {
            refreshing = false
            reduceMotion = false
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { refreshing = false }
        compose.mainClock.advanceTimeBy(400)
        assertEquals("A short request must not leave an offset", original.value, firstRowTop().value, 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun actualTimeTitleAnchorsRefreshAcrossModesAndPageChanges() {
        var mode by mutableStateOf("title")
        var refreshing by mutableStateOf(false)
        var titleId by mutableStateOf(R.string.module_repo)
        var immersive by mutableStateOf(false)
        lateinit var titleBottom: MutableState<Float?>
        var pixelsPerDp = 1f
        var clicks = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 192
                screenHeightDp = 192
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            val density = Density(LocalDensity.current.density, fontScale)
            pixelsPerDp = density.density
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides density,
                LocalWearTimeOptions provides WearTimeOptions(mode, immersive),
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        WearTimeTitleLayout(titleId) {
                            titleBottom = requireNotNull(LocalWearTimeTitleBottom.current)
                            AppScaffold(timeText = { WearTimeText(stringResource(titleId)) }) {
                                WearList(isRefreshing = refreshing) { spec ->
                                    item { WearPageHeader(spec, stringResource(titleId)) }
                                    item { WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("time-first"), onClick = { clicks++ }) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val loading = compose.activity.getString(R.string.wear_loading)
        fun checkTimeTitle() {
            val title = compose.activity.getString(titleId)
            if (round) {
                compose.onNodeWithContentDescription(title).assertIsDisplayed()
                compose.onAllNodesWithText(title).assertCountEquals(0)
            } else {
                compose.onAllNodesWithText(title).assertCountEquals(1)
            }
            val bottom = compose.runOnIdle { requireNotNull(titleBottom.value) / pixelsPerDp }
            val indicator = compose.onNodeWithContentDescription(loading).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Indicator follows the measured time title: bottom=$bottom indicator=$indicator", indicator.top.value >= bottom + 7f)
            assertTrue("The outer TimeText box must not push refresh to the bottom", indicator.top < 96.dp)
            assertTrue(compose.onNodeWithTag("time-first").getUnclippedBoundsInRoot().top >= indicator.bottom)
        }
        compose.runOnIdle { refreshing = true }
        checkTimeTitle()
        compose.onNodeWithTag("time-first").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
        val before = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
        compose.runOnIdle { immersive = true }
        checkTimeTitle()
        assertEquals(before.top.value, compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot().top.value, 1f)
        compose.runOnIdle { titleId = R.string.umount_path_manager }
        assertEquals(before.top.value, compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot().top.value, 1f)
        compose.runOnIdle { titleId = R.string.module_repo }
        for (newMode in listOf("time", "hidden", "title")) {
            compose.runOnIdle { mode = newMode }
            val indicator = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
            assertEquals("This request keeps its root anchor when the title mode changes", before.top.value, indicator.top.value, 1f)
        }
        compose.mainClock.autoAdvance = false
        val fixed = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot().top
        compose.runOnIdle { refreshing = false }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeBy(80)
        assertEquals(fixed.value, compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot().top.value, 1f)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(fixed.value, compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot().top.value, 1f)
        compose.mainClock.autoAdvance = true
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "refresh-time-title-$round-$fontScale.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun refreshIndicatorIsNotVisibleAtTheBottomDuringRefresh() {
        var refreshing by mutableStateOf(false)
        lateinit var listState: TransformingLazyColumnState
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        listState = rememberTransformingLazyColumnState()
                        WearList(isRefreshing = refreshing, listState = listState) { spec ->
                            item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
                            items(20) { WearChip(spec, stringResource(R.string.readme)) }
                        }
                    }
                }
            }
        }
        val loading = compose.activity.getString(R.string.wear_loading)
        compose.runOnIdle { refreshing = true }
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            swipe(Offset(centerX, height * 0.7f), Offset(centerX, height * 0.4f), 500)
        }
        compose.onNodeWithContentDescription(loading).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(20)
        compose.runOnIdle { assertTrue("The reproduction must reach the bottom", !listState.canScrollForward) }
        compose.onNodeWithContentDescription(loading).assertDoesNotExist()
    }

    @Test
    fun refreshRowsStayPaintedWithoutAnotherScroll() {
        var refreshing by mutableStateOf(false)
        var menuOpen by mutableStateOf(false)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 192
                screenHeightDp = 192
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surfaceContainer = Color.Red)) {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            WearOperationsHost(
                                menuKey = "refresh",
                                menuOpen = menuOpen,
                                menuActive = menuOpen,
                                menuContent = { onClosing ->
                                    WearList(onClosePanel = { menuOpen = false }, onPanelClosing = onClosing, closePanelRequested = refreshing) { spec ->
                                        item { WearChip(spec, stringResource(R.string.wear_refresh)) }
                                    }
                                },
                            ) {
                                WearList(isRefreshing = refreshing, onBack = {}, hasOperationsPanel = true, onOpenPanel = { menuOpen = true }) { spec ->
                                    item { WearPageHeader(spec, stringResource(R.string.umount_path_manager)) }
                                    item { WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("painted-row")) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val loading = compose.activity.getString(R.string.wear_loading)
        fun assertRowsPainted(stage: String) {
            val bitmap = compose.onRoot().captureToImage()
            val pixels = bitmap.toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red > 0.5f && color.green < 0.1f && color.blue < 0.1f) count++
                }
            }
            File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "refresh-paint-$stage-$round-$fontScale.png").outputStream().use {
                bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue("The visible rows must be painted during $stage (red pixels: $count)", count > 100)
        }
        assertRowsPainted("idle")
        compose.runOnIdle { menuOpen = true }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeByFrame()
        val requestAnchor = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot().top
        compose.mainClock.advanceTimeBy(400)
        assertRowsPainted("refresh")
        val indicator = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag("painted-row").getUnclippedBoundsInRoot()
        assertEquals("Closing the menu must preserve the request root anchor", requestAnchor.value, indicator.top.value, 1f)
        assertTrue("Refresh space must keep the first row clear of the indicator", row.top >= indicator.bottom)
        compose.runOnIdle { refreshing = false }
        compose.mainClock.advanceTimeBy(80)
        assertRowsPainted("exit")
        compose.mainClock.advanceTimeBy(300)
        assertRowsPainted("completion")
        compose.mainClock.autoAdvance = true
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
