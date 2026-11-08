package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.viewmodel.filterProfileTemplates
import org.bakasu.bakasu.ui.wear.component.WearAppProfileModePage
import org.bakasu.bakasu.ui.wear.component.WearAppProfileTemplateChoicePage
import org.bakasu.bakasu.ui.wear.component.WearSearchPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearTemplateChoiceSearchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun menuSearchSelectsTheMatchingTemplateAndReturnsToTheOwningProfile() {
        val mode = WearRoute.AppProfileMode(10001, "target.app")
        val choice = WearRoute.AppProfileTemplateChoice(mode.uid, mode.packageName)
        val menu = WearRoute.Operations(WearOperationsPage.TemplateChoice)
        val navigator = WearNavigator(NavBackStack<NavKey>(WearRoute.Main, mode, choice, menu), {}, {}, {})
        val templates = listOf(ProfileTemplate("reader", name = "Reader", author = "Alice"), ProfileTemplate("other", name = "Other"))
        val profile = AppProfile(mode.packageName, currentUid = mode.uid, allowSu = true, rootTemplate = "other")
        var selected: ProfileTemplate? = null
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    when (val route = navigator.top) {
                        menu -> WearOperationsPanel(
                            WearOperationsPage.TemplateChoice, navigator::back, navigator::open,
                            onSearch = { navigator.open(WearRoute.AppProfileTemplateSearch(mode.uid, mode.packageName)) },
                            onInstall = {}, rootAvailable = true, fullFeatured = true, susfsAvailable = false, onReboot = {},
                        )

                        is WearRoute.AppProfileTemplateSearch -> WearSearchPage(
                            stringResource(R.string.wear_template_search),
                            "profile-templates",
                            route.query,
                            navigator::back,
                        ) { navigator.submitTemplateChoiceSearch(route, it) }

                        is WearRoute.AppProfileTemplateResults -> WearAppProfileTemplateChoicePage(
                            profile,
                            filterProfileTemplates(templates, route.query),
                            false,
                            false,
                            false,
                            navigator::back,
                            {},
                            onChoose = {
                                selected = it
                                navigator.finishTemplateChoice(route)
                            },
                        )

                        else -> WearAppProfileModePage(profile, {}, {}, {}, {})
                    }
                }
            }
        }
        val searchLabel = compose.activity.getString(R.string.wear_template_search)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(searchLabel))
        compose.onNodeWithText(searchLabel).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("ALICE")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Other").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Reader"))
        compose.onNodeWithText("Reader").performClick()
        compose.runOnIdle {
            assertEquals(templates[0], selected)
            assertEquals(mode, navigator.top)
            assertEquals("other", profile.rootTemplate)
        }
    }
}
