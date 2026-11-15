package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.DynamicManagerApp
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
                    WearDynamicManagerAppsPage(state.value, {}, {}, { retries++ }, { error("Retry must not clear a manager") }, { error("Retry must not select an app") })
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
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.network_retry)).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(query, state.value.search) }
        }
        compose.runOnIdle { assertEquals(3, retries) }
    }

    @Test
    fun grantAndClearRequireTheirOwnConfirmation() {
        val candidate = DynamicManagerApp("Candidate", "example.manager", 10001, "/data/app/example/base.apk")
        val state = mutableStateOf(DynamicManagerUiState(isLoading = false, apps = listOf(candidate)))
        var grants = 0
        var clears = 0
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearDynamicManagerAppsPage(state.value, {}, {}, {}, { clears++ }) {
                        assertEquals(candidate, it)
                        grants++
                    }
                }
            }
        }
        val confirm = compose.activity.getString(androidx.wear.compose.material3.R.string.wear_m3c_alert_dialog_content_description_confirm_button)
        compose.onNodeWithText(candidate.label).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.dynamic_manager_grant_confirm_title)).assertExists()
        compose.runOnIdle { assertEquals(0, grants) }
        confirmDialog(confirm)
        compose.runOnIdle {
            assertEquals(1, grants)
            state.value = state.value.copy(apps = listOf(candidate.copy(isSelected = true, managerSignatureIndex = 255)))
        }
        compose.onNodeWithText(candidate.label).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.dynamic_manager_clear_confirm_title)).assertExists()
        compose.runOnIdle { assertEquals(0, clears) }
        confirmDialog(confirm)
        compose.runOnIdle {
            assertEquals(1, grants)
            assertEquals(1, clears)
        }
    }
    private fun confirmDialog(description: String) {
        val button = compose.onNodeWithContentDescription(description)
        repeat(5) {
            if (button.isDisplayed()) {
                button.performClick()
                return
            }
            compose.onNode(isDialog()).performTouchInput { swipeUp() }
        }
        button.assertIsDisplayed().performClick()
    }
}
