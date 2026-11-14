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
    fun refreshKeepsItsAnchorWhenStartedOverScrolledContent() {
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
                        WearTimeTitleLayout(titleInTime) {
                            AppScaffold(timeText = { WearTimeText(stringResource(R.string.umount_path_manager)) }) {
                                state = rememberTransformingLazyColumnState()
                                WearList(pageTitle = stringResource(R.string.umount_path_manager), isRefreshing = refreshing, listState = state, itemSpacing = spacing) { spec ->
                                    items(20) { WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("row-$it")) }
                                }
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
        if (round) {
            compose.onNodeWithContentDescription(resources.getString(R.string.umount_path_manager)).assertIsDisplayed()
        } else {
            compose.onNodeWithText(resources.getString(R.string.umount_path_manager)).assertIsDisplayed()
        }
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
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
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
                        WearList(pageTitle = stringResource(R.string.module_repo), isRefreshing = refreshing, onBack = {}, listState = listState) { spec ->
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
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeBy(80)
        val entering = firstRowTop()
        val indicatorBefore = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).getUnclippedBoundsInRoot()
        assertEquals(titleBefore.top.value, titleNode.getUnclippedBoundsInRoot().top.value, 4f)
        compose.mainClock.advanceTimeBy(300)
        val settled = firstRowTop()
        val indicatorAfter = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).getUnclippedBoundsInRoot()
        assertTrue("The refresh region remains below the title during entry", indicatorBefore.top >= titleNode.getUnclippedBoundsInRoot().bottom)
        assertTrue("The settled refresh region remains below the title", indicatorAfter.top >= titleNode.getUnclippedBoundsInRoot().bottom)
        assertEquals(titleBefore.top.value, titleNode.getUnclippedBoundsInRoot().top.value, 4f)
        assertTrue("Actions remain in the list region below refresh", action.fetchSemanticsNode().boundsInRoot.top >= compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).fetchSemanticsNode().boundsInRoot.bottom)
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
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeBy(32)
        val reducedPosition = firstRowTop()
        val reducedIndicator = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).fetchSemanticsNode().boundsInRoot
        assertEquals("Reduced motion must immediately reveal the complete indicator", WearLoadingIndicatorSize.value * compose.activity.resources.displayMetrics.density, reducedIndicator.height, 1f)
        compose.mainClock.advanceTimeBy(300)
        assertEquals("Reduced motion must not continue moving the list", reducedPosition.value, firstRowTop().value, 1f)
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
                                WearList(pageTitle = stringResource(titleId), isRefreshing = refreshing) { spec ->
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
        checkTimeTitle()
        compose.runOnIdle { titleId = R.string.module_repo }
        for (newMode in listOf("time", "hidden", "title")) {
            compose.runOnIdle { mode = newMode }
            if (newMode == "title") {
                checkTimeTitle()
            } else {
                val header = compose.onNodeWithText(compose.activity.getString(titleId)).getUnclippedBoundsInRoot()
                val indicator = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
                assertEquals("Refresh follows the current title after a mode change", 8f, (indicator.top - header.bottom).value, 1f)
            }
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
    fun refreshIndicatorStaysRenderedAtTheBottomAndReturnsWithTheTitle() {
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
                        WearList(pageTitle = stringResource(R.string.module_repo), isRefreshing = refreshing, listState = listState) { spec ->
                            items(20) { WearChip(spec, stringResource(R.string.readme)) }
                        }
                    }
                }
            }
        }
        val loading = compose.activity.getString(R.string.wear_loading)
        compose.runOnIdle { refreshing = true }
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        val indicatorId = compose.onNodeWithContentDescription(loading).fetchSemanticsNode().id
        compose.onRoot().performTouchInput {
            swipe(Offset(centerX, height * 0.7f), Offset(centerX, height * 0.4f), 500)
        }
        compose.runOnIdle { listState.requestScrollToItem(20) }
        compose.runOnIdle { assertTrue("The reproduction must reach the bottom", !listState.canScrollForward) }
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        assertEquals("Scrolling to the bottom must retain the refresh node", indicatorId, compose.onNodeWithContentDescription(loading).fetchSemanticsNode().id)
        compose.runOnIdle { listState.requestScrollToItem(0) }
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        assertEquals("Returning to the top must not recreate refresh", indicatorId, compose.onNodeWithContentDescription(loading).fetchSemanticsNode().id)
    }

    @Test
    fun refreshAndBarsShareTheMovingSurfaceDuringPull() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 192
                screenHeightDp = 192
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(surfaceContainer = Color.Red, primary = Color.Green)) {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            WearList(pageTitle = stringResource(R.string.module_repo), isRefreshing = true, onOpenPanel = {}) { spec ->
                                items(20) { WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("pull-row-$it")) }
                            }
                        }
                    }
                }
            }
        }
        val loading = compose.activity.getString(R.string.wear_loading)
        val indicatorBefore = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
        val rowBefore = compose.onNodeWithTag("pull-row-0").getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(Offset(centerX, height * 0.65f))
            moveBy(Offset(0f, height * 0.12f))
        }
        compose.mainClock.advanceTimeBy(80)
        val indicator = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag("pull-row-0").getUnclippedBoundsInRoot()
        val pixels = compose.onNodeWithContentDescription(loading).captureToImage().toPixelMap()
        var green = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y].green > 0.8f && pixels[x, y].red < 0.2f) green++
            }
        }
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        assertTrue("The gesture must move the bars: before=$rowBefore after=$row", row.top > rowBefore.top)
        assertEquals("Refresh and bars move on the same surface", (row.top - rowBefore.top).value, (indicator.top - indicatorBefore.top).value, 1f)
        assertTrue("The bars keep clear of refresh while pulling", row.top >= indicator.bottom)
        assertTrue("The bars must not cover the refresh surface", green > 100)
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
                                WearList(pageTitle = stringResource(R.string.umount_path_manager), isRefreshing = refreshing, onBack = {}, hasOperationsPanel = true, onOpenPanel = { menuOpen = true }) { spec ->
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
        compose.mainClock.advanceTimeBy(400)
        assertRowsPainted("refresh")
        val indicator = compose.onNodeWithContentDescription(loading).getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag("painted-row").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText(compose.activity.getString(R.string.umount_path_manager)).getUnclippedBoundsInRoot()
        assertEquals("The indicator follows the title as the menu closes", 8f, (indicator.top - title.bottom).value, 1f)
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
