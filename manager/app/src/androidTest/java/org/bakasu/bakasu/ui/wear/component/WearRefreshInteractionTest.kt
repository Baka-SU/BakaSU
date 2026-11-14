package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRefreshInteractionTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun refreshReservesItsOwnSpaceAndLocksTheListButKeepsMenusAndPaging() {
        val refreshing = mutableStateOf(false)
        lateinit var list: TransformingLazyColumnState
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        lateinit var view: View
        var menus = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 192
                screenHeightDp = 192
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    view = LocalView.current
                    list = rememberTransformingLazyColumnState()
                    pager = rememberPagerState { 2 }
                    scope = rememberCoroutineScope()
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            WearHorizontalPager(pager) { page ->
                                if (page == 0) {
                                    WearList(pageTitle = stringResource(R.string.module_repo), isRefreshing = refreshing.value, listState = list, onOpenPanel = { menus++ }) { spec ->
                                        items(20) { WearChip(spec, stringResource(R.string.readme), modifier = Modifier.testTag("refresh-row-$it")) }
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize().testTag("next-page"))
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { list.requestScrollToItem(20) }
        compose.runOnIdle { refreshing.value = true }
        val loading = compose.activity.getString(R.string.wear_loading)
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        val indicator = compose.onNodeWithContentDescription(loading).fetchSemanticsNode()
        val header = compose.onNodeWithText(compose.activity.getString(R.string.module_repo)).fetchSemanticsNode().boundsInRoot
        assertTrue("The title and refresh animation must not overlap", header.bottom <= indicator.boundsInRoot.top)
        val visible = compose.runOnIdle { list.layoutInfo.visibleItems.map { it.index }.filter { it > 0 } }
        visible.forEach { index ->
            val bounds = compose.onNodeWithTag("refresh-row-${index - 1}").fetchSemanticsNode().boundsInRoot
            if (bounds.height > 0f) assertTrue("The list must remain below the refresh region", bounds.top >= indicator.boundsInRoot.bottom)
        }
        File(compose.activity.getExternalFilesDir(null), "refresh-no-overlap-$round-$fontScale.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        fun anchor() = compose.runOnIdle { list.anchorItemIndex to list.anchorItemScrollOffset }
        val locked = anchor()
        compose.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.4f), 400) }
        compose.runOnUiThread {
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val coordinates = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_SCROLL, 2f) }
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0)
            try {
                view.dispatchGenericMotionEvent(event)
            } finally {
                event.recycle()
            }
        }
        assertEquals("Touch and crown scrolling must be locked during refresh", locked, anchor())
        compose.runOnIdle { list.requestScrollToItem(0) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        assertEquals("Returning to the top retains the refresh node", indicator.id, compose.onNodeWithContentDescription(loading).fetchSemanticsNode().id)
        compose.runOnIdle { assertTrue("The menu pull starts at the list's top boundary: anchor=${list.anchorItemIndex}/${list.anchorItemScrollOffset}, first=${list.layoutInfo.visibleItems.firstOrNull()}, backward=${list.canScrollBackward}", list.isAtWearListStart(4)) }
        compose.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.85f), 400) }
        compose.runOnIdle { assertEquals("The operations pull remains available", 1, menus) }
        compose.onRoot().performTouchInput { swipe(Offset(width * 0.85f, centerY), Offset(width * 0.15f, centerY), 500) }
        compose.runOnIdle { assertEquals("Horizontal paging remains available", 1, pager.currentPage) }
        compose.runOnIdle { scope.launch { pager.scrollToPage(0) } }
        compose.runOnIdle { refreshing.value = false }
        compose.runOnIdle { list.requestScrollToItem(5) }
        val unlocked = anchor()
        compose.onRoot().performTouchInput { swipe(Offset(centerX, height * 0.8f), Offset(centerX, height * 0.25f), 400) }
        assertTrue("List scrolling resumes after refresh", unlocked != anchor())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
