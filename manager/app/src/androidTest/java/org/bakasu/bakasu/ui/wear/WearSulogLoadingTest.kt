package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.SulogEntry
import org.bakasu.bakasu.domain.model.SulogEventType
import org.bakasu.bakasu.ui.viewmodel.SulogLoading
import org.bakasu.bakasu.ui.viewmodel.SulogUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WearSulogLoadingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyPageLoadHidesCachedLogsWhileManualRefreshKeepsThem() {
        val loading = mutableStateOf(SulogLoading.PAGE)
        val entry = SulogEntry("entry", SulogEventType.RootExecve, "su", null, mapOf("comm" to "Cached log"))
        val state = SulogUiState(isLoading = false, isRefreshing = true, entries = listOf(entry), visibleEntries = listOf(entry))
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    Box(Modifier.fillMaxSize().testTag("logs")) {
                        WearLogsPage(state, {}, {}, {}, onEntry = {}, loading = loading.value)
                    }
                }
            }
        }
        val title = compose.activity.getString(R.string.sulog)
        val indicator = compose.activity.getString(R.string.wear_loading)
        repeat(3) {
            compose.runOnIdle { loading.value = SulogLoading.PAGE }
            compose.onNodeWithText(title).assertDoesNotExist()
            compose.onNodeWithText("Cached log").assertDoesNotExist()
            val bounds = compose.onNodeWithContentDescription(indicator).getUnclippedBoundsInRoot()
            val viewport = compose.onNodeWithTag("logs").getUnclippedBoundsInRoot()
            assertEquals((viewport.top.value + viewport.bottom.value) / 2, (bounds.top.value + bounds.bottom.value) / 2, 1f)
            compose.runOnIdle { loading.value = SulogLoading.NONE }
            compose.onNodeWithText("Cached log").assertExists()
            compose.onNodeWithContentDescription(indicator).assertDoesNotExist()
        }
        compose.runOnIdle { loading.value = SulogLoading.REFRESH }
        compose.onNodeWithText("Cached log").assertExists()
        val titleBounds = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val refreshBounds = compose.onNodeWithContentDescription(indicator).getUnclippedBoundsInRoot()
        assertTrue(refreshBounds.top >= titleBounds.bottom)
        compose.runOnIdle { loading.value = SulogLoading.OPERATION }
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithText("Cached log").assertDoesNotExist()
    }

    @Test
    fun oldFailureStaysHiddenUntilTheCurrentRequestFinishes() {
        val loading = mutableStateOf(SulogLoading.PAGE)
        val state = mutableStateOf(SulogUiState(isLoading = false, errorMessage = "Previous failure"))
        compose.setContent {
            MaterialTheme {
                AppScaffold {
                    WearLogsPage(state.value, {}, {}, {}, onEntry = {}, loading = loading.value)
                }
            }
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Previous failure").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(errorMessage = null)
            loading.value = SulogLoading.NONE
        }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.wear_no_logs)).assertExists()
    }
}
