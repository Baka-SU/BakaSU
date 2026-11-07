package org.bakasu.bakasu.ui.wear

import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.viewmodel.TemplateUiState
import org.bakasu.bakasu.ui.wear.component.WearAppProfileConfig
import org.bakasu.bakasu.ui.wear.component.WearAppProfileTemplateChoicePage
import org.bakasu.bakasu.ui.wear.component.WearOperationsHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearTemplatePageTest(private val round: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var resources: Resources

    @Test
    fun emptyLoadingFailureAndSearchEmptyAreDistinct() {
        val state = mutableStateOf(TemplateUiState())
        val search = mutableStateOf(false)
        show {
            WearTemplatePage(state.value, {}, {}, { _, _, _ -> }, {}, search.value)
        }
        val empty = resources.getString(R.string.wear_template_empty)
        compose.onNodeWithText(empty).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isLoading = false) }
        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.app_profile_template_create)).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(loadFailed = true) }
        compose.onNodeWithText(empty).assertDoesNotExist()
        compose.onNodeWithText(resources.getString(R.string.wear_load_failed)).assertExists()
        compose.runOnIdle {
            state.value = state.value.copy(loadFailed = false)
            search.value = true
        }
        compose.onNodeWithText(resources.getString(R.string.wear_template_no_matches)).assertIsDisplayed()
    }

    @Test
    fun managementMenuHasFiveActionsAndNoRefresh() {
        val actions = mutableListOf<String>()
        show {
            WearOperationsHost(
                menuKey = WearOperationsPage.Templates,
                menuOpen = true,
                menuActive = true,
                menuContent = { onClosing ->
                    Box(Modifier.testTag("template-menu")) {
                        WearOperationsPanel(
                            page = WearOperationsPage.Templates,
                            onBack = {},
                            onOpen = { actions += if (it is WearRoute.TemplateEditor) "create" else "cloud" },
                            onSearch = { actions += "search" },
                            onInstall = {},
                            rootAvailable = false,
                            fullFeatured = false,
                            susfsAvailable = false,
                            onReboot = {},
                            onClosing = onClosing,
                            onImportTemplates = { actions += "import" },
                            onExportTemplates = { actions += "export" },
                        )
                    }
                },
            ) {}
        }
        val labels = listOf(R.string.wear_template_search, R.string.app_profile_template_create, R.string.wear_template_view_online, R.string.app_profile_import_from_clipboard, R.string.app_profile_export_to_clipboard)
        labels.forEachIndexed { index, label ->
            compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(hasTestTag("template-menu")), useUnmergedTree = true).performScrollToIndex(index)
            compose.onNodeWithText(resources.getString(label)).performClick()
        }
        compose.onNodeWithText(resources.getString(R.string.wear_refresh)).assertDoesNotExist()
        assertEquals(listOf("search", "create", "cloud", "import", "export"), actions)
    }

    @Test
    fun cloudAndChoiceMenusDispatchTheirOwnRefreshOnce() {
        val page = mutableStateOf(WearOperationsPage.OnlineTemplates)
        val open = mutableStateOf(true)
        val refreshed = mutableListOf<WearOperationsPage>()
        show {
            WearOperationsHost(
                menuKey = page.value,
                menuOpen = open.value,
                menuActive = open.value,
                menuContent = { onClosing ->
                    WearOperationsPanel(
                        page = page.value,
                        onBack = { open.value = false },
                        onOpen = {},
                        onSearch = {},
                        onInstall = {},
                        rootAvailable = false,
                        fullFeatured = false,
                        susfsAvailable = false,
                        onReboot = {},
                        onClosing = onClosing,
                        onRefresh = { refreshed += page.value },
                    )
                },
            ) {}
        }
        val refresh = resources.getString(R.string.wear_refresh)
        compose.onNodeWithText(refresh).performClick()
        compose.runOnIdle { assertEquals(false, open.value) }
        compose.runOnIdle {
            page.value = WearOperationsPage.TemplateChoice
            open.value = true
        }
        compose.onNodeWithText(refresh).performClick()
        compose.runOnIdle { assertEquals(false, open.value) }
        assertEquals(listOf(WearOperationsPage.OnlineTemplates, WearOperationsPage.TemplateChoice), refreshed)
    }

    @Test
    fun legacyTemplateCanRequestDeletionButOpeningItStaysReadOnly() {
        val template = ProfileTemplate(id = "legacy", name = "Legacy", local = false)
        var readOnly = false
        var deleted = false
        show {
            WearTemplatePage(TemplateUiState(templateList = listOf(template), isLoading = false), {}, {}, { _, value, _ -> readOnly = value }, { deleted = true })
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Legacy").performClick()
        assertTrue(readOnly)
        val delete = resources.getString(R.string.app_profile_template_delete)
        val node = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions) and
                SemanticsMatcher("Template delete action") { node ->
                    node.config[SemanticsActions.CustomActions].any { it.label == delete }
                },
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == delete }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
        compose.onNodeWithText(resources.getString(R.string.wear_template_delete_message, "Legacy")).assertExists()
        assertEquals(false, deleted)
    }

    @Test
    fun selectorKeepsSelectionAndContentDuringRefresh() {
        val refreshing = mutableStateOf(false)
        val template = ProfileTemplate(id = "saved", name = "Saved")
        var chosen: ProfileTemplate? = null
        show {
            WearAppProfileTemplateChoicePage(
                profile = AppProfile("app", allowSu = true, rootTemplate = template.id),
                templates = listOf(template),
                isRefreshing = refreshing.value,
                isLoading = false,
                loadFailed = false,
                onBack = {},
                onOpenPanel = {},
                onChoose = { chosen = it },
            )
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("Saved").assertIsSelected()
        compose.runOnIdle { refreshing.value = true }
        compose.onNodeWithText("Saved").assertExists().assertIsSelected()
        // Refresh opens space below the title; scroll the row clear of the edge button before tapping.
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText("Saved").assertIsDisplayed().performClick()
        assertEquals(template, chosen)
    }

    @Test
    fun customProfileOpensTheSharedTemplateSelectorWithoutSaving() {
        var opened = 0
        var saves = 0
        show {
            WearAppProfileConfig(
                profile = AppProfile("app", allowSu = true, rootUseDefault = false, rootTemplate = "saved"),
                sepolicyValid = true,
                onValidateSepolicy = {},
                onProfileChange = { saves++ },
                onManageTemplates = {},
                onViewTemplate = {},
                onBack = {},
                templates = TemplateUiState(templateList = listOf(ProfileTemplate(id = "saved", name = "Saved")), isLoading = false),
                onChooseTemplate = { opened++ },
            )
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText(resources.getString(R.string.profile_template)).performClick()
        assertEquals(1, opened)
        assertEquals(0, saves)
    }

    private fun show(content: @Composable () -> Unit) {
        resources = compose.activity.resources
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenLayout = (screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or
                    if (round) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
                fontScale = 1.15f
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                MaterialTheme {
                    AppScaffold(timeText = {}) { Box(Modifier.requiredSize(216.dp)) { content() } }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "round={0}")
        fun devices() = listOf(arrayOf(true), arrayOf(false))
    }
}
