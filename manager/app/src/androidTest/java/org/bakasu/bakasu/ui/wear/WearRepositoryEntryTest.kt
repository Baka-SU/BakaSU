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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.domain.model.RepositorySource
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiState
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearRepositoryEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var resources: Resources
    private lateinit var navigator: WearNavigator
    private var requests = 0
    private var request: CompletableJob? = null
    private var publishRequestState = true
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
            finish()
            state = state.copy(failures = mapOf(RepositorySource.KERNEL_SU_URL to RepositoryFailure(RepositoryError.NETWORK, httpStatus = 502)))
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.wear_load_failed)).assertExists()
        compose.onNodeWithText("502", substring = true).assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("502", substring = true))
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText(resources.getString(R.string.network_retry)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, requests) }
        val loading = resources.getString(R.string.wear_loading)
        compose.onAllNodesWithContentDescription(loading).assertCountEquals(1)
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.module_repo)).assertDoesNotExist()
        compose.runOnIdle {
            finish()
            navigator.open(WearRoute.Operations(WearOperationsPage.Repository))
        }
        compose.onNodeWithText(resources.getString(R.string.repo_manage)).performClick()
        compose.runOnIdle { assertEquals(WearRoute.RepositorySources, navigator.top) }
    }

    @Test
    fun resumeAndChildPagesDoNotReloadButNewVisitsAndSourceChangesDo() {
        showRepository()
        openRepository()
        compose.runOnIdle { finish() }
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

    @Test
    fun cachedEntriesAreHiddenOnEveryVisitBeforeTheRepositoryPublishesLoading() {
        showRepository()
        openRepository()
        compose.runOnIdle {
            finish()
            state = state.copy(modules = listOf(cachedModule()))
        }
        compose.onNodeWithText("Cached module").assertExists()
        repeat(2) {
            compose.runOnIdle { navigator.back() }
            compose.runOnIdle {
                publishRequestState = false
                navigator.open(WearRoute.ModuleRepo)
            }
            compose.onNodeWithText("Cached module").assertDoesNotExist()
            compose.onNodeWithText(resources.getString(R.string.module_repo)).assertDoesNotExist()
            assertCenteredLoading()
            compose.runOnIdle { finish() }
            compose.onNodeWithText("Cached module").assertExists()
        }
        compose.runOnIdle { assertEquals(3, requests) }
    }

    @Test
    fun closingTheRefreshMenuAndReturningFromChildrenKeepsTheManualRequest() {
        showRepository()
        openRepository()
        compose.runOnIdle {
            finish()
            state = state.copy(modules = listOf(cachedModule()))
            navigator.open(WearRoute.Operations(WearOperationsPage.Repository))
        }
        val refresh = hasText(resources.getString(R.string.wear_refresh)) and hasClickAction()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("menu")), useUnmergedTree = true).performScrollToNode(hasText(resources.getString(R.string.wear_refresh)))
        compose.onNode(refresh).performClick()
        compose.waitUntil { navigator.top == WearRoute.ModuleRepo }
        compose.onNodeWithText("Cached module").assertExists()
        val title = compose.onNodeWithText(resources.getString(R.string.module_repo)).getUnclippedBoundsInRoot()
        val indicator = compose.onNodeWithContentDescription(resources.getString(R.string.wear_loading)).getUnclippedBoundsInRoot()
        assertTrue(indicator.top >= title.bottom)
        compose.runOnIdle { navigator.open(WearRoute.ModuleRepoSearchResults) }
        compose.runOnIdle { navigator.back() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle {
            assertEquals(2, requests)
            assertTrue(state.isPullRefreshing)
        }
        compose.onNodeWithText("Cached module").assertExists()
    }

    @Test
    fun cancellationAndSourceChangesDoNotFinishTheNextVisitAndMissingSourcesDoNotLoad() {
        showRepository()
        openRepository()
        val old = compose.runOnIdle { checkNotNull(request) }
        compose.runOnIdle { navigator.back() }
        compose.runOnIdle { navigator.open(WearRoute.ModuleRepo) }
        compose.runOnIdle { old.complete() }
        assertCenteredLoading()
        compose.runOnIdle {
            finish()
            state = state.copy(modules = listOf(cachedModule()), repositoryUrl = "https://example.com/other")
        }
        assertCenteredLoading()
        compose.runOnIdle {
            request?.cancel()
            state = state.copy(isRefreshing = false)
        }
        compose.onNodeWithContentDescription(resources.getString(R.string.wear_loading)).assertDoesNotExist()
        compose.runOnIdle { state = ModuleRepoUiState() }
        compose.onNodeWithText(resources.getString(R.string.repo_source_empty)).assertExists()
        compose.onNodeWithContentDescription(resources.getString(R.string.wear_loading)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, requests) }
    }

    private fun assertCenteredLoading() {
        val indicator = compose.onNodeWithContentDescription(resources.getString(R.string.wear_loading)).getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("repository").getUnclippedBoundsInRoot()
        assertEquals((viewport.top.value + viewport.bottom.value) / 2, (indicator.top.value + indicator.bottom.value) / 2, 1f)
    }

    private fun cachedModule() = CatalogModule("cached", "Cached module", "Author", emptyList(), "", false, 0, "", "", "", "", 0, null, "", emptyList(), RepositorySource.KERNEL_SU_URL)

    private fun finish() {
        state = state.copy(isRefreshing = false, isPullRefreshing = false)
        request?.complete()
    }

    private fun refresh(manual: Boolean = false): Job {
        requests++
        if (publishRequestState) state = state.copy(isRefreshing = true, isPullRefreshing = manual)
        return Job().also { request = it }
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
            val pageLoading = rememberWearRepositoryLoading(WearRoute.ModuleRepo in navigator.backStack, state.repositoryUrl) { refresh() }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    if (WearRoute.ModuleRepo in navigator.backStack) {
                        val menu = navigator.operationsFor(WearRoute.ModuleRepo)
                        WearOperationsHost(
                            menuKey = WearOperationsPage.Repository,
                            menuOpen = menu != null,
                            menuActive = navigator.top == menu,
                            menuContent = { closing ->
                                Box(Modifier.fillMaxSize().testTag("menu")) {
                                    WearOperationsPanel(WearOperationsPage.Repository, navigator::back, navigator::open, {}, {}, true, true, false, {}, onClosing = closing, onRefresh = { refresh(true) }, isRefreshing = pageLoading || state.isRefreshing)
                                }
                            },
                        ) {
                            Box(Modifier.fillMaxSize().testTag("repository")) {
                                WearModuleRepoPage(state, { refresh(true) }, navigator::back, { navigator.open(WearRoute.Operations(WearOperationsPage.Repository)) }, true, pageLoading, onRetry = { refresh() }) {}
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
