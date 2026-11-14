package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.viewmodel.OnlineTemplateUiState
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorUiAction
import org.bakasu.bakasu.ui.viewmodel.TemplateEditorViewModel
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.bakasu.bakasu.ui.wear.component.WearToastState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class WearTemplateActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cloudImportUsesOneEdgeActionAndDisablesWhileImportingOrImported() {
        val template = ProfileTemplate(id = "cloud", name = "Cloud template")
        var state by mutableStateOf(OnlineTemplateUiState(templates = listOf(template), loading = false, hasLoaded = true))
        var imports = 0
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearOnlineTemplateDetail(state, template.id, {}, {
                        imports++
                        state = state.copy(adding = true)
                    }, {})
                }
            }
        }
        val import = compose.activity.getString(R.string.wear_template_add)
        compose.onAllNodesWithText(import).assertCountEquals(1)
        compose.onNodeWithText(import).assert(!hasAnyAncestor(hasScrollToIndexAction()))
        repeat(8) { compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() } }
        compose.onNodeWithText(import).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, imports) }
        compose.onNodeWithText(import).assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(adding = false, addedIds = setOf(template.id)) }
        compose.onNodeWithText(compose.activity.getString(R.string.wear_template_added)).assertIsNotEnabled()
        compose.onNodeWithText(import).assertDoesNotExist()
    }

    @Test
    fun invalidLocalCreationShowsOneToastAndKeepsTheDraft() {
        lateinit var viewModel: TemplateEditorViewModel
        lateinit var toast: WearToastState
        var backs = 0
        compose.setContent {
            MaterialTheme {
                WearToastHost {
                    toast = LocalWearToast.current
                    viewModel = koinViewModel(parameters = { parametersOf("", false, true) })
                    AppScaffold(timeText = {}) { WearTemplateEditor("", false, true) { backs++ } }
                }
            }
        }
        compose.runOnIdle { viewModel.dispatch(TemplateEditorUiAction.Update(ProfileTemplate(id = "", name = "Draft"))) }
        val save = compose.activity.getString(R.string.app_profile_template_save)
        compose.onAllNodesWithText(save).assertCountEquals(1)
        compose.onNodeWithText(save).assert(!hasAnyAncestor(hasScrollToIndexAction()))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(12)
        compose.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText(save).performClick()
        compose.waitUntil(5_000) { toast.sequence == 1 }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(compose.activity.getString(R.string.app_profile_template_save_failed), toast.message)
            assertEquals("Draft", viewModel.state.value.template.name)
            assertEquals(0, backs)
        }
    }
}
