package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventType
import org.bakasu.bakasu.ui.viewmodel.SulogLoading
import org.bakasu.bakasu.ui.viewmodel.SulogUiState
import org.bakasu.bakasu.ui.wear.component.LocalWearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.bakasu.bakasu.ui.wear.component.WearTimeOptions
import org.bakasu.bakasu.ui.wear.component.WearTimeText
import org.bakasu.bakasu.ui.wear.component.WearTimeTitleLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearRefreshPageTest(private val round: Boolean, private val mode: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun logMenuRefreshKeepsTheIndicatorAtItsViewportAnchorDuringEveryScrollFrame() {
        var loading by mutableStateOf(SulogLoading.NONE)
        var menu by mutableStateOf(false)
        val records = (1..100).map { SulogEntry("$it", SulogEventType.RootExecve, "seq=$it comm=Record-$it", null, mapOf("comm" to "Record-$it")) }
        val state = SulogUiState(isLoading = false, isSulogEnabled = true, sulogStatus = "supported", visibleEntries = records)
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalWearTimeOptions provides WearTimeOptions(mode)) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp).testTag("viewport")) {
                        WearTimeTitleLayout(R.string.sulog) {
                            AppScaffold(timeText = { WearTimeText(compose.activity.getString(R.string.sulog)) }) {
                                WearOperationsHost("logs", menu, menu, { closing ->
                                    WearList(onClosePanel = { menu = false }, onPanelClosing = closing, closePanelRequested = loading == SulogLoading.REFRESH) { spec ->
                                        item { WearChip(spec, compose.activity.getString(R.string.wear_refresh), onClick = { loading = SulogLoading.REFRESH }) }
                                    }
                                }) {
                                    WearLogsPage(state, {}, {}, { loading = SulogLoading.REFRESH }, { menu = true }, {}, hasOperationsPanel = true, loading = loading)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { menu = true }
        compose.onNodeWithText(compose.activity.getString(R.string.wear_refresh)).performClick()
        val indicator = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading))
        val top = indicator.getUnclippedBoundsInRoot().top.value
        // Accessibility/instant scrolling may update the layout without isScrollInProgress=true.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        assertEquals("Refresh must not follow a newly measured title", top, indicator.getUnclippedBoundsInRoot().top.value, 1f)
        compose.mainClock.autoAdvance = false
        repeat(12) {
            compose.runOnIdle {
                val now = SystemClock.uptimeMillis()
                val coords = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_SCROLL, -4f * compose.activity.resources.displayMetrics.density / ViewConfiguration.get(compose.activity).scaledVerticalScrollFactor) }
                val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(MotionEvent.PointerProperties().apply { id = 0 }), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0)
                try {
                    compose.activity.dispatchGenericMotionEvent(event)
                } finally {
                    event.recycle()
                }
            }
            compose.mainClock.advanceTimeBy(32)
            assertEquals("Crown frame $it", top, indicator.getUnclippedBoundsInRoot().top.value, 1f)
        }
        compose.onNodeWithTag("viewport").performTouchInput { down(Offset(centerX, height * 0.75f)) }
        repeat(8) {
            compose.onNodeWithTag("viewport").performTouchInput { moveBy(Offset(0f, -4f), 16) }
            compose.mainClock.advanceTimeByFrame()
            assertEquals("Touch frame $it", top, indicator.getUnclippedBoundsInRoot().top.value, 1f)
        }
        compose.onNodeWithTag("viewport").performTouchInput { up() }
        compose.runOnIdle { loading = SulogLoading.NONE }
        compose.mainClock.advanceTimeBy(400)
        indicator.assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, mode={1}")
        fun configurations() = listOf(true, false).flatMap { round -> listOf("time", "title", "hidden").map { arrayOf<Any>(round, it) } }
    }
}
