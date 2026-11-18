package org.bakasu.bakasu.ui.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
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
    private val seed = mutableStateOf(0xFFFF0000.toInt())
    private val actions = mutableListOf<SettingsUiAction>()
    private lateinit var navigator: WearNavigator

    private fun show() {
        compose.setContent {
            MaterialTheme {
                WearToastHost {
                    AppScaffold(timeText = {}) {
                        navigator = rememberWearNavigator({}, {}, {})
                        var draft by rememberSaveable { mutableStateOf<FloatArray?>(null) }
                        NavDisplay(
                            backStack = navigator.backStack,
                            onBack = navigator::back,
                            sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                            entryProvider = entryProvider {
                                entry<WearRoute.Main> {
                                    WearCustomColorPage(
                                        draft ?: seed.value.toWearColorHsv(),
                                        state.value,
                                        onHsvChange = { draft = it },
                                        onEditHex = { navigator.open(WearRoute.ColorHex(it)) },
                                        onAction = {
                                            actions += it
                                            state.value = state.value.copy(applyingThemeColor = true)
                                        },
                                        onBack = navigator::back,
                                    )
                                }
                                entry<WearRoute.ColorHex> { route ->
                                    WearColorHexPage(route.initial) {
                                        draft = it
                                        navigator.back()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun invalidHexCannotSubmitAndValidHexUpdatesThePickerBeforeApplying() {
        show()
        compose.onNodeWithText("#FF0000").performClick()
        compose.runOnIdle { assertEquals(WearRoute.ColorHex("#FF0000"), navigator.top) }
        val confirm = compose.activity.getString(R.string.confirm)
        val title = compose.activity.getString(R.string.wear_color_hex)
        compose.onNodeWithContentDescription(title).performTextReplacement("#12")
        compose.onNodeWithContentDescription(confirm).assertIsNotEnabled()
        compose.onNodeWithContentDescription(title).performTextReplacement("#ZZZZZZ")
        compose.onNodeWithContentDescription(confirm).assertIsNotEnabled()
        compose.onNodeWithContentDescription(title).performTextReplacement("#336699")
        compose.onNodeWithContentDescription(confirm).assertIsEnabled().performClick()
        compose.onNodeWithText("#336699").assertExists()
        assertSlider(R.string.wear_color_hue, 210f)
        assertSlider(R.string.wear_color_saturation, 0.67f)
        assertSlider(R.string.wear_color_brightness, 0.6f)
        compose.runOnIdle {
            assertEquals(WearRoute.Main, navigator.top)
            assertEquals(0, actions.size)
        }
        applyColor("#336699")
    }

    @Test
    fun slidersFollowTheCurrentSeedAndBackDiscardsUnconfirmedHex() {
        show()
        compose.runOnIdle { seed.value = 0xFF336699.toInt() }
        compose.onNodeWithText("#336699").performClick()
        val title = compose.activity.getString(R.string.wear_color_hex)
        compose.onNodeWithContentDescription(title).performTextReplacement("#112233")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("#336699").assertExists()
        assertSlider(R.string.wear_color_hue, 210f)
        assertSlider(R.string.wear_color_saturation, 0.67f)
        assertSlider(R.string.wear_color_brightness, 0.6f)
        compose.runOnIdle { assertEquals(WearRoute.Main, navigator.top) }
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
        applyColor("#66CC66")
    }

    private fun assertSlider(resource: Int, value: Float) {
        val label = compose.activity.getString(resource)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription(label))
        val progress = compose.onNodeWithContentDescription(label).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(value, progress.current, 0.001f)
    }

    private fun applyColor(hex: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        val confirm = compose.activity.getString(R.string.confirm)
        compose.onNodeWithText(confirm).assertIsEnabled().performClick()
        compose.onNodeWithText(confirm).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(SettingsUiAction.ApplyWearThemeColor(hex)), actions) }
    }
}
