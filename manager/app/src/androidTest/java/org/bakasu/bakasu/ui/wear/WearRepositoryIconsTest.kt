package org.bakasu.bakasu.ui.wear

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import kotlinx.coroutines.CompletableDeferred
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.CatalogModule
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoUiState
import org.junit.Rule
import org.junit.Test

class WearRepositoryIconsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listRemainsHiddenUntilEveryIconFinishesIncludingFailures() {
        val first = module("First")
        val last = module("Last")
        val firstIcon = CompletableDeferred<Bitmap?>()
        val lastIcon = CompletableDeferred<Bitmap?>()
        val state = ModuleRepoUiState(modules = listOf(first, last), repositoryUrl = first.repositoryUrl)
        compose.setContent {
            val icons by rememberWearRepositoryIcons(state) {
                mapOf(first.catalogId to firstIcon.await(), last.catalogId to lastIcon.await())
            }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearModuleRepoPage(state, {}, {}, {}, isPageLoading = icons == null, icons = icons.orEmpty(), onOpen = {})
                }
            }
        }
        val loading = compose.activity.getString(R.string.wear_loading)
        compose.onNodeWithContentDescription(loading).assertIsDisplayed()
        compose.onNodeWithText(first.moduleName).assertDoesNotExist()
        firstIcon.complete(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        compose.waitForIdle()
        compose.onNodeWithText(first.moduleName).assertDoesNotExist()
        lastIcon.complete(null)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(loading).assertDoesNotExist()
        compose.onNodeWithText(first.moduleName).assertExists()
        compose.onNodeWithText(last.moduleName).assertExists()
    }

    @Test
    fun switchingListsCannotUseTheOldBatchesReadiness() {
        val old = module("Old")
        val next = module("Next").copy(repositoryUrl = "https://other.example/")
        val state = mutableStateOf(ModuleRepoUiState(modules = listOf(old), repositoryUrl = old.repositoryUrl))
        val oldIcons = CompletableDeferred<Map<String, Bitmap?>>()
        val newIcons = CompletableDeferred<Map<String, Bitmap?>>()
        compose.setContent {
            val icons by rememberWearRepositoryIcons(state.value) { modules ->
                if (modules.first() == old) oldIcons.await() else newIcons.await()
            }
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearModuleRepoPage(state.value, {}, {}, {}, isPageLoading = icons == null, icons = icons.orEmpty(), onOpen = {})
                }
            }
        }
        compose.runOnIdle { state.value = state.value.copy(modules = listOf(next), repositoryUrl = next.repositoryUrl) }
        oldIcons.complete(mapOf(old.catalogId to null))
        compose.waitForIdle()
        compose.onNodeWithText(next.moduleName).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.wear_loading)).assertIsDisplayed()
        newIcons.complete(mapOf(next.catalogId to null))
        compose.waitForIdle()
        compose.onNodeWithText(next.moduleName).assertExists()
        compose.onNodeWithText(old.moduleName).assertDoesNotExist()
    }

    private fun module(id: String) = CatalogModule(
        moduleId = id, moduleName = id, authors = "", authorList = emptyList(), summary = "", metamodule = false,
        stargazerCount = 0, updatedAt = "", createdAt = "", latestRelease = "", latestReleaseTime = "", latestVersionCode = 0,
        latestAsset = null, sourceUrl = "", releases = emptyList(), repositoryUrl = "https://example.com/", iconUrl = "https://example.com/$id",
    )
}
