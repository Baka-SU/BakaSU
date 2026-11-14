package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.wear.component.WearChip
import org.bakasu.bakasu.ui.wear.component.WearHorizontalPager
import org.bakasu.bakasu.ui.wear.component.WearList
import org.bakasu.bakasu.ui.wear.component.WearOperationsBackHandler
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearMenuNavigationTest(private val round: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val menu = WearRoute.Operations(WearOperationsPage.Home)
    private val stack = NavBackStack<NavKey>(WearRoute.Main, menu)
    private val navigator = WearNavigator(stack, {}, {}, {})
    private lateinit var pager: PagerState

    @Test
    fun mainPagerWorksInBothDirectionsAfterClosingMenu() {
        showPages()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("menu")), useUnmergedTree = true)
            .performScrollToIndex(3)
        compose.onNodeWithTag("menu").performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.20f), 300)
        }
        compose.runOnIdle { assertEquals(WearRoute.Main, navigator.top) }
        compose.onNodeWithTag("main").performTouchInput {
            swipe(Offset(width * 0.85f, centerY), Offset(width * 0.15f, centerY), 300)
        }
        compose.runOnIdle { assertEquals(1, pager.currentPage) }
        compose.onNodeWithTag("main").performTouchInput {
            swipe(Offset(width * 0.15f, centerY), Offset(width * 0.85f, centerY), 300)
        }
        compose.runOnIdle { assertEquals(0, pager.currentPage) }
    }

    @Test
    fun childBackWorksWhileMenuRemainsInHistory() {
        showPages()
        compose.runOnIdle { navigator.open(WearRoute.HomeVersionInfo) }
        backGesture("child")
        compose.runOnIdle { assertEquals(menu, navigator.top) }
        backGesture("menu")
        compose.runOnIdle { assertEquals("The restored menu must still block horizontal Back", menu, navigator.top) }
        compose.runOnIdle {
            navigator.back()
            navigator.open(WearRoute.HomeVersionInfo)
        }
        backGesture("child")
        compose.runOnIdle { assertEquals(WearRoute.Main, navigator.top) }
    }

    private fun backGesture(tag: String) {
        compose.waitForIdle()
        if (Build.VERSION.SDK_INT >= 36) {
            compose.runOnIdle {
                val dispatcher = compose.activity.onBackPressedDispatcher
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, 0.8f, BackEventCompat.EDGE_LEFT))
                dispatcher.onBackPressed()
            }
        } else {
            compose.onNodeWithTag(tag).performTouchInput {
                swipe(Offset(1f, centerY), Offset(width - 1f, centerY), 300)
            }
        }
        compose.waitForIdle()
    }

    private fun showPages() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        NavDisplay(
                            backStack = stack.filterNot { it is WearRoute.Operations },
                            onBack = navigator::back,
                            sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                            entryProvider = entryProvider {
                                entry<WearRoute.Main> {
                                    WearOperationsHost(
                                        menuKey = WearOperationsPage.Home,
                                        menuOpen = navigator.operationsFor(WearRoute.Main) != null,
                                        menuActive = navigator.top == menu,
                                        menuContent = { onClosing ->
                                            Box(Modifier.fillMaxSize().testTag("menu")) {
                                                WearList(onClosePanel = navigator::back, onPanelClosing = onClosing) { spec ->
                                                    items(4) { WearChip(spec, stringResource(R.string.settings)) }
                                                }
                                            }
                                        },
                                    ) {
                                        pager = rememberPagerState { 2 }
                                        WearHorizontalPager(pager, Modifier.fillMaxSize().testTag("main")) {
                                            Box(Modifier.fillMaxSize())
                                        }
                                    }
                                }
                                entry<WearRoute.HomeVersionInfo> { Box(Modifier.fillMaxSize().testTag("child")) }
                            },
                        )
                        WearOperationsBackHandler(navigator.top == menu, navigator::back)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun configurations() = listOf(arrayOf(true), arrayOf(false))
    }
}
