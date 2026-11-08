package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.DynamicManagerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearDynamicManagerAppsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noMatchesEmptyCandidatesAndReadFailuresHaveAnEdgeRetryWithoutChangingSearch() {
        val state = mutableStateOf(DynamicManagerUiState(isLoading = false, search = "missing.package"))
        var retries = 0
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearDynamicManagerAppsPage(state.value, {}, {}, { retries++ }, { error("Retry must not select an app") })
                }
            }
        }
        for ((query, failed, message) in listOf(
            Triple("missing.package", false, R.string.search_no_any_match),
            Triple("", false, R.string.no_apps_found),
            Triple("missing.package", true, R.string.wear_load_failed),
        )) {
            compose.runOnIdle { state.value = state.value.copy(search = query, loadFailed = failed) }
            val text = compose.activity.getString(message)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.network_retry)).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(query, state.value.search) }
        }
        compose.runOnIdle { assertEquals(3, retries) }
    }
}
