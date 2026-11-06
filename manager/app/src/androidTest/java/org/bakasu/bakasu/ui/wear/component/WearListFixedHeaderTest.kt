package org.bakasu.bakasu.ui.wear.component

import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearListFixedHeaderTest(private val round: Boolean, private val fontScale: Float) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addStaysFixedAndClickableWhilePathsScroll() {
        lateinit var state: TransformingLazyColumnState
        var clicks = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
                fontScale = this@WearListFixedHeaderTest.fontScale
            }
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp)) {
                        AppScaffold(timeText = {}) {
                            state = rememberTransformingLazyColumnState()
                            WearList(
                                listState = state,
                                onBack = {},
                                fixedHeader = {
                                    Column {
                                        WearPageHeader(stringResource(R.string.umount_path_manager))
                                        WearActionButton(
                                            Icons.Rounded.Add,
                                            stringResource(R.string.add_umount_path),
                                            { clicks++ },
                                            modifier = Modifier.testTag("add"),
                                        )
                                    }
                                },
                            ) { spec ->
                                items(12) { WearChip(spec, stringResource(R.string.mount_path)) }
                            }
                        }
                    }
                }
            }
        }
        val add = compose.onNodeWithTag("add")
        val before = add.getUnclippedBoundsInRoot()
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("Paths must still scroll by touch", state.canScrollBackward) }
        assertEquals(before, add.assertIsDisplayed().getUnclippedBoundsInRoot())

        val anchorBefore = compose.runOnIdle { state.anchorItemIndex to state.anchorItemScrollOffset }
        compose.runOnIdle {
            val activity = compose.activity
            val coordinates = MotionEvent.PointerCoords().apply {
                setAxisValue(MotionEvent.AXIS_SCROLL, -64f * activity.resources.displayMetrics.density / ViewConfiguration.get(activity).scaledVerticalScrollFactor)
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
        compose.runOnIdle {
            assertTrue("Paths must still scroll with the crown", anchorBefore != (state.anchorItemIndex to state.anchorItemScrollOffset))
        }
        assertEquals(before, add.assertIsDisplayed().getUnclippedBoundsInRoot())
        add.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, fontScale={1}")
        fun configurations() = listOf(arrayOf<Any>(true, 1f), arrayOf<Any>(false, 1f), arrayOf<Any>(true, 1.3f), arrayOf<Any>(false, 1.3f))
    }
}
