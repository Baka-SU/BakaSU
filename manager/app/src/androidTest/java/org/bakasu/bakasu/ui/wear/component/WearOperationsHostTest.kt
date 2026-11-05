package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearOperationsHostTest(private val round: Boolean, private val inPager: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var menuOpen by mutableStateOf(false)
    private lateinit var pageState: TransformingLazyColumnState
    private lateinit var menuState: TransformingLazyColumnState

    @Test
    fun crownOpensFullMenuAndReturnsFocusAfterClosing() {
        showHost()
        openWithCrown()
        assertFullMenu()

        rotateCrown(64f)
        compose.runOnIdle {
            assertTrue("The open menu must receive rotary scrolling", menuState.canScrollBackward)
            assertFalse("The covered page must stay at the top", pageState.canScrollBackward)
        }
        closeWithCrown()
        openWithCrown()
        assertFullMenu()
    }

    @Test
    fun touchOpeningStillHandsCrownFocusToTheMenu() {
        showHost()
        compose.onNodeWithTag("page").performTouchInput {
            swipe(Offset(centerX, height * 0.25f), Offset(centerX, height * 0.85f), 300)
        }
        compose.waitForIdle()
        assertFullMenu()
        closeWithCrown()
        openWithCrown()
        assertFullMenu()
    }

    @Test
    fun ordinaryCrownScrollingStaysOnThePage() {
        showHost()
        rotateCrown(64f)
        compose.runOnIdle {
            assertTrue(pageState.canScrollBackward)
            assertFalse(menuOpen)
        }
        compose.onNodeWithTag("menu").assertDoesNotExist()
    }

    private fun showHost() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        Box(Modifier.fillMaxSize().testTag("viewport")) {
                            WearOperationsHost(
                                menuKey = "operations",
                                menuOpen = menuOpen,
                                menuActive = menuOpen,
                                onBack = { menuOpen = false },
                                menuContent = { onClosing ->
                                    menuState = rememberTransformingLazyColumnState()
                                    Box(Modifier.fillMaxSize().testTag("menu")) {
                                        WearList(
                                            listState = menuState,
                                            onClosePanel = { menuOpen = false },
                                            onPanelClosing = onClosing,
                                        ) { spec ->
                                            items(8) { WearChip(spec, stringResource(R.string.wear_filter_sort)) }
                                        }
                                    }
                                },
                            ) {
                                if (inPager) {
                                    WearHorizontalPager(rememberPagerState { 1 }) { Page() }
                                } else {
                                    Page()
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Page() {
        pageState = rememberTransformingLazyColumnState()
        Box(Modifier.fillMaxSize().testTag("page")) {
            WearList(listState = pageState, hasOperationsPanel = true, onOpenPanel = { menuOpen = true }) { spec ->
                items(8) { WearChip(spec, stringResource(R.string.home)) }
            }
        }
    }

    private fun openWithCrown() {
        // Each step is below the 144dp crown threshold. A preview is composed between steps,
        // so this catches it taking focus before the page can finish accumulating the rotation.
        repeat(3) { rotateCrown(-64f) }
        compose.runOnIdle { assertTrue("Continuous crown input must finish opening the menu", menuOpen) }
    }

    private fun closeWithCrown() {
        repeat(16) {
            if (compose.runOnIdle { menuOpen }) rotateCrown(96f)
        }
        compose.runOnIdle { assertFalse("The crown must close the menu past its end", menuOpen) }
        compose.onNodeWithTag("menu").assertDoesNotExist()
    }

    private fun assertFullMenu() {
        compose.runOnIdle { assertTrue(menuOpen) }
        val viewport = compose.onNodeWithTag("viewport").getUnclippedBoundsInRoot()
        val menu = compose.onNodeWithTag("menu").getUnclippedBoundsInRoot()
        assertEquals(viewport.top.value, menu.top.value, 0.5f)
        assertEquals(viewport.bottom.value, menu.bottom.value, 0.5f)
    }

    private fun rotateCrown(distanceDp: Float) {
        compose.runOnIdle {
            val activity = compose.activity
            val pixels = distanceDp * activity.resources.displayMetrics.density
            val coordinates = MotionEvent.PointerCoords().apply {
                setAxisValue(MotionEvent.AXIS_SCROLL, -pixels / ViewConfiguration.get(activity).scaledVerticalScrollFactor)
            }
            val properties = MotionEvent.PointerProperties().apply { id = 0 }
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coordinates),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0,
            )
            try {
                activity.dispatchGenericMotionEvent(event)
            } finally {
                event.recycle()
            }
        }
        compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, pager={1}")
        fun configurations() = listOf(arrayOf(true, true), arrayOf(false, true), arrayOf(true, false), arrayOf(false, false))
    }
}
