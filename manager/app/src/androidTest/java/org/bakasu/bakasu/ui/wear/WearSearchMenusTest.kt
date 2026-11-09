package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WearSearchMenusTest(private val page: WearOperationsPage) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun watchPhoneAndAutomaticLinkChoicesKeepTheirDistinctIcons() {
        assertEquals(Icons.Rounded.Watch, selectionIcon(WearSelection.LinkMode, "webview"))
        assertEquals(Icons.Rounded.Smartphone, selectionIcon(WearSelection.LinkMode, "phone"))
        assertEquals(Icons.Rounded.AutoAwesome, selectionIcon(WearSelection.LinkMode, "auto"))
    }

    @Test
    fun menuRoutesSearchAndRepositoryAdditionToTheirOwnPages() {
        var searches = 0
        var opened: WearRoute? = null
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearOperationsPanel(page, {}, { opened = it }, { searches++ }, {}, false, false, false, {})
                }
            }
        }
        fun click(resource: Int) {
            val label = compose.activity.getString(resource)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performClick()
        }
        click(
            when (page) {
                WearOperationsPage.OnlineTemplates -> R.string.wear_template_search
                WearOperationsPage.DynamicManagerApps -> R.string.search_apps
                WearOperationsPage.Repository -> R.string.search_modules
                else -> R.string.wear_search_repositories
            },
        )
        assertEquals(1, searches)
        if (page == WearOperationsPage.Repository || page == WearOperationsPage.RepositorySources) {
            click(R.string.repo_add_repository)
            assertEquals(WearRoute.AddRepository, opened)
        }
        if (page == WearOperationsPage.Repository) {
            click(R.string.wear_search_repositories)
            assertEquals(WearRoute.RepositorySourceSearch, opened)
            assertEquals(1, searches)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "page={0}")
        fun parameters() = listOf(WearOperationsPage.OnlineTemplates, WearOperationsPage.DynamicManagerApps, WearOperationsPage.Repository, WearOperationsPage.RepositorySources).map { arrayOf(it) }
    }
}
