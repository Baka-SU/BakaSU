package org.bakasu.bakasu.ui.wear

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.DpRect
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.CompletableDeferred
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.RepositoryError
import org.bakasu.bakasu.domain.model.RepositoryFailure
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.wear.component.LocalWearSwipeEnabled
import org.bakasu.bakasu.ui.wear.component.WearOperationsBackHandler
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.bakasu.bakasu.ui.wear.component.WearRepositoryConnectionDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearRepositoryEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navigator: WearNavigator
    private lateinit var entry: WearModuleRepoEntry
    private val responses = ArrayDeque<CompletableDeferred<RepositoryFailure?>>()
    private var requests = 0
    private val menu = WearRoute.Operations(WearOperationsPage.Modules)

    @Test
    fun offlineDismissalsKeepTheMenuAndItsPosition() {
        repeat(3) { responses.add(CompletableDeferred(RepositoryFailure(RepositoryError.OFFLINE))) }
        showMenu()
        val before = repositoryButton().getUnclippedBoundsInRoot()
        repeat(3) { dismissal ->
            repositoryButton().performClick()
            compose.onNode(isDialog()).assertExists()
            compose.runOnIdle { assertEquals(menu, navigator.top) }
            when (dismissal) {
                0 -> compose.onNode(dialogAction(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_dismiss_button)).performClick()
                1 -> InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                else -> compose.onNode(isDialog()).performTouchInput { swipeRight() }
            }
            compose.onNode(isDialog()).assertDoesNotExist()
            compose.runOnIdle { assertEquals("Dismissal $dismissal must keep its menu", menu, navigator.top) }
            assertSamePosition(before, repositoryButton().getUnclippedBoundsInRoot())
        }
        compose.runOnIdle { assertEquals(3, requests) }
    }

    @Test
    fun retryLoadsOnceAndASecondOpeningStillChecksTheNetwork() {
        responses.add(CompletableDeferred(RepositoryFailure(RepositoryError.OFFLINE)))
        val retry = CompletableDeferred<RepositoryFailure?>()
        responses.add(retry)
        responses.add(CompletableDeferred(RepositoryFailure(RepositoryError.OFFLINE)))
        showMenu()
        repositoryButton().performClick()
        compose.onNode(dialogAction(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_confirm_button)).performClick()
        repositoryButton().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(menu, navigator.top)
            retry.complete(null)
        }
        compose.onNodeWithTag("repository").assertExists()
        compose.runOnIdle {
            assertEquals(1, navigator.backStack.count { it == WearRoute.ModuleRepo })
            assertEquals(2, requests)
            navigator.back()
        }
        repositoryButton().performClick()
        compose.onNode(isDialog()).assertExists()
        compose.runOnIdle {
            assertEquals(menu, navigator.top)
            assertEquals(3, requests)
        }
    }

    @Test
    fun repeatedImmediateFailureKeepsTheRetryDialogVisible() {
        repeat(2) { responses.add(CompletableDeferred(RepositoryFailure(RepositoryError.OFFLINE))) }
        showMenu()
        repositoryButton().performClick()
        compose.onNode(dialogAction(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_confirm_button)).performClick()
        compose.onNode(isDialog()).assertExists()
        compose.runOnIdle {
            assertEquals(menu, navigator.top)
            assertEquals(2, requests)
        }
    }

    @Test
    fun sortPageDoesNotCloseWhenPulledPastItsEndAndReturnsToTheSameMenuPosition() {
        showMenu()
        val sort = hasText(compose.activity.getString(R.string.wear_sort)) and hasClickAction()
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex)).performScrollToNode(sort)
        val before = compose.onNode(sort).getUnclippedBoundsInRoot()
        compose.onNode(sort).performClick()
        repeat(4) { compose.onNodeWithTag("sort").performTouchInput { swipeUp() } }
        compose.runOnIdle { assertEquals(WearRoute.ModuleSort, navigator.top) }
        compose.runOnIdle { navigator.back() }
        assertSamePosition(before, compose.onNode(sort).getUnclippedBoundsInRoot())
    }

    private fun showMenu() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            entry = remember {
                WearModuleRepoEntry(scope) {
                    requests++
                    responses.removeFirst().await()
                }
            }
            navigator = rememberWearNavigator(entry::cancel, {}, {})
            val openRepository = {
                entry.open { if (navigator.top == menu) navigator.open(WearRoute.ModuleRepo) }
            }
            CompositionLocalProvider(LocalWearSwipeEnabled provides true) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        NavDisplay(
                            backStack = navigator.backStack.filterNot { it is WearRoute.Operations },
                            onBack = navigator::back,
                            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                            entryProvider = entryProvider {
                                entry<WearRoute.Main> {
                                    WearOperationsHost(
                                        menuKey = WearOperationsPage.Modules,
                                        menuOpen = navigator.operationsFor(WearRoute.Main) != null,
                                        menuActive = navigator.top == menu,
                                        menuContent = { onClosing ->
                                            WearOperationsPanel(
                                                page = WearOperationsPage.Modules,
                                                onBack = navigator::back,
                                                onOpen = { destination ->
                                                    if (destination == WearRoute.ModuleRepo) openRepository() else navigator.open(destination)
                                                },
                                                onSearch = {},
                                                onInstall = {},
                                                rootAvailable = true,
                                                fullFeatured = true,
                                                susfsAvailable = false,
                                                onReboot = {},
                                                moduleRepoLoading = entry.isLoading,
                                                onClosing = {
                                                    entry.cancel()
                                                    onClosing()
                                                },
                                            )
                                        },
                                    ) { Text(stringResource(R.string.module)) }
                                }
                                entry<WearRoute.ModuleRepo> {
                                    Box(Modifier.fillMaxSize().testTag("repository")) { Text(stringResource(R.string.module_repo)) }
                                }
                                entry<WearRoute.ModuleSort> {
                                    Box(Modifier.fillMaxSize().testTag("sort")) {
                                        WearModulePanel(ModuleUiState(), navigator::back) {}
                                    }
                                }
                            },
                        )
                        WearOperationsBackHandler(navigator.top is WearRoute.Operations) { navigator.back() }
                        WearRepositoryConnectionDialog(entry.failure, openRepository, entry::cancel)
                    }
                }
            }
        }
        compose.runOnIdle { navigator.open(menu) }
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollToIndex)).performScrollToNode(repositoryMatcher())
    }

    private fun repositoryMatcher() = hasText(compose.activity.getString(R.string.module_repo)) and hasClickAction()
    private fun repositoryButton() = compose.onNode(repositoryMatcher())
    private fun dialogAction(id: Int) = hasContentDescription(compose.activity.getString(id)) and hasClickAction()

    private fun assertSamePosition(expected: DpRect, actual: DpRect) {
        assertEquals(expected.top.value, actual.top.value, 0.5f)
        assertEquals(expected.bottom.value, actual.bottom.value, 0.5f)
        assertTrue(actual.bottom > actual.top)
    }
}
