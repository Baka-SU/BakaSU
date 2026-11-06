package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.ModuleUiState
import org.bakasu.bakasu.ui.viewmodel.SortType
import org.bakasu.bakasu.ui.viewmodel.SuperUserUiState
import org.bakasu.bakasu.ui.wear.component.LocalWearPanelPull
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearPanelsTest(private val round: Boolean, private val page: WearOperationsPage) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var menuOpen by mutableStateOf(false)
    private var filterOpen by mutableStateOf(false)
    private lateinit var pull: MutableFloatState

    @Test
    fun newPreviewStartsAtBottomAndCancelledPreviewIsDiscarded() {
        showPanels()
        previewMenu()
        assertMenuAtBottom()
        menuList().performScrollToIndex(0)

        compose.runOnIdle { pull.floatValue = 0f }
        compose.onNodeWithTag("menu", useUnmergedTree = true).assertDoesNotExist()
        previewMenu()
        assertMenuAtBottom()
    }

    @Test
    fun filterReturnKeepsPositionButNewOpeningStartsAtBottom() {
        showPanels()
        previewMenu()
        compose.runOnIdle { menuOpen = true }
        menuList().performScrollToIndex(0)
        val previousPosition = menuScrollRange().value()
        assertTrue(previousPosition < menuScrollRange().maxValue())

        // Like a navigation entry, the covered host leaves composition and restores its saved state.
        compose.runOnIdle { filterOpen = true }
        compose.onNodeWithTag("menu", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { filterOpen = false }
        assertEquals(previousPosition, menuScrollRange().value(), 0.01f)

        compose.runOnIdle { menuOpen = false }
        compose.onNodeWithTag("menu", useUnmergedTree = true).assertDoesNotExist()
        previewMenu()
        assertMenuAtBottom()
    }

    @Test
    fun upwardPullAtBottomClosesOperationsPanel() {
        showPanels()
        previewMenu()
        compose.runOnIdle { menuOpen = true }
        assertMenuAtBottom()
        compose.onNodeWithTag("menu", useUnmergedTree = true).performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.25f), 300)
        }
        compose.runOnIdle { assertFalse("An upward pull at the bottom must close the operations panel", menuOpen) }
        compose.onNodeWithTag("menu", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun filterUsesBackButtonAndDoesNotCloseOnBottomPull() {
        showPanels()
        compose.runOnIdle {
            menuOpen = true
            filterOpen = true
        }
        val filterList = compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("filter")), useUnmergedTree = true)
        filterList.performScrollToIndex(if (page == WearOperationsPage.SuperUser) 2 + SortType.entries.size else 2)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).assertExists()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_close_panel)).assertDoesNotExist()
        compose.onNodeWithTag("filter").performTouchInput {
            swipe(Offset(centerX, height * 0.85f), Offset(centerX, height * 0.15f), 300)
        }
        compose.runOnIdle { assertTrue("A bottom pull must not leave the filter page", filterOpen) }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.runOnIdle { assertFalse(filterOpen) }
    }

    private fun showPanels() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) {
                        val entries = rememberSaveableStateHolder()
                        Box(Modifier.size(192.dp)) {
                            if (filterOpen) {
                                Box(Modifier.fillMaxSize().testTag("filter")) {
                                    if (page == WearOperationsPage.SuperUser) {
                                        WearSuperUserPanel(SuperUserUiState(), { filterOpen = false }, {})
                                    } else {
                                        WearModulePanel(ModuleUiState(), { filterOpen = false }, {})
                                    }
                                }
                            } else {
                                entries.SaveableStateProvider("owner") {
                                    WearOperationsHost(
                                        menuKey = page,
                                        menuOpen = menuOpen,
                                        menuActive = menuOpen,
                                        menuContent = { onClosing ->
                                            Box(Modifier.fillMaxSize().testTag("menu")) {
                                                WearOperationsPanel(
                                                    page = page,
                                                    onBack = { menuOpen = false },
                                                    onOpen = { filterOpen = true },
                                                    onSearch = {},
                                                    onInstall = {},
                                                    rootAvailable = true,
                                                    fullFeatured = true,
                                                    susfsAvailable = true,
                                                    onReboot = {},
                                                    onClosing = onClosing,
                                                )
                                            }
                                        },
                                    ) {
                                        pull = LocalWearPanelPull.current
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun previewMenu() {
        compose.runOnIdle {
            assertFalse(menuOpen)
            pull.floatValue = 0.35f
        }
        compose.waitForIdle()
    }

    private fun menuList() = compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("menu")), useUnmergedTree = true)

    private fun menuScrollRange() = menuList().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun assertMenuAtBottom() {
        val range = menuScrollRange()
        assertTrue("The menu must be scrollable in this viewport", range.maxValue() > 0f)
        assertEquals("The preview must already be at the bottom", range.maxValue(), range.value(), 0.01f)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}, page={1}")
        fun configurations() = listOf(
            arrayOf<Any>(true, WearOperationsPage.SuperUser),
            arrayOf<Any>(false, WearOperationsPage.SuperUser),
            arrayOf<Any>(true, WearOperationsPage.Modules),
            arrayOf<Any>(false, WearOperationsPage.Modules),
        )
    }
}
