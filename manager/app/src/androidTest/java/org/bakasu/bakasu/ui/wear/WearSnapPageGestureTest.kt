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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.domain.model.InstalledApp
import org.bakasu.bakasu.domain.model.InstalledAppGroup
import org.bakasu.bakasu.domain.model.InstalledModule
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.bakasu.bakasu.ui.wear.component.isAtWearListStart
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearSnapPageGestureTest(private val round: Boolean, private val modules: Boolean) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var list: TransformingLazyColumnState
    private var menu by mutableStateOf(false)

    @Test
    fun returningToTopDoesNotOpenTheMenuAndTheNextPullOpensImmediately() {
        show()
        compose.runOnIdle { list.dispatchRawDelta(40f * compose.activity.resources.displayMetrics.density) }
        compose.runOnIdle { assertTrue(list.canScrollBackward) }
        pull()
        compose.runOnIdle { assertFalse(menu) }
        pull()
        compose.runOnIdle { assertTrue("The second pull must open a real snapping page", menu) }
    }

    @Test
    fun crownScrollsToTheTopThenOpensTheMenuAndAStoppedPreviewResets() {
        show()
        compose.runOnIdle { list.dispatchRawDelta(80f * compose.activity.resources.displayMetrics.density) }
        repeat(10) {
            if (!compose.runOnIdle { list.isAtWearListStart(2) }) crown(-96f)
        }
        compose.runOnIdle { assertTrue("Crown must reach the list start: ${list.layoutInfo.visibleItems.firstOrNull()}", list.isAtWearListStart(2)) }
        compose.runOnIdle { assertFalse(menu) }
        compose.mainClock.autoAdvance = false
        crown(-48f)
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertFalse(menu) }
        repeat(8) {
            crown(-64f)
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("Crown must open at the boundary: ${list.layoutInfo.visibleItems.firstOrNull()}", menu) }
    }

    private fun pull() {
        compose.onNodeWithTag("page").performTouchInput {
            swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.8f), 600)
        }
    }

    private fun crown(distance: Float) {
        compose.runOnIdle {
            val now = SystemClock.uptimeMillis()
            val coords = MotionEvent.PointerCoords().apply {
                setAxisValue(MotionEvent.AXIS_SCROLL, -distance * compose.activity.resources.displayMetrics.density / ViewConfiguration.get(compose.activity).scaledVerticalScrollFactor)
            }
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(MotionEvent.PointerProperties().apply { id = 0 }), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0)
            try {
                compose.activity.dispatchGenericMotionEvent(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun show() {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                screenWidthDp = 192
                screenHeightDp = 192
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                MaterialTheme {
                    Box(Modifier.requiredSize(192.dp).testTag("page")) {
                        AppScaffold(timeText = {}) {
                            list = rememberTransformingLazyColumnState()
                            WearOperationsHost("page", menu, menu, { closing ->
                                WearList(onClosePanel = { menu = false }, onPanelClosing = closing) { }
                            }) {
                                if (modules) {
                                    WearModulesPage(
                                        ModuleUiState(
                                            moduleList = (1..20).map { i ->
                                                InstalledModule("$i", "Module $i", "Author", "v1", 1, "Description", true, false, false, "", false, false, false, null, null, "$i", null)
                                            },
                                        ),
                                        null,
                                        {},
                                        {},
                                        { menu = true },
                                        listState = list,
                                        hasOperationsPanel = true,
                                    )
                                } else {
                                    WearSuperUserPage(
                                        SuperUserUiState(
                                            appGroupList = (1..20).map { i ->
                                                val app = InstalledApp("test.$i", "App $i", 10000 + i)
                                                InstalledAppGroup(app.uid, app.packageName, listOf(app))
                                            },
                                        ),
                                        null,
                                        {},
                                        { _, _ -> },
                                        {},
                                        { menu = true },
                                        listState = list,
                                        hasOperationsPanel = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, modules={1}")
        fun parameters() = listOf(arrayOf(true, true), arrayOf(false, true), arrayOf(true, false), arrayOf(false, false))
    }
}
