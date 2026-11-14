package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.KernelVersion
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.KernelStatus
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearShellRootSettingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shellRestrictionUsesTheSharedActionAndHonorsKernelFeatureStatus() {
        val state = mutableStateOf(SettingsUiState(shellSuAdbOnlyStatus = "supported"))
        val actions = mutableListOf<SettingsUiAction>()
        compose.setContent {
            MaterialTheme {
                AppScaffold(timeText = {}) {
                    WearSettingsPage(state.value, KernelStatus(kernelVersion = KernelVersion(6, 1, 0), isFullFeatured = true), actions::add, {}, {}, WearSettingsCategory.Security)
                }
            }
        }
        val title = compose.activity.getString(R.string.settings_shell_su_adb_only)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
        compose.onNodeWithText(title).assertIsEnabled().assertIsOff().performClick()
        compose.runOnIdle {
            assertEquals(listOf(SettingsUiAction.SetShellSuAdbOnly(true)), actions)
            state.value = state.value.copy(isShellSuAdbOnlyEnabled = true)
        }
        compose.onNodeWithText(title).assertIsOn()
        listOf(
            "unsupported" to R.string.feature_status_unsupported_summary,
            "managed" to R.string.feature_status_managed_summary,
            "forced" to R.string.feature_status_forced_summary,
        ).forEach { (status, summary) ->
            compose.runOnIdle { state.value = state.value.copy(shellSuAdbOnlyStatus = status) }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
            compose.onNodeWithText(title).assertIsNotEnabled().assertIsOn()
            compose.onNodeWithText(compose.activity.getString(summary), useUnmergedTree = true).assertExists()
        }
    }
}
