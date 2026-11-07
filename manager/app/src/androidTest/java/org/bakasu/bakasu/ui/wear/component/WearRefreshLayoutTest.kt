package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
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
    fun refreshFollowsTheTitleAndStaysVisibleWithoutChangingTheScrolledItem() {
        val resources = compose.activity.resources
        var refreshing by mutableStateOf(false)
        var titleInTime by mutableStateOf(false)
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
                            WearList(isRefreshing = refreshing, listState = state) { spec ->
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
        assertTrue("Indicator must follow the whole title", indicator.top - title.bottom >= 7.dp)
        assertTrue("Indicator must leave space before the first row: title=$title indicator=$indicator row=$row", row.top - indicator.bottom >= 7.dp)
        assertTrue("The first bar row must settle below the title during refresh: before=$firstRowBefore after=$row title=$title indicator=$indicator", row.top - firstRowBefore.top >= 48.dp)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "refresh-spacing-$round-$fontScale.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
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
                        WearList(isRefreshing = refreshing) { spec ->
                            item { WearPageHeader(spec, stringResource(R.string.module_repo)) }
                            item {
                                WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("first-row"))
                            }
                        }
                    }
                }
            }
        }
        fun firstRowTop() = compose.onNodeWithTag("first-row").getUnclippedBoundsInRoot().top
        val original = firstRowTop()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { refreshing = true }
        compose.mainClock.advanceTimeBy(80)
        val entering = firstRowTop()
        compose.mainClock.advanceTimeBy(300)
        val settled = firstRowTop()
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
        assertEquals("Reduced motion must apply the full gap immediately", (reducedBaseline + 64.dp).value, firstRowTop().value, 1f)
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

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
