package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.viewmodel.TemplateUiState
import org.bakasu.bakasu.ui.wear.component.WearAppProfileTemplateChoicePage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearTemplateChoiceLoadingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun managementVisitsIgnoreSharedRefreshAndRetainLoadingAcrossChildren() {
        val open = mutableStateOf(false)
        val child = mutableStateOf(false)
        val failed = mutableStateOf(false)
        var pending = CompletableDeferred<Unit>()
        var requests = 0
        compose.setContent {
            val loading = rememberWearTemplateLoading(WearRoute.Templates.takeIf { open.value }) {
                requests++
                pending.await()
            }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    if (open.value && !child.value) {
                        WearTemplatePage(
                            TemplateUiState(
                                templateList = listOf(ProfileTemplate(id = "cached", name = "Cached template")),
                                isLoading = loading.loading,
                                isRefreshing = false,
                                loadFailed = failed.value,
                            ),
                            {},
                            {},
                            { _, _, _ -> },
                            {},
                        )
                    }
                }
            }
        }
        val indicator = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading))
        repeat(3) { visit ->
            compose.runOnIdle {
                pending = CompletableDeferred()
                open.value = true
            }
            indicator.assertIsDisplayed()
            compose.onNodeWithText("Cached template").assertDoesNotExist()
            if (visit == 0) {
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                File(compose.activity.getExternalFilesDir(null), "templates-entry-loading.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            compose.runOnIdle {
                assertEquals(visit + 1, requests)
                pending.complete(Unit)
            }
            indicator.assertDoesNotExist()
            compose.runOnIdle { child.value = true }
            compose.runOnIdle { child.value = false }
            indicator.assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(visit + 1, requests)
                open.value = false
                failed.value = visit == 0
            }
        }
    }

    @Test
    fun coldAndCachedVisitsUsePageLoadingAndOnlyTheirOwnRefreshShowsTheIndicator() {
        val parent = WearRoute.AppProfileMode(10001, "example")
        val choice = WearRoute.AppProfileTemplateChoice(10001, "example")
        val stack = NavBackStack<NavKey>(parent)
        val templates = mutableStateOf(emptyList<ProfileTemplate>())
        var pending = CompletableDeferred<Unit>()
        var requests = 0
        lateinit var loading: WearTemplateChoiceLoading
        val template = ProfileTemplate(id = "example", name = "Loaded template")
        compose.setContent {
            loading = rememberWearTemplateLoading(stack.filterIsInstance<WearRoute.AppProfileTemplateChoice>().lastOrNull()) {
                requests++
                pending.await()
                templates.value = listOf(template)
            }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    NavDisplay(
                        backStack = stack,
                        onBack = { stack.removeAt(stack.lastIndex) },
                        entryProvider = withPageTransitions(
                            entryProvider {
                                entry(parent) { Box(Modifier.fillMaxSize()) }
                                entry(choice) {
                                    WearAppProfileTemplateChoicePage(AppProfile("test"), templates.value, loading.refreshing, loading.loading, false, {}, {}, {})
                                }
                                entry<WearRoute.AppProfileTemplateSearch> { Box(Modifier.fillMaxSize()) }
                            },
                        ) { _, content -> content() },
                    )
                }
            }
        }
        val indicator = compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading))
        repeat(2) { visit ->
            compose.runOnIdle {
                pending = CompletableDeferred()
                stack.add(choice)
            }
            indicator.assertIsDisplayed()
            compose.onNodeWithText(template.name).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(visit * 2 + 1, requests)
                pending.complete(Unit)
            }
            compose.mainClock.autoAdvance = false
            repeat(8) {
                compose.mainClock.advanceTimeByFrame()
                compose.onNodeWithText(template.name).assertExists()
                indicator.assertDoesNotExist()
            }
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { stack.add(WearRoute.AppProfileTemplateSearch(10001, "example", "")) }
            compose.runOnIdle { stack.removeAt(stack.lastIndex) }
            indicator.assertDoesNotExist()
            compose.runOnIdle {
                pending = CompletableDeferred()
                loading.refresh()
            }
            compose.onNodeWithText(template.name).assertExists()
            indicator.assertIsDisplayed()
            compose.runOnIdle { pending.complete(Unit) }
            indicator.assertDoesNotExist()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { stack.removeAt(stack.lastIndex) }
            repeat(20) {
                compose.mainClock.advanceTimeBy(16)
                indicator.assertDoesNotExist()
                compose.runOnIdle { assertEquals(visit * 2 + 2, requests) }
            }
            compose.mainClock.autoAdvance = true
        }
    }
}
