package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.wear.component.LocalWearToast
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.bakasu.bakasu.ui.wear.component.WearToastState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearColorInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun incompleteAndInvalidInputsCannotSubmitAndSavingDisablesConfirmation() {
        val state = mutableStateOf(SettingsUiState())
        val actions = mutableListOf<SettingsUiAction>()
        lateinit var toast: WearToastState
        compose.setContent {
            MaterialTheme {
                WearToastHost {
                    toast = LocalWearToast.current
                    AppScaffold(timeText = {}) {
                        WearCustomColorPage("#12", state.value) {
                            actions += it
                            state.value = state.value.copy(applyingThemeColor = true)
                        }
                    }
                }
            }
        }
        val confirm = compose.activity.getString(R.string.confirm)
        val title = compose.activity.getString(R.string.wear_custom_color)
        compose.onNodeWithContentDescription(confirm).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, toast.sequence) }
        compose.onNodeWithContentDescription(title).performTextReplacement("#ZZZZZZ")
        compose.onNodeWithContentDescription(confirm).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, toast.sequence) }
        compose.onNodeWithContentDescription(title).performTextReplacement("#336699")
        compose.onNodeWithContentDescription(confirm).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription(confirm).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(SettingsUiAction.ApplyWearThemeColor("#336699")), actions) }
    }
}
