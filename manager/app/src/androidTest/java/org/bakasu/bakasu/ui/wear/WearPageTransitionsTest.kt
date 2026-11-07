package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearPageTransitionsTest(private val destination: WearRoute, private val round: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app: WearRoute = when (destination) {
        is WearRoute.AffectedApps, is WearRoute.AppActions, is WearRoute.AppProfileMode,
        is WearRoute.AppProfileTemplateChoice, is WearRoute.AppProfileCustom,
        -> WearRoute.App(10000, "test.app")

        else -> WearRoute.Main
    }
    private val stack = NavBackStack<NavKey>(app)

    @Test
    fun entryAndButtonBackBothAnimate() {
        showPages()
        openPage()
        assertTrue(compose.onNodeWithTag("child").getUnclippedBoundsInRoot().left.value > 0f)
        settle()
        assertAtOrigin("child")

        compose.runOnIdle { stack.removeAt(stack.lastIndex) }
        compose.mainClock.advanceTimeBy(96)
        assertTrue(compose.onNodeWithTag("child").getUnclippedBoundsInRoot().left.value > 0f)
        settle()
        assertAtOrigin("app")
        compose.onNodeWithTag("child").assertDoesNotExist()
    }

    @Test
    fun predictiveBackDuringEntryCanCancelThenReturn() {
        showPages()
        openPage()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.4f))
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        settle()
        assertEquals(destination, stack.last())
        assertAtOrigin("child")

        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.6f))
        }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertEquals(app, stack.last())
        assertAtOrigin("app")
        compose.onNodeWithTag("child").assertDoesNotExist()
    }

    @Test
    fun predictiveBackBeforeEntryFinishesCanComplete() {
        showPages()
        openPage(elapsedMillis = 16)
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.6f))
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
        assertEquals(app, stack.last())
        assertAtOrigin("app")
        compose.onNodeWithTag("child").assertDoesNotExist()
    }

    private fun showPages() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    NavDisplay(
                        backStack = stack,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                        entryProvider = withPageTransitions(
                            entryProvider {
                                entry(app) { Box(Modifier.fillMaxSize().background(Color.Black).testTag("app")) }
                                entry(destination) { Box(Modifier.fillMaxSize().background(Color.Black).testTag("child")) }
                            },
                        ) { _, content -> content() },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun openPage(elapsedMillis: Long = 96) {
        compose.runOnIdle { stack.add(destination) }
        compose.mainClock.advanceTimeBy(elapsedMillis)
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    private fun assertAtOrigin(tag: String) {
        val bounds = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertEquals(0f, bounds.left.value, 0.5f)
        assertEquals(0f, bounds.top.value, 0.5f)
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 100f, progress, BackEventCompat.EDGE_LEFT)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, round={1}")
        fun destinations(): List<Array<Any>> = listOf(
            WearRoute.App(10000, "test.app"),
            WearRoute.AffectedApps(10000, "test.app"),
            WearRoute.AppActions(10000, "test.app"),
            WearRoute.AppProfileMode(10000, "test.app"),
            WearRoute.AppProfileTemplateChoice(10000, "test.app"),
            WearRoute.AppProfileCustom(10000, "test.app"),
            WearRoute.Module("test.module"),
            WearRoute.ModuleRepo,
            WearRoute.OnlineTemplateDetail("test.template"),
            WearRoute.Settings(WearSettingsCategory.General),
            WearRoute.HomeVersionInfo,
        ).flatMap { destination -> listOf(true, false).map { round -> arrayOf(destination, round) } }
    }
}
