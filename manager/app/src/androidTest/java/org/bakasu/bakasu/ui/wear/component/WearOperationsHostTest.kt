package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.WearSuperUserPanel
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
    private var pageLoading by mutableStateOf(false)
    private var pageHasEdgeButton = false
    private var pageRevision by mutableIntStateOf(0)
    private lateinit var pageState: TransformingLazyColumnState
    private lateinit var menuState: TransformingLazyColumnState
    private lateinit var composeView: View
    private val hapticEvents = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            hapticEvents += hapticFeedbackType
        }
    }

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
    fun secondPullOpensAfterReturningToTheTop() {
        showHost()
        compose.runOnIdle { pageState.dispatchRawDelta(80f * compose.activity.resources.displayMetrics.density) }
        compose.runOnIdle { assertTrue("The first gesture must start away from the top", pageState.canScrollBackward) }
        compose.onNodeWithTag("page").performTouchInput {
            swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.8f), 600)
        }
        compose.runOnIdle { assertFalse("Scrolling to the top must not open the menu", menuOpen) }
        compose.onNodeWithTag("page").performTouchInput {
            swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.8f), 300)
        }
        assertFullMenu()
    }

    @Test
    fun nearTopResidualDoesNotBlockTheNextPull() {
        showHost()
        // Reproduce the small residual offset left by the transforming list after returning.
        compose.runOnIdle { pageState.dispatchRawDelta(2f * compose.activity.resources.displayMetrics.density) }
        compose.runOnIdle {
            assertTrue("The reproduction must exercise canScrollBackward=true", pageState.canScrollBackward)
        }
        compose.onNodeWithTag("page").performTouchInput {
            swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.8f), 300)
        }
        assertFullMenu()
    }

    @Test
    fun pullingPastThePageEndDoesNotRefreshOrOpenTheMenu() {
        showHost()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("page")), useUnmergedTree = true).performScrollToIndex(7)
        compose.onNodeWithTag("page").performTouchInput { swipeUp() }
        compose.runOnIdle { assertFalse(menuOpen) }
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

    @Test
    fun crownStillScrollsWhenTheEdgeButtonHasFocus() {
        pageHasEdgeButton = true
        showHost()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("page")), useUnmergedTree = true).performScrollToIndex(7)
        compose.onNodeWithText(compose.activity.getString(R.string.confirm))
            .performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        val before = compose.runOnIdle { pageState.layoutInfo.visibleItems.first().let { it.index to it.offset } }
        rotateCrown(-64f)
        val after = compose.runOnIdle { pageState.layoutInfo.visibleItems.first().let { it.index to it.offset } }
        assertTrue("Crown scrolling must still reach the list from its edge button", before != after)
    }

    @Test
    fun continuousCrownRotationKeepsItsDistanceAcrossRecompositions() {
        showHost()
        val initial = compose.runOnIdle { pageState.layoutInfo.visibleItems.first().offset }
        compose.mainClock.autoAdvance = false
        try {
            repeat(30) {
                compose.runOnUiThread {
                    dispatchCrown(8f)
                    pageRevision++
                }
                compose.mainClock.advanceTimeBy(16)
            }
            compose.mainClock.advanceTimeBy(1500)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.runOnIdle {
            val first = pageState.layoutInfo.visibleItems.first()
            val movedPastFirst = first.index > 0 || initial - first.offset > 80f * compose.activity.resources.displayMetrics.density
            assertTrue("Thirty 8dp crown events must move more than a few pixels: first=$first", movedPastFirst)
        }
    }

    @Test
    fun crownFocusReturnsAfterPageLoading() {
        showHost()
        rotateCrown(64f)
        compose.runOnIdle { pageLoading = true }
        compose.runOnIdle { pageLoading = false }
        val before = compose.runOnIdle { pageState.layoutInfo.visibleItems.first().let { it.index to it.offset } }
        rotateCrown(64f)
        val after = compose.runOnIdle { pageState.layoutInfo.visibleItems.first().let { it.index to it.offset } }
        assertTrue("Loaded content must regain rotary scrolling", before != after)
    }

    @Test
    fun horizontalSwipesDoNotCloseMenuOrVibrate() {
        showHost(initiallyOpen = true)
        compose.onNodeWithTag("menu").performTouchInput {
            swipe(Offset(1f, centerY), Offset(width - 1f, centerY), 300)
            swipe(Offset(width - 1f, centerY), Offset(1f, centerY), 300)
        }
        assertFullMenu()
        compose.runOnIdle { assertTrue("Ignored horizontal gestures must not emit haptics", hapticEvents.isEmpty()) }
    }

    @Test
    fun systemBackExclusionExistsOnlyWhileMenuIsActive() {
        if (Build.VERSION.SDK_INT < 29) return
        showHost()
        val originalExclusions = compose.runOnIdle { composeView.systemGestureExclusionRects.toList() }
        compose.runOnIdle { menuOpen = true }
        compose.runOnIdle {
            assertTrue(
                "An active menu must exclude both system back edges",
                composeView.systemGestureExclusionRects.any { it.left <= 0 && it.right >= composeView.width && it.height() > 0 },
            )
            menuOpen = false
        }
        compose.runOnIdle { assertEquals(originalExclusions, composeView.systemGestureExclusionRects) }
    }

    @Test
    fun upwardPullClosesMenuOnlyAfterThreshold() {
        showHost(initiallyOpen = true)
        menuList().performScrollToIndex(7)
        compose.onNodeWithTag("menu").performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.70f), 300)
        }
        assertFullMenu()
        compose.onNodeWithTag("menu").performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.25f), 300)
        }
        compose.runOnIdle { assertFalse("An upward pull at the bottom must close the menu", menuOpen) }
        compose.onNodeWithTag("menu").assertDoesNotExist()
    }

    @Test
    fun scrollingDownFromTheEndDoesNotCloseMenu() {
        showHost(initiallyOpen = true)
        menuList().performScrollToIndex(7)
        compose.onNodeWithTag("menu").performTouchInput {
            swipe(Offset(centerX, height * 0.25f), Offset(centerX, height * 0.85f), 300)
        }
        assertFullMenu()
    }

    @Test
    fun downwardPullAtTheTopDoesNotCloseMenu() {
        showHost(initiallyOpen = true)
        compose.onNodeWithTag("menu").performTouchInput {
            swipe(Offset(centerX, height * 0.25f), Offset(centerX, height * 0.85f), 300)
        }
        assertFullMenu()
    }

    @Test
    fun predictiveBackFromEitherEdgeDoesNotCloseMenu() {
        showHost(initiallyOpen = true)
        listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT).forEach { edge ->
            compose.runOnIdle {
                compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, edge))
            }
            compose.runOnIdle {
                compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.8f, edge))
            }
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            assertFullMenu()
        }
    }

    @Test
    fun backButtonStillClosesMenu() {
        showHost(initiallyOpen = true)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertFalse(menuOpen) }
    }

    @Test
    fun filterBottomScrollingNeverNavigatesBack() {
        var backs = 0
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    Box(Modifier.fillMaxSize().testTag("filter")) {
                        WearSuperUserPanel(SuperUserUiState(), { backs++ }) {}
                    }
                }
            }
        }
        repeat(16) { rotateCrown(96f) }
        repeat(3) { compose.onNodeWithTag("filter").performTouchInput { swipeUp() } }
        compose.runOnIdle { assertEquals(0, backs) }
    }

    @Test
    fun stoppingTheCrownReleasesBothOpeningAndClosingPreviews() {
        showHost()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatchCrown(-48f) }
        compose.mainClock.advanceTimeBy(32)
        compose.mainClock.advanceTimeBy(420)
        compose.runOnIdle { assertFalse(menuOpen) }
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        openWithCrown()
        menuList().performScrollToIndex(7)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatchCrown(48f) }
        compose.mainClock.advanceTimeBy(32)
        compose.mainClock.advanceTimeBy(420)
        assertFullMenu()
        compose.mainClock.autoAdvance = true
        closeWithCrown()
    }

    @Test
    fun crownPauseRestoresTheMovingListWithin420Milliseconds() {
        showHost(initiallyOpen = true)
        menuList().performScrollToIndex(7)
        val fullBottom = menuList().getUnclippedBoundsInRoot().bottom.value
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { dispatchCrown(48f) }
        var firstRestored: Int? = null
        repeat(27) { frame ->
            compose.mainClock.advanceTimeBy(16)
            val bottom = menuList().getUnclippedBoundsInRoot().bottom.value
            if (frame == 1) assertTrue(bottom < fullBottom - 1f)
            if (frame > 1 && kotlin.math.abs(bottom - fullBottom) < 0.5f && firstRestored == null) firstRestored = (frame + 1) * 16
            android.util.Log.i("Wear1110Fixed", "rotary round=$round pager=$inPager t=${(frame + 1) * 16} bottom=$bottom")
            if (round && inPager && frame in listOf(1, 11, 19, 26)) {
                val bitmap = compose.onNodeWithTag("viewport").captureToImage().asAndroidBitmap()
                java.io.File(compose.activity.getExternalFilesDir(null), "fixed-rotary-${(frame + 1) * 16}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        assertTrue("The actual moving list must settle promptly: $firstRestored", firstRestored != null && firstRestored!! <= 420)
        assertFullMenu()
        compose.mainClock.autoAdvance = true
    }

    private fun menuList() = compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("menu")), useUnmergedTree = true)

    private fun showHost(initiallyOpen: Boolean = false) {
        menuOpen = initiallyOpen
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalHapticFeedback provides haptics) {
                composeView = LocalView.current
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        Box(Modifier.fillMaxSize().testTag("viewport")) {
                            WearOperationsHost(
                                menuKey = "operations",
                                menuOpen = menuOpen,
                                menuActive = menuOpen,
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
                            WearOperationsBackHandler(menuOpen) { menuOpen = false }
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
            WearList(
                listState = pageState,
                panelLabel = pageRevision.toString(),
                isLoading = pageLoading,
                hasOperationsPanel = true,
                onOpenPanel = { menuOpen = true },
                onConfirm = if (pageHasEdgeButton) ({}) else null,
                confirmLabel = stringResource(R.string.confirm),
            ) { spec ->
                items(8) { WearChip(spec, stringResource(R.string.home)) }
            }
        }
    }

    private fun openWithCrown() {
        // Each step is below the 144dp crown threshold. A preview is composed between steps,
        // so this catches it taking focus before the page can finish accumulating the rotation.
        compose.mainClock.autoAdvance = false
        try {
            repeat(3) {
                compose.runOnUiThread { dispatchCrown(-64f) }
                compose.mainClock.advanceTimeBy(32)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
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
        val menu = menuList().getUnclippedBoundsInRoot()
        assertEquals(viewport.top.value, menu.top.value, 0.5f)
        assertEquals(viewport.bottom.value, menu.bottom.value, 0.5f)
    }

    private fun rotateCrown(distanceDp: Float) {
        compose.runOnIdle { dispatchCrown(distanceDp) }
        compose.waitForIdle()
    }

    private fun dispatchCrown(distanceDp: Float) {
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

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, pager={1}")
        fun configurations() = listOf(arrayOf(true, true), arrayOf(false, true), arrayOf(true, false), arrayOf(false, false))
    }
}
