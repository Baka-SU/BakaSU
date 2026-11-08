package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.CompletableDeferred
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.wear.component.WearAppProfileTemplateChoicePage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearTemplateChoiceLoadingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun preloadedTemplatesStillWaitForEachVisitAndMenuRefreshKeepsTheChoices() {
        val route = mutableStateOf<WearRoute.AppProfileTemplateChoice?>(null)
        val menu = mutableStateOf(false)
        val refreshing = mutableStateOf(false)
        var pending = CompletableDeferred<Unit>()
        var requests = 0
        val template = ProfileTemplate(id = "example", name = "Loaded template")
        compose.setContent {
            val loading = rememberWearTemplateChoiceLoading(route.value) {
                requests++
                pending.await()
            }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    if (route.value != null && !menu.value) {
                        WearAppProfileTemplateChoicePage(AppProfile("test"), listOf(template), refreshing.value, loading, false, {}, {}, {})
                    }
                }
            }
        }
        repeat(2) { visit ->
            compose.runOnIdle {
                pending = CompletableDeferred()
                route.value = WearRoute.AppProfileTemplateChoice(10001, "example")
            }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).assertIsDisplayed()
            compose.onNodeWithText(template.name).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(visit + 1, requests)
                pending.complete(Unit)
            }
            compose.onNodeWithText(template.name).assertExists()
            compose.runOnIdle { menu.value = true }
            compose.runOnIdle { menu.value = false }
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(visit + 1, requests)
                refreshing.value = true
            }
            compose.onNodeWithText(template.name).assertExists()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).assertExists()
            compose.runOnIdle {
                refreshing.value = false
                route.value = null
            }
        }
    }
}
