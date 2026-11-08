package org.bakasu.bakasu.ui.wear

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.KernelVersion
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.HomeDashboardState
import org.bakasu.bakasu.domain.model.KernelStatus
import org.bakasu.bakasu.domain.usecase.GetInstallEnvironmentUseCase
import org.bakasu.bakasu.ui.viewmodel.InstallUiAction
import org.bakasu.bakasu.ui.viewmodel.InstallUiState
import org.bakasu.bakasu.ui.viewmodel.InstallViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class WearInstallEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstInstallOpensTheRealPatchPageWithoutAnEntryFrameAndReturnsOnce() {
        val stack = NavBackStack<NavKey>(WearRoute.Main)
        val navigator = WearNavigator(stack, {}, {}, {})
        val home = HomeDashboardState(isInitialDataLoaded = true, systemStatus = KernelStatus(kernelVersion = KernelVersion(6, 1, 0)))
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    NavDisplay(
                        backStack = stack,
                        onBack = { navigator.back() },
                        sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                        entryProvider = withPageTransitions(
                            entryProvider {
                                entry<WearRoute.Main> {
                                    WearHomePage(home, null, {}, {}, onInstall = {
                                        assertTrue(home.canOpenPatchDirectly())
                                        navigator.open(WearRoute.LkmInstall, immediately = true)
                                    })
                                }
                                entry<WearRoute.LkmInstall> {
                                    Box(Modifier.fillMaxSize().testTag("patch-entry")) { WearLkmInstallPage({ navigator.back() }, { navigator.open(it) }) }
                                }
                            },
                            immediateEntry = navigator.immediateEntry,
                        ) { _, content -> content() },
                    )
                }
            }
        }
        val install = compose.activity.getString(R.string.home_not_installed)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(install))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(install).performSemanticsAction(SemanticsActions.OnClick) { it() }
        var visibleFrames = 0
        repeat(10) {
            compose.mainClock.advanceTimeByFrame()
            if (compose.onAllNodes(hasTestTag("patch-entry")).fetchSemanticsNodes().isNotEmpty()) {
                val bounds = compose.onNodeWithTag("patch-entry").getUnclippedBoundsInRoot()
                assertEquals("The first and subsequent visible patch frames must have no translation", 0f, bounds.left.value, 0.5f)
                assertEquals(0f, bounds.top.value, 0.5f)
                visibleFrames++
            }
        }
        assertTrue("The patch entry must render during the first navigation frames", visibleFrames > 0)
        assertEquals(listOf(WearRoute.Main, WearRoute.LkmInstall), stack.toList())
        compose.onNodeWithText(compose.activity.getString(R.string.GKI_install_methods)).assertDoesNotExist()
        if (Build.VERSION.SDK_INT >= 36) {
            compose.runOnIdle {
                compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
                compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 100f, 0.5f, BackEventCompat.EDGE_LEFT))
                compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            }
            compose.mainClock.advanceTimeBy(1000)
            assertEquals(WearRoute.LkmInstall, navigator.top)
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(listOf(WearRoute.Main), stack.toList())
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun pausingOrCancellingBackKeepsCachedEnvironmentAndDoesNotRefresh() {
        lateinit var model: InstallViewModel
        lateinit var visible: InstallUiState
        var active by mutableStateOf(true)
        compose.runOnIdle { model = GlobalContext.get().get() }
        compose.setContent {
            CompositionLocalProvider(LocalScreenIsActive provides active) {
                visible = rememberInstallState(model)
            }
        }
        compose.waitUntil(15000) { !model.state.value.loading }
        val initial = compose.runOnIdle { visible }
        compose.runOnIdle { active = false }
        compose.runOnIdle {
            assertFalse(visible.loading)
            assertEquals(initial.refreshVersion, model.state.value.refreshVersion)
            active = true
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            assertFalse(visible.loading)
            assertEquals(initial.refreshVersion, visible.refreshVersion)
            model.dispatch(InstallUiAction.Refresh)
        }
        compose.waitUntil(15000) { !model.state.value.loading && model.state.value.refreshVersion > initial.refreshVersion }
        compose.runOnIdle { assertEquals(initial.refreshVersion + 1, visible.refreshVersion) }
    }

    @Test
    fun unrootedEntryReplacesTheChooserAndBackExitsInstallation() {
        assumeFalse(runBlocking { GlobalContext.get().get<GetInstallEnvironmentUseCase>()(true).rootAvailable })
        var route by mutableStateOf<WearRoute>(WearRoute.KernelInstall)
        var replacements = 0
        compose.setContent {
            // System Back is owned by NavDisplay in WearManager, outside the install page.
            BackHandler { route = WearRoute.Main }
            MaterialTheme {
                AppScaffold {
                    when (route) {
                        WearRoute.KernelInstall -> WearInstallPage({}, { route = it }) {
                            replacements++
                            route = WearRoute.LkmInstall
                        }

                        WearRoute.LkmInstall -> WearLkmInstallPage({ route = WearRoute.Main }, { route = it })

                        else -> Unit
                    }
                }
            }
        }
        compose.waitUntil(15000) { route == WearRoute.LkmInstall }
        compose.waitUntil(15000) { compose.onAllNodes(hasText(compose.activity.getString(R.string.select_file))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.GKI_install_methods)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, replacements) }
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(WearRoute.Main, route) }
    }

    @Test
    fun directAnyKernelEntryCannotContinueWithoutRoot() {
        assumeFalse(runBlocking { GlobalContext.get().get<GetInstallEnvironmentUseCase>()(true).rootAvailable })
        compose.setContent {
            MaterialTheme { AppScaffold { WearAk3InstallPage({}, {}, "content://test/kernel.zip") } }
        }
        val rootRequired = compose.activity.getString(R.string.root_required)
        compose.waitUntil(15000) { compose.onAllNodes(hasText(rootRequired)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.install_next)).assertIsNotEnabled()
    }
}
