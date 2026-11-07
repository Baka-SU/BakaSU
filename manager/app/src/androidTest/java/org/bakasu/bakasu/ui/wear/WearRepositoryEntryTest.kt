package org.bakasu.bakasu.ui.wear

import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.Lifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiState
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearRepositoryEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var resources: Resources
    private lateinit var navigator: WearNavigator
    private var requests = 0
    private var state by mutableStateOf(ModuleRepoUiState(repositoryUrl = RepositorySource.KERNEL_SU_URL, sources = listOf(RepositorySource(RepositorySource.KERNEL_SU_URL, "KernelSU"))))

    @Test
    fun slowRequestOpensThePageImmediatelyAndKeepsTheMenuAvailable() {
        showRepository()
        openRepository()
        compose.onNodeWithTag("repository").assertExists()
        compose.onNodeWithContentDescription(resources.getString(R.string.wear_loading)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(WearRoute.ModuleRepo, navigator.top)
            assertEquals(1, requests)
            navigator.open(WearRoute.Operations(WearOperationsPage.Repository))
        }
        compose.onNodeWithText(resources.getString(R.string.repo_manage)).assertExists()
    }

    @Test
    fun failureIsInlineWithRetryAndOfflineConfiguration() {
        showRepository()
        openRepository()
        compose.runOnIdle {
            state = state.copy(isRefreshing = false, failures = mapOf(RepositorySource.KERNEL_SU_URL to RepositoryFailure(RepositoryError.NETWORK, httpStatus = 502)))
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.wear_load_failed)).assertExists()
        compose.onNodeWithText("502", substring = true).assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("502", substring = true))
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText(resources.getString(R.string.network_retry)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, requests) }
        compose.runOnIdle {
            state = state.copy(isRefreshing = false)
            navigator.open(WearRoute.Operations(WearOperationsPage.Repository))
        }
        compose.onNodeWithText(resources.getString(R.string.repo_manage)).performClick()
        compose.runOnIdle { assertEquals(WearRoute.RepositorySources, navigator.top) }
    }

    @Test
    fun resumeAndChildPagesDoNotReloadButNewVisitsAndSourceChangesDo() {
        showRepository()
        openRepository()
        compose.runOnIdle { state = state.copy(isRefreshing = false) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            assertEquals(1, requests)
            navigator.open(WearRoute.ModuleRepoSearchResults)
        }
        compose.runOnIdle { navigator.back() }
        compose.runOnIdle {
            assertEquals(1, requests)
            state = state.copy(repositoryUrl = "https://example.com/repository")
        }
        compose.runOnIdle {
            assertEquals(2, requests)
            navigator.back()
        }
        compose.runOnIdle { navigator.open(WearRoute.ModuleRepo) }
        compose.runOnIdle { assertEquals(3, requests) }
    }

    private fun refresh() {
        requests++
        state = state.copy(isRefreshing = true)
    }

    private fun openRepository() {
        val button = hasText(resources.getString(R.string.module_repo)) and hasClickAction()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(button)
        compose.onNode(button).performClick()
    }

    private fun showRepository() {
        resources = compose.activity.resources
        compose.setContent {
            navigator = rememberWearNavigator({}, {}, {})
            WearModuleRepoEntry(WearRoute.ModuleRepo in navigator.backStack, state.repositoryUrl, ::refresh)
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    if (WearRoute.ModuleRepo in navigator.backStack) {
                        val menu = navigator.operationsFor(WearRoute.ModuleRepo)
                        WearOperationsHost(
                            menuKey = WearOperationsPage.Repository,
                            menuOpen = menu != null,
                            menuActive = navigator.top == menu,
                            menuContent = { closing ->
                                WearOperationsPanel(WearOperationsPage.Repository, navigator::back, navigator::open, {}, {}, true, true, false, {}, onClosing = closing, onRefresh = ::refresh, isRefreshing = state.isRefreshing)
                            },
                        ) {
                            Box(Modifier.fillMaxSize().testTag("repository")) {
                                WearModuleRepoPage(state, ::refresh, navigator::back, { navigator.open(WearRoute.Operations(WearOperationsPage.Repository)) }, true) {}
                            }
                        }
                    } else {
                        WearOperationsPanel(WearOperationsPage.Modules, navigator::back, navigator::open, {}, {}, true, true, false, {})
                    }
                }
            }
        }
    }
}
