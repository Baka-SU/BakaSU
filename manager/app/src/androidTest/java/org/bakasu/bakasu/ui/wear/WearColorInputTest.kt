package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.SettingsUiAction
import org.bakasu.bakasu.ui.viewmodel.SettingsUiState
import org.bakasu.bakasu.ui.wear.component.WearToastHost
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearColorInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(SettingsUiState())
    private val actions = mutableListOf<SettingsUiAction>()

    private fun show() {
        compose.setContent {
            MaterialTheme {
                WearToastHost {
                    AppScaffold(timeText = {}) {
                        WearCustomColorPage("#FF0000", state.value, onAction = {
                            actions += it
                            state.value = state.value.copy(applyingThemeColor = true)
                        })
                    }
                }
            }
        }
    }

    @Test
    fun invalidHexCannotSubmitAndValidHexUpdatesThePickerBeforeApplying() {
        show()
        compose.onNodeWithText("#FF0000").performClick()
        val confirm = compose.activity.getString(R.string.confirm)
        val title = compose.activity.getString(R.string.wear_color_hex)
        compose.onNodeWithContentDescription(title).performTextReplacement("#12")
        compose.onNodeWithContentDescription(confirm).assertIsNotEnabled()
        compose.onNodeWithContentDescription(title).performTextReplacement("#ZZZZZZ")
        compose.onNodeWithContentDescription(confirm).assertIsNotEnabled()
        compose.onNodeWithContentDescription(title).performTextReplacement("#336699")
        compose.onNodeWithContentDescription(confirm).assertIsEnabled().performClick()
        compose.onNodeWithText("#336699").assertExists()
        compose.runOnIdle { assertEquals(0, actions.size) }
        applyColor("#336699")
    }

    @Test
    fun hsvControlsChangeThePreviewAndApplyTheSelectedColor() {
        show()
        fun adjust(resource: Int, value: Float) {
            val label = compose.activity.getString(resource)
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(label))
            compose.onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }
        adjust(R.string.wear_color_hue, 120f)
        adjust(R.string.wear_color_saturation, 0.5f)
        adjust(R.string.wear_color_brightness, 0.8f)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("#66CC66").assertExists()
        java.io.File(compose.activity.getExternalFilesDir(null), "wear-hsv-color.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        applyColor("#66CC66")
    }

    private fun applyColor(hex: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        val confirm = compose.activity.getString(R.string.confirm)
        compose.onNodeWithText(confirm).assertIsEnabled().performClick()
        compose.onNodeWithText(confirm).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(SettingsUiAction.ApplyWearThemeColor(hex)), actions) }
    }
}
