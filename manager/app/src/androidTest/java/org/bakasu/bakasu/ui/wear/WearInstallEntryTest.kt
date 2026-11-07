package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.runBlocking
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.usecase.GetInstallEnvironmentUseCase
import org.bakasu.bakasu.ui.viewmodel.InstallUiAction
import org.bakasu.bakasu.ui.viewmodel.InstallUiState
import org.bakasu.bakasu.ui.viewmodel.InstallViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

class WearInstallEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
